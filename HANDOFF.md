# Agent handoff

Updated 28 October 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Current installed state

Local plugin/companion **#190 remains installed**. Actual JAR hashes still match its original
manifest, installed 27 October at 17:12:26 UTC. No client restart or account-database restoration
occurred during #191â€“#194. Source `a8b2c134f69d868f6636b9f78768e1ff0594221e`, merge
`4198e215ccd7633be4311b55b346237f7d3cbc55`; clean 2,504 Java tests (six existing skips),
132 tooling, Java 11 and both CI platforms `40546616198` pass. Backup
`20261027-091216-f89a7888` restores 255 files, six configurations and all 12 original tables;
old/new ledger, stock, scope, continuation, exposure and original-risk reads agree, including
intervening trades. First scoped GET after background preparation passes. Fresh #190 controlled
trading and sustained/native latency remain open. #186 is the frozen economic reference.
[Local receipt](docs/history/incremental-scope-install-2026-10-27.md).

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

The public archive is active: the verified prefix has 178 seals, 1,156 captures over 19.25 hours,
zero failure records, 64/64 successful seed attempts and 1,400 verified referenced component hashes.
Maximum capture gap is 61.163 seconds; 92,385,071 bytes used against the 2-GiB cap. Latest checked
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

## Queued source: exact public numeric inputs

`codex/exact-public-market-inputs` adds shared exact decoding across public client/companion/replay
boundaries, scoped history refusal, widened midpoint arithmetic and current cached-entry rejection.
Unsupported managed-stock valuation retains exact cost and pauses that item under V3; a valid
neighbour still receives independent final authority. No financial schema/event/learning migration.
Clean 2,539 Java tests (six existing skips), 144 tooling and bytecode 55 pass. Java 11 artifact,
frozen preservation, exact-head CI and installation are not yet claimed.
[Input contract](docs/architecture/exact-public-market-inputs.md).

On 28 October the client is closed. The old companion PID 6728 remains listening but both global
and scoped read-only health probes time out after three seconds. A bounded attempt to obtain a
thread dump also does not complete; the agent's diagnostic helper is stopped, not the companion.
Retain `exact-market-prior-health.json` and the earlier #190 latency failures. Investigate after
supported paired maintenance; this is not new-runtime evidence or permission to change account data.

## Verified behavior and limits

#188â€“#190 prepare validated immutable source prefixes, original acquisition/execution context,
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
1,669/1,012/603ms. Most prior sampled deliveries were roughly 0.5â€“1.5s. Source preparation gains,
transport success or a later successful GET do not establish sustained eligible-action latency.
The original deployment permission failure and recovery are retained in the hosted receipt.

## Remaining work

Every one of the 81 previously unchecked records is mapped in the
[acceptance inventory](docs/verification/trading-pass-acceptance-inventory.md); 80 remain open.
#194 is closed against its CI/deployment proof. Completed older details are preserved in
[the task snapshot](docs/history/tasks-before-input-hardening-2026-10-28.md) and
[the prior handoff](docs/history/handoff-before-input-hardening-2026-10-28.md).

Next: exact public-market numeric decoding and item-local refusal across client, companion and
replay; no truncation, clamping or global feed veto. Then finish the main/selected-sale delivery
matrix and actual cold/warm/native latency, including failures. Targets remain cached render
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
