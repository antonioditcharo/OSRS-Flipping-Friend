# Agent handoff

Updated 16 October 2026 (America/Anchorage). Read [AGENTS.md](AGENTS.md) and [TASKS.md](TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

## Normal-user companion updates

Source branch codex/user-owned-companion-updates starts from #75/1f98391. Read its PR for exact-head
Windows/Linux checks and the merge receipt. The real permission repair has not been applied.
The owner is away; no UAC prompt, permission write or paired installation was started.

The current task already runs as this user, Interactive/Limited, but Administrators owns it and
its explicit user ACE permits reads only. A real read-only native scope check reports
NORMAL_USER_MAINTENANCE_READY=False / CURRENT_CALLER_CAN_REPAIR=False. The user's existing shortcuts
do not request elevation; task maintenance is the confirmed permission boundary.

`6 - Enable User Updates.bat`, run as administrator once under the same Windows account, backs up
and verifies the task XML/SDDL before granting that one user control of this one limited task.
It preserves the other access rules, owner/group, action, triggers and enabled state. It creates
no elevated updater or password store, changes no global Windows security setting, and opens no
database/account records. Foreign/elevated/credentialed tasks and denying/unsupported ACLs fail.
Snapshot changes abort before write; a failed postcondition restores the original DACL.
Fresh registration performs this grant before the pair is copied/enabled. Ordinary updates check
access before backup/stopping; after repair, invoke tools/apply-update.ps1 -Unattended normally,
not an old ad hoc helper which unconditionally requires administrator rights. Read
[the workflow](docs/development-workflow.md) and [Start here](START%20HERE.md).

Regression-first 2c2a5ab reproduces late access denial and missing setup maintenance rights.
Native tests 26fe7c6 cover scoped permissions, preservation, owner/admin/idempotent repair, races,
write failure/rollback and Windows' omitted XML RunLevel. Typed native level 0/logon 3 is required;
missing XML alone is not authorization. Early PS5.1 enum/hash issues and the native/snapshot cases
were caught in the new unshipped helper before fixes; they are not claimed as shipped regressions.
Windows CI initially exposed a fake-task SDDL spelling mismatch for the built-in Administrator SID.
462b39f reproduces it locally; the fixture now returns native-rendered aliases before and after writes.
Production rollback checks are unchanged. Final tooling: 94 tests / 76.495s / OK / exit 0;
PowerShell 7 native gate: 13 / 13.727s / OK. See [#76](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/76) for final exact-head CI.
Standard clean build: BUILD SUCCESSFUL in 49s / 18 tasks / exit 0; 1,528 Java passed/six existing
skips, zero failures/errors. Java source is unchanged by this tooling slice. Logs: reusable
.gradle/task-access-*. Real scope inspection changes no permissions; full repair and a following
normal-user paired update remain explicit acceptance gates.

## Installed runtime and queued source

Installed pair remains #69/86f69da, API 1.13.2: plugin 486,494 bytes and companion 17,878,455 bytes,
all project bytecode Java 11. Backup 20261016-002238-6687103d and earlier status/compatibility/
GET pipeline proof are preserved in history. They do not establish current trading eligibility.

#72/eaa63b1 (episode replay prevention) and #75/1f98391 (accurate durable-risk explanations) are
merged/CI-passed, queued together for one supported paired update after task permission repair.
#75 passed at c038cab in run 38946004083: Windows 117125172627 / Ubuntu 117125172354.
[Delivery](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/75#issuecomment-6221973637).
#72 passed at da449a7 in run 38876972197: Windows 116894240240 / Ubuntu 116894240365.
Historical main remains 0cb8ef8; the required ee0d3af ancestor is preserved.

On the owner's explicit request, one standard Process.CloseMainWindow request was accepted for
RuneLite PID 27600 and that process exited. No force-kill or game input was used. Desktop-control
initialization failed before any UI action; process lifecycle control completed the close.
[Receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/75#issuecomment-6222321342).
Latest observed state is client closed, companion task enabled, pair #69 unchanged. Do not ask the
owner to close the already closed client again; recheck process state before installing. Login and
all game actions remain manual. Permission tooling itself requires no JAR replacement.

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

Full prior receipts are retained in [latest history](docs/history/handoff-through-2026-10-16-risk-pauses.md),
[source-audit history](docs/history/handoff-through-2026-10-16-source-audit.md) and
[earlier history, including PR #31's checklist](docs/history/handoff-through-2026-10-15.md).
