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

## Current release and queued tooling

#195 uses PUBLIC_NUMERIC_INPUT_V1 across client/companion/replay numeric boundaries, scoped history
refusal, overflow-safe midpoint and current cached-entry rejection. Unsupported managed valuation
retains exact cost and pauses that item under V3; a valid neighbour gets independent final authority.
No financial schema/event/learning migration. [Contract](docs/architecture/exact-public-market-inputs.md).

The old #190 listener timed out and its thread dump did not complete. An update attempt failed
before artifact replacement after native process access denial; the old process was then absent.
The supported retry installs #195 and restores service reads. First observed post-preparation reads
are 19.86–97.83ms, warm health 3.57ms; these are not cold-start/durable-ACK/native acceptance.
RuneLite has reopened; the owner defers manual login/GE until 19:00 local on 28 October.
Do not request another login before then. The frozen-artifact soak started 21:59:40 UTC and
FAILED after 3,220.484 seconds (53.7 minutes): 108 group runs, with a final SocketException
("No buffer space available") during fixture connection setup. It is not a completed hour.
The full 185,450-row frozen export also FAILED at the fixed 1-GiB heap while serializing JSON;
raw payloads alone are 276,793,276 bytes, exceeding the old 256-MiB file limit.
[Failed gates and repair contract](docs/verification/offline-export-and-soak-repair.md).

Queued `codex/verified-exit-race-and-delivery-audit` changes tooling only: accept a freshly proven
exited PID after a native-stop race, retain live/reused/unidentified process refusal, and analyze
main/editor delivery traces reproducibly. No JAR reinstall for that follow-up. All 155 tooling checks and exact-head Windows/Linux CI 40780984649 pass. Release remains
pending the current documentation correction and its exact-head CI. Native, full-account export,
replacement 60-minute soak and controlled-trading gates remain open.

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

Next: finish the main/selected-sale delivery matrix and actual cold/warm/native latency, including
failures. The historical #190 audit has 811 outcome records and 100 linked main-action post-ACK
observations: p50 580ms, p95 4,656ms, max 12,021ms. These fail the one-second target; keep WAITs
separate, repeated working actions explicit and old untraceable records unknown. Targets remain cached render
p95 <=100ms and eligible publication p95 <=1s after required durable frame ACK; report persistence,
ACK and total latency independently. Player sale revision/partial/late-fill/cancel/collect/relist/
completion/restart and native chart acceptance remain manual.

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
