# Agent handoff

Updated 17 October 2026 (America/Anchorage). Read [AGENTS.md](AGENTS.md) and [TASKS.md](TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

## Current source and installed runtime

Queued on codex/current-account-inventory-policy: owner explicitly selected current-account policy
on 18 October. Historical uncertainty remains reported/affected stock paused; fresh current funds
may support unrelated buys. Confirmed losses and the original anchor persist; unresolved live
offers still block entry. Regression-first changes also reserve unpriced/unseen exact stock cost,
carry physical observations separately, avoid phantom sales, support unknown-cost observed-sale
maintenance and stabilize/exactly price the Holdings display. Final verification/delivery pending;
#86 remains installed below. [Contract](docs/architecture/current-account-inventory-policy.md).

PR #86 is merged at 478426f and installed through the supported paired updater. Exact source
8fe03fa passes Windows/Linux CI (run 39108916385), including 103 Python tests. Standard Windows
clean build: 91s/18 tasks, 1,604 Java passed/six existing skips. Backup 20261017-235035-8555dccc is
VERIFIED; installed hashes match, Java 11, lifecycle=1, protected task XML/SDDL unchanged. Native
updater/status/compatibility/scoped GET pipeline all exit 0. Supported Start relaunched RuneLite.
[Delivery, hashes and acceptance state](docs/history/history-display-delivery-2026-10-18.md).

Owner reached #84 Missed flips but reported its table flickering empty. Reproduced/fixed pending
delivery clearing history and unchanged heartbeats discarding selection. Read-only account history
now survives temporary fresh-ledger loss, with an updating/as-of notice and lifecycle response fence;
logout/account change clears it. Trading/Performance retain fresh evidence requirements. Records
and filter labels are explicit. [Behavior and regressions](docs/architecture/history-display-retention.md).
Owner confirms the installed window stays populated while filtering. Fresh post-login eight-slot
proof passes at age six seconds; this accepts history stability, not controlled trading.

#84's [history-tolerant planning](docs/architecture/history-tolerant-planning.md) separates lifetime
exposure from newly exposed risk-window capital, avoids charging old quarantined sales as new loss,
and reserves fresh purchases of paused stock. Historical exposure, affected-item pauses, financial
rows/unknown profit, current proof and the original risk anchor remain intact. Missed flips adds
filtering and exact source/offer references. Unrelated final buy authority passes with retained
history gaps in a regression; this does not establish live entry eligibility.

#86's fresh installed eight-slot proof has eight
free slots and no unbounded gap scope. Recorded ledger remains 32 sale updates/18 unresolved/zero
ranges. The actual prospective uncertainty reserve still exhausts its allowance, so buys remain
refused. Owner asked how Copilot handles discrepancies: its client separates Missed flips/current
observations, but private server risk rules are unknown. No reserve-guard override was inferred.
#86's before/after read-only comparison preserves all ledger content except its read timestamp,
the risk evidence and historical gaps. [Earlier #84 delivery](docs/history/history-planning-delivery-2026-10-18.md).
Controlled trading, remaining UI workflows and sustained timing remain open.

#83's sale authority, exact-cost maintenance, durable sale investigation, graph lookup, market-drop
investigation, alerts, executable account replay and frozen completion fitting remain installed.
[Implementation/deferred capabilities/player steps](docs/architecture/parity-account-evaluation.md);
[earlier #83 delivery](docs/history/parity-delivery-2026-10-17.md). The sole new known out-of-time
outcome has zero Brier improvement. Learning remains public-price baseline/HOLD; runtime model
promotion/rollback/drift controls, complete replay and real four-cadence benchmarks remain open.
No full-parity or performance claim is established.

## Routine updates

Use tools/apply-update.ps1 -Unattended from the clean primary checkout after both CI checks pass.
The verified process lifecycle handles companion maintenance without admin/INSTALL replies; leave
protected task XML/permissions unchanged. The optional #76 ACL repair was never applied and is not
needed. Never force-kill RuneLite or script game input. Login and all GE actions remain manual.
Read [the workflow](docs/development-workflow.md), [lifecycle contract](docs/architecture/user-process-updates.md)
and [first normal-user delivery](docs/history/normal-user-update-2026-10-16.md).

## Missing sells, independent evidence and latency

Keep [#70](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70) open. Prevention does
not repair old affected quantities/costs. A nine-unit offer has 11 acquired units in affected rows;
aggregate positions are not safe fallback holdings. Preserve original financial rows, unknown
cost/profit, historical gaps and the durable risk window. No database edits, financial replay,
adoption, outbox clearing or risk reset may be used to activate advice.

#73/cb25c1f audits a complete verified frozen source prefix without opening a database or client.
The retained owner bundle has 11,598 sources/cursor 13,221; 35 observed identities/23 with flags.
Audit/wrapper/retained-artifact verification all exit 0 and authorize neither accounting nor trading.
Raw maxima and unflagged identities are not current holdings or known costs. Complete hashes and
flag receipts are in [history](docs/history/handoff-through-2026-10-16-risk-pauses.md).
Private bundles: primary local-data/execution-evaluation/sell-gap-evidence-20261016 and
local-data/trade-audits/sell-recovery-20261016. The exploratory terminal-envelope calculation is
not a validated recovery policy or permission to reduce a reserve.

The already retrieved independent Copilot owner history contains 17,829 unique transactions,
latest 15 October 02:17:25 UTC. Only 16 overlap the retained Friend range; 3,037 nonempty Friend
observations are later. It cannot close the affected current-stock evidence gap. Raw response,
decoded rows/provenance manifest remain private in local-data/copilot-corroboration/20261016-093739.
No credentials were logged, other-account transactions retrieved, or records imported. Do not
repeat this export without new evidence; coverage, exact tax/fill timing and profit remain unproven.

At 10:07 local, authenticated portfolio/action/ledger/learning reads take 1,062/79/1,765/703ms;
outbox zero; ledger 11 sale updates/eight unresolved. Plan COMPANION_STATE_UNAVAILABLE has no
canonicalRisk, so earlier negative-budget results are historical, not this read's current result.
Installed native SQLite 3.45.3 uses an ordinary event_log scan; no index change is indicated.
Isolated read-only ledger probe: 753ms cold, 194/165ms warm, valid/same counts/zero eligible stock.
These are standalone JVM timings, not full HTTP acceptance. Private diagnostics and probe code
remain in primary local-data/diagnostics and .gradle. Sustained latency/final entry acceptance remain open.

## Remaining work and source map

The [17 October Copilot investigation](docs/research/flipping-copilot-2026-10-17.md) retains the installed
JAR/decompilation, complete client/protocol reports and current parity/learning matrix in ignored local-data.
One live price response matches the installed decoder; private backend/model internals remain unknown.
The research itself changed no Friend runtime code or ledger data; the implementation candidate
is described above. Trading and learning gates remain open.

[TASKS.md](TASKS.md) preserves every unchecked release gate. Finish safe evidence-based stock/risk
resolution, companion sell/replacement authority and the ledger consumer audit, then controlled
player-operated buy/collect/sell, partial-cancel/relist/restart and search/key/pause acceptance.
Complete chronological account replay and gated learning (#56); runtime learning remains the public
price baseline with promotion held. Benchmark 100M members/eight slots separately at 5m/30m/2h/8h.
Historical CSVs have no session/cadence labels; higher GP/hour remains unmeasured.

Active contracts: [trade scope](docs/architecture/automatic-trade-scope.md),
[gap scope](docs/architecture/trade-gap-scoping.md), [current planning](docs/architecture/bounded-current-planning.md),
[episode identity](docs/architecture/offer-episode-identity.md), [startup](docs/architecture/startup-offer-evidence.md),
[durable risk](docs/architecture/durable-risk-window.md), [ledger](docs/architecture/durable-profit-ledger.md),
[offline audit](docs/architecture/trade-evidence-audit.md), [compact UI](docs/architecture/compact-panel.md)
and [manual assistance](docs/architecture/trade-input-assistance.md).

Full prior receipts are retained in [task-access history](docs/history/handoff-through-2026-10-16-user-task-access.md),
[risk history](docs/history/handoff-through-2026-10-16-risk-pauses.md),
[source-audit history](docs/history/handoff-through-2026-10-16-source-audit.md) and
[earlier history, including PR #31's checklist](docs/history/handoff-through-2026-10-15.md).
