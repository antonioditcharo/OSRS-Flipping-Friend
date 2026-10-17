# Agent handoff

Updated 17 October 2026 (America/Anchorage). Read [AGENTS.md](AGENTS.md) and [TASKS.md](TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

## Current source continuation

PR #79 is merged as 7d2032a38bb39e051163570fcdb8297ca988f8f9. Both exact-head checks at
df3da1e pass in run 39068966156: Windows 117502409112 / Ubuntu 117502408869. Billing no longer
blocks CI. The primary rebuild checkout is clean and fast-forwarded; installed runtime stays #77.
The open native RuneLite process has no developer flag; scoped GET pipeline exits 0 but reports
WAIT/UNAVAILABLE awaiting client coins/slots, ledger 11 sale updates/eight unresolved. These are
pre-update transport observations, not evidence of active Friend UI or controlled trade acceptance.

New branch codex/replacement-entry-authority starts from that merged rebuild. Regression 40a45d5
reproduces three actual plugin-delivery failures: local replacement BUY bypasses companion refusal,
ignores the fresh authorized order and admits an unrelated/oversized replacement. The fix routes
replacement proposals through the existing final entry boundary, keeps the same item and caps the
quantity without resizing a forecast. WAIT retains its companion reason. Only authorized buys write
runtime replacement plans; no accounting/data repair. Read
[the contract](docs/architecture/replacement-entry-authority.md). Exact remainder construction and
durable replacement restart workflow are still open; this is not activation of the old scaffolding.
Standard Windows clean verification passes in 1m 2s / 18 tasks / exit 0: 1,566 Java tests,
1,560 passed/six existing skips, zero failures/errors. Plugin 490,419 bytes; companion 17,903,176;
all project bytecode Java 11. Earlier 103 tooling tests remain applicable (tooling unchanged).
Focused actual-delivery/partial-buy tests also pass. Logs: reusable .gradle/replacement-entry-*.
This source is not installed; both exact-head CI checks must pass before merge and paired delivery.

## Merged safe sell-relist slice — not installed

PR #79 preserves exact taxed cost floors, canonical/visible cancelled remainders, fresh scoped
ledger/quote checks, managed cost-required guidance and episode matching through restart. Clean
external sale profit stays unknown. Regressions 02d1c4b/8692ed3/bc05fb7 precede the fixes.
Final standard: 54s / 18 tasks / exit 0; 1,555 Java passed/six skips; prior 103 tooling tests pass.
Plugin 489,750 bytes; companion 17,902,507 bytes. No real database edit, update or game input.
#70 and full policy/live-workflow acceptance remain open. Read the
[contract](docs/architecture/safe-sell-relist.md), [detailed receipts](docs/history/sell-guidance-2026-10-17.md)
and [continuation guide](CONTINUE%20DEVELOPMENT.md).

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
