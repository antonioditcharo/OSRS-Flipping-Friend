# Plan: 2M gp/hour on a 100M bankroll

## The finding that reframes everything

I measured what actually limits trade size at a 100M bank, across every liquid item
(≥500 trades/hour) with a positive post-tax margin:

| What binds the trade size | Items |
|---|---|
| Volume | 277 |
| Buy limit | 139 |
| **Capital** | **25** |

**At 100M you are almost never short of money.** You are short of buy limit, volume, and above all
Grand Exchange slots. That inverts the design the plugin currently has.

Every sizing rule in the engine today — fractional Kelly, `maxCapitalFraction`, the fill-probability
ceiling — exists to stop a trade consuming too much of the bank. At 10M those rules are the thing
keeping you solvent. At 100M they are throttling trades that were never going to be capital-limited
in the first place, and the throttle costs real gold.

### What 2M/hour actually requires

Eight slots, so 250k per slot-hour. Measured against the live market, the best liquid items right
now return, per 4-hour buy-limit window:

| Item | Throughput per window | Bound by |
|---|---|---|
| Araxyte venom sac | 2,321,000 | buy limit |
| Irit potion (unf) | 1,834,000 | volume |
| Triangle sandwich | 726,600 | volume |
| Super antifire potion(4) | 702,463 | capital |

The best single item is worth ~580k/hour from **one slot**. Forty-four items currently clear the
250k/slot-hour bar. Filling eight slots with the best available and ignoring execution friction gives
a theoretical ceiling around **7.5M/hour**.

So 2M/hour means capturing roughly **27% of the theoretical maximum**. That is a demanding but
entirely reachable bar — the missing 73% is queue competition, slippage, slots idling between
rotations, and the simple fact that the best item is not always available. It is not a fantasy
number, and it does not require beating the market: it requires not wasting the slots.

**Where we are now.** The backtest puts Moderate at ~96k/slot-hour, which is 768k/hour across eight
slots — and that figure is already optimistic because the simulation cannot see queue position. The
gap to 2M/hour is roughly **2.6×**, and the analysis above says most of it is recoverable from how
slots are allocated rather than from better price prediction.

---

## Phase 1 — Stop wasting slots (the largest single win)

The engine currently answers "what is the best next trade?". At 100M the question that matters is
"what is the best *set of eight* trades, and which slot should move next?". Those have different
answers, and the difference is most of the 2.6×.

1. **Throughput objective.** Rank candidates by `net margin × min(buy limit, fillable volume)` — the
   gold a slot can actually produce per window — instead of by margin percentage. An item whose
   trade size is capped by its buy limit should be sized *to that limit*, not to a Kelly fraction of
   a bankroll that is not the constraint.
2. **Bankroll-aware sizing.** Make the capital rules bind only when capital is genuinely scarce.
   Below roughly 4× the cost of a full-limit position, keep today's conservative sizing; above it,
   let the buy limit be the ceiling.
3. **Slot portfolio allocation.** Plan all free slots together against distinct items, maximising
   total throughput subject to slots, limits and cash. The player still sees one instruction at a
   time — that decision stands — but the instruction comes from a plan that knows about the other
   seven slots.
4. **Buy-limit rotation.** Track when each item's 4-hour window will exhaust and pre-plan the slot's
   next item, so a slot never sits idle waiting for a limit to reset. `BuyLimitTracker` already
   records the window; nothing currently acts on it.

*Expected contribution: the bulk of the gap. Slots idling and under-sized orders are pure loss.*

## Phase 2 — Fix the defects already found

These are known, verified, and two of them are currently lying to the user.

1. **Concentration risk.** The buy path never checks what is already held, and `spendableCoins` does
   not subtract coins committed to open buy offers. At 100M across 8 slots this can over-commit.
2. **`Conservative pricing`** is a visible setting that no code reads. Either implement it or remove
   it; a setting that does nothing is worse than an absent one.
3. **`undercutAggression`** is a per-profile parameter nothing reads. Same choice.
4. **Correlated-group exposure cap** is described in the plan document and does not exist. At eight
   simultaneous slots this matters: eight positions in one market segment is one position.

## Phase 3 — Retune at the right scale

Everything so far was tuned against a 50M simulated bank on 40 sampled items.

1. Re-run the backtester at **100M with 8 slots**, and with the item universe drawn from the
   throughput ranking rather than a liquidity spread.
2. Retune all three profiles against that, using the existing `-PnoContext` style A/B switch to
   attribute each change.
3. Resolve **High risk**, which remains strictly dominated by Moderate (45.7k vs 95.9k per
   slot-hour). Under a throughput objective its longer holds may finally justify themselves; if they
   still do not, High should be redefined rather than left as a worse Moderate.
4. Extend history to the **6h (91-day)** and **24h (364-day)** timesteps, both already available from
   the same endpoint and both still unused. Weekly and seasonal cycles are invisible in 15 days.

*Target: ≥2.6M/hour in simulation, since the simulation is optimistic and needs a discount before it
means 2M live.*

## Phase 4 — Always-on learning

### Why it can be a separate process

The model code has no RuneLite dependency. The backtester already runs the real `Scorer`,
`ManipulationFilter`, `FeatureEngine` and `FillModel` headlessly. A learning daemon is therefore a
new entry point over existing code, not a second implementation — which matters, because a learner
that trained a *different* model than the one that ships would be worse than no learner.

### Architecture

```
  ┌──────────────────────────────┐        ┌────────────────────────────┐
  │  Learning daemon (headless)  │        │  RuneLite plugin           │
  │  runs at login, always on    │        │  runs when you play        │
  │                              │        │                            │
  │  polls wiki → screens →      │        │  reads shared learning     │
  │  paper-trades → resolves     │        │  + its own execution       │
  └──────────────┬───────────────┘        └─────────────┬──────────────┘
                 │                                      │
                 ▼                                      ▼
        market-cache/learning/           accounts/<profile>/
        (shared, market-level)           (private, execution-level)
```

**The split is deliberate and load-bearing.** Two different things are being learned:

- **Market behaviour** — whether an item's spread persists, whether its price reverts, whether the
  filters are rejecting profitable trades. None of that depends on who is playing, so the daemon can
  learn it continuously and share it across every account.
- **Execution** — how fast *your* offers fill, given your worlds, your timing, your queue position.
  That is a property of you. It stays account-scoped, and only real flips inform it.

Conflating them is the trap: a daemon has no queue, so if its fill times fed the speed model the
plugin would start promising fills it cannot deliver. The existing two-channel `Calibrator` already
enforces this separation, and the daemon plugs into the paper channel only.

### Work

1. **`LearningDaemon` main class** — poll, screen, score, paper-trade, resolve, persist. Reuses the
   entire existing pipeline.
2. **Shared learning store** — market-level evidence written where every account can read it, with
   file locking so daemon and plugin can both touch it safely.
3. **Plugin reads it on startup and periodically**, merging shared market knowledge with its own
   private execution calibration.
4. **Windows install** — `tools/install-daemon.ps1` registering a scheduled task at logon, plus
   `tools/uninstall-daemon.ps1`. Target footprint: one thread, well under 100MB, a handful of
   requests per minute — the same polite request rate the plugin already uses, since it is the same
   client hitting the same cached endpoints.
5. **Visibility and control** — daemon status in the Learning panel, and a hard off switch. Something
   running on your PC at all times must be trivially inspectable and trivially killable.

### What it buys

The shadow trader currently learns only while you are playing. A daemon runs 24/7, which is roughly
**10–20× the observations**, and — more importantly — it observes the hours you do *not* play. The
hour-of-day liquidity model is currently learned only from hours you happen to be online, which is a
biased sample of exactly the variable it is trying to measure.

## Phase 5 — Validation

Claiming 2M/hour is easy; demonstrating it is the point.

1. **Simulation gate** — backtest at 100M must clear 2.6M/hour before any live claim is made.
2. **Live measurement** — the Performance panel already tracks realised gp/hour net of tax from
   completed flips only. That is the number that counts, not the simulation.
3. **Filter audit** — the Learning panel's approved-vs-rejected comparison says whether the filters
   are earning their place. If rejected trades keep outperforming, the thresholds are wrong and the
   evidence will say so.
4. **Honest reporting** — if the backtest clears 2.6M and live comes in at 900k, I will say the
   simulation is optimistic and investigate the difference, not quietly restate the target.

---

## Order of work

| # | Phase | Why here |
|---|---|---|
| 1 | Concentration + `spendableCoins` fix (Phase 2.1) | Live financial risk, small change |
| 2 | Throughput objective + limit-aware sizing (1.1, 1.2) | Largest single win |
| 3 | Slot portfolio + rotation (1.3, 1.4) | Second largest |
| 4 | Retune at 100M (Phase 3) | Needs 2–3 in place to tune against |
| 5 | Learning daemon (Phase 4) | Independent; can proceed in parallel |
| 6 | Dead settings + correlation cap (2.2–2.4) | Correctness, no profit impact |

## Two honest caveats

**On the 2M/hour figure.** The arithmetic supports it: ~7.5M/hour is theoretically on the table from
eight well-chosen slots, and 2M is 27% of that. I have not verified anyone else's published results
and will not treat them as a specification — what I can commit to is measuring this build honestly
against the target and telling you plainly where it lands.

**On what stays unverified.** The overlay walkthrough still has never run against a live Grand
Exchange interface. None of this plan changes that, and only you can test it.
