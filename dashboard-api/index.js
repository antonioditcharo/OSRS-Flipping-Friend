const express = require('express');
const dotenv = require('dotenv');
const Database = require('better-sqlite3');
const path = require('path');
const os = require('os');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3001;

// Same origin only. `cors()` with no options sets Access-Control-Allow-Origin: *, which on a
// service that serves this account's complete trade history means any page in any tab could read
// it. Nothing legitimate here is cross-origin: the UI is served from this same process.
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// --- access control -------------------------------------------------------------------
//
// jsonwebtoken was a declared dependency and no route was authenticated. Combined with
// app.listen(PORT) binding every interface, any host that could reach port 3001 could read the
// lot. This shares the companion's token rather than inventing a second secret.
const fs = require('fs');
const TOKEN_HEADER = 'x-flipping-friend-token';

function companionToken() {
    if (process.env.FLIPPING_FRIEND_TOKEN) {
        return process.env.FLIPPING_FRIEND_TOKEN.trim();
    }
    try {
        const file = path.join(os.homedir(), '.runelite', 'osrs-flipping-friend', 'companion',
            'companion.properties');
        const lines = fs.readFileSync(file, 'utf8').split(String.fromCharCode(10));
        const line = lines.map(l => l.trim()).find(l => l.startsWith('token='));
        return line ? line.slice('token='.length).trim() : null;
    } catch (e) {
        return null;
    }
}

const API_TOKEN = companionToken();

// Fails closed. A service that believes it is authenticated but is not is worse than one that is
// plainly refusing, and this one holds every trade the account has ever made.
app.use('/api', (req, res, next) => {
    if (!API_TOKEN) {
        return res.status(503).json({ error: 'No companion token available; refusing to serve.' });
    }
    if (req.get(TOKEN_HEADER) !== API_TOKEN) {
        return res.status(401).json({ error: 'Missing or invalid token.' });
    }
    next();
});

// Path to the plugin's SQLite database
const dbPath = path.join(os.homedir(), '.runelite', 'osrs-flipping-friend', 'companion', 'flipping-friend.db');
let db;
try {
    db = new Database(dbPath, { readonly: true, fileMustExist: false });
    console.log(`Connected to database at ${dbPath}`);
} catch (err) {
    console.error(`Failed to connect to database: ${err.message}`);
}

const cache = {
    performance: { data: null, lastFetch: 0 },
    itemsPerformance: { data: null, lastFetch: 0 }
};

app.get('/api/performance', (req, res) => {
    if (!db) return res.status(500).json({ error: 'Database not connected' });
    
    if (Date.now() - cache.performance.lastFetch < 5000 && cache.performance.data) {
        return res.json(cache.performance.data);
    }

    try {
        const stats = db.prepare('SELECT item_id, completed, observed, fill_minutes, predicted_minutes, paired_fill_minutes, paired_completed FROM execution_stat').all();
        
        let totalObserved = 0;
        let totalCompleted = 0;
        let totalAiTime = 0;
        let totalRealTime = 0;
        
        stats.forEach(row => {
            totalObserved += row.observed;
            totalCompleted += row.completed;
            
            if (row.paired_completed > 0 && row.predicted_minutes > 0) {
                totalAiTime += row.predicted_minutes;
                totalRealTime += row.paired_fill_minutes;
            }
        });

        let totalRealizedProfit = 0;
        
        // Compute total realized profit
        try {
            const boughtEvents = db.prepare("SELECT payload FROM event_log WHERE event_type='BOUGHT'").all();
            const soldEvents = db.prepare("SELECT payload FROM event_log WHERE event_type='SOLD'").all();
            let inventory = {};
            boughtEvents.forEach(row => {
                const ev = JSON.parse(row.payload);
                if (!inventory[ev.itemId]) inventory[ev.itemId] = { qty: 0, cost: 0 };
                inventory[ev.itemId].qty += ev.filledQuantity;
                inventory[ev.itemId].cost += ev.spent;
            });
            soldEvents.forEach(row => {
                const ev = JSON.parse(row.payload);
                if (inventory[ev.itemId] && inventory[ev.itemId].qty > 0) {
                    const avgCost = inventory[ev.itemId].cost / inventory[ev.itemId].qty;
                    const matchedQty = Math.min(inventory[ev.itemId].qty, ev.filledQuantity);
                    const cogs = avgCost * matchedQty;
                    const itemRev = (ev.spent / ev.filledQuantity) * matchedQty;
                    // Net of Grand Exchange tax. This figure was gross, with no tax term anywhere,
                    // so every completed flip was overstated by roughly 2% of its sale value - which
                    // on a typical 2% margin is close to the entire profit, on the one number anyone
                    // would judge the system by. The tax is carried on the event, computed by the
                    // same TaxCalculator the engine prices trades with, rather than reimplemented
                    // here where the two copies would quietly stop agreeing.
                    const itemTax = ((ev.tax || 0) / ev.filledQuantity) * matchedQty;
                    totalRealizedProfit += (itemRev - itemTax - cogs);
                    inventory[ev.itemId].qty -= matchedQty;
                    inventory[ev.itemId].cost -= cogs;
                }
            });
        } catch (e) { console.error(e); }

        const responseData = {
            overallWinRate: totalObserved > 0 ? (totalCompleted / totalObserved) : 0,
            aiTimeVsRealTimeRatio: totalAiTime > 0 ? (totalRealTime / totalAiTime) : 0,
            totalRealizedProfit: totalRealizedProfit
        };
        cache.performance.data = responseData;
        cache.performance.lastFetch = Date.now();
        res.json(responseData);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/ai-suggestions', (req, res) => {
    if (!db) return res.status(500).json({ error: 'Database not connected' });

    try {
        const planRow = db.prepare('SELECT payload FROM portfolio_plan ORDER BY created_at DESC LIMIT 1').get();
        if (!planRow || !planRow.payload) {
            return res.json({ suggestions: [] });
        }

        const plan = JSON.parse(planRow.payload);
        const allocations = plan.bench || plan.allocations || [];
        
        const formatGp = (gp) => {
            if (gp >= 1000000) return (gp / 1000000).toFixed(1) + 'm';
            if (gp >= 1000) return (gp / 1000).toFixed(1) + 'k';
            return gp.toString();
        };

        const formattedSuggestions = allocations.map(alloc => {
            const c = alloc.candidate;
            if (!c) return null;

            let confidence = c.buyFillProbability * c.sellFillProbability;
            if (c.displayBuyFillProbability && c.displayBuyFillProbability > 0) {
                confidence = c.displayBuyFillProbability * c.sellFillProbability;
            }

            return {
                itemId: c.itemId,
                item: c.itemName || `Item ${c.itemId}`,
                // A real zero rendered as 80%. Never substitute a default that looks like a
                // measurement: null becomes an em dash in the UI, which is honest about not knowing.
                confidence: confidence > 0 ? confidence : null,
                expectedProfit: formatGp(c.netProfit || 0),
                rawExpectedProfit: c.netProfit || 0,
                reason: c.group || null
            };
        }).filter(Boolean).slice(0, 8); // Return top 8 suggestions

        res.json({
            suggestions: formattedSuggestions
        });
    } catch (err) {
        console.error("Error fetching suggestions:", err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/history/profit', (req, res) => {
    if (!db) return res.status(500).json({ error: 'Database not connected' });

    try {
        // Fetch the last 100 plans
        const plans = db.prepare('SELECT created_at, payload FROM portfolio_plan ORDER BY created_at ASC LIMIT 100').all();
        const history = plans.map(row => {
            const plan = JSON.parse(row.payload);
            const allocations = plan.bench || plan.allocations || [];
            let totalExpectedProfit = 0;
            allocations.forEach(alloc => {
                const c = alloc.candidate;
                if (c && c.netProfit) {
                    totalExpectedProfit += c.netProfit;
                }
            });
            const date = new Date(row.created_at * 1000);
            return {
                time: date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                timestamp: row.created_at,
                expectedProfit: totalExpectedProfit
            };
        });
        res.json({ profitHistory: history });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/history/actual-profit', (req, res) => {
    if (!db) return res.status(500).json({ error: 'Database not connected' });

    try {
        const events = db.prepare("SELECT observed_at, event_type, payload FROM event_log WHERE event_type IN ('BOUGHT', 'SOLD') ORDER BY observed_at ASC").all();

        let inventory = {};
        let totalProfit = 0;
        let profitHistory = [];

        events.forEach(row => {
            const ev = JSON.parse(row.payload);
            const itemId = ev.itemId;
            if (!inventory[itemId]) {
                inventory[itemId] = { qty: 0, cost: 0 };
            }
            
            if (row.event_type === 'BOUGHT') {
                inventory[itemId].qty += ev.filledQuantity;
                inventory[itemId].cost += ev.spent;
            } else if (row.event_type === 'SOLD') {
                const soldQty = ev.filledQuantity;
                const rev = ev.spent;
                
                if (inventory[itemId].qty > 0) {
                    const avgCost = inventory[itemId].cost / inventory[itemId].qty;
                    const matchedQty = Math.min(inventory[itemId].qty, soldQty);
                    const cogs = avgCost * matchedQty;
                    const itemRev = (rev / soldQty) * matchedQty;
                    
                    const itemTax = ((ev.tax || 0) / ev.filledQuantity) * matchedQty;
                    const profit = itemRev - itemTax - cogs;
                    totalProfit += profit;
                    
                    inventory[itemId].qty -= matchedQty;
                    inventory[itemId].cost -= cogs;
                }
            }
            
            profitHistory.push({
                time: new Date(row.observed_at * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                timestamp: row.observed_at,
                actualProfit: totalProfit
            });
        });

        // To avoid returning thousands of points, we sample/aggregate the history to 100 points
        if (profitHistory.length > 100) {
            const step = Math.ceil(profitHistory.length / 100);
            profitHistory = profitHistory.filter((_, i) => i % step === 0);
        }

        res.json({ actualProfitHistory: profitHistory });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/history/gates', (req, res) => {
    if (!db) return res.status(500).json({ error: 'Database not connected' });

    try {
        // Fetch gate history
        const gates = db.prepare('SELECT evaluated_at, gate, passed, measured FROM gate_result ORDER BY evaluated_at ASC LIMIT 200').all();
        const gateHistory = gates.map(row => {
            const date = new Date(row.evaluated_at * 1000);
            let measuredVal = 0;
            // parse things like "0.74 > 0.60" to get the first number
            const match = row.measured.match(/([\d\.]+)/);
            if (match) {
                measuredVal = parseFloat(match[1]);
            }
            
            return {
                time: date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                timestamp: row.evaluated_at,
                gate: row.gate,
                passed: row.passed === 1,
                measured: measuredVal
            };
        });
        res.json({ gateHistory });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/items/performance', (req, res) => {
    if (!db) return res.status(500).json({ error: 'Database not connected' });

    if (Date.now() - cache.itemsPerformance.lastFetch < 5000 && cache.itemsPerformance.data) {
        return res.json(cache.itemsPerformance.data);
    }

    try {
        const stats = db.prepare('SELECT item_id, completed, observed, fill_minutes, predicted_minutes, paired_fill_minutes, paired_completed FROM execution_stat').all();
        
        // Fetch item names from recent portfolio plans
        const recentPlans = db.prepare('SELECT payload FROM portfolio_plan ORDER BY created_at DESC LIMIT 500').all();
        const itemNameMap = {};
        recentPlans.forEach(row => {
            const plan = JSON.parse(row.payload);
            const allocations = plan.bench || plan.allocations || [];
            allocations.forEach(alloc => {
                if (alloc.candidate && alloc.candidate.itemId && alloc.candidate.itemName) {
                    itemNameMap[alloc.candidate.itemId] = alloc.candidate.itemName;
                }
            });
        });

        // Compute per-item actual profit and ROI
        const events = db.prepare("SELECT event_type, payload FROM event_log WHERE event_type IN ('BOUGHT', 'SOLD')").all();
        let inventory = {};
        let itemProfit = {};
        let itemInvestment = {};

        events.forEach(row => {
            const ev = JSON.parse(row.payload);
            const itemId = ev.itemId;
            
            if (!inventory[itemId]) inventory[itemId] = { qty: 0, cost: 0 };
            if (!itemProfit[itemId]) itemProfit[itemId] = 0;
            if (!itemInvestment[itemId]) itemInvestment[itemId] = 0;
            
            if (row.event_type === 'BOUGHT') {
                inventory[itemId].qty += ev.filledQuantity;
                inventory[itemId].cost += ev.spent;
                itemInvestment[itemId] += ev.spent;
            } else if (row.event_type === 'SOLD') {
                const soldQty = ev.filledQuantity;
                const rev = ev.spent;
                
                if (inventory[itemId].qty > 0) {
                    const avgCost = inventory[itemId].cost / inventory[itemId].qty;
                    const matchedQty = Math.min(inventory[itemId].qty, soldQty);
                    const cogs = avgCost * matchedQty;
                    const itemRev = (rev / soldQty) * matchedQty;
                    const itemTax = ((ev.tax || 0) / soldQty) * matchedQty;

                    itemProfit[itemId] += (itemRev - itemTax - cogs);
                    
                    inventory[itemId].qty -= matchedQty;
                    inventory[itemId].cost -= cogs;
                }
            }
        });

        const items = stats.map(row => {
            const p = itemProfit[row.item_id] || 0;
            const inv = itemInvestment[row.item_id] || 0;
            const roi = inv > 0 ? (p / inv) : 0;

            return {
                itemId: row.item_id,
                itemName: itemNameMap[row.item_id] || `Item ${row.item_id}`,
                observed: row.observed,
                completed: row.completed,
                winRate: row.observed > 0 ? (row.completed / row.observed) : 0,
                aiPredictedTime: row.paired_completed > 0 ? (row.predicted_minutes / row.paired_completed) : 0,
                realFillTime: row.completed > 0 ? (row.fill_minutes / row.completed) : 0,
                profit: p,
                roi: roi
            };
        }).sort((a, b) => b.profit - a.profit).slice(0, 50); // Sort by highest profit instead of most observed

        const responseData = { items };
        cache.itemsPerformance.data = responseData;
        cache.itemsPerformance.lastFetch = Date.now();
        res.json(responseData);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Loopback only. Everything that legitimately reads this runs on the same machine, and binding
// every interface put the account's full trade history on the local network.
app.listen(PORT, '127.0.0.1', () => {
    console.log(`API running on http://127.0.0.1:${PORT}`);
});
