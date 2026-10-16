# Agent handoff

Updated 15 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## Recommendation latency diagnosis and regression source — 15 October 2026

Owner reports multi-minute gaps, then no buys after changing to 5m/Medium and following cancellation
advice. Client remains open. Normal rebuild is clean at #63 merge
`e14121b3d5c6fbf157fa4f255ee7b4c58bf72d66`; installed runtime is still #61 via tooling #62 below.
Read-only probe: portfolio/current timed out at 15,141 ms; action took 3,516 ms. Forty pending
outbox observations were preserved. Learning read showed seven canceled/censored episodes, six
full forecasts, zero completed intervals; cancellations are not failed/completed fills.

Companion-only JVM thread dump showed account ingestion holding service/store locks in
SqliteStore.currentOfferEvidence while ledger reads waited. No game process was attached or stopped.
Read-only query inspection found the existing identity index is partial. The JDBC runtime rebuilt
an automatic covering index for the current-slot join because the nonblank predicate was missing.
Same scoped database/query: original 387 ms; existing-index predicate 1 ms. Python's SQLite chose
a different plan (full scan, 63 ms), so JDBC results are the relevant runtime evidence.

`codex/recommendation-latency` starts at that rebuild. Regression `17bf941` reproduces heartbeat and
restart reads exceeding deterministic SQLite VM budgets over 30,000 fixture rows; four source-guard
cases already pass. `016018e` fixes current-slot and original risk-anchor lookups using the existing
partial index, retaining LEFT JOIN missing-source failure. Regression `79555e1` separately
reproduces reading full canonical history twice for gap scope and capital bounds. Operation-local
history sharing fixes that duplication without a cross-request cache. A later-foreign-evidence
test ensures a successful earlier read cannot hide new uncertainty. See
[the evidence-read contract](docs/architecture/durable-risk-window.md#evidence-read-work-budget).

Before-query-fix read-only full planning probes took 2,052/866 ms (cold/warm); the index-only
candidate took 1,269/327 ms; the final shared-history candidate took 555/196 ms, retaining all eight
current sources. These are local active-database measurements, not a latency guarantee. Final standard:
BUILD SUCCESSFUL in 57s / all 18 tasks executed / exit 0; 1,477 Java passed, six existing skips,
zero failures/errors. Python: Ran 79 tests in 100.299s / OK / exit 0 (tooling unchanged thereafter).
Candidate plugin: 486,079 bytes / 218 project classes; companion: 17,878,040 bytes / 453 project
classes; every project class targets Java 11. All probes use existing explicitly scoped data in mode=ro; raw source
and row-level results stay in ignored local-data. No DDL/index migration, database repair, event
drop, accounting/risk reset, timeout increase or game input is part of the fix. Current risk/cadence
preferences stay as the owner selected them. Faster reads are not new buy authorization; require
post-update fresh acknowledgement, current cash/slot/risk and final entry-policy proof.

Private evidence: normal-checkout local-data/diagnostics/recommendation-*, companion-stall-threads-*
and ignored .gradle read-only probes; reusable worktree .gradle/recommendation-*.log. Runtime update,
live timing and resumed advice remain pending. Batch one supported paired update after both CI
checks; player closes/logs in manually, agent handles backup/install/launch/diagnostics.

## Installed through #61; supported recovery and fresh entry read — 15 October 2026

Normal rebuild is `fc62526a793e08a8a3b68320a14d2bf57edaa186` (#62 merge). Runtime Java is the
tested #61 source; #62 changes tooling only. #61 passed both checks at
`13e3ded155c6d485b8fa44f5f799086406434019` in
[run 38828090222](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38828090222).
#62 passed Windows job 116781965493 and Ubuntu job 116781965138 at
`e89323498d531b2edc022e71b86327a8022a88eb` in
[run 38841359657](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38841359657).
[Tooling delivery receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/62#issuecomment-6206825082).
Historical main remains `0cb8ef8`; required rebuild ancestry is preserved.

After Windows elevation, the supported updater used -Unattended -ResumeBackup with the exact
verified interruption below. Its read-only proof passed before the retry and again under the
installation lock. A fresh backup verified before both JARs were replaced:

```text
C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-184127-76f3a3f8
backup-verification.json            335 bytes / status VERIFIED
companion-task.xml                 1,925 bytes
flipping-friend-companion.jar 17,832,054 bytes
installation.json                   458 bytes
osrs-flipping-friend.jar          469,023 bytes
companion accounts: 3 files / 13,000,352 bytes
plugin accounts:   22 files / 1,498,001 bytes
```

The receipt checks old JAR/manifest hashes, task export and account relative names/sizes without
opening or hashing account contents. The original failed-attempt backup and logs remain intact.
The successful helper receipt names fc62526, success=true/exitCode=0, finishing at
02:41:41 UTC on 16 October (15 October local). Supported build: BUILD SUCCESSFUL in 2s, eight tasks
up-to-date. Installed plugin 486,099 bytes / 218 project classes and companion 17,877,081 bytes /
452 classes match normal build and manifest hashes. Every project class matches the standard-tested
candidate and targets Java 11; ZIP packaging is 20 bytes larger per JAR without a class change.

```text
PAIRED_INSTALL_PROOF=PASS / PAIRED_PROOF_EXIT=0
Overall status: PAIR VERIFIED / COMPANION RESPONDING / STATUS_HOST_EXIT=0
RuneLite 1.13.2 and the installed plugin API match; cached artifacts verified.
COMPATIBILITY_HOST_EXIT=0
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_HOST_EXIT=0
```

The agent started tools/start.ps1 with hidden console/log capture. One RuneLite client was observed;
initialization completed in 9,302 ms. The owner logged in; fresh authenticated account observations
arrived. Post-login pipeline exit 0 retains four sale updates/two unresolved and shows Action WAIT /
Plan READY. Read-only plan/risk/final-policy checks observed:

```text
First capture: PLAN_STATUS=READY / ALLOCATION_COUNT=3
RISK_STATUS=AVAILABLE / RISK_FRESH=True / RISK_BUDGET_POSITIVE=True
ENTRY_ACTION=WAIT / ENTRY_REASON=COMPANION_STATE_UNAVAILABLE
Later current-plan read: PLAN_STATUS=READY / ALLOCATION_COUNT=4 / RISK_STATUS=AVAILABLE
ENTRY_ACTION=PLACE_BUY / ENTRY_REASON=ENTRY_CANDIDATE_SELECTED
LEARNING_STATUS=OBSERVING / LEARNING_MODEL=PUBLIC_PRICE_BASELINE
LEARNING_PROMOTION=HOLD_FOR_VALIDATION / LEARNING_OBSERVATIONS=0 / LEARNING_FULL_FORECASTS=0
```

Both entry responses are retained, including the initial refusal; no offer was placed by the
agent. Fresh GET approval does not certify UI behavior, fills, profit or controlled restart. The
first login logs contain a RuneLite LootTrackerPlugin.onChatMessage NullPointerException at
18:43:20 local; the recorded stack contains no Flipping Friend frame. It is not a clean-exception
claim or a reproduced Flipping Friend bug. Normal reflective-access warnings are also retained.

The owner reports "everything looks good so far" and supplied two live screenshots. BUY guidance
matches the committed editor quantity/price and a normal highlighted Confirm. In the negative case,
both committed values differ: both input controls remain highlighted and Confirm says Locked-rightclick.
Both show eight free slots, so no placement
is established by those images. One image shows waiting for the account ledger; the other shows
known recorded profit 6,480 GP with four sale updates/two unresolved. Screenshot order does not
establish a timed availability sequence. Search and player key behavior are not visible in these
images and remain specifically unverified.

Subsequent player offers reached canonical intake. The first read observed one open BUY with a
full original forecast, predictionComparable=true and timingCoherent=true; no fill bound was present.
A read-only complete-prefix freeze at 18:53 local then observed three episodes, two eligible
forecasts and one unqualified episode. Both eligible horizon outcomes remain ambiguous, with zero
known completions/incompletions; they are not labeled failed fills or successful flips. Packaged
verification, independent retained-artifact verification and comparison against the pre-install
frozen baseline all exit 0. The comparison confirms an unchanged training prefix and a later
prospective cohort; promotion remains HOLD_FOR_VALIDATION, without fitting or return claims.

```text
status=VERIFIED / sourceCursor=6695 / sourceRows=5656
totalObservations=3 / eligible=2 / knownCompleted=0 / knownIncomplete=0 / ambiguous=2
manifestSha256=976e5d51f4b52db1a5eae7051587e2bb0bdb19fb39166c8a5d92daf775c5d785
READ_ONLY_FREEZE_EXIT=0 / CAPTURE_VERIFY_EXIT=0 / RETAINED_ARTIFACT_VERIFY_EXIT=0
FROZEN_PREFIX_COMPARE_EXIT=0 / status=RECORDED_PREFIX_COMPARISON
```

Private capture: normal checkout local-data/execution-evaluation/live-first-prospective-20261015-185318.
Its evaluator.jar.bin is 17,877,081 bytes; baseline is the reusable worktree's
local-data/execution-evaluation/baseline-20261015-retained. Raw sources, forecasts, scored rows and
CLI logs remain in local-data. These reads do not edit the live database or require closing the game.
Controlled fill/collection/sell completion, partial cancel/relist and restart remain pending.

Private normal-checkout evidence: .gradle/batched-forecast-{update,retry}*, batched-install-*,
batched-launch.* and local-data/diagnostics/batched-{postinstall,postlogin}-pipeline-20261015.txt,
batched-live-{plan,entry,learning}-20261015-*.json. No agent game input, synthetic live POST, manual
database edit, migration, repair, assignment or historical backfill occurred. Normal application
ingestion continues from real client observations. This documentation receipt changes no runtime
source and requires no reinstall. Player UI/search/key/Confirm checks were requested as one batch;
the screenshot Confirm/highlight checks above pass, with the remaining UI checks still open.
controlled trade/partial cancel/collect/relist/restart acceptance remains pending. Next implementation
work remains companion sell/replacement authority and #56 evaluation/learning gates.

## Interrupted paired update and tooling regression — 15 October 2026

At clean rebuild `75190a28a6b6f680ca616517aac61d2b01180574` (#61 merge), the owner approved
Windows elevation. The supported -Unattended updater created and verified
`C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-181822-c05e3e5f`:
three companion account files and 22 plugin account files; JAR/manifest hashes and task export match.
It then stopped in tools/build.ps1 when PowerShell 5.1 redirected a normal javac deprecation note
as a NativeCommandError under Stop. Receipt success=false/exitCode=1 at 02:18:33 UTC on 16 October
(15 October local). Installed JARs/manifest were not replaced; the #53 task is disabled.

`codex/update-build-recovery` starts at that rebuild. Regression `83abfda` reproduces the native
warning abort and missing interrupted-update recovery before source changes. The builder now
retains diagnostics and checks the native exit code; real build failures stay failures. Explicit
-Unattended -ResumeBackup proves the unchanged pair/manifest, original task definition except
Enabled, verified local backup and account names/sizes, then repeats proof under the lock. It never
enables the task before both copies, changes a database or rewrites the old backup. Ordinary disabled
tasks remain rejected. See [recovery workflow](docs/development-workflow.md).

Standard verification: BUILD SUCCESSFUL in 49s, all 18 tasks executed; 1,469 Java passed, six existing
skips, zero failures/errors. Python: Ran 79 tests in 71.147s / OK / exit 0. Exact-head CI is to be
recorded at delivery.
Runtime Java is unchanged from tested #61; installed runtime remains #53 until the supported retry.
No live readiness, launch or trading acceptance is claimed by this checkpoint.

## Exact exit cost source; paired update not started — 15 October 2026

`codex/exact-exit-costs` starts from #60 merge `36ee500`. Regression `f446c27` reproduces rounded
cost overstating actual initial sale guidance and allowing a fractional-average loss to look like
break-even. `fa31f49` reproduces the same errors in sell maintenance. Source
`4198a8cf84e53afd13314101940fb9ed96341e87` uses exact total acquisition costs in price ranking,
the durable SaleAccounting.allocateCost convention for suggested/repriced subsets, and the lowest
legal taxed quote covering those costs. Stabilization cannot defeat the exact floor; unreachable
non-loss prices are reported rather than clamped to a false break-even. Read
[the contract](docs/architecture/exact-exit-costs.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 48s / 18 actionable tasks: 18 executed / exit 0
Plugin: 631; Core: 179; Companion: 665 including six existing skips
Total: 1,469 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 56.930s / OK / exit 0
Candidate plugin: 486,079 bytes / 218 project classes
Candidate companion: 17,877,061 bytes / 452 project classes
Both project JARs: class major 55 (Java 11)
```

All changes are source-only; no game input, manual financial-row write, historical profit rewrite
or tax-rule migration occurred. Private evidence: reusable worktree .gradle/exact-exit-*.log.
Companion exit-policy migration and full-account performance evaluation remain open.

After #60 merge, status read verified the installed #53 pair/companion responding and no RuneLite
process was observed. One hidden elevated helper was requested for the authorized -Unattended
paired update. Windows returned "The operation was canceled by the user" before Start-Process
returned a PID. No updater receipt/log or helper process appeared; installation never started.
No backup or task/data change was performed by that attempt. Do not start a second simultaneous
updater or claim #57 onward is installed. The next successful administrator launch must target the
then-current clean rebuild revision. Login and every game action remain manual.

## Frozen evaluation and read-only account capture — 15 October 2026

PR #60 merged as `36ee500dd0b682a786adc073cae2ffb6a8502e87` after both checks at
`5993fc56eddbab1498250afef6259a5760867323` passed in
[run 38826360749](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38826360749).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/60#issuecomment-6204815483).

`codex/frozen-execution-evaluation` starts at #59 merge `0fba976`. Runtime/tool source through
`39569c2` implements a complete offline freeze -> verify -> recorded-prefix comparison workflow.
Regressions `3b71dec` (literal-hash read-only path), `25f5bec` (unassigned named gap ignored) and
`7115219` (proven clear context globally refused) precede fixes. Existing TradeGapEvidence source
proof now scopes evaluation uncertainty by affected items; unknown source scope remains held.
The read-only HTTP learning report and offline evaluator use the same complete source snapshot.

Captures retain all source rows, raw reconciliation evidence, original forecasts, hashes and the
actual executing evaluator artifact. Verification reproduces scores without opening a database.
Horizon Brier/completion-rate bounds retain early cancellations and uncertain readout intervals as
unknown. Comparison rejects changed/reversed/foreign prefixes and excludes older offers settling
later. This is not fitted learning or measured account returns. See
[the contract and commands](docs/architecture/frozen-execution-evaluation.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 39s / 18 actionable tasks: 18 executed / exit 0
Plugin: 623; Core: 176; Companion: 665 including six existing skips
Total: 1,458 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 57.643s / OK / exit 0
Candidate plugin: 485,476 bytes / 218 project classes
Candidate companion: 17,876,458 bytes / 452 project classes
Both project JARs: class major 55 (Java 11)
```

Packaged PowerShell 5.1 read-only capture of the explicitly scoped existing account passed, then
the retained evaluator.jar.bin independently verified its capture without reopening that database:

```text
status=VERIFIED / sourceCursor=5573 / sourceRows=4651
totalObservations=0 / eligible=0 / promotionStatus=HOLD_FOR_VALIDATION
manifestSha256=a4f47e35bc4a296c9854a1204384faded28edb48d3fb9f4feeac3deb475d1da6
READ_ONLY_CAPTURE_EXIT=0 / RETAINED_ARTIFACT_VERIFY_EXIT=0
```

Zero prospective observations reflects the installed #53 source; older rows were not backfilled.
The first capture attempt safely refused unnamed historical gaps; a reproduced source-scope fix,
not a database change, allowed capture. No rows were repaired, assigned or removed. Private data:
reusable worktree local-data/execution-evaluation/baseline-20261015-retained; logs are ignored
.gradle/frozen-evaluation-*.log. The earlier first capture remains private historical evidence.
No new runtime installation, live pipeline result or game action is claimed. Runtime remains #53;
batch #57/#58/#59 and this source for the next paired update. Issue #56 remains open for supported
censor-aware fitting, frozen challenger/training artifacts, chronological calibration/return/loss
gates and full-account replay at all four cadences. Hashes alone do not authenticate historical time.

## Accepted guidance and immutable leg forecasts — 15 October 2026

PR #59 merged as `0fba976167b310f5fb800dfe635764ba7290c1b2` after both checks at
`2b31e06d8bf3431d3319f9869dd9a314d1588114` passed in
[run 38810497176](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38810497176).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/59#issuecomment-6202774257).

`codex/immutable-execution-forecasts` starts at rebuild `f161a39` (#58 merge). Regression `9471568`
reproduces premature proposal attribution, whole-flip probability used for a buy, and missing sell
timing replaced by whole-flip timing. `8ffb3b2` reproduces the generic sell-editor quote overwriting
managed guidance. The active entry-policy converter's missing leg metadata was also observed as
a failing behavioral test before correction; it now shares conversion with the legacy adapter.
Implementation is `a18210f1fac3e064cff82fdb3025ec91a118fb80`.

Only client-thread accepted foreground guidance records evidence; paused, stale, foreign and
deferred proposals do not. Buy/sell forecasts keep actual price/quantity, configured model/version,
horizon, curve inputs, waiting, working, minimum overhead and probability. Original outbox binding
survives ACK/restart/expiry. The canonical readout validates and exposes the original snapshot;
missing older context stays missing. Managed sells recompute for actual displayed inputs; generic
personal quotes stay unknown and cannot erase an exact managed instruction. See
[the contract](docs/architecture/immutable-execution-forecasts.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 39s / 18 actionable tasks: 17 executed, 1 up-to-date / exit 0
Plugin: 623; Core: 176; Companion: 644 including six existing skips
Total: 1,437 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 49.509s / OK / exit 0
Candidate plugin: 485,476 bytes / 218 project classes
Candidate companion: 17,856,011 bytes / 441 project classes
Both project JARs: class major 55 (Java 11)
```

Private regression/build evidence: reusable worktree .gradle/immutable-forecast-*.log. Full-suite
source-hash/text guards were updated for reviewed metadata and converter changes; behavioral gates
remain. Runtime is still the #53 pair below; #57/#58 and this source are not installed. Batch the
next supported paired update and player acceptance. No new live pipeline or trade result is
claimed; no manual database change or agent game input occurred. Promotion stays held. #56 remains
open for censor-aware fitting, frozen baseline/manifests, chronological return/loss/calibration
gates and full-account four-cadence evaluation.

## Observation intervals and calibration fence source — 15 October 2026

PR #58 merged as `f161a3964da8e00d0f592d033eddc55e39e7231d` after both checks at
`910a5c0e5161d3be5d54a1032d186227651c7bc3` passed in
[run 38777494280](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38777494280).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/58#issuecomment-6198271091).

`codex/execution-observation-intervals` starts from #57 merge `58480d6`. Regression `d209012`
reproduces the actual account planner halving waiting estimates from forty canceled offers, or
from forty completed offers available only as aggregate totals. Prospective source reads now keep
placement, first-fill and full-completion intervals, censor canceled/ended observations, preserve
the first authenticated terminal bound across reconnects and fail closed on inconsistent clocks
or source scope. Account API GET learning/current exposes bounded diagnostic rows/full counts;
account/header/token/method isolation and SELECT-only reopening pass. The service reads this
canonical evidence and keeps the public-price baseline active pending challenger validation.

This is a promotion fence and real observation pipeline, not completed learning. Even forty
qualified completions cannot promote without forecast decomposition and chronological account
return/loss/calibration gates. Historical aggregates/claims, financial source rows, uncertain sales
and all exposure guards remain unchanged. Read [the contract](docs/architecture/execution-observation-intervals.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 48s / 18 actionable tasks: 18 executed / exit 0
Plugin: 606; Core: 176; Companion: 641 including six existing skips
Total: 1,417 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 49.946s / OK / exit 0
Candidate plugin: 477,859 bytes; companion: 17,848,203 bytes
Both project JARs: class major 55 (Java 11)
```

New-path identity collision, contradictory-clock, source-cursor and terminal-quantity edge failures
were also observed before correction; private logs are .gradle/execution-observation-*.log in the
reusable worktree. Source is not installed; runtime remains the #53 pair below. Batch #57 and this
change with the next supported paired update. No manual live database write or agent game input
occurred. Issue #56 remains open: immutable model/horizon/wait-work forecast context, censor-aware
fitting, frozen manifests/baseline, held-out evaluation, full-account four-cadence replay and promotion.

## Execution prediction provenance source — 15 October 2026

PR #57 merged as `58480d679981e7156f979c2ff89134401aa40579` after both checks at
`b37a19d9bc0da34ab811c443a240825d7dcd84c1` passed in
[run 38772952899](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38772952899).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/57#issuecomment-6197609988).
Branch `codex/execution-advice-provenance` started at rebuild `b516b7e` (#55). The installed
runtime remains through #53, with the live READY/entry GET receipt below; this source work is not
installed or live-accepted. Batch a later paired update, without another immediate login cycle.

Regressions `d1d18dd` (six publisher failures), `43f5c34` (five companion failures), `942fcbe`
(refreshed forecast borrowing an older clock), `878db45` (missing provenance) and `32a567d`
(saved binding schema) and `2980fa5` (same-second ambiguity) preceded their fixes. Original advice and negative bindings now persist
atomically with the existing account outbox and survive ACK/restart. The companion validates the
earliest original source and uses its prediction without rewriting raw events. Unknown/mismatched
evidence remains unpaired; existing accounting, dedup keys, gaps and historical totals remain.
See [the provenance contract](docs/architecture/execution-prediction-provenance.md).
Runtime implementation is `c595c42`, `08d4832` and the subsequent strict same-second fix. Current
forecast time must strictly precede first observation to train timing; same-second association can
remain diagnostic. Positive maintenance fixtures now begin with a real authenticated,
zero-fill source before a partial observation; cached or already partial starts cannot invent lineage.

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 46s / 18 actionable tasks: 18 executed / exit 0
Plugin: 606; Core: 176; Companion: 623 including six existing skips
Total: 1,399 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 59.273s / OK / exit 0
Candidate plugin: 473,199 bytes; companion: 17,837,507 bytes
Both project JARs: class major 55 (Java 11)
```

Private evidence is in the reusable worktree's ignored .gradle/execution-*.log. No live database
was changed by the agent, no new installation occurred and no game input was sent. This repairs
prediction attribution, not cancellation censoring, offline completion intervals or calibration
promotion. Issue #56 remains open for those gates and chronological full-account evaluation.

## Installed #51/#52/#53 pair; live entry read passes — 15 October 2026

At this installation checkpoint, normal rebuild was `33f4dc0f18ab6505edb16b54d7c77da3b60b6f6f`
(documentation #54). Installed runtime source is the tested #53 merge `3362b62`; no runtime source changed
in #54 or this receipt. #53 CI and full verification are recorded below. #54 passed both checks
at `37a7bd184a4b7e66595477f25e0d9ad5ffa9860e` in
[run 38765010660](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38765010660).
Runtime PR #53 passed both checks at `65d6f1fcc96703ce4f4c1cab6053df317c57445c` in
[run 38763296900](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38763296900).
Historical main remains `0cb8ef8` and required ancestry remains intact.

The earlier elevated helper was canceled before it started. The owner then opened the supported
administrator Update shortcut. The agent observed its active process/installation lock and verified
its fresh backup read-only before the owner supplied INSTALL in that existing console. No second
updater was launched. Backup:

```text
C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-080957-c3a069ab
companion-task.xml                 1,895 bytes
flipping-friend-companion.jar 17,812,619 bytes
installation.json                   458 bytes
osrs-flipping-friend.jar          461,863 bytes
companion accounts: 3 files / 10,407,584 bytes
plugin accounts:   22 files / 1,499,405 bytes
```

Prior JAR/manifest hashes matched; account relative names/sizes matched without opening/hashing
their contents. Saved/live task action and working directory matched, and both principals resolved
to the current-user SID. An initial text-only principal comparison differed because SID and display
name are different representations; resolved identity verification passed. This was an interactive
update with recorded pre-install checks, not an unattended backup-verification.json receipt.

The new manifest names that backup. Installed plugin 469,023 bytes and companion 17,832,054 bytes
match their normal-checkout builds and manifest hashes. All 214 plugin / 435 companion project
classes exactly match the standard-tested candidate; all target Java 11. The 20-byte package-size
difference from candidate JARs does not change any project class. Read-only receipt:

```text
PAIRED_INSTALL_PROOF=PASS / PAIRED_PROOF_EXIT=0
Overall status: PAIR VERIFIED / COMPANION RESPONDING
STATUS_HOST_EXIT=0
RuneLite 1.13.2 and the installed plugin API match; cached artifacts verified.
COMPATIBILITY_HOST_EXIT=0
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
PIPELINE_HOST_EXIT=0
```

The agent launched tools/start.ps1 with a hidden console and captured logs. One RuneLite client was
observed; initialization completed. Fresh authenticated account observations subsequently arrived.
The post-launch GET-only pipeline exited 0, preserving four sale updates/two unresolved, with
Action WAIT / Plan READY. A separate account-scoped GET observed an unavailable current-proof
response; a later read returned the following actual result:

```text
PLAN_STATUS=READY
ALLOCATION_COUNT=4
RISK_STATUS=AVAILABLE
RISK_FRESH=True
RISK_BUDGET_POSITIVE=True
ENTRY_DECISION=ENTRY
ENTRY_ACTION=PLACE_BUY
ENTRY_REASON=ENTRY_CANDIDATE_SELECTED
GET_ONLY_PROOF_COMPLETE=True
LAUNCH_ERROR_EXCEPTION_LINES=0
```

Final approval was a read-only policy GET, not a placed offer. Do not infer UI selection, successful
fills, realized returns or controlled trade/restart acceptance from it. Login UI itself was not
inspected. Launch stderr contains RuneLite's reflective-access warnings; no ERROR/Exception line
was found in either captured launch log at the check. No agent game input, synthetic live POST,
manual database edit, repair, migration, historical backfill or owner assignment occurred. Normal
application accounting/risk ingestion continues from real client observations.

Private normal-checkout evidence: .gradle/durable-risk-install-proof.py, .gradle/durable-risk-launch.*,
and local-data/diagnostics/durable-risk-{postinstall,postlaunch}-pipeline-20261015.txt plus
durable-risk-live-{plan,entry}-20261015.json. The old canceled helper receipt remains absent.
Keep this current checkpoint compact; previous full states are archived below. Next: player home
checklist acceptance, calibration provenance/censoring audit and companion sell/replacement work,
followed by chronological whole-account cadence evaluation. No additional install for this
documentation-only receipt is required.

## Source contract and verification

Final runtime source is `ef4d863bff77dfbf0355fa2f399688833b0b818e`; the merged tree includes its
regressions and documentation. The original foreign-journal-session regression is `774a521`, local
cache isolation is `bb4339d`, durable migration is `a97e692`, and schema regressions are `02744c7`
before the final fix. The retention failure was also reproduced before its correction.

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 44s / 18 actionable tasks: 18 executed
RISK_FINAL_STANDARD_EXIT=0
Plugin: 591; Core: 175; Companion: 613 including six existing skips
Total: 1,373 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 48.631s / OK / RISK_PYTHON_EXIT=0
Candidate plugin: 469,003 bytes; companion: 17,832,034 bytes
Both project JARs: class major 55 (Java 11)
```

The earlier complete 39s build passed with RuneLite open; the final schema-corrected build ran
after its normal close. Native RuneLiteLAF Plan rendering passed clipping checks and visual review.
Private build/regression evidence remains under the reusable worktree's ignored .gradle/durable-risk-*.
No extra local runtime rebuild is needed for this documentation-only checkpoint: Java sources match
3362b62 and the exact-head CI-tested tree.

The live planner and final entry approval use acknowledged fresh eight-slot/cash proof, conservative
historical capital bounds, paused affected items and one durable prospective risk result. The risk
window records an account/session/cursor, uses realized profit ranges, fresh taxed marks and possible
loss reserves, and survives restart. Client journal totals and wire-supplied risk never authorize
runtime entries. Unknown profit stays unknown. No old unscoped database is assigned, no history is
backfilled, and no live financial rows were repaired or pruned.

## Active source map

| Boundary | Contract/source |
| --- | --- |
| Current cash, offers and historical bounds | [bounded-current-planning.md](docs/architecture/bounded-current-planning.md); CurrentOfferReadSet, BoundedHistoricalExposure, BankCashEvidence |
| Durable risk and restart | [durable-risk-window.md](docs/architecture/durable-risk-window.md); AccountRiskSnapshot, DurableRisk, RiskWindow, RiskLedgerEvidence |
| Accounting and trade ownership | [durable-profit-ledger.md](docs/architecture/durable-profit-ledger.md), [automatic-trade-scope.md](docs/architecture/automatic-trade-scope.md), [trade-gap-scoping.md](docs/architecture/trade-gap-scoping.md) |
| Observation recovery and learning writes | [offer-observation-recovery.md](docs/architecture/offer-observation-recovery.md), [execution-evidence-atomicity.md](docs/architecture/execution-evidence-atomicity.md) |
| Compact UI and manual assistance | [compact-panel.md](docs/architecture/compact-panel.md), [trade-input-assistance.md](docs/architecture/trade-input-assistance.md) |
| Delivery and home acceptance | [development-workflow.md](docs/development-workflow.md), [HOME TEST CHECKLIST.md](HOME%20TEST%20CHECKLIST.md) |

## Remaining work and historical evidence

Complete controlled acceptance from the current installed READY/entry proof. Player login
and all game input remain manual. Then finish companion sell/replacement workflows, remaining
calibration authority, controlled UI/partial-fill/cancel/collect/restart acceptance and chronological
whole-account replay. Benchmark 100M GP, members, eight slots, with 5m/30m/2h/8h visits reported
separately. Historical Copilot exports lack session/cadence labels; do not invent GP/hour or superiority.

Older full checkpoints are preserved in [handoff history](docs/history/handoff-through-2026-10-15.md)
and [task history](docs/history/tasks-through-2026-10-15.md). They include detailed PR #31 live
verification steps, PR #49 installation receipt, earlier PRs and all unfinished acceptance evidence.
Read them only for a specific investigation; their older states and instructions are superseded by
this checkpoint and AGENTS.md. Personal CSVs/raw diagnostics stay ignored in local-data/.
