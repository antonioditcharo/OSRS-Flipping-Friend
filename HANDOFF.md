# Agent handoff

Updated 29 October 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Installed runtime and preservation

Local plugin/companion **#198 is installed**, 29 October at 10:28:07 UTC. Tested source
`2c8b5bca943692c2921460f0c818244e66db7e6c`, merge `f87e0b38ed7271cd3be8079aea17c88249cc1319`;
both exact-head CI platforms `40848616896`, clean 2,556 Java checks (six existing skips),
168 tooling and own bytecode 55 pass. Installed SHA-256:

- Plugin: `3af6edd87af388d2531e54657a6c416b03d2d71ea406adb7d71c3a9f17583f95`.
- Companion: `9ccc6c671b546c7921603096326e552039b91872fc26073e4e719489eaeea5de`.

Backup `20261029-022757-109cdedd` passes disposable restoration of 339 files plus six supplemental
configurations. All 12 original financial tables are identical. Previous #195/current #198 read the
same ledger, stock, scope, exposure, continuation and original-risk results from that restored account;
its database bytes remain unchanged. The unassigned legacy database has a separate read-only backup
and disposable restore; its owner remains unassigned. No live database restoration or financial replay.
**#195 is the immediate code rollback target; #186 is the economic reference.** #190 remains retained.
Rollback changes code while preserving subsequent trades. Protected startup task/XML/permissions are
unchanged. Normal close, paired update, status/launcher/GET-only pipeline and reopening pass.
[Current receipt](docs/history/native-guidance-and-shadow-2026-10-29.md).

RuneLite reopened at the login screen. Fresh #198 login/GE, visible guidance, current-trade retention
and native main/editor timing remain pending. The owner's normal sale revision/cancel/collect/relist,
restart and observed partial progress belong to **#195**. Live late fills are unobserved. Keep its
2,980-ms post-ACK / 3,573-ms total startup outlier unresolved until the new trace profiles it.

## Current source and verification

#198 adds opaque account-scoped main/selected-sale diagnostic traces through completed visible Swing
painting, bounded asynchronous logging, explicit unsuccessful paint outcomes, less unchanged-sidebar
relayout/resetting, rounded midpoint sale/hourly displays and primitive public cache snapshots with
unchanged fingerprints. Financial IDs, acknowledgement semantics and live authority are unchanged.
[Timing contract](docs/architecture/native-guidance-timing.md).

Its fresh strict hour PASSES: 3,601.875 seconds, 120 groups, 8,460 checks, 9,300 instrumented backend
fixture requests, zero test/transport/telemetry failures, 119 native resource samples and both worker
completion markers. All 1,293 frozen inputs rehash correctly. The earlier 1,798.11-second run was
intentionally superseded for account-scope diagnostics and remains incomplete. Host available memory
fell as low as 3 MiB; uncontrolled background load prevents attributing that pressure to guidance.
Group timing and the standalone Swing paint probe are not RuneLite eligible-action latency.

Following source [#199](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/199) implements an
**offline-only common-horizon shadow** using existing scenarios/replay: exact quantity/tax/partial-fill/
remaining-stock value, standing-offer hold versus adjustment, cancelled-sale relist and matching offline
final authority. Live/asynchronous constructors keep the baseline. Clean 2,562 Java checks (six skips),
29 exact-JAR Java 11 core/authority checks and five additional strict loopback runs pass. Source
`66d35a0d3814798c5c2b29c9778d8f68b9aa4d0a` passes both CI platforms `40852265476`; final documentation
head/merge identity is recorded in the PR and private currentDelivery. #199 is **not installed**.
[Shadow contract](docs/architecture/common-horizon-pricing-shadow.md).

Retain Windows CI `40848858178`'s strict five-second GE-offer POST timeout. Its cause is unproven;
bounded failure diagnostics add no retry, timeout increase or production transport change. Later
passes do not erase it. Static shadow comparisons credit no replacement profit; feasible capital/
slot reuse is evaluated in replay. Further joint alternatives and chronological qualification remain
open. Synthetic V3 fit/validation refuses all four cadences and leaves the final fold unopened.

#196/#197 are delivered tooling: exit-race/delivery audit, V2 chunked export and repaired persistent
soak. Full frozen 185,450-offer/1,389-gap export/verify/audits pass at 1 GiB with unchanged source,
observations and scores versus #195. Reuse this proof unless relevant code changes. Keep the original
53.7-minute socket failure, monolithic-export OOME and sampler failures.
[Tooling receipt](docs/history/chunked-execution-validation-2026-10-28.md).

## Hosted state and future gates

Hosted **#194 is unchanged**, source `8ddd05e0c84e3dd4e371ebe86053363edf48e801`, JAR
`2d7ceac821294d170098391b7d1d940ef2e6ea85fc28b3be7f1b82dc972aa7f4`.
Enabled/shadow true; recommendations/coordination false; automatic recovery true. Local companion
remains sole financial authority; no server upgrade. Approved ceiling **$50/month**.
[Hosted receipt](docs/history/hosted-public-archive-install-2026-10-27.md).

Latest read-only archive audit: 292 seals, 1,903 captures across 31.7 hours, zero failures,
2,300 verified component hashes and about 151 MB of 2 GiB. Open tail remains incomplete. Preserve
fixed stop **2026-11-08T01:31:16Z**; do not tune the growing capture or open the final fold. Capacity
#192 remains the last experiment: 1,000 connected/100 active, 8,400 decisions, p95 145.30 ms, zero
errors; this excludes native/local financial ACK latency and public-launch capacity guarantees.

Every original unchecked requirement remains in TASKS and the
[81-record acceptance inventory](docs/verification/trading-pass-acceptance-inventory.md); 79 remain open.
The next gates are:

1. Measure #198 native main and selected-sale paths separately: cached paint p95 <=100 ms and
   eligible publication p95 <=1 second after required durable ACK. Separate persistence, ACK,
   queue/planning/authority/presentation/paint, distinct decisions, repeated cards and healthy WAITs;
   retain failures, outliers and missing stages. Profile before extending authority-sensitive caches.
2. Complete player-operated sale revision, cancel/collect/relist, restart and stable visible guidance
   on #198. Deterministic real-handler late-fill regressions plus monitoring satisfy the selected
   rare-case construction gate; unobserved live examples remain explicitly unverified.
3. Complete joint whole-account pricing alternatives and frozen chronological 60/20/20 qualification,
   tuned exclusions and stress at **5m/30m/2h/8h**, 100M GP/member/eight slots. Failed/unsupported
   challengers retain baseline guidance. Separate immutable market-loss proof and durable original-
   window loss-budget authority remain a later release; age/charts cannot authorize losses.
4. Begin observational completion calibration on the existing V2 reader only after delivery/latency/
   prospective-provenance gates pass. Runtime calibration stays neutral; no learning promotion.
   Optional context cannot block financial delivery; simulations stay separate from real executions.

Private authoritative state is primary `local-data/hosted-pilot/delivery-state.json`, **currentDelivery**;
older root aliases are historical. Detailed older receipts are preserved in
[the previous handoff](docs/history/handoff-before-native-guidance-2026-10-29.md).
Keep unknown costs/coverage unknown, personal belongings outside trading stock, original IDs/plans/
risk anchors intact and independently funded suggestions available under live guards. Never clear
outboxes, restore old account databases or automate game input. Three unrelated primary document
deletions remain untouched.
