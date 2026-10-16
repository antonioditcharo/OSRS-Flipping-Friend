# Tasks

Updated 16 October 2026. The current checkpoint below supersedes historical runtime/review
statements later in this file. Read [HANDOFF.md](HANDOFF.md) for exact evidence.

## Active

- [x] Reproduce startup/capture false lifecycle gaps on codex/startup-offer-evidence from #68/4e822ec.
      Test-first eedb4c2/7cced9d precede corresponding producer fixes. RuneLite documents initial
      EMPTY callbacks even when offers exist. Wait for the first logged-in server tick before
      synthetic restoration/account publication; preserve real terminal-then-clear callbacks.
      Periodic captures must match processed state/counters and cannot add transitions or fills.
      Final standard 44s: 1,490 Java passed/six skips; 79 Python passed in 71.713s. CI/install pending.
- [ ] Restore safe sell guidance for already affected stock through auditable evidence-based
      resolution. Current ledger has zero eligible trading positions, nine sales/seven unresolved.
      A nine-unit offer has 11 units in affected acquisition rows; old aggregate positions are
      not safe fallback quantities. Preserve original rows, unknown costs/profit and risk window.
      Prevention alone does not resolve this task. No manual database repair, replay or bypass.
- [ ] Verify post-update login does not emit false clears and complete a player-operated buy ->
      collect -> sell workflow. Fresh capture/source tests do not establish live eligibility.
- [x] Deliver/install full-counter progress fix from #66 merge 2ebf9cb on codex/full-counter-progress.
      Live BUYING/full then BOUGHT created false source gaps and a 47,147,786 GP uncertainty reserve;
      bounded realized updates remain zero. Regression 8d1eb4e precedes the classifier fix. Full
      counters stay nonterminal; only proven deltas book once. Seven companion cases preserve
      cost/tax, restart/dedup, explicit terminal, initial uncertainty and immutable existing gaps.
      Final standard: 1,484 Java passed/six skips, 51s; 79 Python passed in 83.729s. Both JARs target
      Java 11. Merged #67 as e5bc8da after both checks at 98cb168 pass (run 38850014046; Windows
      116808932982/Ubuntu 116808932841). Owner closed client; supported unattended pair installed
      after verified backup 20261015-203043-87e9237b, helper exit 0. Installed 486,098/17,878,059-byte
      JARs match manifest/build/all tested classes. Status/compatibility/GET pipeline pass; one
      client launched, outbox zero. Exact outputs and private receipt locations are in newest HANDOFF.
      Existing live gaps/reserves remain; no database repair or historical financial backfill.
- [ ] Observe fresh post-login capture and new full-counter/terminal handling after #67. Source
      verification and paired-install proof do not establish a completed live trade or new buy budget.
- [ ] Design an auditable evidence-based resolution for already recorded false progress gaps.
      Preserve the raw sources, financial identities, unknown cost/profit and durable risk window.
      No manual database edit, replay, adoption, risk reset or bypass to force advice.
- [x] Deliver/install recommendation latency fix on codex/recommendation-latency from e14121b.
      Runtime portfolio GET timed out at 15,141 ms with 40 observations pending; companion dump
      identifies the serialized evidence query. Regressions 17bf941/79555e1 precede existing-index
      lookup and operation-local history-read fixes. Preserve fresh proof/unknown scope/accounting
      and current 5m/Medium settings; no database/index migration or timeout bypass. Final standard:
      1,477 Java passed/six skips, 57s; 79 Python passed, both Java 11 JARs. Same scoped read-only
      planning probe improves from 2,052/866 ms to 555/196 ms (cold/warm); live acceptance is separate.
      Merged #64 as a3ef954 after both checks at 82645c0 pass (Windows 116797969029/Ubuntu
      116797970834, run 38845928840). Initial Windows socket timeout, passing local recheck and
      unchanged CI retry are documented. Verified backup 20261015-194438-56468ed6 precedes supported
      unattended paired install; helper exit 0. Plugin 486,099/companion 17,878,060 bytes match all
      tested classes/manifest/build. Status/compatibility/GET pipeline pass; one client launched.
- [x] Verify post-login normal acknowledgement drain and fresh durable eight-slot proof after #64.
      Old 32-event queue drains to zero; a later eight-event batch also drains. Packaged read-only
      CurrentOfferReadSet.valid=true, eight matched sources, capture age four seconds, eight BUYING,
      zero free slots, persisted 5m/MODERATE (Medium). Authenticated reads initially 94/16/218/532 ms
      for portfolio/action/ledger/learning; risk AVAILABLE, plan NO_FREE_SLOT and Action WAIT.
      GET-only pipeline exit 0 retains four sales/two unresolved. No data or game input changed.
- [ ] Finish sustained latency/final eligible entry acceptance. Follow-up ledger read is 1,453 ms;
      plan/risk also refuses a stale/unsupported managed instant-sell quote. Keep that guard and
      current 5m/Medium. All slots are occupied; no current allocation exists for final entry proof.
      Read newest HANDOFF for both passing evidence and retained refusals. No extra restart needed.
- [x] Install the batched runtime through #61 at clean #62 merge fc62526 via supported
      -Unattended -ResumeBackup after verified pre-copy interruption proof. Fresh backup
      20261015-184127-76f3a3f8 verified: three companion/22 plugin account files; old artifacts/task
      preserved. Plugin 486,099 bytes and companion 17,877,081 bytes match manifest/build and every
      tested project class, Java 11. Status/1.13.2 compatibility/GET pipeline exit 0; agent launch
      initialized one client and owner login produced fresh observations. Later plan READY/four
      allocations, risk AVAILABLE and final PLACE_BUY GET pass; the earlier WAIT read is retained.
      Initial learning API read observed zero prospective forecasts and held baseline promotion. No game input
      or database repair. Details and observed external Loot Tracker exception are in newest HANDOFF.
- [x] Owner screenshot acceptance: matching recommended quantity/price show normal Confirm;
      mismatched quantity/price keep both controls highlighted and Confirm locked. Compact panel
      and known recorded-profit/unresolved coverage are visible; owner reports things look good.
- [x] Observe real player offer intake with original full forecast/comparable/coherent timing.
      Read-only 18:53 capture: 5,656 rows/cursor 6695, three episodes/two eligible forecasts; both
      outcomes ambiguous. Packaged and retained artifact verification plus comparison to the frozen
      pre-install prefix exit 0. Private evidence stays in local-data; promotion stays held.
- [ ] Finish search/player-key/pause/context acceptance; these are not established by screenshots.
      Controlled intended trade, partial cancel/collect/relist and restart still need real outcomes.
- [x] Reproduce the approved unattended update's logged javac-warning abort in 83abfda on
      codex/update-build-recovery from #61 merge 75190a2. Source preserves native diagnostics and
      checks Gradle exit status; explicit ResumeBackup proves a pre-copy interrupted update before
      retrying the supported paired lifecycle. Ordinary disabled/mismatched tasks remain rejected.
      Standard: 1,469 Java passed/six skips, 49s; all 79 Python tests passed in 71.147s. Java runtime
      unchanged. Merged as #62/fc62526 after both checks at e893234 passed (run 38841359657);
      guarded supported retry completed above.
- [x] Source exact acquisition cost in initial exits and sell maintenance on codex/exact-exit-costs
      from #60 merge 36ee500. Regressions f446c27/fa31f49 precede source 4198a8c. Quotes and non-loss
      floors retain the exact acquisition pool and canonical proportional subset cost, including
      tax thresholds, exemptions, legal-price limits and price stabilization. Standard: 1,469 Java
      passed/six existing skips, 63 Python passed, both Java 11 JARs, 48s. Installed in the pair above.
- [x] Complete one batched supported paired update for source #57 onward. At #61 merge 75190a2,
      the owner approved elevation; backup 20261015-181822-c05e3e5f verified (3 companion/22 plugin
      account files) before a logged compiler note stopped the builder. Pair/manifest then remained #53;
      task was disabled. Tooling #62 and the guarded supported retry above completed recovery.
      No manual INSTALL/log-paste routine, task guard bypass or database change.

- [x] Complete offline frozen execution capture/verification/recorded-prefix comparison on
      codex/frozen-execution-evaluation from #59 merge 0fba976. Regressions 3b71dec/25f5bec/7115219
      precede fixes. Complete canonical sources and proven reconciliation item scope feed horizon
      outcome/Brier bounds; uncertain cancellations stay in the denominator as unknown. Actual
      evaluator artifact retained; later cohort excludes older settlements and changed prefixes.
      Source through 39569c2: 1,458 Java passed/six existing skips, 63 Python passed, both Java 11
      JARs, 39s. Packaged PS5.1 read-only capture: 4,651 rows/cursor 5573/zero prospective forecasts;
      retained-artifact replay verifies. No backfill or database repair.
      Installed in the combined pair above. This is evaluation infrastructure, not fitted/promoted
      learning or measured higher returns.
      Merged in #60 as 36ee500 after both checks at 5993fc5 passed (run 38826360749).

- [x] Source immutable leg forecasts and accepted-guidance publication on
      codex/immutable-execution-forecasts from #58 merge f161a39. Regressions 9471568/8ffb3b2 and
      the active entry-policy metadata failure precede fixes. Source a18210f shares actual entry
      conversion, preserves exact managed sell guidance and original model/config/horizon/wait-work
      context across publisher/outbox/restart/canonical readout. Missing context stays unknown.
      Merged in #59 as 0fba976 after both checks at 2b31e06 passed (run 38810497176).
      Standard: 1,437 Java passed/six existing skips, 63 Python passed, both Java 11 JARs, 39s.
      Installed in the combined pair above. Held promotion, fitting/evaluation remain #56.

- [x] Source observation intervals and promotion fence on codex/execution-observation-intervals
      from #57 merge 58480d6, merged in #58 as f161a39 after both checks at 910a5c0 passed
      (run 38777494280). Regression d209012 precedes active-consumer fixes. Canonical source
      readouts retain placement/first-fill/completion bounds and censoring, with scoped read-only API,
      contradictory/unknown evidence held, bounded diagnostic rows and complete prefix counts.
      Historical aggregate ratios no longer activate live correction; counts alone cannot promote.
      Standard: 1,417 Java passed/six skips, 63 Python passed, both Java 11 JARs, 48s. Installed in
      the combined pair above. This is not finished learning; fitting/evaluation remain #56.

- [x] Source prospective execution prediction bindings on codex/execution-advice-provenance from
      b516b7e, merged in PR #57 as 58480d6 after both checks at b37a19d passed (run 38772952899).
      Regression commits are in HANDOFF.md. Existing outbox persists original/unknown
      bindings atomically; companion validates the first recorded source before learning. Restart,
      late/mismatched advice, failed writes, legacy queues, account scope and rollback are covered.
      Standard: 1,399 Java passed/six skips, 63 Python passed, both Java 11 JARs, 46s. Installed in
      the combined pair above. Censoring/model evaluation remains #56.

- [x] Install the combined #51/#52/#53 pair through the supported administrator Update shortcut.
      Earlier agent helper was canceled before starting; owner opened the interactive updater.
      Fresh backup 20261015-080957-c3a069ab verified before INSTALL: prior hashes, task action/owner,
      3 companion files / 10,407,584 bytes and 22 plugin files / 1,499,405 bytes matched.
      Installed plugin 469,023 and companion 17,832,054 bytes match builds/manifest and all tested
      project classes. Status/1.13.2 compatibility/GET-only pipeline pass. Agent launched RuneLite.
      Fresh scoped read: READY/four allocations, risk AVAILABLE/fresh/positive budget; final GET
      returned ENTRY/PLACE_BUY. Actual UI/offer/fill/restart acceptance remains open; no game input.

- [x] Deliver durable recommendation risk on codex/durable-risk-authority from rebuild 598fed3.
      Regression 774a521 reproduces foreign local-session profit; bb4339d isolates the local cache.
      Source ef4d863 moves planner/final approval/health/alerts/Plan details to durable account risk,
      with prospective atomic windows, bounded profit, unknown-cost reserves, fresh taxed marks,
      expiry and restart/duplicate protection. No journal fallback for automatic runtime entries.
      JSON schema regressions 02744c7 precede the final fix. Standard: 1,373 Java passed/six skips,
      both Java 11 JARs, 44s; all 63 Python tests pass. PR #53 merged as 3362b62 after both checks at
      65d6f1f passed (run 38763296900). Normal rebuild clean/current; historical main unchanged.
      Runtime now includes #53; current risk/entry GET proof passes as recorded above.

- [x] Deliver atomic execution-evidence transaction on codex/atomic-execution-evidence from c915ea8.
      Verified write-failure regressions at e34927a precede fixes. Canonical acceptance, accounting,
      limits, dedup claim and eligible execution statistics now commit/roll back together. Existing
      keys/stats and incomplete-offer exclusions are preserved; no historical backfill/schema change.
      Standard: 1,350 Java passed/six skips, both JARs, 52s. PR #52 merged as 598fed3 after both
      checks at e2b51c5 passed (run 38716858569). Installed with #51/#53 in the combined pair above.

- [x] Deliver planner-heartbeat follow-up on codex/coherent-plan-heartbeat from 70d8453.
      Live PR #49 observations recovered all current slots: zero ERROR/eight authenticated sources,
      matching current cash/slot proof and bounded historical exposure available; ledger records
      unchanged. Installed planner still retained a transient reconciliation refusal. Regression
      025ed19 reproduces same-economics receipt rotation and spacing failures before source fixes.
      Standard: 1,346 Java passed/six skips, both JARs, 44s. PR #51 merged as c915ea8 after both
      checks at e6ecb10 passed (run 38714913420). Installed with #52/#53 in the combined pair above.
      READY/four allocations and final PLACE_BUY GET observed; game trade/restart still untested.

- [x] Source causal trade-gap scope merged in PR #48 as ea0b3b4; both checks passed at e6fb786
      (run 38683353210). The following details are its source receipt, not installed acceptance:
      342aa1a reproduces lost unrelated managed ownership and rejected/malformed client scope proof.
      Source 774fe04 scopes gaps by recorded account/offer episodes, preserves raw history and future
      clean sale cost matching. Read-only follow-up found 24 context losses; cf7898b reproduces them,
      e4cbbd4 fixes them. 10ee83a/0fb6059 add null-state/account-fence regressions/fixes. Final standard
      build passes: 1,321 Java passed/six skips, both Java 11 JARs; all 61 Python tests pass.
      Read-only existing-account probe: all 1,252 gap scopes known; 1,260 records unchanged, old
      money and unknown sales retained, planner unavailable. Require exact-head CI. New buys remain blocked
      until conservative historical exposure bounds and a coherent current cash/slot read are implemented and observed live.
- [x] Delivered codex/bounded-entry-state from ea0b3b4 in PR #49, merged as f600dae after both
      checks at d2cbe00 passed (run 38711448474). Current acknowledged eight-slot/cash proof,
      conservative uncertain capital bounds, paused affected items, bank cash lower bound, stricter
      client/server limits, immediate cached/displayed-buy invalidation and final entry revalidation.
      Regression-first sources and contract are in newest HANDOFF and bounded-current-planning.md.
      Standard: 1,344 Java passed/six skips, both Java 11 JARs; 61 Python passed. Native Plan render
      passes. Existing-account SELECT-only probe preserves 1,260 events/four sales/two unresolved;
      six old ERROR slots/no authenticated source observations blocked entries before install.
      Installed supported pair after verified backup 20261015-011945-07ffac76. Fresh post-login proof
      and controlled acceptance remain pending; source delivery does not prove actual buy readiness.
- [x] Batched PR #47 current-slot recovery/buy-limit fixes and subsequent recommendation work for one
      supported paired update. PR #47 merged as b390b35 after both checks at eef5661 passed (run
      38681167838); normal rebuild is clean and updated. Its 1,304 Java tests/six skips and 61 Python
      tests passed. Runtime now includes PR #47/#48/#49 in the f600dae installation.
- [ ] Continue batched HOME TEST CHECKLIST.md and PR #31 live acceptance. Actual installed/build/
      manifest comparisons, status and API 1.13.2 compatibility pass. GET-only pipeline exits 0:
      ledger reads four sales/two unresolved; READY/four allocations and final ENTRY/PLACE_BUY observed.
      RuneLite was launched through the supported wrapper, one client observed. The agent reads
      subsequent logs/pipeline directly; actual UI and all game input remain player-operated.
      Startup/UI, search/key/Confirm and controlled trade/cancel/collect/relist/restart remain pending.
- [x] Delivered PowerShell 5.1 pipeline compatibility and installed receipt in PR #50, merged as
      70d8453 after both checks at 3c8006c passed (run 38713257067).
      Regression 98c6016 precedes three basic-parsing JSON reads. Both PowerShell hosts now pass;
      all 63 Python tests pass and account-scope failures still fail closed. Tooling/docs only;
      no additional paired install was required for this tooling-only change.
- [ ] Finish one authoritative ledger for planner, exits, inventory UI, calibration and session risk.
      Durable accounting, Performance, exposure and managed trade quantities/exits are implemented;
      durable recommendation risk is source-verified above; finish calibration/remaining consumer audit
      and preserve positive acquisition/cost evidence. Reproduce
      real faults before fixes; never edit a database or bypass exposure guards to unblock advice.

## Next

- [ ] Audit calibration prediction provenance and cancellation censoring before changing strategy.
      Attribution, observed intervals and the runtime promotion fence are source-verified above.
      Historical execution aggregates still pool censored/completed times but no longer authorize
      runtime correction in the new source. Forecast decomposition is source-verified above;
      complete frozen source captures and conservative horizon scoring are source-verified above.
      Finish supported censor-aware fitting, frozen challenger/training artifacts and promotion gates.
      Preserve existing data/dedup keys and separate
      real/paper evidence. Reproduce concrete faults first; promotion requires chronological held-out
      evaluation against a frozen baseline with return/loss/calibration guardrails.
- [ ] Finish manual cancel -> collect -> revalidate -> replace and partial-fill protection as a
      complete live workflow; migrate sell maintenance and exits to companion policies.
- [ ] Build account-level chronological replay for 100M members at 5m, 30m, 2h, and 8h checks.
      Include end-of-run inventory liquidation, cash, buy limits, tax, and missed visits.
- [ ] Add prospective session/cadence measurement and full-account replay; historical CSVs do not
      contain the session clocks needed for a fair GP/hour baseline.
- [ ] Measure fill calibration, then add gated challenger learning and clearly separated paper data.
- [ ] Close the feature-parity matrix in `docs/project-direction.md` and update user documentation.

## Waiting

- Historical session/cadence labels are unavailable. Do not infer them from execution timestamps.

## Completed current tooling

- [x] Low-interruption workflow PR #46 merged as 9cec7706 after both checks at 4d76fad passed
      (run 38678515832). Verified unattended fresh-backup updates and persistent developer API cache;
      61 Python checks passed. Agent launch, compatibility and a full clean build while RuneLite
      remained open passed. Runtime Java unchanged, so no additional pair install was needed.

## Historical evidence

Full earlier task receipts are preserved in [task history](docs/history/tasks-through-2026-10-15.md).
Use the current Active/Next items above and [HANDOFF.md](HANDOFF.md) for source/runtime state.
