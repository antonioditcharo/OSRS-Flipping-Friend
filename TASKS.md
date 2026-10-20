# Tasks

Updated 20 October 2026. Read [HANDOFF.md](HANDOFF.md) for current source/runtime evidence.
All unchecked release requirements remain visible below; detailed completion receipts are in history.

## Active

Continuation impact order: trade-loop reliability/exit-context provenance, actual cold/event-to-advice
latency, production-policy account replay/returns, validated learning, then remaining product gaps.
Read [the continuation prompt](docs/next-conversation-prompt.md); current delivery facts below govern.

- [ ] Accept live-guidance revalidation with a maintenance editor open: every engine pass checks
      current work; same-item stops withdraw buys, unrelated typing stays bounded, and fallback
      collection/explicit wind-down do not require market prices. Nine added tests include five
      old-behavior failures; contract/receipt: docs/architecture/live-guidance-revalidation.md and
      docs/history/live-revalidation-delivery-2026-10-20.md. Actual source/CI/install/live state:
      local-data/live-revalidation-20261020/delivery-state.json. Manual tests deferred until owner
      returns after 20 October 17:30 local. Controlled trade-loop, sustained timing, complete replay,
      qualified returns/learning and remaining parity requirements below remain open.

- [ ] Deliver/accept fresh stops through saved buy/sell continuations. Preserve healthy partial
      accumulation promises; fresh stops cancel a working buy or sell its verified collected units.
      Commit old-rebuy supersession before accepting a stop sale; failed saves withdraw it. Listed
      fresh stops outrank ordinary relists/replacements and earlier ordinary write backoff. Replay
      V10 covers delayed actions, partial collection, absence and restart. Four old failures and ten
      new behavioral tests; docs/architecture/stop-continuation-priority.md links the receipt.
      Actual source/CI/install/live state: local-data/stop-continuation-20261020/delivery-state.json.
      Owner deferred manual login/game checks until the home batch after 20 October 17:30 local.
      Full trade-loop, timing, complete production replay, return/learning and remaining parity stay open.

- [ ] Deliver/accept cancellation identity and bounded guidance lifetime. Preserve committed
      account/episode replacement plans through changed cards/reconnect; distinguish uncommitted
      and later same-item offers. Bound local foreground/pending/editor/queued-input advice without
      expiring durable plans; withdraw persistent failures behind an editor. Fifteen behavioral
      tests, including three reproduced old-source failures, cover the boundaries. Contract/receipt:
      docs/architecture/guidance-lifetime-and-cancellation.md and
      docs/history/guidance-lifetime-delivery-2026-10-20.md. Actual source/CI/install/live state:
      local-data/episode-guidance-20261020/delivery-state.json. Full controlled trading, sustained
      timing, complete replay/returns/learning and remaining parity gates remain unchecked.
      The game update required a tested 1.13.3 follow-up; actual combined compatibility/CI/install
      state: local-data/runelite-1133-20261020/delivery-state.json. The 1.13.2 update was refused
      before any installation change. See docs/architecture/runelite-compatibility.md.

- [ ] Deliver/accept durable maintenance delivery: forced atomic commits before cancellation
      promises; retry dirty collection/removal evidence across observations/profile switches; no
      cached overwrite of changed files or use of uncommitted replacement hints. Withdraw declined
      foreground sells before applying successors; preserve unrelated personal quotes/new episodes.
      Episode-local ordinary backoff keeps unrelated exits/entries available. Contract/receipt:
      docs/architecture/durable-maintenance-delivery.md and docs/history/durable-maintenance-delivery-2026-10-20.md;
      actual source/CI/install/live flags: local-data/durable-maintenance-20261020/delivery-state.json.
      Controlled full-loop, sustained timing, return/learning and remaining parity gates stay open.

- [ ] Deliver/accept the verified-cash and workflow-recovery batch. Owner-approved V3 separates
      valuation-only uncertainty from missing physical stock; preserve confirmed losses, source/
      exposure/cash/slot/limit guards and pause uncertain items. Durable continuation queues retain
      separate exact remainders, including retained legacy hints; unrelated exits/entries survive
      refusal, refresh/restart and expired advice. Performance shows cash, cost and quoted estimates
      with quantity/age/unknown detail. See docs/architecture/verified-cash-workflow-recovery.md,
      its consolidated receipt and local-data/stock-valuation-recovery-20261019/delivery-state.json
      for actual source/CI/install/live state. Controlled trade-loop, sustained timing, real market
      benchmark and learning gates remain open. Older checkpoints below are included history.

- [ ] Deliver/accept ordered-observation and retry fixes: preserve late/completion retry wakeups,
      batch copied-frame/ACK-prefix local persistence (16 replacements to 2), retain individual
      source delivery, exact identities/provenance and current account proof. Windows clean 91s:
      1,784 Java passed/six existing skips, Java 11; 108 tooling passed. #119/65a45dd merged after
      both exact-head checks for 2fc548d and installed at 19:40 AKDT. VERIFIED backup, hashes/task/
      65,712-row source prefix/1,485 effects/original anchor/status/compatibility/scoped reads pass,
      zero update-created effects. Agent closed normally; RuneLite reopened and loads the plugin.
      Actual post-login evidence is in local-data/loopback-latency-20261019/delivery-state.json;
      docs/architecture/ordered-observation-batches.md links the receipt. Controlled trade-loop
      and sustained timing remain open; source/install do not close those gates.

- [x] Implement offline replay v8 exit/maintenance priorities, last-slot protection, partial-buy
      stops and new-evidence displays within attended visits. Five old-source regressions fail;
      seven new tests plus existing replay cases pass. Windows clean 129s: 1,768 Java passed/six
      existing skips, Java 11; 108 tooling passed; packaged twelve-scenario bundle reproduces.
      See docs/architecture/replay-exit-attendance.md and
      docs/history/replay-exit-attendance-2026-10-19.md. V8 is included in installed #119; V9 source
      follows the current batch above. Manual login/eight-slot proof/BUY and SELL delivery pass, while
      full controlled trade-loop, sustained timing, further replay parity and qualified return/
      learning gates remain open below.

- [ ] Deliver/accept the coherent trade-continuation batch: exact durable buy remainders and restart,
      shared buy/sell callbacks, fresh quantity-bounded replacement planning/forecasts, foreground
      maintenance acceptance, stale/overflow quote guards and replay v7 persisted intents/volume
      integrity. Windows clean 92s: 1,761 Java passed/six skips, Java 11; 108 tooling passed;
      packaged twelve-scenario schema bundle reproduces. #116/7e1385b merged after both exact-head
      checks for 505a912 and installed at 21:48:48Z. VERIFIED backup 20261019-134844-d16fc650; pair/
      task/financial-prefix/anchor/status/compatibility/scoped ledger and bounded-entry GET pass,
      zero new financial effects. Supported Start reopened RuneLite. Contract/receipt:
      docs/architecture/trade-continuations.md and
      docs/history/trade-continuation-batch-2026-10-19.md. Controlled partial cancel/collect/replace/
      relist/restart, real foreground/editor/transport parity, sustained timing and qualified market
      return/learning gates remain open. Batch related fixes before full verification and delivery.

- [ ] Deliver/accept cancelled-sale intent recovery: retain the exact observed collected remainder
      through slot reuse/retention/restart; distinguish already-working listings from a later
      replacement and reject stale episode advice. Two prior-source regressions fail; source fixes
      and real callback/managed decision-chain tests pass. Windows clean 83s: 1,743 Java passed/six
      skips, Java 11; 108 tooling passed. #114/61bf57e merged after both exact-head checks for
      857f36e and installed at 21:06:37Z. VERIFIED backup 20261019-130634-180fa0d6; installed pair/task/
      source prefix/financial effects/original anchor/status/compatibility/scoped reads pass, zero
      new financial effects. Supported Start reopened RuneLite; fresh login/trading acceptance pending.
      See docs/architecture/durable-sale-relist.md and docs/history/durable-sale-relist-2026-10-19.md.
      Current inventory/ledger/cost/target/scope/final authority remain required. Controlled partial
      cancel/collect/relist/restart and persisted-intent replay coverage remain open.

- [ ] Deliver and accept completed-offer latency source: reuse the validated canonical prefix
      during prospective terminal prediction qualification, retaining earliest-original checks,
      tail/external invalidation and atomic rollback/deduplication. The 30,000-source regression
      reproduces the old rescan. #111/b74f93b is merged after both exact-head checks; Windows clean
      91s: 1,736 Java passed/six skips, Java 11; 108 tooling passed. Synthetic median acceptance
      173ms -> 18ms with identical results. INSTALLED at 18:55:13Z through the supported updater
      after owner-authorized normal agent closure. VERIFIED backup 20261019-105509-9ebbbcba;
      installed hashes/task/62,073-row source prefix/1,470 effects/anchor/status/compatibility/scoped
      GET checks pass, zero new financial effects. Supported Start reopened RuneLite. See
      docs/architecture/completed-offer-latency.md and docs/history/completed-offer-latency-2026-10-19.md.
      Fresh controlled source-to-ACK-to-guidance/sustained acceptance remains open. Respect the owner
      availability recorded privately in the delivery receipt; login/gameplay remain manual.

- [x] Implement offline replay v6 canonical acquisition/exit provenance, final companion sale
      authority and delayed/revalidated displayed actions. Original source bindings reload on
      restart; missing context remains unknown. Completed sell orders and disposal updates have
      separate counts. See docs/architecture/replay-canonical-sale.md and
      docs/history/replay-canonical-sale-2026-10-19.md. Windows clean 81s: 1,732 Java passed/six
      existing skips, Java 11; 108 tooling passed; packaged twelve-scenario bundle reproduces.
      Evaluator-only; now included in installed #111.
      V7 adds shared replacement/persisted relist coverage. Remaining real foreground/editor/
      transport/action-order acceptance and qualified benchmark/learning gates remain unchecked below.

- [ ] Accept installed #108/fe8a33b acquisition-bound exit context. Live initial/maintenance/relist/final authority
      now read the original prospective canonical buy context, without the item cache's six-hour
      TTL or a later snapshot's target. Partial fills/sales, ACK and overnight restart retain it;
      mixed/unproven origins stay unknown. Windows clean 66s: 1,726 Java passed/six existing skips,
      Java 11; 108 tooling passed. Both exact-head CI checks pass. Supported update/VERIFIED backup/
      installed hashes/task/financial prefix/risk anchor/status/compatibility/scoped GETs pass;
      RuneLite reopened. Fresh authenticated eight-slot/zero pending outbox/scoped Performance, READY
      and final PLACE_BUY pass; Client delivered BUY/COLLECT/WAIT. Full manual trade-loop, price-quality
      and sustained timing acceptance remain pending. See
      docs/architecture/acquisition-exit-context.md and
      docs/history/acquisition-exit-delivery-2026-10-19.md. Production replay must also qualify this
      boundary before any promotion gate; v6 now covers it, while all controlled workflow/learning/
      benchmark and remaining production replay gates stay open.

- [ ] Accept installed #105/e21a049 patient sell pricing after the premature break-even report.
      Four old-source regressions reproduce; protect initial/quiet targets within the configured
      horizon, full sell patience/recent progress and completed-market support for maintenance.
      Preserve stops/wind-down, exact floors, unknown costs and final source/quantity guards.
      Standard Windows 112s: 1,717 Java passed/six skips, Java 11; 108 tooling passed. Replay v5
      structural twelve-cadence/share bundle reproduces; no return/optimal-pricing claim. Both
      exact-head CI checks pass. Supported update/VERIFIED backup/task/financial preservation/
      status/compatibility/scoped GETs pass; RuneLite reopened. Fresh login/source proof now passes in
      #108; controlled initial/partial/cancel/collect/relist/restart/player quality acceptance remains open. See
      docs/history/patient-sale-delivery-2026-10-19.md. Source/test/install/acceptance differ.

- [ ] Accept installed #103/dd49269 panel/performance/prompt guidance. Both exact-head CI
      checks pass; Windows clean: 1,695 Java passed/six skips and 108 Python passed. Supported
      update/VERIFIED backup/task/financial preservation/status/compatibility/scoped GETs pass;
      RuneLite reopened; fresh authenticated eight-slot bindings/scoped Performance pass. Delivered
      BUY median 1,039ms with a 5,186ms first-publication outlier; WAIT median 607ms. That session's sell-only
      correctly withheld buys; fresh normal-mode READY/final entry passes in #108. Panel/reset,
      controlled trading and sustained timing remain open.
      It adds scoped
      session/period/reset, ledger-based profit/ROI/completed sales, conservative stock marks,
      elapsed clock/rate, paged sales and estimated action profit/time. Five-second minimum spacing
      is replaced by revision/validity reuse; source/history and candidate caches preserve final
      current guards. Cross-account aggregation and broader cadence/benchmark measurement remain
      deferred. Source, exact verification, runtime and player acceptance are distinct; see
      docs/history/panel-speed-delivery-2026-10-19.md and the current HANDOFF checkpoint.

- [ ] Accept owner-approved/installed #101/5260586 quiet-stock valuation: timestamped completed
      public price/volume, 10% haircut/tax and quantity limits. Direct bids retain 120s; qualified
      fallback trades must be within 15m. Missing/unsupported stock remains fully reserved; original
      accounting/anchors, confirmed-loss and final authority guards remain. Windows clean 121s:
      1,680 Java passed/six skips and 108 tooling tests pass; both exact-head CI checks pass.
      Verified backup/pair/task/financial-prefix/status/compatibility/scoped GET checks pass;
      supported Start reopened RuneLite. Fresh authenticated eight-slot proof and qualified quiet
      marks pass; READY plans precede the current eight-occupied NO_FREE_SLOT outcome. Same frozen
      live data changes negative old-policy allowance to positive without financial changes.
      Over-15m unsupported quotes still keep full reserves. Fresh final eligible entry also passes in #108; sustained and
      owner UI acceptance remain pending. See docs/architecture/qualified-stock-risk-marks.md and
      docs/history/qualified-stock-valuation-2026-10-18.md.

- [ ] Deliver/accept merged #98/f662310: current-stock valuation explanations,
      prompt recheck on fresh market evidence and native maximized Records/Missed flips windows.
      Preserve historical separation, exact reserves, qualified price/source guards and original loss anchor.
      Windows clean 92s: 1,658 Java passed/six skips, Java 11; 108 tooling tests pass.
      Both exact-head Windows/Linux checks pass. #98 is INSTALLED with VERIFIED backup
      20261018-210634-e9da041d; hashes/task/financial-prefix/status/compatibility/scoped GET checks
      pass and it remains included in #101. Native player acceptance remains
      pending. Genuine risk waits remain. See docs/history/buy-readiness-windows-delivery-2026-10-18.md.

- [ ] Deliver/accept the source graph increment in docs/architecture/market-graphs.md: independent
      public 5m/1h/6h history, low/high/volume, selected-item stats, ranges/zoom/pan/reset/expand and
      explicitly experimental historical-return bands with reproducible rolling-origin evaluation.
      Native QA/full build pass: 1,653 Java passed/six skips and 108 tooling tests. Frozen evaluation
      reproduces; all four horizons have worse known-target MAE than persistence. Keep display-only.
      #96/1ebe4f8 is merged/installed after both exact-head CI checks passed. Verified backup,
      hashes/Java 11/task/financial-prefix/status/compatibility/scoped GET checks pass; supported
      Start reopened RuneLite. Fresh login/installed owner graph and guidance acceptance is pending.
      See docs/history/market-graph-delivery-2026-10-18.md.
      Installed #101 runtime includes #94 sells and #96 graphs. Other players' trades never become
      this account's missing ledger rows or execution samples. Validated forecast quality remains open.

- [x] Deliver/accept initial sell guidance in installed #94. Owner reported indefinite verification
      after #91/#92; fresh scoped GETs reproduce SALE_QUOTE_STALE for verified held stock. Shared
      client/companion/replay non-loss limit is installed after both exact-head checks. Windows clean:
      1,636 Java passed/six skips; 108 tooling tests pass. Verified backup/pair/task/financial-prefix/
      status/compatibility/scoped GET checks pass. Owner confirms the fresh Sell-card check "Looks good";
      observed post-update sale and a newly acquired two-unit position listed; fresh eight-slot proof passes.
      Keep fresh evidence for
      loss cuts and live sell cancellation, exact floors, physical/source and historical guards.
      See docs/history/quiet-market-sale-delivery-2026-10-18.md; complete player buy/collect/sell remains open.

- [x] Implement/test the parity increment in docs/architecture/parity-account-evaluation.md:
      final companion managed-sale/maintenance authority, shared exact pricing, durable sale
      investigation, graph lookup, public market-drop watch list, local/webhook warning dispatch,
      executable account replay and frozen conservative completion fitter. Windows standard:
      1,590 Java passed/six existing skips; 103 Python passed. #83 is merged/installed after both CI
      checks; see docs/history/parity-delivery-2026-10-17.md. Manual acceptance remains open.
      These foundations do not close the broader replay, session measurement or learning gates below.

- [x] Deliver #79 sell maintenance/relisting and #80 replacement entry/refusal guards. Installed
      #80/342aa52 after both final Windows/Linux checks passed. Backup 20261017-131556-67c07d16 is
      VERIFIED; hashes, Java 11, unchanged task metadata, native status/compatibility/scoped pipeline
      pass. Client relaunched; owner confirms login. Fresh installed-guard eight-slot source proof
      passes twice; outbox drains normally. UI/controlled trading acceptance remains open. See
      docs/history/postlogin-verification-2026-10-17.md. Final standard 54s:
      1,562 Java passed/six existing skips; prior 103 tooling tests pass. See
      docs/history/recommendation-delivery-2026-10-17.md. No reinstall for this docs receipt.
- [x] Deliver #77 normal-user paired updates without an initial admin approval. Task permissions
      remain read-only and unchanged; optional #76 ACL repair was never applied. Status/start/update
      use processLifecycleVersion=1. See docs/history/normal-user-update-2026-10-16.md.
- [x] Preserve the completed #69/#72 startup/episode prevention, #73 offline audit and #75 accurate
      risk explanations. Prevention/audit do not repair historical stock or release reserves.
      Earlier regression/build/CI/delivery receipts remain in the linked task/handoff history.

- [ ] Restore safe sell guidance for already affected stock through auditable evidence-based
      resolution. The post-login ledger reaches 21 sale updates/nine unresolved; new activity does
      not resolve historical affected stock and unknown acquisition coverage stays unresolved.
      A nine-unit offer has 11 units in affected acquisition rows; old aggregate positions are
      not safe fallback quantities. Preserve original rows, unknown costs/profit and risk window.
      Prevention alone does not resolve this task. No manual database repair, replay or bypass.
      Tracked in https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70.
      Complete read-only canonical prefix retained/independently verified: 11,598 rows/cursor 13,221,
      six eligible forecasts with ambiguous horizon outcomes. No accounting or learning promotion.
      Existing Copilot history was retrieved read-only without another owner export: 17,829 unique
      transactions, latest 15 October 02:17:25 UTC. Only 16 overlap the retained Friend range;
      3,037 nonempty Friend observations are later. This does not close the missing stock evidence.
- [ ] Complete a controlled player-operated buy -> collect -> sell workflow, partial cancel/relist
      and restart. Fresh #69 capture/source tests do not establish live eligibility or acceptance.
- [ ] Observe new full-counter/terminal buy accounting in a controlled trade. Fresh #69 capture
      passes; it does not establish a completed live trade or new buy budget.
- [ ] Design an auditable evidence-based resolution for already recorded false progress gaps.
      Preserve the raw sources, financial identities, unknown cost/profit and durable risk window.
      No manual database edit, replay, adoption, risk reset or bypass to force advice.
- [ ] Finish sustained latency and final-authority acceptance. #88 fresh READY/final PLACE_BUY and
      owner-confirmed buy guidance pass after normal history warming. Sale/entry reads can exceed the
      old 750ms client budget; transport and quiet-market corrections are installed. Measure sustained post-update reads,
      ACK drain and final eligible advice while retaining freshness, funding, loss and 5m/Medium
      settings. No manufactured funds, cleared reserve or risk reset to force an answer.
- [ ] Finish search/player-key/pause/context acceptance; these are not established by screenshots.
      Controlled intended trade, partial cancel/collect/relist and restart still need real outcomes.
- [ ] Continue batched HOME TEST CHECKLIST.md and PR #31 live acceptance. Installed combined #91 runtime/#92 tooling,
      status/API 1.13.2 compatibility and GET pipeline pass; fresh post-login source proof passes.
      Investigate unattributed repeated Swing layout exceptions before claiming UI stability.
      Confirm/highlight screenshot checks pass. Search/player-key/pause/context and controlled
      trade/cancel/collect/relist/restart still need outcomes. Agent collects logs directly; every
      game input remains manual. Earlier READY responses remain historical evidence only.
- [ ] Finish one authoritative ledger for planner, exits, inventory UI, calibration and session risk.
      Durable accounting, Performance, exposure and managed trade quantities/exits are implemented;
      durable recommendation risk is source-verified above; finish calibration/remaining consumer audit
      and preserve positive acquisition/cost evidence. Reproduce
      real faults before fixes; never edit a database or bypass exposure guards to unblock advice.
      Current consumer audit/final sale authority are source-verified in
      docs/architecture/parity-account-evaluation.md; controlled runtime acceptance stays open.

- [ ] Finish explicit account catalog/selection and cross-account performance aggregation without
      assigning legacy data or overstating coverage. Current sidebar is visibly current-account only.

## Next

- [x] Deliver #84 history-tolerant planning: lifetime and prospective capital are separated;
      paused-item exposure, new-capital reserves, unknown financial rows and the anchor persist.
      Windows clean 89s: 1,596 Java passed/six existing skips; 103 Python passed in 127.800s.
      Both exact-head CI checks pass; verified paired update/status/compatibility/scoped API pass.
      docs/history/history-planning-delivery-2026-10-18.md records source, install and live limits.
- [x] Reproduce/fix the owner's flickering Missed flips window and unstable selection. #86 is merged
      and installed after both exact-head CI checks: 1,604 Java passed/six existing skips; 103 Python
      tests pass. Verified backup/pair/Java 11/task/status/compatibility/scoped GET checks pass.
      Read-only ledger/risk/gap preservation passes. docs/history/history-display-delivery-2026-10-18.md
      records delivery. Owner confirms installed Missed flips stays populated while filtering;
      fresh #86 authenticated eight-slot proof passes at age six seconds.
- [x] Deliver owner-selected current-account discrepancy/risk policy (#88/3eb37d4). Original financial
      rows, unknown amounts, paused items and risk anchor persist. Both exact-head CI checks pass;
      1,616 Java passed/six existing skips; 103 Python passed; supported backup/pair/status/compatibility/
      scoped GET checks pass. Fresh READY/final PLACE_BUY and owner-confirmed buy guidance establish
      entry availability. See docs/history/current-account-delivery-2026-10-18.md. This does not
      resolve affected historical stock, establish complete trading acceptance or validate learning.
- [ ] Accept the installed combined sell-verification fixes (through #94/c8d3a18). Delayed reads,
      missing sell identities, acknowledged partial-fill races and incompatible retained prices are
      reproduced/fixed. Windows clean: 1,628 Java passed/six existing skips (80s), Java 11; 108 tooling
      tests pass. Both exact-head CI checks, verified pair/backup/task/financial-preservation/status/
      compatibility/scoped GET checks pass. Owner still saw waiting; #94 adds the quiet-market fix
      above. Initial sell guidance now has owner confirmation and fresh recorded trade transitions;
      complete maintenance/partial/cancel/relist/restart acceptance remains pending. See
      docs/history/quiet-market-sale-delivery-2026-10-18.md. No task/privilege/data repair or bypass.
- [ ] Audit calibration prediction provenance and cancellation censoring before changing strategy.
      Attribution, observed intervals and the runtime promotion fence are source-verified in history.
      Historical execution aggregates still pool censored/completed times but no longer authorize
      runtime correction in the new source. Forecast decomposition is source-verified above;
      complete frozen source captures and conservative horizon scoring are source-verified above.
      Finish supported censor-aware fitting, frozen challenger/training artifacts and promotion gates.
      Preserve existing data/dedup keys and separate
      real/paper evidence. Reproduce concrete faults first; promotion requires chronological held-out
      evaluation against a frozen baseline with return/loss/calibration guardrails.
      A reproducible bounded-logit/minimax Brier fitter and chronological validation CLI now exist;
      unknown/censored labels remain visible. The earlier validation has zero paired Brier improvement;
      the newer fit's later 25-forecast cohort (13 known/12 uncertain) still permits degradation.
      No model activates. See
      docs/history/prospective-evaluation-2026-10-18.md. Runtime promotion/compatible forecast loader, rollback
      and drift controls remain unimplemented. No fitted artifact activates learning.
- [ ] Finish manual cancel -> collect -> revalidate -> replace and partial-fill protection as a
      complete live workflow. Shared sell pricing and final companion authority are implemented;
      accept their production sequence. Acquisition-bound target/stop/duration replaces the live
      item-keyed TTL cache in the installed increment above; accept that provenance through restart.
- [ ] Build account-level chronological replay for 100M members at 5m, 30m, 2h, and 8h checks.
      Include end-of-run inventory liquidation, cash, buy limits, tax, and missed visits.
      AccountReplay now models shared cash/eight slots, partial fills, sequential attendance,
      collection, tax, canonical limits/risk, restart and conservative terminal marks in twelve
      scenarios. Replay v6 retains shared display stability, quiet-stock/qualified mark clocks and
      patient pricing; original plans now qualify through canonical sources/restart and final sale
      authority. Abstract displayed actions require a later delay/revalidation. Complete production
      real foreground/editor/transport and remaining action-order policy coverage and a
      qualified frozen market benchmark remain open; fixtures are not performance evidence.
      Queued v7 now exercises shared durable buy/sell continuations and bounded replacement plans.
- [ ] Finish prospective cadence/attendance and full-account return measurement. The new sidebar
      adds independent prospective session cursors and elapsed clocks; installed/player acceptance
      remains open. Historical CSVs still lack clocks/cadence labels for a fair GP/hour baseline.
- [ ] Measure fill calibration, then add gated challenger learning and clearly separated paper data.
- [ ] Close the feature-parity matrix in `docs/project-direction.md` and update user documentation.
      Deferred: validated forecast bands/actionable dump strategy, bank tags/badges/slot colors,
      broader paginated history/export and optional cross-device sync. Current details/player steps:
      docs/architecture/parity-account-evaluation.md. No parity claim is established.

## Waiting

- Historical session/cadence labels are unavailable. Do not infer them from execution timestamps.

## Historical evidence

Detailed completion receipts are preserved in [the 17 October task archive](docs/history/tasks-through-2026-10-17-recommendation-delivery.md). The earlier full pre-compaction task list remains in
[task history](docs/history/tasks-through-2026-10-16-source-audit.md). Earlier receipts remain in
[older task history](docs/history/tasks-through-2026-10-15.md). All unchecked work remains above;
read HANDOFF.md for current source/runtime evidence.
