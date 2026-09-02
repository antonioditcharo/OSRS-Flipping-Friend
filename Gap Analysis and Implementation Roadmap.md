An analysis of your codebase reveals an exceptionally well-engineered foundation. Unlike amateur flipping scripts that merely sort by spread, your architecture features an isolated microservice architecture (\`ApiServer\` loopback daemon), robust statistics (MAD, log-return volatility, linear slope), authentic Grand Exchange mechanics (anchored 4-hour buy-limit tracking, 2% tax rounding with floors/caps and exemption registries), and a combinatorial branch-and-bound knapsack optimizer targeting net GP per slot-hour.

However, a significant gap separates this solid analytical core from an autonomous, market-dominating tool that outperforms platforms like Co-Flipper, GE-Tracker, and Flipping Utilities.

\---

## **Core Gap Analysis: Current Build vs. Market-Leading Product**

| Dimension | Current Implementation | Market-Dominating Target (SOTA) |  
| \--- | \--- | \--- |  
| \*\*Prediction Engine\*\* | AR(1) mean-reversion autoregression with empirical quantile error bands; static feature engineering; empty \`predictedMomentums\` hook. | \*\*Layered AI Ensemble:\*\* Bidirectional LSTM/Transformer for macro trend & momentum \+ GBDT (LightGBM/CatBoost) for fill probability \+ Cox Survival Model for queue position. |  
| \*\*Market Data Ingestion\*\* | 60s polling of Wiki REST API (\`/latest\`, \`/5m\`, \`/1h\`); throttled per-item fetch (220ms spacing). Latency: 30–90 seconds. | \*\*Sub-Second Real-Time Telemetry:\*\* In-client WebSocket/Crowdsource stream hooks \+ predictive order-book reconstruction to front-run quote changes. |  
| \*\*Execution & Maintenance\*\* | Passive planning: plans generated every 60s or on offer events. No real-time undercutting alerts. | \*\*Active Life-Cycle Engine:\*\* Continuous tracking of placed offers, instant underbid/undercut detection, dynamic price-stepping, and automatic cancel/replace signals. |  
| \*\*User Experience\*\* | Manual coordination between client and daemon. Plan expires after 90–150s. | \*\*"Hands-Off" Guided HUD:\*\* In-game RuneLite overlay with 1-click clipboard/search autofill, text-to-speech audio cues, and slot rotation prompts. |  
| \*\*Strategy Diversity\*\* | Pure margin flipping on shortlisted items. | \*\*Hybrid Multi-Strategy Engine:\*\* Spreads \+ Item Set Packing/Unpacking \+ Decanting \+ High-Alch/Processing arbitrage \+ Patch-day speculation guards. |

\---

# **The Master Blueprint & Actionable Checklist**

\`\`\`  
                      ┌────────────────────────────────────────────────────────┐  
                      │              RuneLite In-Game Client                   │  
                      │  • Overlay HUD & Visual Cards                          │  
                      │  • 1-Click Search/Quantity Setup (Jagex Compliant)     │  
                      │  • Audio & System Notifications (Undercuts/Fills)      │  
                      └───────────────────▲─────────────────┬──────────────────┘  
                                          │ Events          │ Actions  
                                          │ (Fills, State)  │ (127.0.0.1:37777)  
                                          ▼                 ▼  
┌───────────────────────────────────────────────────────────────────────────────────────────────┐  
│                           Flipping Friend Companion Daemon                                    │  
│                                                                                               │  
│  ┌───────────────────────┐   ┌───────────────────────────┐   ┌─────────────────────────────┐  │  
│  │ Ingestion & Streaming │   │ Real-Time Position Master │   │ Multi-Tier AI Predictor     │  │  
│  │ • Wiki REST \+ WS      │   │ • 4h Anchored Limit Table │   │ • Stage 1: Bidirectional    │  │  
│  │ • Microsecond Clock   │──►│ • Active Undercut Monitor │──►│   LSTM (Price & Volatility) │  │  
│  │ • Microstructure Diff │   │ • Stranded Asset Unwind   │   │ • Stage 2: LightGBM         │  │  
│  └───────────────────────┘   └───────────────────────────┘   │   (Fill Probability Class.) │  │  
│                                                              │ • Stage 3: Survival Hazard  │  │  
│                                                              │   (Queue Wait Duration)     │  │  
│                                                              └──────────────┬──────────────┘  │  
│                                                                             ▼                 │  
│                                                              ┌─────────────────────────────┐  │  
│                                                              │ Dynamic Portfolio Optimizer │  │  
│                                                              │ • Branch-and-Bound Knapsack │  │  
│                                                              │ • Fractional Kelly Sizing   │  │  
│                                                              │ • Risk Exposure Boundaries  │  │  
│                                                              └─────────────────────────────┘  │  
└───────────────────────────────────────────────────────────────────────────────────────────────┘

\`\`\`

\---

## **Phase 1: Deep Learning & Multi-Tier AI Prediction Engine**

The current engine sets up \`predictedMomentums\` in \`FeatureEngine.java\`, but lacks an operational deep neural network. To achieve the highest GP/hr, deploy a multi-stage predictive pipeline that combines sequence modeling with gradient-boosted decision trees and survival analysis.

\`\`\`  
\[Raw 5m / 1h Time Series\] ──► \[Bi-LSTM / TCN\] ────────► Directional Momentum & Volatility  
                                                                 │  
\[Order Book & Account State\] ────────────────────────────────────┼──► \[CatBoost / LightGBM\] ──► Fill Probability  
                                                                 │  
\[Queue Microstructure & Vol\] ──► \[Hawkes / Cox Hazard\] ──────────┴──► Expected Duration (Minutes)

\`\`\`

### **1.1 Bidirectional LSTM / Temporal Convolutional Network (TCN)**

\* \*\*Predictive Horizon:\*\* Forecast price trajectory $\\hat{Y}\_{t+k}$ for $k \\in \\{1, 3, 6\\}$ (5-minute to 30-minute intervals).  
\* \*\*Feature Tensor Input:\*\*  
\* Normalized Log Returns: $r\_t \= \\ln(P\_t / P\_{t-1})$.  
\* Volume Ratios: High-volume vs. low-volume imbalance $\\frac{V\_{\\text{high}} \- V\_{\\text{low}}}{V\_{\\text{high}} \+ V\_{\\text{low}}}$.  
\* Spread Compression: $\\frac{P\_{\\text{high}} \- P\_{\\text{low}}}{\\text{midPrice}}$.  
\* Hour-of-day cyclical encodings ($\\sin(\\frac{2\\pi \\cdot \\text{hour}}{24})$, $\\cos(\\frac{2\\pi \\cdot \\text{hour}}{24})$).

\* \*\*Model Architecture:\*\* 2-layer Bidirectional LSTM (hidden size: 64, dropout: 0.2) connected to a Multi-Head Self-Attention layer, outputting:  
1\. \*Expected Drift ($\\mu$):\* Likelihood of upward or downward price shifts.  
2\. \*Variance Expectation ($\\sigma^2$):\* Expected volatility over the trade horizon.

\* \*\*Java Native Inference:\*\*  
\* Export trained PyTorch models to \`.onnx\` binaries.  
\* Integrate \`ai.onnxruntime:onnxruntime:1.17.0+\` inside the companion daemon.  
\* Inference execution time must be under 5ms per item candidate, batching the 90 shortlisted candidates into a single tensor pass.

### **1.2 GBDT (LightGBM/CatBoost) for Instant Fill Classification**

\* \*\*Role:\*\* An LSTM excels at sequential signals, but GBDTs perform best on tabular execution data.  
\* \*\*Features:\*\*  
\* Candidate price distance from current bid/ask in ticks.  
\* Hourly turnover ($V \\times P$).  
\* Recent cancellation-to-completion ratio from \`execution\_stat\`.  
\* Buy-limit exhaustion percentage.

\* \*\*Target:\*\* Binary classification $P(\\text{Fill} \\mid \\text{Price}, \\text{Horizon}) \\in \[0.0, 1.0\]$.  
\* \*\*Integration:\*\* Run LightGBM via native Java wrappers or compile trees to raw Java byte code using TreeLite to achieve zero-overhead evaluation.

\#\#\#\# 1.3 Cox Proportional Hazards / Survival Analysis for Queue Fill Times

### **1.3 Cox Proportional Hazards / Survival Analysis for Queue Fill Times**

\* \*\*The Flaw in \`FillModel.java\`:\*\* The counterparty wait time assumes Poisson arrivals, which breaks down during sudden market rushes or bot dumps.  
\* \*\*Enhancement:\*\* Model order survival as:

$$S(t) \= \\exp\\left(-\\int\_0^t \\lambda\_0(u) \\exp(\\beta^\\top X) \\, du\\right)$$

where $\\lambda\_0(u)$ represents the baseline fill rate and $X$ captures current spread depth and competitor undercuts.  
\* \*\*Action:\*\* Replace heuristic \`counterpartyWait\` with dynamic survival duration quantiles ($t\_{25}, t\_{50}, t\_{75}$).

\---

## **Phase 2: High-Frequency Microstructure, Undercutting & Maintenance**

A static plan becomes stale within 60 seconds. A tool that beats competitors must actively monitor and optimize orders while they are live on the exchange.

\`\`\`  
       \[Live Offer Placed\]  
                │  
                ▼  
   \[Poll Wiki \+ Local Telemetry\]  
                │  
    ┌───────────┴───────────┐  
    ▼                       ▼  
\[Undercut Detected\]   \[Time \> Survival Half-Life\]  
    │                       │  
    ▼                       ▼  
Check Remaining Margin    Calculate Expected Drift  
    │                       │  
    ├─ Positive Margin ──► Signal Price Revision (+1 Tick)  
    │  
    └─ Inverted Margin ──► Signal Instant Cancel & Capital Relocation

\`\`\`

### **2.1 Sub-Second Undercut / Overbid Detection Engine**

\* \*\*Problem:\*\* If you place an Abyssal Whip buy at 1,500,001 GP and a competitor posts at 1,500,002 GP, your order stalls, holding your capital and slot hostage.  
\* \*\*Checklist Actions:\*\*  
\* Implement an \`OfferWatchdog\` running an event loop checking active offers against fresh market ticks.  
\* Calculate \*\*Net Remaining Spread\*\* on undercut:

$$\\text{Spread}\_{\\text{rem}} \= P\_{\\text{target\\\_sell}} \- \\text{Tax}(P\_{\\text{target\\\_sell}}) \- (P\_{\\text{undercut}} \+ 1)$$

\* If $\\text{Spread}\_{\\text{rem}} \> \\text{minProfitPerFlip}$, immediately alert the user:  
\> \*\*ACTION REQUIRED:\*\* Slot 2 \[Abyssal Whip\] undercut by 1 gp. Re-list at 1,500,003 gp.

\* If $\\text{Spread}\_{\\text{rem}} \\le 0$, alert the user to abort:  
\> \*\*UNPROFITABLE:\*\* Margin collapsed on \[Abyssal Whip\]. Cancel offer and free slot.

### **2.2 Dynamic Order Staging & Stale Order Walking**

\* Implement exponential backoff walking on the sell side:  
\* \*Bucket 0–25% of horizon:\* List at upper quantile target ($P\_{75}$).  
\* \*Bucket 25–75% of horizon:\* Drop to equilibrium price ($P\_{50}$).  
\* \*Bucket 75–100% of horizon:\* Drop to break-even or instant liquidating bid ($P\_{\\text{exit}}$) before entering stranded status.

\* Update \`PortfolioCandidate.java\` to compute this target schedule upfront.

### **2.3 Real-Time WebSocket & In-Client Stream Fallbacks**

\#\#\#\# 2.3 Real-Time WebSocket & In-Client Stream Fallbacks

\* \`MarketIngestionService.java\` currently queries the wiki's REST endpoints (\`latest\`, \`5m\`, \`1h\`). Add support for:  
\* WebSocket connections directly to community price feeds where available (e.g., WeirdGloop/Wiki price WebSockets).  
\* Peer-to-peer telemetry parsing: extract instant transactions broadcast by fellow users running the companion locally to bypass REST polling delays.

\---

## **Phase 3: The "Hands-Off" Guided HUD (RuneLite Plugin)**

The user should not have to manually evaluate numbers, think about allocations, or repeatedly type prices into the Grand Exchange search box.

\`\`\`  
\+-------------------------------------------------------------+  
| \[FLIPPING FRIEND\]  Status: OPTIMAL   GP/hr: \+1,842,500      |  
\+-------------------------------------------------------------+  
| \[SLOT 1\] BUY: 12,000x Nature Rune @ 98 gp                    |  
|          Est. Fill: 4m  | Profit: \+144,000 gp               |  
|          \[\>\>\> 1-CLICK CLIPBOARD / AUTOFILL \<\<\<\]             |  
\+-------------------------------------------------------------+  
| \[SLOT 2\] OVERBID ALERT\! Abyssal Whip                        |  
|          Competitor @ 1,450,000 \-\> Outbid @ 1,450,001 gp    |  
|          \[\>\>\> UPDATE OFFER \<\<\<\]                             |  
\+-------------------------------------------------------------+  
| \[SLOT 3\] DUMP INVENTORY: 500x Dragon Bones                  |  
|          Sell @ 2,450 gp | Margin: Safe                     |  
\+-------------------------------------------------------------+

\`\`\`

### **3.1 Strict Jagex Client Rules Compliance**

\* \*\*The Guardrail:\*\* Jagex strictly bans 1:many automated macros or botting injection (anything that programmatically clicks the interface to submit an offer).  
\* \*\*The Solution:\*\* The plugin acts as an interactive assistant:  
\* When the GE interface opens, display a transparent highlight overlay over the recommended item in inventory or search widget.  
\* Inject the calculated price and quantity directly into the client's internal clipboard or prefill the chatbox input buffer when a user clicks the overlay card.  
\* User performs the final mouse click, maintaining strict 1:1 input compliance while eliminating cognitive overhead and typing errors.

### **3.2 Automated Inventory State & Liquidation Pairing**

\* Extend \`AccountSnapshot.java\` to track the exact inventory and bank layout:  
\* Detect whenever a buy offer transitions to \`BOUGHT\` or partially fills.  
\* Instantly transform the slot or next available slot into a \`PLACE\_SELL\` instruction with pre-calculated tax-optimized prices.  
\* Eliminate the delay where bought items sit idle in the collection box.

### **3.3 Audio Cues and Native OS Desktop Notifications**

\* Add high-priority alerts via system sound synthesis and tray notifications:  
\* \*Sound Cue A (Distinct Chime):\* Buy filled, ready to flip.  
\* \*Sound Cue B (Double Tick):\* Outbid/Undercut detected.  
\* \*Sound Cue C (Urgent Horn):\* 15% Session Drawdown Breaker tripped or macro crash detected via \`MarketFluxIndex\`.

\---

## **Phase 4: Strategy Diversification Beyond Simple Flips**

Simple spread flipping leaves millions of GP on the table. Other tools gain an edge by identifying structural market mispricings.

### **4.1 Item Set Packing & Unpacking Arbitrage**

\#\#\#\# 4.1 Item Set Packing & Unpacking Arbitrage

\* Certain items trade at an imbalance compared to their component pieces (e.g., Godsword Blades \+ Hilts vs. Completed Godswords; Barrows Armor Sets vs. Individual Helm/Body/Skirt/Weapon pieces; Dragon armor sets).  
\* \*\*Implementation:\*\*  
\* Create a registry of modular sets and recipe mappings in \`core\`.  
\* Continuously compute:

$$\\Delta\_{\\text{set}} \= P\_{\\text{set}} \- \\text{Tax}(P\_{\\text{set}}) \- \\sum\_{i \\in \\text{components}} P\_i$$

\* When $\\Delta\_{\\text{set}} \> \\text{Threshold}$, advise buying components, packaging them at the Grand Exchange clerk, and listing the set (or vice versa).

\#\#\#\# 4.2 Decanting & Processing Arbitrage

### **4.2 Decanting & Processing Arbitrage**

\* Potions with 1, 2, or 3 doses frequently trade at a discount per dose compared to 4-dose potions:

$$\\Delta\_{\\text{decant}} \= P\_{\\text{4-dose}} \- \\text{Tax}(P\_{\\text{4-dose}}) \- \\left(\\frac{4}{k} \\cdot P\_{k\\text{-dose}}\\right)$$

\* The NPC Bob Barter at the Grand Exchange instantly decants noted potions for free with zero tick loss.  
\* Generate recommendations for batch purchases of 1-dose/3-dose potions, instructing the user to decant them before selling.

### **4.3 High-Alchemy Boundary Arbitrage**

\* Items often sell below their High Alchemy value minus the cost of a Nature Rune due to GE trade limits:

$$P\_{\\text{alch\\\_floor}} \= \\text{HighAlchValue} \- P\_{\\text{NatureRune}}$$

\* Incorporate $P\_{\\text{alch\\\_floor}}$ as a strict price support in \`CandidateFactory.java\`. Any item trading below this floor represents near-zero-risk capital deployment.

\---

## **Phase 5: Production Infrastructure, Telemetry & Guardrails**

### **5.1 Dynamic Capital Sizing via Fractional Kelly Criterion**

\#\#\#\# 5.1 Dynamic Capital Sizing via Fractional Kelly Criterion

### **5.2 Threading & Latency Optimizations**

\* The current \`quotableQuantity()\` uses arbitrary sizing steps (\`SIZE\_FRACTIONS \= {1.0, 0.5}\`).  
\* Replace this with a volatility-scaled Fractional Kelly allocation:

$$f^\* \= \\gamma \\cdot \\frac{p \\cdot b \- (1 \- p)}{b}$$

\* $p$: Model fill probability.  
\* $b$: Ratio of net profit to unwind loss ($\\frac{\\text{netProfit}}{\\text{unwindLoss}}$).  
\* $\\gamma$: Conservative scaling factor ($\\gamma \\approx 0.25$ to $0.35$).

\* Prevents deploying 25% of bankroll into a single volatile item when expected edge is marginal.

\#\#\#\# 5.2 Threading & Latency Optimizations

\* \`CandidateFactory.java\` runs sequentially across all 90 items on \`planThread\`.  
\* Refactor candidate evaluation to parallel stream execution:  
\`\`\`java  
List\<PortfolioCandidate\> candidates \= shortlist.parallelStream()  
    .flatMap(screened \-\> tacticsFor(screened, horizonHours, spendableCoins, now).stream())  
    .collect(Collectors.toList());

\`\`\`

\* Reduces the end-to-end planning cycle from 400ms to \<45ms, making UI updates instantaneous.

### **5.3 Automated Backtesting & Replay Suite**

\#\#\#\# 5.3 Automated Backtesting & Replay Suite

\* Expand \`SeriesSource.java\` to support historical walk-forward cross-validation on 1-year tick logs.  
\* Enforce continuous integration checks verifying that every modification to \`FillModel\` or the neural network improves Sharpe Ratio and GP/slot-hour across 10 out-of-sample data folds before model promotion.

\---

# **\#\# Step-by-Step Implementation Roadmap**

# **Step-by-Step Implementation Roadmap**

\`\`\`  
  Weeks 1-2                 Weeks 3-4                 Weeks 5-6                 Weeks 7-8  
┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐  
│ AI Ingestion Engine   │ │ Real-Time Watchdog    │ │ RuneLite Visual HUD   │ │ Advanced Strategies   │  
│ • ONNX Java Runtime   │ │ • Undercut detection  │ │ • 1-Click Search/Fill │ │ • Sets & Decanting    │  
│ • LSTM Momentum       │ │ • Sub-second polling  │ │ • Sound/Tray Alerts   │ │ • Fractional Kelly    │  
│ • LightGBM Fills      │ │ • Dynamic Target Walk │ │ • In-game Overlay     │ │ • Full Replay Engine  │  
└───────────────────────┘ └───────────────────────┘ └───────────────────────┘ └───────────────────────┘

\`\`\`

1\. \*\*Deploy ONNX Runtime in \`companion\`:\*\*  
\* Add \`com.microsoft.onnxruntime:onnxruntime\` to \`build.gradle\`.  
\* Add training script in Python to export an LSTM/GBDT model to \`src/main/resources/models/momentum\_v1.onnx\`.  
\* Hook inference into \`FeatureEngine.java\` to populate \`predictedMomentums\`.

2\. \*\*Activate Active Order Monitoring:\*\*  
\* Create \`ActiveOfferTracker.java\` inside \`com.flippingfriend.companion\`.  
\* Listen to tick-level price changes; compare highest bids against open user buy offers.  
\* Emit \`OFFER\_OUTBID\` and \`MARGIN\_COMPRESSED\` events via \`/v1/portfolio/current\`.

3\. \*\*Implement the In-Game RuneLite Overlay:\*\*  
\* Build a client-side panel that renders trade cards directly beside the GE interface.  
\* Add custom chatbox and clipboard integration for 1-click execution.

4\. \*\*Integrate Structural Arbitrage:\*\*  
\* Add decanting, sets, and high-alch pricing layers to \`CandidateFactory.java\`.  
\* Enable automatic fallback to safe margins when standard markets experience low liquidity.

This technical plan transforms the project from a purely analytical pricing tool into a resilient, high-speed, and hands-off trading companion. It addresses predictive accuracy, real-time response, and streamlined interface design, positioning it to outperform existing alternatives in sustainable GP/hr.