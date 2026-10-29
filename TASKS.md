# Tasks

Updated 29 October 2026. Read [HANDOFF.md](HANDOFF.md) and the current hosted-pilot receipt at
`local-data/hosted-pilot/delivery-state.json` first for the installed pair and pending source;
#162 was the hosted pilot's original baseline. Implemented/tested, merged,
installed and player accepted are separate states. The owner confirms fresh #195 login/GE, sale revision/cancel/collect/relist and restart on 28 October;
Those reports belong to #195; #200 is now installed with fresh login/native acceptance pending.
Late fills and complete native/sustained timing remain open.
All game interaction remains manual; batch runtime updates and acceptance work.

All 81 prior unchecked records have stable IDs in [the acceptance inventory](docs/verification/trading-pass-acceptance-inventory.md).
Older completed details are retained in [the exact source snapshot](docs/history/tasks-before-input-hardening-2026-10-28.md).
No outstanding requirement was removed; #194 deployment and #195 numeric validation are closed
against their own verified evidence. Controlled/native acceptance records remain open.

## Current batch

- [ ] Accept the installed #200 foreground follow-up: critical action/control refresh before optional
      sidebar work, lazy hidden Records/Missed flips, actual EDT/card stages and serial worker
      task attribution. Preserve financial ordering and final authority. Require clean checks,
      exact-head CI, fresh hour, preservation and native/player acceptance. Clean 2,565 Java/six
      skips, 175 tooling and 38 actual-JAR Java 11 checks pass. #200 merged at `579c9fb` after
      both final-head CI platforms `40947246833`; installed 29 October 21:06:55 UTC. Fresh hour
      passes 3,601.625s/8,640 checks/9,300 requests/zero failures, both markers and 1,300 input hashes.
      Restoration of 345 files/six configurations, all 12 original tables and previous #195/#198
      financial/risk reads pass. Native/player gates stay open; retain initial fixture failures,
      production-byte equivalence and outliers. [Receipt](docs/history/foreground-guidance-and-budget-shadow-2026-10-29.md).


- [ ] Investigate the retained #198 scheduled-companion outage; global health and a thread dump
      timed out. Same-JAR recovery restores guidance and all 286 original pending sources commit
      unchanged, but root cause/recurrence and recovered startup/warm sale delays remain open.
      Do not infer resolution from the ownership-query tooling fix or unrelated successful probes.
- [x] Deliver #198 timing/display runtime: opaque account-scoped main/selected-sale/maintenance
      traces, bounded asynchronous telemetry, honest visible-paint outcomes, unchanged sidebar
      refreshes and rounded sale/hourly values. Clean 2,556 Java/six skips, 168 tooling, Java 11,
      both exact-head CI platforms 40848616896 and uninterrupted 3,601.875s hour pass. Installed;
      339-file/six-configuration restoration, all 12 financial tables and previous-#195 reads pass.
      #195 remains the immediate rollback target; #186 the economic reference.
      [Receipt](docs/history/native-guidance-and-shadow-2026-10-29.md).
- [ ] Finish carried #198 native qualification on installed #200: fresh login/GE, retained trades, visible main/editor
      guidance and distinct eligible decisions. Measure idle/mixed conditions and cached paint
      p95 <=100 ms / eligible publication p95 <=1s after durable ACK separately per path. Retain
      failures/missing stages/repeats and profile the old 2,980-ms startup outlier before extending
      authority-sensitive preparation. Player operations remain manual.
      Complete #198 session: MAIN 32 paints p95 116.429ms; six distinct eligible actions p95
      1,372.189ms. Four editor paints exist, but source identity is unproven and zero eligible
      editor-publication samples qualify. Preserve both sale-to-WAIT events and their limitations.
      [Contract](docs/architecture/native-guidance-timing.md).
- [x] Implement/verify the isolated common-horizon pricing source #199: shared entry/exit horizon,
      quantity/tax/partial-fill/terminal-value comparison, standing hold versus completed relist,
      matching offline final authority and qualifier V3/replay 29. Clean 2,562 Java/six skips,
      29 exact-JAR Java 11 checks, 170 tooling checks, five extra strict loopback runs and source-head CI 40852265476
      pass. Final delivery-head CI/merge is recorded in the PR/currentDelivery. Dormant offline
      code is included in installed #200; live constructors remain baseline. Retain original
      Windows CI 40848858178 timeout/cause unknown.
      [Shadow contract](docs/architecture/common-horizon-pricing-shadow.md).
- [ ] Complete joint account alternatives and chronological pricing qualification, then observational
      learner construction only after delivery/latency/provenance gates. Keep 5m/30m/2h/8h results,
      100M/eight slots, original risk, fixed public stop and unopened final fold. New market-loss
      authority is separate; no pricing/learning promotion from synthetic results. Deterministic
      real-handler late-fill regressions plus live monitoring satisfy only the selected construction
      gate; an unobserved live late fill stays explicitly unverified.
      Following offline source retains budget-diverse entry sizes without widening the optimizer:
      clean 2,568 Java/six skips and 48 actual-JAR Java 11 checks pass. Replay 30/qualifier V4 refuse
      synthetic promotion at all cadences. Initial #201 exact-head CI `40951693894` passes both
      platforms; final delivery-head CI/merge is recorded in the PR/currentDelivery. Real
      qualification and native acceptance remain open; retain installed #200 JARs for offline work.

- [x] Verify/deliver #196, the script-only exit-race and delivery-audit follow-up. Continue a process
      drain only after fresh proof the target PID is absent; retain all live/reused/unknown
      listener refusals. Audit main/editor traces, duplicate/conflicting identities, explicit
      phase timings and unknown coverage without granting native or trading acceptance.
      No JAR reinstall; 155 tooling checks and both exact-head CI platforms 40812821792 pass;
      merge a4d37d6 is delivered to the primary checkout.

- [x] Replace offline export with hash-bound version-two chunks (4 MiB/1,024 rows, eight-chunk
      cache, 5-GiB budget, 768-MiB reserve) from one read-only snapshot. Keep V1 readers, exact
      evaluator semantics and the 1-GiB heap ceiling. Verify/audit all 185,450 frozen rows unchanged.
- [x] Repair fixture transport cleanup and persistent bounded loopback workers; collect socket,
      handle, memory, failure and phase telemetry. Preserve the FAILED 53.7-minute run and require
      a new uninterrupted 60-minute run with zero failures, without retry filtering.
      [Contract and retained failures](docs/verification/offline-export-and-soak-repair.md).
      Strict hour: 3,601.062s, 120 groups, 7,980 checks, 9,300 instrumented requests, zero failures;
      119 native resource samples and both completion markers verified. Full-account source/
      observation/score equivalence to #195 and unchanged database bytes pass at 1 GiB.
      [Release receipt](docs/history/chunked-execution-validation-2026-10-28.md).

- [x] Deliver approved optimization stage A #188: original
      acquisition/execution prefix indexes, explained main/editor outcomes, successor after expiry,
      hidden investigation work removed from the card timer, and honest latest-quote chart joins.
      Clean 2,476 Java (six skips), 121 tooling, Java 11, frozen equivalence, both CI platforms and
      paired preservation pass. Installed; 251-file/six-configuration restoration, all 12 tables,
      original risk/old-runtime reads and fresh eight-slot BUY/COLLECT delivery pass. First cold
      scoped GET still exceeds ten seconds; sustained/manual acceptance remains open below.
      [Receipt](docs/history/optimization-stage-a-install-2026-10-27.md).
- [x] Verify/deliver #189 `codex/account-pricing-qualification`: chunked canonical prefixes,
      exact-image outbox read reuse, bounded pre-login preparation and offline common-horizon
      pricing/qualification/public-availability tools and optional first-source display context.
      Clean 2,493 Java (six existing skips), 126 tooling, frozen source/financial comparison,
      Java 11 and both exact-head CI platforms pass. Installed; 252-file/six-configuration restore,
      12 tables, old/new original-risk reads and fresh frame/main/editor SELL/native BUY pass.
      Sustained/manual acceptance remains open. [Receipt](docs/history/optimization-stage-b-install-2026-10-27.md).
- [x] Verify/deliver #190 `codex/compact-public-cohorts`: profile-driven persistent acquisition-
      context snapshots and incremental unchanged-frame preparation; preserve full-reader/source/
      financial equivalence, compact public capture/referenced replay and a real smoke cohort.
      Clean 2,504 Java (six skips), 132 tooling, Java 11, frozen readers/financial/risk and both
      exact-head CI platforms pass. Installed; 255-file/six-configuration restoration, all 12
      original tables and previous-runtime reads pass. Fresh login and sustained acceptance open.
      The 31-observation smoke correctly refuses duration qualification; final test stays unopened.
      [Contract](docs/architecture/compact-public-cohorts-and-scope-preparation.md).
- [x] Verify/deliver #191 `codex/public-forecast-calibration`: prospective quote targets, separate
      low/high/persistence errors, central-band coverage and all unsupported/missing/right-censored
      outcomes in offline qualification v2. Clean 2,512 Java (six existing skips), 132 tooling and
      bytecode 55, exact-artifact Java 11 smoke and both CI platforms `40605290513` pass. Active
      trading classes match installed #190; no client reinstall. Calibration support cannot replace stressed whole-account
      return gates or activate a model. [Contract](docs/architecture/public-forecast-qualification.md).
- [x] Verify/deliver #192 `codex/hosted-public-archive`: replace interrupted local public capture with an isolated hosted archive worker, bounded
      sealed segments/shared blobs and a versioned verified reader. Keep the incomplete 39/163-
      record captures unchanged, preserve actual availability clocks, test restart/corruption/disk
      limits and repeat hosted capacity with capture before activation. Clean 2,519 Java (six skips),
      138 tooling, bytecode 55 and Java 11 writer/Python reader interoperability pass;
      both exact-head CI platforms and enabled hosted capacity pass: 1,000 connected/100 active,
      8,400 decisions over 421.02s, p95 145.30ms, zero errors; one verified seal/seven captures.
      Deployed; authenticated health and first real seed captures pass. No local reinstall or
      financial authority. [Delivery](docs/history/hosted-public-archive-install-2026-10-27.md).
<!-- REQ-7bfec73286cc -->
- [ ] Verify first sealed production public segment, uninterrupted coverage/byte budget through
      restart and final stop, then freeze the new cohort with retained tuned-fixture exclusions.
      The 32-item seed worker runs independently; its warm-up cannot delay live guidance.
- [x] Add a permission-preserving Linux archive switch helper after the retained deployment
      permission failure: exact-byte backups, owner/group/mode, unchanged account authorization,
      custom settings, idempotency and concurrent-edit refusal. Six regressions and all 144 tooling
      checks pass; actual Linux disposable-copy restoration passes without changing live config.
<!-- REQ-3905b72b27ad -->
- [x] Verify/deliver #194 `codex/public-capture-seed-resume`: skip retained attempts immediately after
      a partial-seed restart, preserve every old attempt/clock and refuse corrupted catalogue bounds.
      Clean 2,521 Java (six skips), bytecode 55 and nine exact-artifact Java 11 archive tests pass;
      all 144 tooling checks and exact-head CI 40611404568 pass. Deployed; all 60 retained attempts,
      selection and stop clock survive restart, and four missing histories continue immediately.
<!-- REQ-6e77516523b0 -->
- [x] Audit all runtime typed market adapters for exact numeric bounds and item-local refusal.
      Source #195 and the paired local installation pass: 2,539 Java (six skips), 144 tooling,
      bytecode 55, 80 exact-artifact Java 11 checks, both CI platforms 40773463909, 290-file/six-
      configuration restoration, all 12 original tables and previous-runtime financial/risk reads.
      Exact raw archive data and all source/financial/learning identities remain intact. Unsupported
      managed valuation pauses that item; unrelated verified cash remains usable. Hosted #194 and
      unpromoted pricing/learning remain separate. [Receipt](docs/history/exact-public-input-install-2026-10-28.md).

<!-- REQ-0f35c977855a -->
- [ ] Complete approved pricing stages B/C: constrained whole-account alternatives, shared entry/
      hold/adjust/exit evaluation, distinct evidence-bound budgeted loss authority and prospective
      decision context. Freeze a new 60/20/20 chronological cohort, exclude tuned fixtures, and
      qualify 5m/30m/2h/8h separately before activation. Existing pricing remains the fallback.
<!-- REQ-72df7bde8615 -->
- [ ] Complete asynchronous hosted public preparation on the current budget, repeat capacity
      experiment before increasing hosted responsibility, and verify 60-minute controlled mixed-load
      soak/manual workflows. Begin execution-first learning only after delivery/latency/evidence
      gates; learning remains observational until its own promotion requirements pass.
<!-- REQ-8aaf66885fb2 -->
- [ ] Accept combined installed #186: fresh post-update ordered eight-slot ACKs, useful visible
      guidance, preserved trades, cold/warm timing and player-operated cancel/late-fill/collect/
      relist/restart and native charts. Initial #186 manual login/GE, eight-slot proof and visible
      BUY/full-slot guidance pass, with COLLECT/SELL/BUY logs. The first prefix has 250 completed
      offer calls without transport failures and a 25 ms histogram p95 upper bound. Most sampled
      delivery times are about 0.5–1.5 seconds; sustained targets remain unproven. Prior cold/UI
      timing failures remain retained in the linked #184 receipt.
<!-- REQ-5975361995a6 -->
- [ ] Remove remaining original-context/execution-prediction history scans with full provenance,
      duplicate-advice, external-edit, rollback and learning-identity preservation. Active #186
      JFR still records an offer monitor wait of 2.04 seconds. Classify three proposed SELL-to-WAIT
      outcomes (editor override/refusal versus lost delivery) before claiming complete maintenance
      delivery. One optional history-upload timeout recovered; 87/87 retained files have valid ACKs.
<!-- REQ-604ba60fea0c -->
- [ ] Accept installed #178 through fresh manual login/GE, preserved trades, ordered frame ACKs,
      actual guidance and sustained timing including failed requests. The 4,532ms prelaunch ledger
      cache warm-up is not current-frame or live latency acceptance. Include cold account-open,
      durable-projection reuse and genuine legacy fallback. [Contract](docs/architecture/report-and-replication-latency.md).
<!-- REQ-9f720b822b0c -->
- [ ] Accept installed recovered day-ahead chart availability and visible viewport expansion,
      preserving manual zoom and honest missing/flat forecasts. Regenerated Swing images match the
      saved side-chat renders at three widths; native live charts remain unaccepted. Longer bands
      remain uncalibrated display estimates. [Contract](docs/architecture/day-ahead-chart-forecasts.md).
<!-- REQ-cd3475d7a63f -->
- [ ] Complete `codex/workflow-verification-dynamic-pricing`: remove profiled verification scans/
      background churn with indexed and exact-prefix bounded reads; preserve financial/source/risk
      data, current guards and safe cancelled-loss relisting. Verify full builds/tooling/both CI,
      frozen old/new semantics, native work budgets, paired preservation/rollback and sustained live
      account acknowledgements. [Contract](docs/architecture/workflow-verification-and-predictive-pricing.md).
<!-- REQ-6458451b0fb8 -->
- [ ] Deliver market-predictive pricing from the owner's appended Copilot references: changing low/
      high paths with qualified uncertainty, actual entry/exit price selection, supported hold/
      adjustment/loss decisions and whole-account evaluation. Keep persistence/seasonal comparisons,
      5m/30m/2h/8h results, source/receipt compatibility, loss budgets and guidance fallback explicit.
      Workflow-only changes and visually curved charts do not complete this requirement.

<!-- REQ-b22e58580c2c -->
- [ ] Accept installed #173 supported small adjustment through player cancellation/late fill,
      collection, exact relisting, completion and restart. All slots were full after installation;
      the copied current sells had no supported routine change. Fresh frame/WAIT and frozen Marlin
      component replay are not controlled maintenance acceptance. Keep sustained UI/event/ACK timing
      open: cold/later five-second retries and many roughly 0.8–2s subsequent refreshes miss the
      instant-guidance goal.

<!-- REQ-1b76631b79e5 -->
- [ ] Implement the owner's 25 October market-informed loss/hold policy: compare waiting, small
      profitable adjustments and bounded loss exits from history, volume, spread, volatility,
      trend/recovery and realistic reusable-slot/capital opportunities. Preserve exact acquisition
      allocations, original stops/anchors and durable loss budgets. Carry an explicit evidence-bound
      reason through final authority, cancellation/late fills/collection/relist/restart and compatible
      rollback. Test falling/recovering/sparse markets and whole accounts at 5m/30m/2h/8h; do not use
      age alone, unvalidated learning or chart projections to authorize a loss. Keep foreground
      suggestions independent of optional analysis. Requirements remain in the contract above.

<!-- REQ-4c6e122a226c -->
- [ ] Accept installed #172 fresh guidance, stable BUY entry, collected exact sale/relist and restart
      through player operation; measure sustained timing separately. Remaining cold/ledger/report
      stalls still appear. Owner reports normal workflow but long-held sale maintenance is under
      investigation. Source/replay tests and backup checks cannot
      close player acceptance or hosted advice activation.

<!-- REQ-96ddf967c04e -->
- [ ] Complete latency/player acceptance after installed #164: provided Gson 2.8.5 live history ACKs,
      fresh guidance, clean Java/tooling, both CI platforms, original data preservation and rollback
      reads pass. PR #165 also passed those checks and is installed: it removes quadratic sale-scope
      copies and unnecessary acquisition/gap history scans. Its 51-file restoration and exact 12-table
      preservation pass; #164 rollback reads agree. Current
      cash, holdings, prices, account/source checks and financial authorization remain fresh.
      [Contract](docs/architecture/pilot-client-compatibility.md); receipts
      `local-data/hosted-pilot/compatibility-delivery.json` and `guidance-hot-path-delivery.json`.
      Sustained warm-path timing, final UI latency, player trading/restart and active hosted advice
      outage acceptance remain open; installed #168 is the current baseline. Its latest receipt
      records fresh login/guidance/timing separately; source tests alone cannot close those gates.

<!-- REQ-6236db127a3b -->
- [ ] Complete the approved hosted pilot: preservation/rollback proof, isolated background history
      support and canonical qualified repair, flexible preferences/rounded estimates, reusable Java
      core, account writer fencing, authenticated scoped replication, fair shared allocations and
      asynchronous local fallback. Verify clean Java/tooling tests and exact-head Windows/Linux CI,
      encrypted off-server restore, 1,000 connected/100 planning capacity, shadow comparisons,
      installation, fresh suggestions, outage fallback and sustained player/restart acceptance.
      [Contract](docs/architecture/hosted-pilot.md); `local-data/hosted-pilot/delivery-state.json`.
      Read the receipt for the installed pair; staging server running is not local activation.

<!-- REQ-ab2d6bd317a7 -->
- [ ] Deliver/accept after-tick EMPTY prevention, cold canonical-write ACKs and complete current-frame
      baseline recovery without booking missed executions. Preserve source/financial/risk rows and
      item pauses; prove unrelated verified-cash suggestions with bounded active sells. Retain newly
      interrupted source episodes in Missed flips for separately qualified execution recovery.
      [Contract](docs/architecture/current-frame-quarantine-recovery.md); current delivery remains in
      `local-data/ordered-frame-recovery-20261024/delivery-state.json`.

<!-- REQ-ab8d7c032f67 -->
- [ ] Deliver/accept ordered account-frame recovery and explicit source-proven sale amendments.
      Preserve all original sources/effects/risk anchors; verify current cash/eight-slot ACKs,
      corrected costs/reporting/risk, eligible unrelated entry delivery and independent maintenance.
      [Contract](docs/architecture/ordered-account-frame-recovery.md); latest receipt:
      `local-data/ordered-frame-recovery-20261024/delivery-state.json`. Earlier release gates remain open.

<!-- REQ-914a570dcd80 -->
- [ ] Deliver/accept the owner-approved no-trade-left-behind pass: every unfinished exact offer,
      canonical unlisted acquisition and saved continuation has an action or explicit blocker;
      preserve original replacement budgets, durable reassessment, independent maintenance,
      exact costs/stops and all source/financial/risk evidence. Accept the native recovery view,
      full partial/cancel/late-fill/collect/replacement-or-exit/completion/restart workflow and
      sustained latency. [Contract](docs/architecture/no-trade-left-behind.md). Source is merged as
      #159; current API pin is 1.13.4. Read the receipt for installation and controlled acceptance.

<!-- REQ-4eb733f85e98 -->
- [ ] Deliver/accept item-local stop rechecks across additional ordinary buys, cached READY plans,
      exact cancelled-buy replacements, partials and restart. Preserve exact remainders, fresh
      recovery, unrelated entries, non-loss exits, confirmed stops and original finance/risk evidence.
      [Contract](docs/architecture/stop-entry-pause.md).

<!-- REQ-0d995805f6ac -->
- [ ] Deliver/accept confirmed automatic stop losses across initial exits, working sales, exact
      collected relists and restart. Preserve profitable limits without completed-market support,
      confirmed urgent stops, wind-down, exact costs, old receipts and financial/risk identities.
      Keep owned-stock history fresh outside the entry shortlist. [Contract](docs/architecture/confirmed-stop-loss.md).

<!-- REQ-be2a39c2c4b4 -->
- [ ] Verify/deliver full-board churn recovery: one engine-pass ledger, consistent last-slot exit
      priority after sale/continuation refusals, canonical entry/replacement reservation and sale
      preflight before freeing a slot. Accept the retained Fury relist and newest-offer stability;
      preserve legitimate stops, exact replacements, partials, spare-slot entries and risk guards.
      [Contract](docs/architecture/full-board-exit-reservation.md).

<!-- REQ-e08be6a94993 -->
- [ ] Verify/deliver exact-episode collection retry across unreadable workflow documents, repeated
      EMPTY and unrelated slot reuse. Keep login/account reset, cached-predecessor and new same-item
      supersession guards. [Contract](docs/architecture/collection-observation-retry.md).

<!-- REQ-f36daf84bab4 -->
- [ ] Complete controlled acceptance of original sale-acceptance renewal across fresh source IDs,
      partial progress and restart. PR #154 is installed; six actionable sale deliveries and two
      native observations passed without the Saving blocker. Preserve original acceptance/source
      identity; controlled cancellation/collection/relist and sustained latency remain open.
      [Contract](docs/architecture/sale-acceptance-renewal.md).

<!-- REQ-d238b8b44046 -->
- [ ] Verify/deliver canonical capped replacement requests, bounded fully reauthorized sale-price
      correction and distinct working maintenance after urgent refusals. PR #153/#154 are installed;
      fresh eight-slot/ACK/actionable sale recovery passed. Direct PLACE_BUY is authority evidence,
      not observed BUY presentation; controlled trading and sustained latency remain open.
      [Contract](docs/architecture/trading-flow-authority-recovery.md).

<!-- REQ-1de0f41e301a -->
- [ ] Complete controlled logged-in cadence/retry recovery after installed PR #154. Recorded UTC trade
      clocks and order deadlines remain unchanged; controlled trading and sustained latency stay open.

<!-- REQ-a10a81ba68fc -->
- [ ] Verify fresh post-update ordered ACK/account/maintenance/entry recovery from the live blockade;
      player trading and sustained latency remain separate from source/concurrency regressions.
<!-- REQ-9af9b47ca0ed -->
- [ ] Preserve first-cancellation acceptance through a genuinely unreadable acceptance document
      using prospective evidence handling; never retrofit a published receipt-free cancellation.
      Source regressions now pass; controlled read-outage/restart/player acceptance stays open.
<!-- REQ-9ed74ee0f03b -->
- [ ] Reassess independently eligible BUY maintenance after all routine sale candidates are refused,
      with bounded excluded-episode traversal and no invisible continuation promises. Source
      regressions now pass; controlled player acceptance remains open.

<!-- REQ-83fb20dbf9ac -->
- [ ] Accept prospective sale receipts, cadence changes, receipt outages and distinct-origin relists
      through player operation; source/HTTP regressions are separate from controlled acceptance.

<!-- REQ-91a24c133d80 -->
- [ ] Accept live original deadlines, source-write outage/recovery and overdue cancellation with
      recent partial fills through player operation; prior frozen metadata remains unchanged.

<!-- REQ-b6a14af1b85a -->
- [ ] Accept independent plan capture, partial fills, retry/ACK/restart and exact managed exits through
      player operation. Legacy unknown plans remain unknown; learning remains unvalidated.

<!-- REQ-9cc5e93eafa3 -->
- [ ] Accept parallel same-item origins, smaller replacements, child cancel/collect, late original
      fills, failed writes and restart through player operation. Source tests are separate.
<!-- REQ-a43bb2af0de0 -->
- [ ] Bound relationship retention through proven origin reachability; never discard live proof by TTL.

<!-- REQ-9dfcbee66f3e -->
- [ ] Accept evidence-loss/recovery, partial collection, exact relists, stops/wind-down and restart
      through player trading; source verification and synthetic replay remain separate.
<!-- REQ-bcd678479b7b -->
- [ ] Deliver/accept multiple same-item continuation preservation through explicit successor-to-origin
      evidence; no inferred association or weaker unknown-order supersession. See current batch.

<!-- REQ-47b4d2edbf5c -->
- [ ] Accept failed-write withdrawal/revalidation and original BUY deadlines through player-operated
      cancel/late fills/collect/exact replacement or relist/restart. Offline regressions remain separate.

<!-- REQ-8260e837cde4 -->
- [ ] Accept clock-recovery and corrected late-callback workflows through player-operated cancellation,
      collection, exact replacement/relist and restart. Offline timestamp simulations are separate.

<!-- REQ-6055eb811a5c -->
- [ ] Accept crash/reconnect restoration, late fills, collection and exact replacement/relist through
      player operation. Source regressions and installed hashes do not establish trading acceptance.

<!-- REQ-15dcb01fc05b -->
- [ ] Accept both fixes through player cancel/late fills/collect/relist/restart, including parallel
      same-item sale progress. Installation and source regressions do not establish this acceptance.

<!-- REQ-94bffd310136 -->
- [ ] Accept all ordinary maintenance sale alternatives through final authority after a refused first
      reduction. Persist only the presented exact episode; preserve late-fill/collection caps, original
      costs/plans, earlier-buy and fresh-stop priority, rejection withdrawal and account isolation.
      [Contract](docs/architecture/ordinary-sale-maintenance-queue.md) /
      [receipt](docs/history/ordinary-sale-queue-delivery-2026-10-21.md).

<!-- REQ-3ff5a52a22bd -->
- [ ] Accept active managed-sale wait explanations in Records â†’ Recommendation: shared evaluator,
      exact episode/progress and evidence expiry, separate original actual-order estimates and
      clock-only reassessment. Eligibility never bypasses final authority or changes pricing/risk.
      [Contract](docs/architecture/sale-maintenance-diagnostics.md) /
      [receipt](docs/history/sale-maintenance-diagnostics-delivery-2026-10-21.md).

<!-- REQ-dff45398b6c8 -->
- [ ] Deliver/accept same-second acquisition-plan binding, mixed-origin remaining ranges, no invented
      stops, selected managed subset authority, earlier supported non-loss cancel/collect/relist,
      progress-aware and bounded buy maintenance, shared horizons and frozen unvalidated diagnostics.
      Preserve source/financial history and risk; controlled full-loop/restart acceptance remains open.
      [Contract](docs/architecture/trade-plan-maintenance.md) /
      [receipt](docs/history/trade-plan-maintenance-delivery-2026-10-20.md).

<!-- REQ-5001e207b5d2 -->
- [ ] Deliver/accept all eligible exits through final companion authority, exact saved relist caps,
      stop priority and withdrawal of every rejected foreground/pending/editor trade. Separate actual
      inventory from equipment, preserve bank/collection evidence, cap initial exits to available
      inventory with exact subset cost and verify the companion inventory bound.
      [Contract](docs/architecture/exit-authority-queue.md) /
      [receipt](docs/history/exit-queue-delivery-2026-10-20.md).

<!-- REQ-a2aec38ef2bd -->
- [ ] Deliver/accept exact recorded-sale disposition of older collected rebuy promises, durable source
      receipts, collected/cancelled-sale restart recovery and persistence retries across account switches.
      Installed #132 now retires Dual macuahuitl with its exact sale receipt after fresh login. Preserve unrelated hints,
      exact holdings/costs/plans, financial/source rows, CURRENT_ACCOUNT_V3 and every risk guard.
      [Contract](docs/architecture/continuation-sale-disposition.md) /
      [receipt](docs/history/continuation-sale-disposition-delivery-2026-10-20.md).

## Outstanding acceptance and release requirements

<!-- REQ-8d6fb85a375f -->
- [ ] Accept expected-exit profit and cancelled-buy recovery (#131). Try every eligible saved remainder through final authority; exit verified acquired partials after refusal, including funding/last-slot cases. Preserve exact pooled costs/tax, original targets and separate instant-sale/risk values. Accept durable healthy/observed-sale supersession.
      [Contract](docs/architecture/exit-profit-and-replacement.md).
<!-- REQ-be74ecc72c52 -->
- [ ] Accept callback capture before worker dispatch (#130): original fill/terminal/EMPTY sources and advice survive logout, stopped/rejected workers, retries, account switches and restart. Local capture performs no HTTP; ordered prefix ACKs and backoff persist.
      [Contract](docs/architecture/capture-before-dispatch.md).
<!-- REQ-c172b74499ef -->
- [ ] Accept exact-source reversible review archival and durable accounting mode (#129). Only closed unmatched sales without earlier purchases qualify; retain purchase evidence, all financial rows, unknown coverage and risk guards. New failures remain visible; reject legacy accounting downgrade.
      [Contract](docs/architecture/trade-review-archive.md).
<!-- REQ-06d3d8024946 -->
- [ ] Accept startup sale accounting/evidence display (#128). Restore recorded automatic scope before offer drain without stale cash authority; full SELLING then SOLD counts once. Keep unmatched cost unknown, selected sale investigation and logout/expired-card withdrawal.
      [Contract](docs/architecture/startup-sale-accounting.md).
<!-- REQ-4ea3f25fdbf3 -->
- [ ] Accept forced atomic offer persistence/recovery (#127). Retain original IDs/order/counters/plan bindings through save failures and profile switches; preserve changed files and withdraw stale collection advice. UI reads do not force retries; unsaved memory is not crash durability.
      [Contract](docs/architecture/offer-persistence-recovery.md).
<!-- REQ-61c32b73f0c3 -->
- [ ] Accept live revalidation with a maintenance editor open (#126). Recheck work every pass; same-item stops withdraw buys, unrelated typing protection stays bounded, and collection/explicit wind-down need no market quote.
      [Contract](docs/architecture/live-guidance-revalidation.md).
<!-- REQ-3e25de7f6c0b -->
- [ ] Accept fresh stops through saved buy/sell continuations (#125). Preserve healthy accumulation, stop the working buy or exit verified collected units, commit rebuy retirement before sale delivery and bypass ordinary backoff for fresh stops. Keep replay V10 boundaries.
      [Contract](docs/architecture/stop-continuation-priority.md).
<!-- REQ-28b723b1ca8a -->
- [ ] Accept committed cancellation identity and bounded advice lifetime (#123/#124). Preserve saved account/episode promises through changed cards/restart; reject unrelated/uncommitted cancellations and delayed/stale editor/input delivery. Keep installed 1.13.3 compatibility.
      [Contract](docs/architecture/guidance-lifetime-and-cancellation.md).
<!-- REQ-c1587e9864d2 -->
- [ ] Accept durable maintenance delivery (#122): forced atomic cancellation promises, dirty progress/removal retries across profiles, changed-file preservation, episode-local backoff and immediate withdrawal of declined foreground sells.
      [Contract](docs/architecture/durable-maintenance-delivery.md).
<!-- REQ-673214fe2834 -->
- [ ] Accept CURRENT_ACCOUNT_V3 and workflow recovery (#121). Fresh verified cash supports unrelated entries through valuation-only stock uncertainty; preserve affected-item pauses, unseen-stock cost, confirmed losses and every source/exposure/funding/slot/limit guard. Keep durable exact remainders and explicit estimate coverage/ages.
      [Contract](docs/architecture/verified-cash-workflow-recovery.md).
<!-- REQ-1c94c2a2514e -->
- [ ] Accept ordered-observation batching and retry wakeups (#119): exact copied individual sources/bindings survive batch persistence, prefix ACK races and late/completion retries without lost wakeups. Measure installed sustained timing.
      [Contract](docs/architecture/ordered-observation-batches.md).
<!-- REQ-99fc0716306c -->
- [ ] Accept complete durable buy/sell continuation workflow (#116): exact remainders through late fills/collection/restart, bounded fresh replacement authority/forecasts, foreground maintenance identity, stale/overflow guards and replay v7 volume integrity.
      [Contract](docs/architecture/trade-continuations.md).
<!-- REQ-01148241b614 -->
- [ ] Accept cancelled-sale recovery (#114): retain exact collected remainder through slot reuse/retention/restart, distinguish earlier listings from later replacements and reject stale episode advice. Keep current physical/ledger/cost/target/final authority.
      [Contract](docs/architecture/durable-sale-relist.md).
<!-- REQ-7b882b1137c5 -->
- [ ] Accept completed-offer latency (#111): canonical prefix reuse retains earliest-original checks, tail/external invalidation and transactional rollback/deduplication. Observe actual source-to-ACK-to-guidance latency; synthetic timing is insufficient.
      [Contract](docs/architecture/completed-offer-latency.md).
<!-- REQ-66ec6a52345a -->
- [ ] Accept acquisition-bound target/stop/sell-duration (#108) through partial fills/sales, ACK/relist/restart. Later snapshots/item TTL cannot create original plans; mixed/unproven context stays unknown. Finish consumer authority and production replay qualification.
      [Contract](docs/architecture/acquisition-exit-context.md).
<!-- REQ-2a7de3344604 -->
- [ ] Accept patient initial/maintenance selling (#105): protect available original healthy targets within horizon; routine reductions now follow the owner-approved earlier supported non-loss policy in the current contract, with no recent fills and sustained completed-market support. Preserve fresh stops/wind-down, exact floors, unknown cost and final authority.
      [Contract](docs/architecture/patient-sale-pricing.md).
<!-- REQ-ffb7acc79a72 -->
- [ ] Accept expanded panel/performance/prompt guidance (#103): session/period/reset, durable profit/ROI/completed orders, elapsed hourly results, marks, paged sales and estimated action profit/time. Reset preserves finance/risk; validate live UI and sustained delivery.
      [Contract](docs/architecture/panel-performance-and-latency.md).
<!-- REQ-e7efb5ad9b9f -->
- [ ] Accept qualified quiet-stock marks (#101) under the current V3 workflow: direct bids 120s; completed public evidence at most 15m, 10% haircut/tax and quantity limits; no gain credit. Unsupported/unseen coverage and all original financial/loss guards persist.
      [Contract](docs/architecture/qualified-stock-risk-marks.md).
<!-- REQ-0151b284a8c8 -->
- [ ] Accept stock-valuation explanations, fresh-evidence recheck and native maximized Records/Missed flips (#98). Preserve exact uncertainty/reserve explanations and historical separation; genuine waits remain.
      [Contract](docs/architecture/verified-cash-workflow-recovery.md).
<!-- REQ-7c87bfeb4f0f -->
- [ ] Accept public market graphs (#96): independent 5m/1h/6h price/volume history, selected-item investigation, ranges/zoom/pan/reset/expand and explicit experimental return scenarios. Frozen bands were worse than persistence; remain display-only.
      [Contract](docs/architecture/market-graphs.md).
<!-- REQ-6c5885081e00 -->
- [ ] Resolve already affected stock safely with auditable evidence (issue #70). A nine-unit offer has 11 acquired units in affected rows; maxima/aggregate positions are unsafe remaining-stock/cost proof. Preserve unknowns, all original rows and risk anchor; retained Copilot export has insufficient overlap.
      [Contract](docs/architecture/trade-evidence-audit.md).
<!-- REQ-a40188cfa928 -->
- [ ] Complete controlled player buy â†’ partial/full fill â†’ collect â†’ sell â†’ collect, partial cancel/relist and restart. Source tests/fresh capture do not establish controlled trading acceptance.
      [Contract](docs/architecture/automatic-trade-scope.md).
<!-- REQ-9e00b7d89fc5 -->
- [ ] Observe new full-counter progress/terminal buy accounting in a controlled trade. Prevention/source capture does not prove completion or fresh buying power.
      [Contract](docs/architecture/full-counter-progress.md).
<!-- REQ-b187a97bde46 -->
- [ ] Resolve previously recorded false-progress gaps using auditable evidence. Preserve raw sources, financial identities, unknown cost/profit and risk; never edit/replay/adopt/reset to force advice.
      [Contract](docs/architecture/full-counter-progress.md).
<!-- REQ-e300193113ac -->
- [ ] Finish sustained latency and final-authority acceptance: observation, ordered outbox/ACK, planning, sale/entry authority and actual client delivery. Report median/tail/cold scope; preserve freshness/funding/loss/settings and never manufacture funds or release reserves.
      [Contract](docs/architecture/panel-performance-and-latency.md).
<!-- REQ-aaafa90fbe68 -->
- [ ] Finish search/player-key/pause/context acceptance during intended trades and partial cancel/collect/relist/restart. Screenshots alone are insufficient; narrow player-key exception never confirms/places/collects.
      [Contract](docs/architecture/trade-input-assistance.md).
<!-- REQ-eb3c688989f6 -->
- [ ] Complete HOME TEST CHECKLIST.md and PR #31 live acceptance; investigate unattributed recurring Swing layout exceptions before UI-stability claims. Confirmation/highlight screenshots do not establish search/input/context/trade outcomes.
      [Contract](docs/architecture/panel-performance-and-latency.md).
<!-- REQ-43d7e96b6d4f -->
- [ ] Finish one authoritative ledger for planner, exits, inventory UI, calibration and session risk. Retain positive acquisition/cost provenance; complete calibration/remaining-consumer audit and controlled runtime acceptance without database repair or guard bypass.
      [Contract](docs/architecture/parity-account-evaluation.md).
<!-- REQ-e607f46c376c -->
- [ ] Finish explicit account catalog/selection and real cross-account performance aggregation. Current sidebar is current-account only; legacy unassigned data stays unassigned and coverage stays explicit.
      [Contract](docs/architecture/account-isolation.md).
<!-- REQ-36f5979f2f99 -->
- [ ] Accept combined sell-verification paths through #94: delayed reads, missing sell identities, ACK partial races, retained-price incompatibility and quiet-market limits. Initial Sell guidance was owner accepted; full maintenance/partial/cancel/relist/restart remains open.
      [Contract](docs/architecture/sale-guidance-delivery.md).
<!-- REQ-f544d2af055d -->
- [ ] Finish calibration provenance/cancellation censoring before strategy changes. Preserve immutable prospective advice, deduplication and unknown/censored labels; complete compatible runtime forecast loader, frozen challenger/training, chronological return/loss/calibration gates and explicit promotion/rollback/drift controls. Current cohorts do not establish improvement.
      [Contract](docs/architecture/frozen-execution-evaluation.md).
<!-- REQ-ee319548df3a -->
- [ ] Finish manual cancel â†’ collect â†’ fresh revalidation â†’ exact replacement and partial-fill protection as a complete production workflow, including original acquisition target/stop/duration through restart.
      [Contract](docs/architecture/trade-continuations.md).
<!-- REQ-0a4df0acd4fb -->
- [ ] Finish account chronological replay/qualified frozen benchmarks for 100M GP members, eight slots, separately at 5m/30m/2h/8h. Include cash/limits/tax/losses/partial fills/missed visits/terminal inventory. Complete real foreground/editor/transport/action-order parity; synthetic scenarios and price touches are not returns.
      [Contract](docs/architecture/parity-account-evaluation.md).
<!-- REQ-d86d7c6d043d -->
- [ ] Finish prospective cadence/attendance/full-account return measurement. Session cursors/elapsed clocks need installed player acceptance; historical CSVs lack cadence/session clocks and cannot establish GP/hour.
      [Contract](docs/architecture/panel-performance-and-latency.md).
<!-- REQ-a4dc9037d0b4 -->
- [ ] Measure eligible real execution calibration, then gate challenger learning with chronological frozen evidence and clearly separate paper data. Runtime stays PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION.
      [Contract](docs/architecture/execution-observation-intervals.md).
<!-- REQ-e5a17496feb8 -->
- [ ] Close the project-direction parity matrix and user documentation. Still deferred: validated forecast bands/actionable dump feed/opt-in local-sound-webhook delivery, bank tags/badges/slot colors, paginated investigation/export and optional cross-device sync. No full-parity claim.
      [Contract](docs/architecture/parity-account-evaluation.md).

## Historical evidence

Completed receipts and the full prior requirements are preserved in
[the PR #131 task archive](docs/history/tasks-through-2026-10-20-pr131.md), which links older history.
Historical session/cadence labels remain unavailable; do not infer them from execution timestamps.
No account-data edits, replay, adoption, outbox clearing, risk reset, game input or forced client termination.
