# Agent handoff

Updated 17 October 2026 (America/Anchorage). Read [AGENTS.md](AGENTS.md) and [TASKS.md](TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

## Queued safe sell-relist slice

17 October continuation: bc05fb7 reproduces five additional sell-maintenance failures before the
fix. Maintenance and relisting now share fresh matching-account ledger/gap checks and current
two-sided quote checks. Mixed-cost offers cannot lose their known subset's floor. Unrelated scoped
gaps preserve clean sale advice; external profit remains unknown. Standard clean verification:
BUILD SUCCESSFUL in 54s / 18 tasks / exit 0; 1,561 Java tests, 1,555 passed/six existing skips,
zero failures/errors. Plugin 489,750 bytes; companion 17,902,507 bytes. Prior 103 tooling tests
remain passing; no tooling changes since that run. Logs: reusable .gradle/sell-maintenance-evidence-*.
Owner reports GitHub billing updated; fresh latest-head CI is required before merge/install.

Branch codex/safe-sell-relist starts at #78/a142f4f. Regression commits 02d1c4b and 8692ed3
reproduce six failures before fixes: lost cost floor, excess personal units, missing ledger/gap
bypasses, offer identity confusion and malformed terminal counters. The fix requires matching fresh
durable evidence and quote, preserves exact subset costs/floors and stop/sell-only rules, caps
quantities and retains episode/cost-required guidance through restart. Personal sale profit stays
unknown. Read [the contract](docs/architecture/safe-sell-relist.md) and
[portable continuation instructions](CONTINUE%20DEVELOPMENT.md).

This slice is queued source, not installed or controlled-trade acceptance. Installed runtime remains
#77 below. No client restart/update, real database changes or in-game actions were performed for
this slice. #70 remains open; this does not repair historical stock or complete companion sell-policy
migration. Windows standard verification: BUILD SUCCESSFUL in 53s, all 18 tasks executed, exit 0;
1,552 Java tests / 1,546 passed / six existing skips / zero failures/errors. Plugin 489,471 bytes,
companion 17,902,228 bytes; 218/461 project classes, all Java 11 (major 55). Logs are in reusable
.gradle/safe-relist-*. A focused invocation passed the root tests but selected no companion tests;
the subsequent standard command above completed all modules. All 103 tooling tests pass in 100.027s,
exit 0. Implementation 187a79a is pushed in [PR #79](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/79).
Both exact-head CI jobs were refused before starting: "The job was not started because your account
is locked due to a billing issue." Run 39068304395, Windows 117500451578 / Ubuntu 117500451725.
This original run predates the owner's reported billing update. Rerun both latest-head checks. Logs
could not be fetched (provider HTTP 502); check annotations establish the refusal, not a code failure.
PR remains unmerged; require both exact-head checks before merging. No automatic installation.

## Companion updates without administrator approval

Runtime #77 is installed from merge 3dcd8313255235dcca13e3d56016386876811710. Both exact-head
checks at fdcaa94 pass in run 38955937701 (Windows 117160940451 / Ubuntu 117160942779). An initial
Windows HTTP isolation-test timeout passed on retry without source changes. #76's optional ACL
repair was never applied. No initial UAC reply, task/permission change or elevated helper was used.
Read the [full delivery receipt](docs/history/normal-user-update-2026-10-16.md).

The protected task already runs as this user, native Limited/Interactive, while allowing only read
access to task management. Read-only process inspection also confirms its JVM belongs to this user.
The new supported -Unattended -UserProcess transition leaves task XML/ACLs unchanged and manages
only exact installed-JAR companion processes owned by this user. No administrator approval, new
task, elevated helper, credential store or global security change is required. Root 6 now invokes
this transition normally; it no longer asks to Run as administrator. Do not use old elevated helpers.

The companion takes an installation/startup gate and lifetime singleton before any token/database
or retention work. It verifies its installed pair before store access. Windows native sharing,
two child JVMs, real-main refusal and incomplete-pair behavior are tested with disposable files.
The updater backs up original runtime artifacts before cutover, atomically installs guarded code,
drains old JVMs a second time before account backup, verifies the backup and installs the plugin.
Pre-commit rollback restores runtime JARs/manifest only; account/database files are never restored
or repaired. Unknown listeners, owners/tasks, missing guard and a running client still refuse.
Persistent processLifecycleVersion=1 selects ordinary unattended updates and direct Start later.

Feature-first afd2b0d specifies the new process lifecycle; it initially fails for the unsupported
UserProcess option, rather than proving that old code already had this feature. Existing protected
task denial is reproduced by #76's earlier regression. Local and exact-head CI/delivery receipts
are recorded above; real primary-checkout cutover and normal-user Start now pass.
Standard: BUILD SUCCESSFUL in 59s / 18 tasks / exit 0; 1,534 Java passed/six existing skips,
zero failures/errors. Tooling: 103 / 99.585s / OK; PowerShell 7 process gate: ten / 25.351s / OK.
The BOM manifest/native/singleton focused gate also passes. Worktree plugin 487,383 bytes/218 classes;
companion 17,900,140 bytes/461 classes; all Java 11. Logs: reusable .gradle/process-update-*.
Read [the lifecycle contract](docs/architecture/user-process-updates.md),
[workflow](docs/development-workflow.md) and [Start here](START%20HERE.md).

## Installed runtime

Installed #77 includes #72 episode replay prevention, #75 accurate durable-risk explanations and
the startup/process guard. API 1.13.2; plugin 487,403 bytes/218 project classes, companion 17,900,160
bytes/461 classes, all Java 11. Both hashes match the manifest; processLifecycleVersion=1.
Backup 20261016-114457-2a290e0e is VERIFIED: original JAR/manifest/task plus three companion account
files and 22 plugin account files. XML and SDDL remain identical; current process elevation=false
and task maintenance readiness remains false. Actual installed archive bytes differ from worktree
packaging; exact hashes/sizes are in the delivery receipt. Historical main/required ancestor remain intact.

Native status, launcher CheckOnly, StartOnly and explicit-account GET pipeline each exit 0. Ledger:
11 sale updates/eight unresolved/zero profit ranges. Action WAIT, plan UNAVAILABLE: waiting for
client coins/free slots. This is transport proof, not current trading eligibility. The native updater
completion/manifest are observed; its exact exit was not retained after outer log-capture cleanup.
Independent verification exits are recorded, not inferred.

RuneLite was previously closed normally on the owner's request. The supported Start script has
now relaunched it; one client process is observed. No login/UI/trade outcome is observed and no game
input was sent. Runtime through #77 is installed; this receipt is docs-only and needs no reinstall.
Later ordinary Update/Start use the manifest's process lifecycle automatically, without admin or
INSTALL replies. Do not ask the owner to run the optional ACL repair or another installation.

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

Full prior receipts are retained in [task-access history](docs/history/handoff-through-2026-10-16-user-task-access.md),
[risk history](docs/history/handoff-through-2026-10-16-risk-pauses.md),
[source-audit history](docs/history/handoff-through-2026-10-16-source-audit.md) and
[earlier history, including PR #31's checklist](docs/history/handoff-through-2026-10-15.md).
