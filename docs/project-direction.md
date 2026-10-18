# Project direction and takeover assessment

Decision date: 11 October 2026. Assessment base: `CoPilot's-Rebuild-from-Main` at
`1a4c665` (Package 3.85). Original assessment working branch: `codex/project-direction` (merged).

Continuation as of 13 October 2026: PRs #27, #28, and #29 are merged into the active/default
`CoPilot's-Rebuild-from-Main` branch. Account isolation, long-offer recovery, scoped transport/cache
fencing, read-only diagnostics, and durable sale accounting/live Performance are implemented.
See [the current handoff](../HANDOFF.md), [account isolation](architecture/account-isolation.md),
and [sale accounting](architecture/durable-profit-ledger.md). The findings below retain their
original assessment context. The supplied [Copilot baseline](benchmarks/copilot-2026-10-11.md)
contains 2,117 flip summaries across two accounts and 17,240 execution records for one account.
Historical session/cadence labels are unavailable; GP/hour remains unmeasured.

## Installed continuation — 18 October 2026

The owner subsequently reports continued sale waiting. Fresh GETs reproduce a quiet last-trade
freshness refusal on verified held stock. The shared client/companion/replay non-loss limit fix is
source-verified and queued, not installed; [current receipt](history/quiet-market-sale-delivery-2026-10-18.md).
Replay v3 distinguishes the changed exit semantics. Entry, risk marks, loss-cut/maintenance
freshness and historical accounting guards remain; real player acceptance is still required.

The owner-selected current-account policy and combined sell-verification fixes through #92 are
installed. Historical unknown money stays visible/affected stock paused, while verified current
funds can support unrelated buys. Owner-confirmed buy availability is recorded; fresh updated
sell/maintenance and controlled trading acceptance remain pending. The [delivery receipt](history/sale-guidance-delivery-2026-10-18.md)
separates source, tests, installed pair, financial preservation and acceptance.
Replay v2 shares live display-price stability; complete replacement/relist/foreground coverage
and qualified market benchmarks remain open. [New frozen evidence/fitting](history/prospective-evaluation-2026-10-18.md)
preserves uncertain outcomes and establishes no out-of-time calibration gain. Runtime remains on
the public-price baseline/HOLD; no parity or higher account GP/hour claim is established.

## Current source continuation — 17 October 2026

The [Copilot research](research/flipping-copilot-2026-10-17.md) is reconciled against the latest
checkout in the [implementation and ledger-consumer receipt](architecture/parity-account-evaluation.md).
The new source adds final companion sale authority, durable recorded-sale investigation, graph
lookup and independently sourced market/held-stock alert workflows. Executable account replay and
conservative frozen completion fitting now exist, with explicit remaining production-policy,
measurement, model release and client-convenience gaps. Source verification is distinct from
installation and controlled player acceptance; [HANDOFF](../HANDOFF.md) records current delivery.

## Current continuation — 15 October 2026

PRs through #62 are merged into the rebuild branch. The combined 1.13.2 runtime through #61 is
installed via supported tooling #62 after verified backup 20261015-184127-76f3a3f8. Ledger reads, fresh account risk, a READY
plan with four allocations and final ENTRY/PLACE_BUY GET pass. This is backend readiness evidence;
controlled UI/trade/restart acceptance and measured returns remain pending. Historical guards remain. Compact UI,
real cadence presets, automatic managed quantities, personal sell setup quotes, Missed flips,
walkthrough protection and player-triggered input assistance are implemented, with live acceptance
still pending. Prospective forecast lineage, observation intervals, held promotion, frozen offline
evaluation and exact acquisition-cost exits are installed. Initial live prospective forecasts are
captured/verified against the frozen earlier prefix; completion and performance gates remain open.
The assessment tables below describe their original revisions. See the newest
[handoff](../HANDOFF.md) and [release gates](release-readiness.md) for current evidence and work.

## Product and success criteria

Build a reliable, understandable flipping copilot with Flipping Copilot's player workflows,
then demonstrate higher net account GP/hour under comparable conditions. Profit superiority is
an experimental result, not a model-complexity claim. Retain the Java plugin, shared core, and
local companion; repair their authority and accounting boundaries before replacing the strategy.

The owner specified 100M GP, a members account, and checks every 5 minutes, 30 minutes, 2 hours,
and 8 hours. These are four separate benchmark scenarios, each with eight GE slots. The current
merged #43 source exposes actual 5m, 30m, 2h and 8h settings and preserves legacy custom values.
Its 1.13.2 pair is installed, with live acceptance in progress; earlier builds had only 3h
as their long-interval option. Never substitute 3h for either benchmark cadence.

Primary operational metric: realized net trading profit / measured elapsed session hours,
with a documented session-time policy. Waiting and completed-but-uncollected offers consume time.
For controlled experiments, also report total account equity change / wall-clock hours, valuing
remaining stock at a conservative executable exit after tax. This prevents a strategy from
appearing profitable by realizing winners and leaving losing inventory unsold. Report both
metrics alongside starting/ending holdings, drawdown, loss rate, calibration, capital use, and
required player interactions.

Candidate GP per slot-hour remains a useful resource-efficiency diagnostic. It is not the product
objective: summing independent rates does not model sequential capital reuse, shared buy limits,
actual visits, blocked collection slots, or the time needed to unwind holdings. The future planner
should choose actions that improve expected net account value over a common planning horizon,
under the same capital, exposure, loss, slot, and attendance constraints. Change the current
ranking only after the evaluation harness can compare it against a frozen baseline.

## What the history establishes

| Reference | Evidence | Decision |
| --- | --- | --- |
| `main` at `0cb8ef8` | Ends at the revert of an accidental early dashboard-cleanup merge | Do not use this as the latest rebuild |
| Package branch at `1a4c665` | Phase 1 cleanup, Phase 2 transactional recovery, entry authority, buy-cancellation authority, and replacement foundations | Base current assessment and incremental work here |
| `copilot-rebuild` at `63caa09` | Separate recovery line from the earlier working build, durable HTTP/outbox work, local RuneLite version update | Preserve and compare useful changes individually |
| `stash@{0}` at takeover | Local Suggestion and StepGuide regression edits saved before main synchronization | Preserve; not automatically applied across divergent branches |

The package history reflects the prior command-block workflow: narrowly scoped implementation,
tests, and merge commits. There is substantial useful work, but package count does not establish
product completion. The latest replacement/account-constraint packages explicitly remain
non-activated. Entry decisions and buy-maintenance cancellation have live companion authority;
sell maintenance, repricing/replacement guidance, and exits still depend on plugin-side logic.

No Codespaces session or old chat transcript was available in this assessment. Git history,
architecture records, source, and fresh local execution are the evidence used here.

## Findings and priorities

| Priority | Finding and source | Required outcome |
| --- | --- | --- |
| P0 | `AccountSnapshot` has no account identifier; `OfferEvent` has a session identifier but no account identifier. `CompanionMain` opens one shared database. `SqliteStore` keys positions by item and offers by slot. | Account-specific events, snapshots, projections, limits, and execution evidence; account-switch/restart tests and a conservative migration for legacy unscoped rows |
| P0 | The plugin applies local `OfferTracker` mutations before publishing the durable companion event, then persists separate JSON stores. Companion positions are transactional, but live guidance and headline profit still depend on local `PositionBook`/`TradeJournal`. | One accepted fill produces one durable accounting effect; planner, exits, UI, and statistics read the same account ledger. Inject crashes between local mutation, publication, acknowledgement, and rendering |
| P0 | `CompanionService.rehydrate()` rebuilds in-memory active offers from only the last four hours of events. Durable offer projections already exist. | Reconstruct active offers regardless of age. The four-hour buy-limit expiry must not erase an eight-hour sell/collection workflow |
| P0 | `AccountMonitor` removes blocked items from inventory/equipment and bank observations but retains collection-box items. | Physical observations always preserve ownership; apply trading preferences at the policy boundary. Fixed in this change with reproducing regressions |
| P0 | `FlippingFriendPlugin.syncHoldings()` automatically adopts inventory/equipment as flipping stock even when adoption is off. | Require the existing adoption opt-in for all personal stock and respect blocked names; continue reconciling complete physical observations. Fixed here with reproducing plugin-level regressions |
| P1 | Companion `position_event` records disposal quantity and removed cost, but not realized proceeds/tax/profit. `OfferTracker` separately computes journal profit. | Durable disposal proceeds and cost attribution with evidence quality, auditable net realized profit, and conservation over partial fills and restarts |
| P1 | `TaxCalculator` correctly implements homogeneous-price floors, caps, and exemptions; `OfferTracker.applyFill()` estimates mixed-fill tax from a rounded average price. | Preserve observed cumulative gross deltas. Distinguish exact tax from estimates when only aggregate prices are observable; do not call reconstructed mixed-fill accounting exact |
| P1 | `FlippingFriendPlugin.requestEngineRefresh()` still calls `engine.refresh(false)` for maintenance/exits and filters buy cancellation through the companion. | One coherent live policy chain, with complete cancel/collect/revalidate/replace handling, partial holdings protected, and explicit abstention when inputs disagree |
| P1 | `Backtester` uses `CandidateFactory`, then takes `tactics.get(0)` and simulates each item independently. It does not invoke the portfolio optimizer or replay a whole account. | Chronological account replay using the production policy, shared bankroll, eight slots, visits, limits, cancellation, inventory, and terminal valuation |
| P1 | The README promised shadow trading, online logistic learning, exploration, and an always-on learner that are not wired as described. Actual `refreshCalibration()` feeds execution timing corrections into the planner. | Accurate status, measured calibration, immutable recommendation/outcome lineage, and gated model promotion; retain useful real-fill calibration |
| P1 | Fresh baseline Windows root suite failed on a raw source hash solely because Git checked out CRLF. A subsequent full run exposed a diagnostic verifier emitting CRLF despite its LF output contract. No GitHub CI workflow existed on the assessed branch. | Cross-platform tests and supported artifacts on every PR. Normalize source-hash line endings and emit explicit LF in the diagnostic verifier while keeping its strict tests; fixed here |
| P1 | First remote CI failed in checkout: `flipping-copilot-source` was an unconfigured gitlink, with no `.gitmodules` URL. No source code references it. | Remove the unused gitlink from tracking so normal repository checkout works. Its original reference was `5ca1224e9cddd173968ea9f5bbe1f981a7170440`; preserve comparison evidence through the official source links above |

Account isolation is a confirmed architectural gap. It does not prove that the owner has already
suffered cross-account corruption. The publication/persistence ordering is a remaining crash window,
not proof that Phase 2 transaction tests failed. Source findings must stay distinct from observed
production incidents and from historical comments about old incidents.

Inventory decisions will distinguish observed locations, cached bank evidence, owned quantities,
flipping stock, and known acquisition cost. Absence from a container does not mean sale. Unknown
cost does not become zero cost or current market cost. Blocking new trading does not erase an asset.
Any manual disposal/import requires explicit accounting evidence and an audit trail.

## Feature parity

Reference inspected: official [Flipping Copilot source](https://github.com/cbrewitt/flipping-copilot)
at `cb9251e8f46eb85762401ab1158e4caad272a424`, including its README, configuration, portfolio UI,
offer-event handler, statistics, and API client. This public repository is the RuneLite client.
Its `ApiRequestHandler` calls `api.flippingcopilot.com`; the server's recommendation model is not
contained in the inspected source. Client feature parity can be audited; prediction parity and
profit superiority require observable results.

| Player capability | Flipping Friend status at assessment | Acceptance evidence |
| --- | --- | --- |
| Item, price, quantity, and rationale | Present; live companion entry policy | Valid decision lineage and current affordability/limits |
| Cancel/reprice/replace a slow offer | Split authority; much replacement scaffolding inactive | Full manual workflow, including restart and partial fills |
| Manage held/banked stock and collection | Present with parallel local/durable records | Correct quantities, location uncertainty, known cost, and reconciliation |
| Graphs, history, realized/unrealized profit, hourly statistics | Present locally; accounting/metric gaps above | Independently reconciled ledger and defined session clock |
| Preferences, skip/block, sell-only, risk and cadence | Present; 2h/8h not explicit options | Policy honors preferences without corrupting observations |
| Guided GE actions and quick price/quantity access | Highlights and clipboard present | Usable workflow and compatibility review for any additional assistance |
| Misclick prevention, slot colors, portfolio tooltips/bank tags | Additional Copilot configuration/UI capabilities | Inventory of individual interactions and targeted parity work after accounting |
| Alerts and streamed dump opportunities | Some local notification code; no verified equivalent dump stream | Working notifications and independently sourced opportunity evidence |
| Cross-device history/account portfolios | No verified equivalent; local companion is the current product | Explicit sync/export design after account isolation and ledger migration |
| Quant predictions and adaptation | Analytical forecasts plus real-fill timing calibration | Chronological forecasting/fill validation and gated challenger promotion |

This is a source-backed initial matrix, not a claim of exhaustive feature parity or a live UI test.
Public client code does not provide a GE order book, queue position, or a proprietary server model.
Those must not be invented as available inputs in another roadmap.

## Delivery sequence and gates

1. Establish a reproducible baseline and accurate documentation. Keep both existing supported JARs,
   add Linux/Windows CI, fix demonstrated observation defects, and preserve historical branches/data.
2. Complete account isolation and ledger authority as a working end-to-end migration. Include
   duplicate delivery, lost acknowledgement, account switching, partial sells, collection, and an
   eight-hour restart. Exit gate: recorded cash flows, ownership, removed cost, realized profit,
   and buy-limit use reconcile without duplicate effects across every interruption point.
3. Complete live offer workflows and consolidate companion policies. Exit gate: a user can follow
   buy -> partial fill -> cancel -> collect -> revalidate -> replace/sell through restarts, with
   coherent advice and no abandoned cost basis. Each delivery must reach a user workflow or close
   an accounting invariant; avoid dozens of separately inactive adapters.
4. Build full-account replay and import the Copilot baseline. Freeze data, configuration, strategy
   versions, and start/end accounting. Exit gate: reproducible results for all four checking cadences,
   no future-price leakage, conservative queue/fill assumptions, and explicit end-of-run holdings.
5. Tune the account objective and add learning. Start with completion/timing calibration and
   chronological residuals; add model complexity only when it improves held-out results. Keep
   simulated price touches separate from real fills. Evaluate a frozen baseline and a challenger on
   later time windows; promote only when net return improves within drawdown and calibration limits.
   Preserve rollback and test degraded/stale-data abstention. Exploration must fit the same risk budget.
6. Finish UI parity and run prospective matched sessions against Copilot. Alternate tools across
   comparable sessions; do not run two tools competing for the same bankroll and call it a fair test.
   Report dates, attendance, bankroll, start/end holdings, median returns, uncertainty, loss/drawdown,
   and user actions for each cadence. No superiority claim from candidate replay or a single session.

## Copilot-history intake

The owner has offered their history; it can be supplied now. Prefer the original CSV or JSON export,
including account alias, item ID/name, timestamps/timezone, side, quantity, actual buy cost, sale
proceeds, tax, and reported profit where available. Completed-flip rows are still useful if raw fills
are unavailable. Session starts/ends, checking cadence, starting bankroll, open offers, and remaining
holdings are needed for a trustworthy hourly comparison. Missing fields remain missing; never infer
active session hours solely from first/last completed trade or mix all four cadences into one result.
Credentials are unnecessary. Preserve the original export and record transformations separately.

History measures what Copilot actually achieved, but cannot establish what our unplaced orders
would have filled. Historical comparison, simulated replay, and future live comparison will therefore
be reported as separate evidence. The importer/evaluation harness is queued; it is not implemented
by this takeover change.

## Verification record

Initial clean-checkout attempt: Windows, installed JDK 23, Gradle 8.14, RuneLite dependency 1.12.39.
The root suite ran 452 tests and failed only on the CRLF-sensitive Suggestion source hash; later
module tests/artifacts were not completed by that failed invocation. The blocked-holdings regressions
then ran seven tests and reproduced two failures before the production change. Three plugin-level
adoption tests reproduced two additional failures before the adoption change. The subsequent module
run exposed two LF-contract failures in `DiagnosticCaptureVerifierTest`; the verifier now emits LF
explicitly rather than weakening that contract.

Final local verification: `gradlew.bat clean test jar :companion:shadowJar --console=plain`
succeeded on Windows/JDK 23 in 38 seconds, executing all 18 tasks. Across 293 suites there were
1,127 tests: 1,121 passed, six capture-dependent tests skipped, zero failures, and zero errors.
Root: 458 tests; core: 154; companion: 515 including the six skips. Both supported JARs were built.

The added CI workflow checks Java 21 on Linux and Windows while retaining Java 11 bytecode; its
remote results are distinct from this local verification. Building both JARs does not certify live
RuneLite compatibility, feature parity, or profitability. This change does not install the candidate
build into the game.

## Durable profit continuation — 11 October 2026

The [durable sale ledger](architecture/durable-profit-ledger.md) closes the missing-proceeds portion
of the initial assessment: accepted sale updates now store gross proceeds, removed cost, recorded
rule evidence, tax/profit bounds, and unresolved coverage atomically. Live Performance reads the
scoped ledger. Local history/valuation graphs remain labeled estimates; GP/hour headlines await
session measurement. Planner exposures, exits, inventory UI, calibration, and session risk still
require migration, so the broader single-authority exit gate remains open. Initial findings and
verification counts above describe their original revisions.

## Automatic stock and sale workflow — 14 October 2026

The owner corrected the product direction: routine inventory management belongs in automatic
execution tracking. Personal belongings must not be adopted into trading stock or generate
unsolicited sales/review tasks. Track the remaining quantity from observed purchases, including
manually chosen buys, while retaining recommendation attribution separately. Extra personally
owned units of that item remain outside the matched position.

For other owned items, suggest a fresh current price when the player selects a sell setup, then
maintain the observed sell offer to completion/collection. Missing acquisition cost does not
become zero cost, a borrowed recent purchase price, or realized profit. Incomplete trading records
belong in a separately opened Missed flips window, with scoped evidence and coverage visible.

The [accepted workflow](architecture/automatic-trade-scope.md) supersedes the earlier Review holdings
and adoption approach. Its source implementation migrates trade quantity ownership, exits and Holding
UI to accepted durable executions, and replaces review rows with a read-only Missed flips window.
The compatible 1.13.2 pair is installed; live ledger/startup fixes and manual acceptance remain pending.
Realized session risk and calibration
still require migration. Historical/account data and active exposure protections remain
intact. This UI/ownership correction does not establish feature parity, learning improvement or
profit superiority.
