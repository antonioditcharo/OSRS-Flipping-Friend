# Agent handoff

Updated 15 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## PR #53 delivered; Windows elevation canceled — 15 October 2026

[PR #53](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/53) is merged as
`3362b62bee6256527ba4c401106713c70336308d`. Both final-head checks passed at
`65d6f1fcc96703ce4f4c1cab6053df317c57445c` in
[run 38763296900](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38763296900):

```text
Supported build (ubuntu-latest): completed / success
Supported build (windows-latest): completed / success
```

Normal rebuild is clean, fast-forwarded and tree-identical to that tested head. Required ee0d3aff
ancestry exits 0; historical main remains `0cb8ef8aab9114f02b16d2eca4433c0f7e010894`.
Final source/standard/Python evidence is summarized below: 1,373 Java passed/six existing
skips, both Java 11 JARs, 44s; all 63 Python tests passed. This checkpoint is documentation-only;
runtime source is unchanged from the tested/merged tree.

The player confirmed RuneLite closed; process inspection observed zero clients. The agent prepared
the supported `tools/apply-update.ps1 -Unattended` helper and requested Windows elevation for the
existing companion task. Windows consent.exe was observed while no helper log or exit receipt
existed. Start-Process then returned exactly:

```text
This command cannot be run due to the error: An error occurred trying to start process
'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' with working directory
'C:\Users\Antonio\OSRS Flipping Friend Rebuild'. The operation was canceled by the user.
UPDATER_LOG_NOT_CREATED_YET
```

The updater did not start. No new backup, JAR replacement, task stop, database operation, launch
or game input occurred. Do not mistake the empty launcher process ID/PowerShell wrapper exit for
installation success. Read-only verification afterward:

```text
Companion task: Running
Overall status: PAIR VERIFIED / COMPANION RESPONDING
STATUS_AFTER_ELEVATION_EXIT=0
Manifest backup: ...\backups\20261015-011945-07ffac76
Installed companion: 17,812,619 bytes
Installed plugin: 461,863 bytes
```

Runtime is still verified #49. #51/#52/#53 remain one queued installation batch. The pre-install
API compatibility and GET-only pipeline are the existing runtime's results, not acceptance
of the new sources. Windows administrator approval is still required; do not bypass it or repeatedly
relaunch elevation after cancellation. The already pending player question asks about that prompt.
After renewed readiness, use the prepared normal-checkout .gradle/elevated-update-durable-risk.ps1
through Windows elevation, verify its fresh backup receipt and pair with
.gradle/durable-risk-install-proof.py, run status/compatibility/pipeline and launch via tools/start.ps1.
Those private helpers contain no credentials and do not patch databases. Observe actual outcomes;
do not infer a new risk window or a BUY before fresh authenticated post-install account evidence.

Current continuation branch: codex/durable-risk-live-verification from 3362b62. Deliver this
checkpoint through a documentation PR with both exact-head checks; no additional JAR install or
repeated local runtime build is needed for documentation-only changes. Remaining recommendation,
manual acceptance, calibration and whole-account benchmark work stays active.

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

Pre-install GET-only pipeline on the existing #49 pair (account ID redacted):

```text
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Working out the best use of your slots.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_EXIT=0
```

Read-only API compatibility host exit was 0 (1.13.2). A first launcher diagnostic named the wrong
script directory and exited -196608; corrected tools/run-dev.ps1 CheckOnly passed. These are
transport/compatibility results, not live BUY/UI/trade/restart acceptance or benchmark evidence.

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

Finish the queued supported installation and observe fresh account/risk/plan evidence. Player login
and all game input remain manual. Then finish companion sell/replacement workflows, remaining
calibration authority, controlled UI/partial-fill/cancel/collect/restart acceptance and chronological
whole-account replay. Benchmark 100M GP, members, eight slots, with 5m/30m/2h/8h visits reported
separately. Historical Copilot exports lack session/cadence labels; do not invent GP/hour or superiority.

Older full checkpoints are preserved in [handoff history](docs/history/handoff-through-2026-10-15.md)
and [task history](docs/history/tasks-through-2026-10-15.md). They include detailed PR #31 live
verification steps, PR #49 installation receipt, earlier PRs and all unfinished acceptance evidence.
Read them only for a specific investigation; their older states and instructions are superseded by
this checkpoint and AGENTS.md. Personal CSVs/raw diagnostics stay ignored in local-data/.
