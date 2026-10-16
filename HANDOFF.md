# Agent handoff

Updated 16 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## Verified-capture trade evidence audit — 16 October 2026

Primary rebuild is clean/current at #72 merge `eaa63b1cfc11351fbe96bf75603fbcb29799cca4`.
#72 passed both checks at `da449a7a6d7d801ef76a6dca4921027884dcf3e8` in
[run 38876972197](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38876972197)
(Windows 116894240240 / Ubuntu 116894240365). [Delivery receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/72#issuecomment-6211948560).
Runtime remains installed #69/86f69da; #72 is queued for a batched update. This new offline tool
does not require another installation or player restart. Historical main remains untouched.

The previous goal turn was progress: it delivered the tested episode guard, fixed stale cached
Collect advice, and established fresh #69 eight-slot/acknowledgement proof. New branch
`codex/trade-evidence-audit` starts from eaa63b1. The next safe action is to establish explicit
source ambiguities for #70, preserving original financial rows rather than replaying old money.

New tools/audit-trades.ps1 runs a complete verified-capture -> private audit -> recomputation
workflow. No database, token, service or game client is opened. Source references and anomaly flags
identify repeated identities, initial filled baselines, missing terminals, regressed counters and
metadata/order/session/clock conflicts. Raw cumulative maxima are never summed into positions or
profit. Reports always authorize neither accounting nor trading; unflagged rows are not eligible
holdings. Parent capture, report bytes and retained actual auditor artifact are hash-bound.
Foreign/changed prefixes, rehashed edited claims, unsupported versions, missing/incomplete outputs
and overwrites fail. Read [the contract/commands](docs/architecture/trade-evidence-audit.md).

Feature-first tests c3f4936/476b0b8 initially fail compilation because the audit capability does not
yet exist; they are not claimed as regressions of shipped runtime. Regression 744b75b separately
reproduces unsupported manifest versions being accepted in the new unshipped command before its
constructor-field fix. Final standard: BUILD SUCCESSFUL in 47s / 18 tasks executed / exit 0;
Java 1,520 passed/six existing skips, zero failures/errors (641 plugin/179 core/706 companion).
Python: 79 tests in 85.248s / OK / exit 0. Plugin 486,838 bytes/218 project classes; companion
17,895,369 bytes/460 classes; all project bytecode Java 11. Exact-head CI/delivery pending here.

The packaged PowerShell 5.1 command audits the retained complete source prefix from 00:03 local,
then wrapper verification and independent verification using its retained auditor.jar.bin pass:

```text
status=OBSERVATION_AUDIT_ONLY / sourceRows=11598
identities=35 / identitiesWithFlags=23 / unassignedProblems=0
EMPTY_WITHOUT_TERMINAL=13 / IDENTITY_REAPPEARED_AFTER_EMPTY=19
INITIAL_FILLED_BASELINE=6 / INITIAL_TERMINAL_BASELINE=4
RECORDED_RECONCILIATION=19 / TERMINAL_TO_ACTIVE=1
authorizesAccounting=false / authorizesTrading=false
OWNER_CAPTURE_AUDIT_EXIT=0 / OWNER_AUDIT_VERIFY_EXIT=0 / RETAINED_AUDITOR_VERIFY_EXIT=0
sourceManifestSha256=003c8d207f1462215fe7455413c8223ee630032d419ee939ff945bae12bad3b6
auditManifestSha256=42c07d429a5bc19f8a0552d5d65f5c8c1febbb3f40ae3ddd73a236fe0ef84f2d
```

Flag counts overlap and describe observed identities, not erroneous trades or losses. No remaining
quantity/cost/profit or clearance of the old reserve is inferred. Inputs lack complete financial
effects and independent disposal/funding evidence, so the missing-sell problem remains open in #70.
An additional private exploratory terminal-envelope calculation (JSON only, not production policy)
reduces the raw envelope for 15 terminal-witness segments but retains 14 full envelopes; the
candidate raw upper bound remains above the account allowance. It does not authorize a risk change
or establish a safe recovery algorithm. Do not implement it merely to reduce a refusal.

Private primary bundle: local-data/trade-audits/sell-recovery-20261016, alongside the original
local-data/execution-evaluation/sell-gap-evidence-20261016. Raw rows/items remain private. Reusable
worktree .gradle/trade-audit-*.log records feature tests, verification and actual commands. Private
primary .gradle/terminal-envelope-investigation.py and local-data/diagnostics/terminal-envelope-
investigation-20261016.json retain the exploratory calculation. No game input, database repair,
financial replay/backfill, risk reset, adoption, outbox clearing or player interaction occurred.

## Installed runtime and queued source

Installed runtime is #69/86f69da, API 1.13.2: plugin 486,494 bytes/218 project classes and companion
17,878,455 bytes/453 classes, all Java 11. Verified backup: 20261016-002238-6687103d. Status,
compatibility and GET pipeline pass; exact pair/build/class receipts are preserved in the history below.
Newest installed proof is fresh/valid, eight EMPTY/eight matched sources, capture age three seconds,
5m/Medium; a four-event outbox batch drains normally to zero. Thirty post-risk-cursor gaps remain
unchanged. Risk still refuses entries on historical uncertainty; this is not a realized-loss claim.

#72/eaa63b1 is merged/CI-passed but not installed. It preserves persistent new episode IDs and legacy
keys, prevents replayed cumulative accounting after clear, and withdraws stale Collect cache on
rejection. Batch its supported paired update with remaining work; do not create another player cycle
solely for this offline audit. Login and every game action remain manual.

## Active source map

| Boundary | Contract/source |
| --- | --- |
| Current cash, offers and historical bounds | [bounded-current-planning.md](docs/architecture/bounded-current-planning.md); CurrentOfferReadSet, BoundedHistoricalExposure, BankCashEvidence |
| Durable risk and restart | [durable-risk-window.md](docs/architecture/durable-risk-window.md); AccountRiskSnapshot, DurableRisk, RiskWindow, RiskLedgerEvidence |
| Accounting and trade ownership | [durable-profit-ledger.md](docs/architecture/durable-profit-ledger.md), [automatic-trade-scope.md](docs/architecture/automatic-trade-scope.md), [trade-gap-scoping.md](docs/architecture/trade-gap-scoping.md) |
| Observation recovery and learning writes | [offer-observation-recovery.md](docs/architecture/offer-observation-recovery.md), [execution-evidence-atomicity.md](docs/architecture/execution-evidence-atomicity.md) |
| Startup, episode identity and offline audit | [startup-offer-evidence.md](docs/architecture/startup-offer-evidence.md), [offer-episode-identity.md](docs/architecture/offer-episode-identity.md), [trade-evidence-audit.md](docs/architecture/trade-evidence-audit.md) |
| Compact UI and manual assistance | [compact-panel.md](docs/architecture/compact-panel.md), [trade-input-assistance.md](docs/architecture/trade-input-assistance.md) |
| Delivery and home acceptance | [development-workflow.md](docs/development-workflow.md), [HOME TEST CHECKLIST.md](HOME%20TEST%20CHECKLIST.md) |

## Remaining work and historical evidence

Keep #70 open: establish safe remaining quantity/cost and conservative risk/exposure from complete
financial sources and independent missing evidence. No manual database repair, source replay into
a writer, adoption, profit backfill or risk reset. The audit above identifies uncertainty only.
Finish companion sell/replacement authority, calibration consumers, controlled UI/partial-fill/
cancel/collect/relist/restart acceptance, chronological account replay and gated learning (#56).
Report 100M members/eight-slot benchmarks separately for 5m/30m/2h/8h. Historical CSVs lack the
session/cadence labels needed for fair GP/hour comparison; superior returns remain unmeasured.

All detailed checkpoints through this source-audit milestone are preserved in
[handoff history](docs/history/handoff-through-2026-10-16-source-audit.md) and
[task history](docs/history/tasks-through-2026-10-16-source-audit.md). Earlier checkpoints, including
the PR #31 live checklist, remain in [older handoff history](docs/history/handoff-through-2026-10-15.md).
Historical runtime/approval statements are superseded by this file and AGENTS.md. Personal CSVs,
raw source rows and diagnostics stay ignored in local-data. Read history for a specific investigation.
Current root notes were reduced from 999/270 to 108/87 lines before this receipt; all 14 unchecked
requirements remain, full snapshots are archived, and every local Markdown link target resolves.
