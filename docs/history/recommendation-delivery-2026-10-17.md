# Recommendation fixes delivered — 17 October 2026

Installed from rebuild merge **342aa523ca35ff10fbb1bdc42e9778c2762ca994** (#80), including #79.
Historical main remains 0cb8ef8aab9114f02b16d2eca4433c0f7e010894; required ancestor ee0d3aff is retained.

## Source and verification

- #79 merge 7d2032a38bb39e051163570fcdb8297ca988f8f9; tested df3da1e.
  Run 39068966156: Windows 117502409112 and Ubuntu 117502408869 both pass.
- #80 tested head 8a666e553c5e811e3ffbf148c8a6b9636c20cdd0.
  Run 39069865905: Windows 117505107495 and Ubuntu 117505107647 both pass.
  Prior checks at 3944767 do not cover the later editor refusal fix.
- Final Windows standard: `gradlew.bat clean test jar :companion:shadowJar --console=plain`,
  BUILD SUCCESSFUL in 54s / 18 tasks / exit 0. Java: 1,568 total, 1,562 passed, six existing skips,
  zero failures/errors. Worktree artifacts 490,472/17,903,229 bytes. Project bytecode targets Java 11.
- Earlier `python -m unittest discover -s tools/tests`: 103 tests / 100.027s / OK / exit 0;
  no tooling changes since. Final delivery-document local target check: 46 valid links; diff check passes.
- Regressions before fixes: 02d1c4b/8692ed3/bc05fb7 (#79); 40a45d5/b374b5a (#80).
  Contracts: [sell guidance](../architecture/safe-sell-relist.md),
  [replacement entry authority](../architecture/replacement-entry-authority.md).

## Actual paired delivery

The exact current-user native RuneLite process was requested to close through CloseMainWindow;
normal close returned true and process exit was observed. No force kill or game input was used.
The supported primary-checkout `tools/apply-update.ps1 -Unattended` then built/installed both JARs.
Release preflight: official RuneLite 1.13.2 matches source. Wrapper build: 7s / eight tasks, exit not
separately retained, with BUILD SUCCESSFUL printed. Installed manifest UTC: 2026-10-17T21:15:59.4615468Z.

Backup `%USERPROFILE%\.runelite\osrs-flipping-friend\backups\20261017-131556-67c07d16` is VERIFIED.
It includes three companion account files and 22 plugin account files; verification checks relative
names/sizes, without opening or hashing account/database contents. Original artifacts:

| File | Bytes |
| --- | ---: |
| backup-verification.json | 335 |
| companion-task.sddl | 164 |
| companion-task.xml | 1,895 |
| flipping-friend-companion.jar | 17,900,160 |
| installation.json | 494 |
| osrs-flipping-friend.jar | 487,403 |

Installed plugin: 490,492 bytes,
SHA256 `5c24d70ea850bd4d6b275564d977f8b02bfab714b12f5d637dbe1aadf518118d`.
Installed companion: 17,903,249 bytes,
SHA256 `ead12f0e24721776b01522a1abb15b8e0ca1ce4955abdc9bb5ade7c233fb30b8`.
Both match installation.json; processLifecycleVersion=1. Native primary packaging differs from
worktree bytes; installed hashes are observed, not inferred from worktree artifacts.
Installed project classes: plugin 218 / companion 461, all bytecode major 55 (Java 11).

Task XML/SDDL compare identical before/after. Current caller elevation=false and task maintenance
readiness=false. No task ACL change, UAC prompt, elevated helper or account/database repair/replay.
The unassigned legacy database remains unassigned; it was not used to authorize recommendations.

The updater log prints backup verification, companion ready and both JARs verified. The private
Start-Process log helper lost the native exit code (null) and exited 1 on that missing receipt.
This is not an observed nonzero updater exit. The update was not repeated. Independent checks
below establish installed-pair and API state. Exact logs/JSON are private in primary .gradle:
recommendation-update.{out,err}.log, recommendation-update-exit.json, recommendation-installed-proof.json,
recommendation-task-{before,after}.json.

## Independent checks and launch

Native Windows PowerShell status, run-dev CheckOnly and explicit-account pipeline each exit 0:

- Status: PAIR VERIFIED / COMPANION RESPONDING; saved login present; process lifecycle enabled.
- Compatibility: RuneLite 1.13.2 matches installed API; cached artifacts verified.
- Pipeline: both artifacts pass, accounts-v1 passes, market READY. Ledger 11 sale updates/eight
  unresolved/zero profit ranges. Action WAIT; plan UNAVAILABLE awaiting client coins/free slots.
  No synthetic snapshots, offers or trades posted. Full private output: recommendation-pipeline.log.

The supported tools/start.ps1 was launched with a hidden console. One developer-mode Java client
was observed; its log confirms the installed plugin was side-loaded and client initialization
completed. Stderr contains the existing RuneLite ReflectUtil reflective-access warnings. No login,
UI acceptance or controlled trade outcome was observed; no game input was sent. Private logs:
recommendation-launch.{json,log,err}, recommendation-status.log, recommendation-compatibility.log.

## Remaining gates

Issue #70 is open. These fixes do not restore historical stock or release risk reserves. Exact
remainder-constrained server candidates, durable buy replacement intent/restart, companion sell/exit
policy consolidation and controlled manual workflows remain open. Learning stays PUBLIC_PRICE_BASELINE
with promotion held. All unchecked release gates remain in [TASKS](../../TASKS.md).

The old GitHub billing lock was reproduced by check annotations, then resolved after the owner
updated billing. Both final runtime CI checks actually executed and passed. No further billing
action is required by this delivery. This receipt is documentation-only and needs no reinstall.

## Retained pre-delivery checkpoint (historical)

## Agent handoff

Updated 17 October 2026 (America/Anchorage). Read [AGENTS.md](../../AGENTS.md) and [TASKS.md](../../TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

### Current source continuation

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
[the contract](../architecture/replacement-entry-authority.md). Exact remainder construction and
durable replacement restart workflow are still open; this is not activation of the old scaffolding.
Standard Windows clean verification passes in 1m 2s / 18 tasks / exit 0: 1,566 Java tests,
1,560 passed/six existing skips, zero failures/errors. Plugin 490,419 bytes; companion 17,903,176;
all project bytecode Java 11. Earlier 103 tooling tests remain applicable (tooling unchanged).
Focused actual-delivery/partial-buy tests also pass. Logs: reusable .gradle/replacement-entry-*.
This source is not installed; both exact-head CI checks must pass before merge and paired delivery.
Continuation b374b5a reproduces two actual editor/key-queue faults before fixing them. Entry WAIT
now immediately withdraws BUY/MODIFY_BUY instead of being deferred behind its editor, and advances
the guide generation so a queued player-key fill cannot survive refusal and identical reapproval.
Other actionable trade deferral stays intact. Final standard for this added change passes in 54s /
18 tasks / exit 0: 1,568 Java tests, 1,562 passed/six existing skips, zero failures/errors.
Focused guide/key/refresh tests also pass. Logs: reusable .gradle/entry-refusal-editor-*.
No tooling changes; the prior 103-test Python gate remains applicable. PR #80's latest exact head
requires both CI checks before merge/install; earlier checks at 3944767 do not cover this addition.

### Merged safe sell-relist slice — not installed

PR #79 preserves exact taxed cost floors, canonical/visible cancelled remainders, fresh scoped
ledger/quote checks, managed cost-required guidance and episode matching through restart. Clean
external sale profit stays unknown. Regressions 02d1c4b/8692ed3/bc05fb7 precede the fixes.
Final standard: 54s / 18 tasks / exit 0; 1,555 Java passed/six skips; prior 103 tooling tests pass.
Plugin 489,750 bytes; companion 17,902,507 bytes. No real database edit, update or game input.
##70 and full policy/live-workflow acceptance remain open. Read the
[contract](../architecture/safe-sell-relist.md), [detailed receipts](../history/sell-guidance-2026-10-17.md)
and [continuation guide](../../CONTINUE%20DEVELOPMENT.md).

### Companion updates without administrator approval

Runtime #77 is installed from merge 3dcd8313255235dcca13e3d56016386876811710. Both exact-head
checks at fdcaa94 pass in run 38955937701 (Windows 117160940451 / Ubuntu 117160942779). An initial
Windows HTTP isolation-test timeout passed on retry without source changes. #76's optional ACL
repair was never applied. No initial UAC reply, task/permission change or elevated helper was used.
Read the [full delivery receipt](../history/normal-user-update-2026-10-16.md).

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
Read [the lifecycle contract](../architecture/user-process-updates.md),
[workflow](../development-workflow.md) and [Start here](../../START%20HERE.md).

### Installed runtime

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

