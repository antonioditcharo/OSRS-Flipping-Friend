# Agent handoff

Updated 28 October 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Current installed state

Local plugin/companion **#195 is installed**, 28 October at 21:53:12 UTC. Tested source
`44fb16bbb1389d973f8b3b0b721a817629cc266d`, merge `2d63c2e8e44eaa7a8b302ffef46ea889315daeb7`;
both exact-head CI platforms `40773463909`, clean 2,539 Java (six existing skips), 144 tooling,
bytecode 55 and 80 exact-artifact Java 11 checks pass. Installed hashes: plugin
`34627219524ecd1bde2494f2effc6e5525434e6eae1ad0d7a65759732ccf8a79`, companion
`49d921a247cdd3166b555b460702c46b472d407ca82219185d1869b7838933f9`.
Backup `20261028-135259-030281fa` restores 290 files, six supplemental configurations and all 12
original financial tables. Previous #190/new-runtime ledger, stock, scope, exposure, continuation
and original-risk reads agree on the fresh backup and an unchanged earlier frozen database.
Runtime rollback preserves subsequent trades; no account database restoration occurred.
#190 is the compatible code fallback; #186 remains the economic reference.
[Delivery receipt](docs/history/exact-public-input-install-2026-10-28.md).

Hosted **#194 is deployed**, source `8ddd05e0c84e3dd4e371ebe86053363edf48e801`, merge
`b9a4d5db6115f3868bddd0ec467a27a729507fb4`; JAR SHA-256
`2d7ceac821294d170098391b7d1d940ef2e6ea85fc28b3be7f1b82dc972aa7f4`.
Clean 2,521 Java tests (six existing skips), all 144 tooling tests, bytecode 55, nine exact-JAR
Java 11 archive regressions and both exact-head CI platforms `40611404568` pass. Normal hosted
restart preserves all 60 original seed attempts, the catalogue and original stop clock; four
missing histories continue immediately. Only the public worker class differs from hosted #192.
The permission-preserving Linux flag helper #193 passes six regressions, actual disposable Linux
UID/GID/mode restoration and both CI platforms `40610225037`.
[Hosted delivery](docs/history/hosted-public-archive-install-2026-10-27.md).

The public archive is active: the verified prefix has 196 seals, 1,275 captures over 21.23 hours,
zero failure records, 64/64 successful seed attempts and 1,544 verified referenced component hashes.
Maximum capture gap is 61.163 seconds; 101,661,507 bytes used against the 2-GiB cap. Latest checked
backup at `20261028T200242Z` verifies disposable restore and encrypted off-server upload. The fixed
capture stops 8 November at 01:31:16 UTC. Sustained completion, final stop and qualification remain
open; an open tail is not completed evidence. Private current state:
primary `local-data/hosted-pilot/delivery-state.json`, field `hostedPublicArchive`.

Offline qualification **#191 is merged**, source `d2cc4dfae44ba419a949055c188f7bf4f5804a5b`,
merge `b8e40e4d2837c93773d81ebf464e713e464415d4`; both CI platforms `40605290513` pass.
Public targets retain independent side clocks, unsupported/missing/censored outcomes and calibration
support before stressed account gains. Exact-JAR Java 11 smoke refuses all four cadences for
insufficient duration; final test remains unopened. Keep original 39/163-record captures incomplete,
old evaluator bundles and declared tuned/synthetic exclusions. Current capture is too short for
final qualification. [Contract](docs/architecture/public-forecast-qualification.md).

Maintenance 12/replay 28 and sale-receipt family 10 remain active. Hosted enabled/shadow true;
recommendations/coordination false; automatic recovery true. Local companion is sole financial
authority. New market-loss authority and learning remain off. Existing server/budget remain unchanged:
original base quote approximately $36.20 before tax, approved ceiling $50/month. Three unrelated
primary-checkout document deletions stay untouched.

## Current release and tooling

#195 uses PUBLIC_NUMERIC_INPUT_V1 across client/companion/replay numeric boundaries, scoped history
refusal, overflow-safe midpoint and current cached-entry rejection. Unsupported managed valuation
retains exact cost and pauses that item under V3; a valid neighbour gets independent final authority.
No financial schema/event/learning migration. [Contract](docs/architecture/exact-public-market-inputs.md).

The old #190 listener timed out and its thread dump did not complete. An update attempt failed
before artifact replacement after native process access denial; the old process was then absent.
The supported retry installs #195 and restores service reads. First observed post-preparation reads
are 19.86–97.83ms, warm health 3.57ms; these are not cold-start/durable-ACK/native acceptance.
The owner has now logged in, confirmed normal sale revision/cancel/collect/relist without stuck
cards and performed a normal restart. Fresh post-restart eight-slot durable/account matching passes;
partial progress is observed and no new reconciliation rows appear. Keep the first startup
MODIFY_SELL outlier: 3,573ms total / 2,980ms post-ACK. Warm 40-action p95 is 878ms, with repeated
working cards included; native rendering, selected-sale timing, live late fills and sustained
acceptance remain open. Installed #195 hashes are unchanged.
The original 53.7-minute socket-failed soak and monolithic 1-GiB export OOME remain failures.
[Failed gates and repair contract](docs/verification/offline-export-and-soak-repair.md).

#196 is merged (`a4d37d6013dd722727cd01109b543c3d07bdc92c`), tested head
`1781a1a709bea14514e73eb433ec399e12dc0c5c`, both exact-head CI platforms `40812821792`.
It changes tooling only: accept a freshly proven
exited PID after a native-stop race, retain live/reused/unidentified process refusal, and analyze
main/editor delivery traces reproducibly. All 155 tooling checks pass; the primary checkout is
updated and installed #195 is unchanged. Current offline gate results follow below; native/
selected-sale timing, live late fills and sustained eligible latency remain open.

Offline tooling #197 (`codex/chunked-execution-validation`, [PR](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/197))
provides V2 complete read-only prefixes: 4-MiB/1,024-row chunks, one eight-chunk cache, disk
indexes, 5-GiB budget and 768-MiB reserve, with V1 readers/evaluation mathematics preserved.
Final-source 185,450-offer/1,389-gap export, verification and both audits pass at 1 GiB with exact
#195 source/observation/score hashes and unchanged database bytes. Clean 2,550 Java (six skips),
81 exact-artifact Java 11 checks and source 7f94d5a CI 40819785528 pass; tooling totals 161 cases.
The strict hour PASSES: 3,601.062s / 120 groups / 7,980 checks / 9,300 instrumented requests,
zero failures, 119 native resource samples and verified worker completion markers. Keep the
original socket/OOME and later WMI-sampler failures. Final delivery-head CI/merge identity is in
PR #197 and private currentDelivery. This is an offline tool release; installed #195 and all
357 plugin classes stay unchanged. [Receipt](docs/history/chunked-execution-validation-2026-10-28.md).

## Verified behavior and limits

#188–#190 prepare validated immutable source prefixes, original acquisition/execution context,
unchanged-frame recovery and bounded pre-login work. External edits, account switches, shrinking
prefixes and rollback invalidate preparation; current financial authority is never cached away.
Guidance outcomes cover main/editor delivery, supersession and expiry, with bounded successor work.
Chart endpoints retain actual quote clocks, honest gaps, zero-horizon joins and manual zoom.
[Workflow contract](docs/architecture/panel-guidance-continuity.md),
[prepared inputs](docs/architecture/compact-public-cohorts-and-scope-preparation.md).

Capacity #192: 1,000 connected/100 active, 8,400 decisions over 421.02s, zero errors,
p95/p99 145.30/713.18ms; 6,000 synthetic public items and one verified seal/seven captures.
Earlier #189 hour: 72,000 decisions, zero errors, p95 152.17ms. Separate mixed synthetic workflow
hour: 120 group runs/6,900 checks/zero failures. These exclude native rendering, local financial
ACK latency and player acceptance. Do not relabel them as live profit or public-launch capacity.

Prior live failures remain evidence: first cold scoped GET over ten seconds, 2.04s offer-monitor
wait, three proposed SELL-to-WAIT outcomes requiring classification, and SELL post-ACK outliers
1,669/1,012/603ms. Most prior sampled deliveries were roughly 0.5–1.5s. Source preparation gains,
transport success or a later successful GET do not establish sustained eligible-action latency.
The original deployment permission failure and recovery are retained in the hosted receipt.

## Remaining work

Every one of the 81 previously unchecked records is mapped in the
[acceptance inventory](docs/verification/trading-pass-acceptance-inventory.md); 79 of the original 81 remain open.
#194 and the #195 numeric audit are closed against their source/test/delivery proof. Completed older details are preserved in
[the task snapshot](docs/history/tasks-before-input-hardening-2026-10-28.md) and
[the prior handoff](docs/history/handoff-before-input-hardening-2026-10-28.md).

Next: verify #197 delivery identity in currentDelivery, then finish the main/selected-sale delivery matrix and actual cold/warm/native latency, including
failures. The historical #190 audit has 811 outcome records and 100 linked main-action post-ACK
observations: p50 580ms, p95 4,656ms, max 12,021ms. These fail the one-second target; keep WAITs
separate, repeated working actions explicit and old untraceable records unknown. Targets remain cached render
p95 <=100ms and eligible publication p95 <=1s after required durable frame ACK; report persistence,
ACK and total latency independently. The owner confirms current #195 sale revision/cancel/collect/relist and restart; observed partial
progress is retained separately. Live late fills, sustained/native timing and native chart
acceptance remain open.

Complete common-horizon entry/hold/adjust/exit comparison and separate immutable market-exit
proof plus durable original-window loss reservations. Age, charts and hypothetical replacement
income cannot authorize losses. Keep original acquisition allocation, plans and risk anchors.
Freeze new 60/20/20 chronological inputs, exclude tuned fixtures and qualify 5m/30m/2h/8h separately
at 100M/member/eight slots. Failed challengers retain baseline guidance. Execution-first learning
can be built after delivery/latency/prospective-evidence gates pass; promotion remains independent.
[Approved pass](docs/architecture/optimization-pricing-qualification.md),
[pricing contract](docs/architecture/workflow-verification-and-predictive-pricing.md).

Keep unknown costs/coverage unknown, personal belongings outside trading stock, unassigned legacy
data unassigned, and independent verified-cash opportunities available. Never restore an older
account database, clear an outbox, reset risk or automate game interaction to unblock suggestions.
