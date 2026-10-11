# OSRS Flipping Friend

A RuneLite assistant for Grand Exchange flipping: it suggests trades, observes offers and holdings,
and guides the player through manual actions. A local Java companion supplies entry recommendations
and stores durable trade-state evidence.

The goal is to match Flipping Copilot's player workflows and eventually exceed its measured net
account GP/hour. That result has not yet been demonstrated. The current priorities, source-backed
assessment, feature matrix, and benchmark contract are in [Project direction](docs/project-direction.md).

## Current status

The takeover assessment started from `CoPilot's-Rebuild-from-Main` at Package 3.85 (`1a4c665`).
The first fixes are merged into that rebuild branch; `main` remains an earlier cleanup/revert baseline.
Current continuation adds account isolation and the Copilot-history baseline.

Implemented and exercised by automated tests:

- Companion entry decisions, portfolio constraints, and buy-maintenance cancellation authority.
- Manual guidance for collection, repricing, and selling; graphs, positions, and local trade history.
- Durable offer-event delivery, exact acknowledgements, duplicate protection, transactional companion
  offer/position projections, conservative snapshot reconciliation, and persistent buy-limit windows.
- Account-specific databases, policy/calibration state, scoped API requests/responses, profile-cache
  fencing, and recovery of active/uncollected offers beyond the four-hour buy-limit window.
- Shared sale-tax rules for homogeneous prices, including integer flooring, caps, and exemptions.
- Analytical price/fill models and real-execution timing calibration fed back into candidate planning.
- Membership, risk, spending cap, skipped/blocked items, and sell-only preferences.
- Personal inventory/equipment/bank stock requires the existing adoption opt-in; blocked items
  remain physically visible and are excluded from adoption.

Remaining correctness work includes a single authoritative
ledger for live advice and profit reporting and complete replacement/sell
workflows. Plugin-side JSON records still coexist with companion projections. Mixed-price aggregate
fills require explicit accounting uncertainty. The rebuild is not yet ready for a performance claim.

Shadow trading, online logistic learning, exploration, automatic challenger promotion, cross-device
synchronization, and an independent always-on learner are not implemented as earlier documentation
claimed. Historical roadmaps and package records are retained as references; the current direction
and [task list](TASKS.md) describe the work ahead.

## Running the existing development build

See [START HERE.md](START%20HERE.md) for the existing Windows setup and launcher instructions.
The supported artifacts are:

| Component | Build task | Artifact |
| --- | --- | --- |
| RuneLite plugin | `jar` | `build/libs/osrs-flipping-friend.jar` |
| Local companion | `:companion:shadowJar` | `companion/build/libs/flipping-friend-companion.jar` |

The companion listens on loopback at `127.0.0.1:37777`. Local records live under
`~/.runelite/osrs-flipping-friend/`; live companion accounting is stored under
`companion/accounts/<accountId>/flipping-friend.db`. The old unscoped database is preserved and
unassigned. [Account isolation](docs/architecture/account-isolation.md) describes scope, recovery,
and upgrading both supported JARs together. Public market data comes from the
[OSRS Wiki prices API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices).

Game interaction remains manual. Highlights and clipboard controls help the player act; the plugin
does not place, cancel, or collect offers automatically. Trading preferences do not remove physically
owned items from inventory, equipment, bank, or collection observations.

## Development and verification

Use an installed JDK compatible with Gradle 8.14. Compilation targets Java 11 bytecode; CI uses JDK 21.
The RuneLite compile/test dependency is pinned in `build.gradle` and must be checked against the
client actually used for live verification.

```powershell
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
```

```sh
bash ./gradlew clean test jar :companion:shadowJar --console=plain
```

[GitHub Actions](.github/workflows/verify.yml) runs the tests and packages both supported JARs on
Windows and Linux, retaining test reports and artifacts. Capture-dependent diagnostic tests can skip
when their required external fixtures are absent. A successful build verifies neither live game
behavior nor profit superiority.

`backtest` is developer-only candidate-level historical replay. It uses the candidate factory, but
simulates items individually rather than the production account/portfolio lifecycle. Its results are
not account GP/hour and are optimistic about fills; use them only within that documented scope.

The agreed evaluation scenarios are 100M GP, members, eight slots, and checks every 5m, 30m, 2h,
and 8h. Full-account chronological replay and prospective attendance measurement remain planned.
The [historical baseline](docs/benchmarks/copilot-2026-10-11.md) records the supplied exports' totals
and data-quality limits; they do not contain session hours or cadence labels.

To reproduce ingestion without changing either source:

```sh
python tools/copilot_history.py local-data/copilot/flips.csv --transactions local-data/copilot/transactions.csv --output-dir local-data/copilot/baseline
python -m unittest discover -s tools/tests -v
```

Original exports and row-level outputs are ignored by Git. The committed report contains aggregate
results and source hashes. Transaction-export Tax is a client-calculated estimate; historical flip
profit/tax remain source figures and are not recomputed from rounded average prices.

Licensed under BSD-2-Clause.
