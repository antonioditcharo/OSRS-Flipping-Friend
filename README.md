# OSRS Flipping Friend

A RuneLite plugin that tells you what to flip on the Grand Exchange, walks you through placing each
offer, and decides when to sell.

It assumes you have never flipped before. Every number it shows is explained in plain English, and
profit is always quoted after the 2% sale tax — not before it, which is the difference between a
profitable flip and a break-even one on most items.

**It never clicks or types for you.** It draws highlights and shows numbers. You place every offer
yourself.

---

## What it does

- **Picks one trade at a time.** Item, price, quantity, and why. Not a list to pick through.
- **Works out what you can afford by itself** — coins in your inventory, bank and collection box,
  free Grand Exchange slots, members or free-to-play. It never asks.
- **Sells what you already own.** Items sitting in your bank get sell advice too.
- **Highlights the next click** in the Grand Exchange, with the exact number to type shown beside it.
- **Tracks every position** with a live price chart showing what you paid, your target and your stop.
- **Decides when to sell**, weighing the target price, whether momentum has turned, how long you have
  held, and a hard stop loss.
- **Learns from your own trades.** It records what it predicted against what actually happened and
  corrects itself per item.
- **Three risk levels** — Low, Moderate (default), High.

## How it decides

Most flipping tools rank items by margin. Margin alone is close to useless: the biggest margins on
the Grand Exchange belong to items that barely trade, or to a single fake offer placed to bait
exactly this kind of tool. This one screens differently.

| | What it does | Why |
|---|---|---|
| **Exact tax** | 2% floored per item, capped at 5m, sub-50gp and the full exempt list | A 2% error is bigger than the entire margin on most high-volume items |
| **Fill probability** | Estimates whether an offer at a given price will actually complete, and how long it takes | A 15% margin that never fills is worth less than a 1% margin that fills in four minutes |
| **Outlier rejection** | Median/MAD bands, staleness scaled by how busy the item is | Throws out manufactured spreads and dead items whose quotes have drifted apart |
| **Regime detection** | Fast/slow moving averages against the item's own noise | Stops it buying falling knives |
| **Price optimisation** | Evaluates a grid of prices around the spread | The best margin and the best profit-per-hour are usually different prices |
| **Honest expectation** | Scores upside minus what the losing cases cost | Scoring the upside alone recommends negative-expectation trades |
| **Size from the fill model** | The order is sized so it fills in time with the confidence your risk level demands | Sizing first and checking later makes the two rules fight each other |
| **Self-calibration** | Per-item correction learned from your journal, shrunk towards a neutral prior | How fast offers fill depends on when *you* play — not on an average across thousands of users |
| **Price anchoring** | The spot quote is checked against the 5-minute volume-weighted average and replaced if it disagrees by more than 3% | `/latest` is a single transaction, so one misclick can set it |
| **Your checking habit** | How often you return to the Exchange sets the fill window, and therefore order size and the stale-offer threshold | A four-minute flip is worthless if you only look once an hour |
| **Hour-of-day liquidity** | Learns each item's own daily trading rhythm and adjusts fill estimates for the hour you are actually trading in | The gap between an item's busiest and quietest hour is routinely close to threefold |
| **Fortnight context** | Where the price sits in its 15-day range, and whether it is jumpier than its own norm | An item can look calm across a day while halfway through a week-long slide |
| **Structural breaks** | Detects a recent shift in price level and stops trusting the history before it | After a game update, everything the model knows describes an item that no longer exists |
| **Shadow trading** | Continuously paper-trades every candidate — including rejected ones — against live prices | Real flips give a dozen observations a session, and only ever for trades that were taken |

### Always learning

The plugin paper-trades in the background whenever it is running. Every scoring pass, each candidate
it evaluated is opened as a notional trade and watched against real subsequent prices. Nothing is
placed in game and no coins move.

This exists to fix two problems with learning from real flips alone:

- **Volume.** A session produces perhaps a dozen completed flips. Shadow trading produces hundreds of
  resolved observations an hour, which is the difference between a correction that becomes useful in
  weeks and one that becomes useful today.
- **Counterfactuals.** Real trading only ever reveals the outcome of trades you *took*. A filter that
  rejects profitable trades is invisible no matter how long you trade. Rejected candidates are
  tracked too, so the **Learning** panel can report whether approved trades actually beat rejected
  ones — and say so plainly when they do not.

Simulated and real evidence are kept in separate channels, deliberately. A paper trade knows whether
the market *reached* a price, which is real information about price behaviour. It cannot know whether
your offer would have been at the front of the queue when it got there, so its fill times are
optimistic. Only real flips inform the speed estimate; paper trades inform price behaviour, weighted
well below real ones. Conflating the two would teach the model to promise fills it cannot deliver.

Turn it off with *Keep learning in the background* if you would rather it did not.

### The learned model

Alongside the analytical engine there is a model that learns from outcomes: online logistic
regression over sixteen engineered features (margin, volume, volatility, regime, range position,
hour-of-day liquidity, order size against limit and volume, plus interaction terms).

Three properties matter:

- **It generalises.** Because it learns over *features* rather than item ids, what it learns from
  cannonballs applies to coconuts. The per-item correction it replaces had nothing to say about an
  item never traded before.
- **It never stops.** Every resolved observation is one gradient step. There is no training run and
  no retraining; the weights are the memory, they persist, and the model that loads tomorrow is
  better informed than the one that shut down today. The squared-gradient average is *decayed*
  rather than accumulated for exactly this reason — plain AdaGrad decays its step size towards zero
  and would freeze after a few hundred thousand observations, unable to adapt when the market moves.
- **Influence is earned.** Its weight in the final score is its own measured skill against the
  baseline of guessing the base rate, recomputed continuously. A model that learns nothing gets zero
  weight and the plugin behaves exactly as it did before. A model that stops being right loses
  influence again.

It also **explores**: about one suggestion in ten is a close runner-up rather than the leader.
Always taking the top pick means poorly-rated trades are never attempted, so the model could never
discover it was wrong about them.

**Why not a neural network.** With sixteen features and observations in the thousands, a linear
model in feature space is the right capacity — enough to capture the structure that exists, not
enough to memorise noise. A network would fit the noise beautifully, generalise worse, and cost far
more per prediction on a thread that also has a game to keep responsive. The interaction terms buy
most of what extra depth would.

The **Learning** panel shows how many trades it has trained on, what influence it has earned, and
which factors it now weights most.

### The always-on learner

The companion **is** the always-on learner. It runs beside the client, studies the market
continuously, and keeps learning for as long as it is up — which closes the two holes an
in-session learner cannot:

- **Volume.** A session yields a few dozen resolved observations. Running continuously yields
  thousands.
- **Bias.** The plugin only observes the hours you play, which is a poor sample for a model whose
  job includes working out *what time of day an item trades*.

It runs the shipping model — the same `Scorer`, `ManipulationFilter`, `FeatureEngine` and
`FillModel` — not a copy. A learner trained on a different model than the one that ships would
produce corrections that are worse than useless.

It learns **market behaviour only**, written to a shared store every account can read. How fast your
offers fill is a property of you, and a background learner has no queue to wait in, so execution
stays with the plugin and is informed only by real flips.

`1 - First Time Setup` registers it to start with Windows; `tools\uninstall-companion.ps1` removes
that and stops it. Footprint: a few requests a second to the same public API while it is warming a
shortlist, close to nothing once warm, and a few MB on disk. Nothing touches the game or the account.

> A separate `3 - Background Learning` launcher used to install a second headless daemon for this.
> Its build task and its classes were removed on 2026-09-02 and the launcher went with them on
> 2026-09-04.

### Sizing at scale

Measured against the live market at a 100M bankroll, capital is the binding constraint on only about
**25 of the 441** liquid items worth trading — buy limits bind on 139 and volume on 277. So above a
threshold (roughly four times the cost of a full-limit position) the Kelly stake is dropped and the
buy limit becomes the ceiling. Staking a fraction of a bankroll the trade could never have exhausted
just leaves limit unused, and unused limit is throughput that cannot be recovered later. Below that
threshold the conservative sizing still applies, because there it is what keeps an account solvent.

### Where the data comes from

All of it is the [OSRS Wiki real-time prices API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices),
and deliberately so. That feed is collected from the game itself, and essentially every third-party
OSRS price site re-serves it — scraping them would deliver the same numbers with more latency, more
fragility and someone's terms of service to worry about. Jagex's own item API is an independent
source but publishes lagging daily *guide* prices rather than real transactions, so it is strictly
worse than what we already have.

The depth was the part being wasted. The same endpoint serves four resolutions, and the plugin now
uses two of them:

| Timestep | Span | Used for |
|---|---|---|
| `5m` | ~1.3 days | Spreads, fill probability, price optimisation |
| `1h` | ~15 days | Hour-of-day liquidity, price range position, structural breaks |
| `6h` | ~91 days | *available, not yet used* |
| `24h` | ~364 days | *available, not yet used* |

Run `gradlew backtest` to replay real price history through the same decision code and see how the
profiles compare. Its numbers are optimistic — it can see which prices traded but not the queue
behind them — so use it to compare settings, not to predict profit. `gradlew backtest -PnoContext=true`
re-runs it with the fortnight model switched off, against the same cached data, to measure what that
model is actually contributing.

---

## Installing

**If you just want to use it, open [START HERE.md](START%20HERE.md) instead — two double-clicks and
you are done.** The rest of this section explains what those clicks actually do.

You need RuneLite installed and run at least once. Everything below reuses the client it already
downloaded; nothing extra gets installed.

### Why it is not simply a Plugin Hub install

RuneLite loads custom plugins from `~/.runelite/sideloaded-plugins` only in developer mode, and it
deliberately refuses developer mode when its own launcher started it:

```java
options.has("developer-mode") && RuneLiteProperties.getLauncherVersion() == null
```

The launcher always sets that property, so the client has to be started directly. `tools\run-dev.ps1`
does that using the jars already in `~/.runelite/repository2`.

### The Jagex account step

Starting the client directly means the Jagex Launcher's login tokens are not in the environment.
RuneLite's documented answer is to save them once:

1. Run `RuneLite.exe --configure` (launcher 2.6.3 or newer).
2. Add `--insecure-write-credentials` to **Client arguments**.
3. Launch once from the Jagex Launcher and log in.
4. Remove the flag again.

This writes `~/.runelite/credentials.properties`. **That file can log into your account without your
password. Do not share it.** `tools\cleanup.ps1` deletes it; sessions can also be revoked via "End
sessions" on runescape.com. `1 - First Time Setup` walks through all of this and checks it worked.

If you log in with an old-style username and password, none of this applies.

### The scripts

| Script | What it does |
|---|---|
| `1 - First Time Setup.bat` | The login step, builds and installs, adds a desktop shortcut |
| `2 - Start Flipping Friend.bat` | Rebuilds, reinstalls and launches — the everyday one |
| `tools\install.ps1` | Build and copy the jar to `sideloaded-plugins` |
| `tools\run-dev.ps1` | Launch the client in developer mode |
| `tools\cleanup.ps1` | Remove the saved login |

`run-dev.ps1` also fetches the full RuneLite API jar on first run. The launcher ships a stripped
version with the `gameval` classes removed — harmless for plugins, since those are compile-time
constants that get inlined, but RuneLite's own Developer Tools plugin loads some of them by name and
fails without it.

## Using it

Open the sidebar panel. It tells you one thing to do at a time, in this order:

1. **Collect** anything finished — it is holding a slot and coins hostage.
2. **Fix** an offer the market has moved past, which would otherwise sit there forever.
3. **Sell** something you are holding.
4. **Buy** something new.

Open the Grand Exchange and it highlights what to click, step by step, with the price and quantity
shown next to it. Each number has a **copy** button in the panel.

"Nothing worth trading right now" is a real answer, not a failure. The plugin would rather say
nothing than suggest a bad trade.

### Settings worth knowing

| Setting | Default | Notes |
|---|---|---|
| Account type | Detect automatically | Force free-to-play to limit buys to F2P items and 3 slots |
| Risk level | Moderate | Low trades smaller and faster; High holds longer for bigger margins |
| How often you check the GE | Every 15 minutes | Sets the fill window and therefore order size |
| Risk guards | per profile | Each level also caps how near its fortnight high an item may be bought, and how much jumpier than normal it may be |
| Spending cap | 0 (no cap) | Limits how much of your bank it plans around |
| Minimum profit per flip | 5,000 | Filters out trades not worth the clicks |
| Count bank coins | on | The Grand Exchange has drawn buy offers from the bank since 2023 |
| Also sell items I already own | **off** | Your bank is gear, not flipping stock. Items the plugin buys are always tracked regardless |
| Learn from my trades | on | The self-calibration described above |

### What is and is not tracked

Only what the plugin buys for you becomes a position. Opening your bank does not turn its contents
into things to sell — that would be both wrong and alarming. Bank contents are still read, because a
position the plugin bought and then banked has to stay sellable, and because bank coins count towards
what you can afford. Turn on *Also sell items I already own* if you genuinely want the bank treated
as stock.

---

## Notes

- **Ironman accounts** can only buy bonds on the Grand Exchange, so the plugin says so and stops.
- **Free-to-play** is supported: three slots instead of eight, and members' items are excluded from
  buy suggestions. Selling is deliberately *not* restricted, because the game does not restrict it
  either — a lapsed member holding members' gear can sell all of it, and the plugin says so. Set
  **Account type** to Free-to-play to force this regardless of which world you are on.
- **Data** comes from the [OSRS Wiki real-time prices API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices).
  Nothing is sent anywhere. Your trade history stays in `~/.runelite/osrs-flipping-friend/`, scoped
  per account.
- **When RuneLite updates**, bump `ext.runeLiteVersion` in `build.gradle` and re-run
  `tools\install.ps1`. `run-dev.ps1` warns you when the versions have drifted apart.

## Development

```powershell
.\gradlew.bat test        # unit tests
.\gradlew.bat backtest    # replay real history through the decision code
.\gradlew.bat jar         # build only
```

Licensed under BSD-2-Clause.
