const Database = require('better-sqlite3');
const path = require('path');
const os = require('os');
const dbPath = path.join(os.homedir(), '.runelite', 'osrs-flipping-friend', 'companion', 'flipping-friend.db');
const db = new Database(dbPath, { readonly: true, fileMustExist: false });

const events = db.prepare("SELECT observed_at, event_type, payload FROM event_log WHERE event_type IN ('BOUGHT', 'SOLD') ORDER BY observed_at ASC").all();

let inventory = {}; // itemId -> { qty: 0, cost: 0 }
let totalProfit = 0;
let profitHistory = []; // { time, profit }

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
            
            const profit = itemRev - cogs;
            totalProfit += profit;
            
            inventory[itemId].qty -= matchedQty;
            inventory[itemId].cost -= cogs;
        }
    }
    
    profitHistory.push({
        time: new Date(row.observed_at * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        profit: totalProfit
    });
});

console.log("Total Realized Profit:", totalProfit);
console.log("History length:", profitHistory.length);
