# Agent handoff

Updated 15 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## Frozen evaluation and read-only account capture — 15 October 2026

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
