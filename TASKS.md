# Tasks

Updated 23 October 2026. Read [HANDOFF.md](HANDOFF.md) and the actual delivery receipt at
`local-data/prospective-sale-acceptance-20261023/delivery-state.json` first. Implemented/tested, merged,
installed and player accepted are separate states. Owner is home; older login deferrals are superseded.

## Current batch

- [x] Implement/test independently durable prospective sale-acceptance capture in the outbox before
      presentation, with strict proof format 2 and unchanged legacy format 1. Read outages preserve
      first-cancellation evidence; failed staging cannot self-promote or repair a published source.
      [Contract](docs/architecture/prospective-sale-capture.md); actual delivery in receipt.

- [x] Implement/test bounded distinct working-BUY maintenance after every routine sale refuses;
      preserve urgent/unlisted exits, source/revision fences and durable selected acceptance.
      [Contract](docs/architecture/buy-maintenance-fallthrough.md); actual delivery in receipt.

- [x] Implement/test indexed sale-policy source/account lookups and deduplicated immutable
      first bindings; expose copied market-side prices/ages and material-change threshold in Active
      sales. [Contract](docs/architecture/indexed-sale-policy-evidence.md); installed PR #148 drained
      snapshotted events and delivered BUY guidance, but intermittent maintenance latency remains.

- [x] Repair final replacement-refusal starvation of working maintenance; isolate bounded report
      transport and aggregate immutable Performance inputs outside trading locks with incremental
      completion evidence. Export committed sale-policy acceptance despite dirty later facts.
      [Contract](docs/architecture/maintenance-blockade-recovery.md); actual verification/install in receipt.
- [ ] Verify fresh post-update ordered ACK/account/maintenance/entry recovery from the live blockade;
      player trading and sustained latency remain separate from source/concurrency regressions.
- [ ] Preserve first-cancellation acceptance through a genuinely unreadable acceptance document
      using prospective evidence handling; never retrofit a published receipt-free cancellation.
      Source regressions now pass; controlled read-outage/restart/player acceptance stays open.
- [ ] Reassess independently eligible BUY maintenance after all routine sale candidates are refused,
      with bounded excluded-episode traversal and no invisible continuation promises. Source
      regressions now pass; controlled player acceptance remains open.

- [x] Implement prospective exact sale-policy receipts and separately ordered client acceptance,
      preserving each origin's policy through cadence changes, late fills, collection and restart.
      [Contract](docs/architecture/prospective-sale-policy-receipts.md); actual verification/install in receipt.
- [ ] Accept prospective sale receipts, cadence changes, receipt outages and distinct-origin relists
      through player operation; source/HTTP regressions are separate from controlled acceptance.

- [x] Retain independent original BUY forecasts in live tracking and defer new deadline freezing
      while its exact durable source is unavailable; proven unknown forecasts retain frozen fallback.
      [Contract](docs/architecture/independent-buy-deadlines.md); actual delivery in receipt.
- [ ] Accept live original deadlines, source-write outage/recovery and overdue cancellation with
      recent partial fills through player operation; prior frozen metadata remains unchanged.

- [x] Separate freshly accepted exact BUY plans from expired learning attribution; freeze independent
      source/ordering evidence through outbox, canonical acquisition and maintenance/replay timing.
      [Contract](docs/architecture/independent-accepted-buy-plans.md); verification/install in receipt.
- [ ] Accept independent plan capture, partial fills, retry/ACK/restart and exact managed exits through
      player operation. Legacy unknown plans remain unknown; learning remains unvalidated.

- [x] Implement/test explicit distinct-origin successors across client acceptance/outbox, callback
      reconciliation, residual/child budgets, selected editor, canonical sale authority and replay.
      [Contract](docs/architecture/distinct-continuation-successors.md); actual state in new receipt.
- [ ] Accept parallel same-item origins, smaller replacements, child cancel/collect, late original
      fills, failed writes and restart through player operation. Source tests are separate.
- [x] Add prospective exact policy-setting evidence before relaxing saved-sale eligibility across
      checking-cadence changes; current settings cannot establish historical acceptance eligibility.
- [ ] Bound relationship retention through proven origin reachability; never discard live proof by TTL.

- [x] Prevent accepted sale reductions from falling back to an abandoned target on missing market
      support; share relist evidence across engine/editor/authority/replay and preserve exact floors.
      [Contract](docs/architecture/relist-market-evidence.md); actual delivery in receipt.
- [ ] Accept evidence-loss/recovery, partial collection, exact relists, stops/wind-down and restart
      through player trading; source verification and synthetic replay remain separate.
- [ ] Deliver/accept multiple same-item continuation preservation through explicit successor-to-origin
      evidence; no inferred association or weaker unknown-order supersession. See current batch.

- [x] Stage new continuation acceptance/source upgrades separately from accepted observation retries;
      unify live/replay original BUY timing without learning promotion. Replay 14 / sale policy 5.
      [Contract](docs/architecture/continuation-acceptance-transactions.md); actual delivery in receipt.
- [ ] Accept failed-write withdrawal/revalidation and original BUY deadlines through player-operated
      cancel/late fills/collect/exact replacement or relist/restart. Offline regressions remain separate.

- [x] Preserve future-clock continuations without granting guidance; retain current corrected fills,
      collection and closure through retries/restart. Narrow saved post-collection remainders instead
      of poisoning their document. [Contract](docs/architecture/continuation-clock-preservation.md).
- [ ] Accept clock-recovery and corrected late-callback workflows through player-operated cancellation,
      collection, exact replacement/relist and restart. Offline timestamp simulations are separate.

- [x] Isolate current-login continuation evidence from cached recovery offers; preserve collected
      remainders across snapshot save lag and account changes. Retire rebuy promises only after exact
      cancellation acceptance and durable commit. [Contract](docs/architecture/continuation-restoration-boundaries.md).
- [ ] Accept crash/reconnect restoration, late fills, collection and exact replacement/relist through
      player operation. Source regressions and installed hashes do not establish trading acceptance.

- [x] Implement independent later sale-source acceptance clocks and accepted-board parallel-sale
      proof. Failed commits preserve hints; changed/new/cleared orders cannot borrow permission.
      Paired sale policy 5 / replay 12; exact pooled cost and original financial/risk guards persist.
      [Contract](docs/architecture/sale-continuation-evidence.md); delivery state in the newest receipt.
- [ ] Accept both fixes through player cancel/late fills/collect/relist/restart, including parallel
      same-item sale progress. Installation and source regressions do not establish this acceptance.

- [x] Implement health request isolation and removal of automatic historical diagnostic scans.
      Preserve scoped health, bounded overload/retry identities and all financial/risk authority.
      Receipt: `local-data/health-request-isolation-20261021/delivery-state.json`;
      [contract](docs/architecture/health-request-isolation.md). Read the receipt for verification/install;
      sustained account latency and controlled trading acceptance remain open.

- [x] Repair starter's single-probe refusal of an existing listener: bounded same-PID retry for
      transient health failures, fail-closed credential/protocol/body and listener-change checks,
      no duplicate process/task/data changes. #137 is merged; later live runtime blocking is tracked above.
      Receipt: `local-data/starter-health-retry-20261021/delivery-state.json`.

- [ ] Accept all ordinary maintenance sale alternatives through final authority after a refused first
      reduction. Persist only the presented exact episode; preserve late-fill/collection caps, original
      costs/plans, earlier-buy and fresh-stop priority, rejection withdrawal and account isolation.
      [Contract](docs/architecture/ordinary-sale-maintenance-queue.md) /
      [receipt](docs/history/ordinary-sale-queue-delivery-2026-10-21.md).

- [ ] Accept active managed-sale wait explanations in Records → Recommendation: shared evaluator,
      exact episode/progress and evidence expiry, separate original actual-order estimates and
      clock-only reassessment. Eligibility never bypasses final authority or changes pricing/risk.
      [Contract](docs/architecture/sale-maintenance-diagnostics.md) /
      [receipt](docs/history/sale-maintenance-diagnostics-delivery-2026-10-21.md).

- [ ] Deliver/accept same-second acquisition-plan binding, mixed-origin remaining ranges, no invented
      stops, selected managed subset authority, earlier supported non-loss cancel/collect/relist,
      progress-aware and bounded buy maintenance, shared horizons and frozen unvalidated diagnostics.
      Preserve source/financial history and risk; controlled full-loop/restart acceptance remains open.
      [Contract](docs/architecture/trade-plan-maintenance.md) /
      [receipt](docs/history/trade-plan-maintenance-delivery-2026-10-20.md).

- [ ] Deliver/accept all eligible exits through final companion authority, exact saved relist caps,
      stop priority and withdrawal of every rejected foreground/pending/editor trade. Separate actual
      inventory from equipment, preserve bank/collection evidence, cap initial exits to available
      inventory with exact subset cost and verify the companion inventory bound.
      [Contract](docs/architecture/exit-authority-queue.md) /
      [receipt](docs/history/exit-queue-delivery-2026-10-20.md).

- [ ] Deliver/accept exact recorded-sale disposition of older collected rebuy promises, durable source
      receipts, collected/cancelled-sale restart recovery and persistence retries across account switches.
      Installed #132 now retires Dual macuahuitl with its exact sale receipt after fresh login. Preserve unrelated hints,
      exact holdings/costs/plans, financial/source rows, CURRENT_ACCOUNT_V3 and every risk guard.
      [Contract](docs/architecture/continuation-sale-disposition.md) /
      [receipt](docs/history/continuation-sale-disposition-delivery-2026-10-20.md).

## Outstanding acceptance and release requirements

- [x] Preserve and temporarily refuse well-formed future-clock continuation documents when the local
      clock is behind creation/acceptance, instead of treating them as terminal and removing them.
      Malformed evidence and genuine fresh counter regressions must retain their existing refusal.

All 36 previously unchecked requirements are retained below, with obsolete delivery receipts moved to
[the full prior task archive](docs/history/tasks-through-2026-10-20-pr131.md). Current installed state
comes from the newest receipt, never an old PR's wording. Prioritize reliable workflows, sustained
latency, complete production replay/returns, validated learning, then remaining conveniences.

- [ ] Accept expected-exit profit and cancelled-buy recovery (#131). Try every eligible saved remainder through final authority; exit verified acquired partials after refusal, including funding/last-slot cases. Preserve exact pooled costs/tax, original targets and separate instant-sale/risk values. Accept durable healthy/observed-sale supersession.
      [Contract](docs/architecture/exit-profit-and-replacement.md).
- [ ] Accept callback capture before worker dispatch (#130): original fill/terminal/EMPTY sources and advice survive logout, stopped/rejected workers, retries, account switches and restart. Local capture performs no HTTP; ordered prefix ACKs and backoff persist.
      [Contract](docs/architecture/capture-before-dispatch.md).
- [ ] Accept exact-source reversible review archival and durable accounting mode (#129). Only closed unmatched sales without earlier purchases qualify; retain purchase evidence, all financial rows, unknown coverage and risk guards. New failures remain visible; reject legacy accounting downgrade.
      [Contract](docs/architecture/trade-review-archive.md).
- [ ] Accept startup sale accounting/evidence display (#128). Restore recorded automatic scope before offer drain without stale cash authority; full SELLING then SOLD counts once. Keep unmatched cost unknown, selected sale investigation and logout/expired-card withdrawal.
      [Contract](docs/architecture/startup-sale-accounting.md).
- [ ] Accept forced atomic offer persistence/recovery (#127). Retain original IDs/order/counters/plan bindings through save failures and profile switches; preserve changed files and withdraw stale collection advice. UI reads do not force retries; unsaved memory is not crash durability.
      [Contract](docs/architecture/offer-persistence-recovery.md).
- [ ] Accept live revalidation with a maintenance editor open (#126). Recheck work every pass; same-item stops withdraw buys, unrelated typing protection stays bounded, and collection/explicit wind-down need no market quote.
      [Contract](docs/architecture/live-guidance-revalidation.md).
- [ ] Accept fresh stops through saved buy/sell continuations (#125). Preserve healthy accumulation, stop the working buy or exit verified collected units, commit rebuy retirement before sale delivery and bypass ordinary backoff for fresh stops. Keep replay V10 boundaries.
      [Contract](docs/architecture/stop-continuation-priority.md).
- [ ] Accept committed cancellation identity and bounded advice lifetime (#123/#124). Preserve saved account/episode promises through changed cards/restart; reject unrelated/uncommitted cancellations and delayed/stale editor/input delivery. Keep installed 1.13.3 compatibility.
      [Contract](docs/architecture/guidance-lifetime-and-cancellation.md).
- [ ] Accept durable maintenance delivery (#122): forced atomic cancellation promises, dirty progress/removal retries across profiles, changed-file preservation, episode-local backoff and immediate withdrawal of declined foreground sells.
      [Contract](docs/architecture/durable-maintenance-delivery.md).
- [ ] Accept CURRENT_ACCOUNT_V3 and workflow recovery (#121). Fresh verified cash supports unrelated entries through valuation-only stock uncertainty; preserve affected-item pauses, unseen-stock cost, confirmed losses and every source/exposure/funding/slot/limit guard. Keep durable exact remainders and explicit estimate coverage/ages.
      [Contract](docs/architecture/verified-cash-workflow-recovery.md).
- [ ] Accept ordered-observation batching and retry wakeups (#119): exact copied individual sources/bindings survive batch persistence, prefix ACK races and late/completion retries without lost wakeups. Measure installed sustained timing.
      [Contract](docs/architecture/ordered-observation-batches.md).
- [ ] Accept complete durable buy/sell continuation workflow (#116): exact remainders through late fills/collection/restart, bounded fresh replacement authority/forecasts, foreground maintenance identity, stale/overflow guards and replay v7 volume integrity.
      [Contract](docs/architecture/trade-continuations.md).
- [ ] Accept cancelled-sale recovery (#114): retain exact collected remainder through slot reuse/retention/restart, distinguish earlier listings from later replacements and reject stale episode advice. Keep current physical/ledger/cost/target/final authority.
      [Contract](docs/architecture/durable-sale-relist.md).
- [ ] Accept completed-offer latency (#111): canonical prefix reuse retains earliest-original checks, tail/external invalidation and transactional rollback/deduplication. Observe actual source-to-ACK-to-guidance latency; synthetic timing is insufficient.
      [Contract](docs/architecture/completed-offer-latency.md).
- [ ] Accept acquisition-bound target/stop/sell-duration (#108) through partial fills/sales, ACK/relist/restart. Later snapshots/item TTL cannot create original plans; mixed/unproven context stays unknown. Finish consumer authority and production replay qualification.
      [Contract](docs/architecture/acquisition-exit-context.md).
- [ ] Accept patient initial/maintenance selling (#105): protect available original healthy targets within horizon; routine reductions now follow the owner-approved earlier supported non-loss policy in the current contract, with no recent fills and sustained completed-market support. Preserve fresh stops/wind-down, exact floors, unknown cost and final authority.
      [Contract](docs/architecture/patient-sale-pricing.md).
- [ ] Accept expanded panel/performance/prompt guidance (#103): session/period/reset, durable profit/ROI/completed orders, elapsed hourly results, marks, paged sales and estimated action profit/time. Reset preserves finance/risk; validate live UI and sustained delivery.
      [Contract](docs/architecture/panel-performance-and-latency.md).
- [ ] Accept qualified quiet-stock marks (#101) under the current V3 workflow: direct bids 120s; completed public evidence at most 15m, 10% haircut/tax and quantity limits; no gain credit. Unsupported/unseen coverage and all original financial/loss guards persist.
      [Contract](docs/architecture/qualified-stock-risk-marks.md).
- [ ] Accept stock-valuation explanations, fresh-evidence recheck and native maximized Records/Missed flips (#98). Preserve exact uncertainty/reserve explanations and historical separation; genuine waits remain.
      [Contract](docs/architecture/verified-cash-workflow-recovery.md).
- [ ] Accept public market graphs (#96): independent 5m/1h/6h price/volume history, selected-item investigation, ranges/zoom/pan/reset/expand and explicit experimental return scenarios. Frozen bands were worse than persistence; remain display-only.
      [Contract](docs/architecture/market-graphs.md).
- [ ] Resolve already affected stock safely with auditable evidence (issue #70). A nine-unit offer has 11 acquired units in affected rows; maxima/aggregate positions are unsafe remaining-stock/cost proof. Preserve unknowns, all original rows and risk anchor; retained Copilot export has insufficient overlap.
      [Contract](docs/architecture/trade-evidence-audit.md).
- [ ] Complete controlled player buy → partial/full fill → collect → sell → collect, partial cancel/relist and restart. Source tests/fresh capture do not establish controlled trading acceptance.
      [Contract](docs/architecture/automatic-trade-scope.md).
- [ ] Observe new full-counter progress/terminal buy accounting in a controlled trade. Prevention/source capture does not prove completion or fresh buying power.
      [Contract](docs/architecture/full-counter-progress.md).
- [ ] Resolve previously recorded false-progress gaps using auditable evidence. Preserve raw sources, financial identities, unknown cost/profit and risk; never edit/replay/adopt/reset to force advice.
      [Contract](docs/architecture/full-counter-progress.md).
- [ ] Finish sustained latency and final-authority acceptance: observation, ordered outbox/ACK, planning, sale/entry authority and actual client delivery. Report median/tail/cold scope; preserve freshness/funding/loss/settings and never manufacture funds or release reserves.
      [Contract](docs/architecture/panel-performance-and-latency.md).
- [ ] Finish search/player-key/pause/context acceptance during intended trades and partial cancel/collect/relist/restart. Screenshots alone are insufficient; narrow player-key exception never confirms/places/collects.
      [Contract](docs/architecture/trade-input-assistance.md).
- [ ] Complete HOME TEST CHECKLIST.md and PR #31 live acceptance; investigate unattributed recurring Swing layout exceptions before UI-stability claims. Confirmation/highlight screenshots do not establish search/input/context/trade outcomes.
      [Contract](docs/architecture/panel-performance-and-latency.md).
- [ ] Finish one authoritative ledger for planner, exits, inventory UI, calibration and session risk. Retain positive acquisition/cost provenance; complete calibration/remaining-consumer audit and controlled runtime acceptance without database repair or guard bypass.
      [Contract](docs/architecture/parity-account-evaluation.md).
- [ ] Finish explicit account catalog/selection and real cross-account performance aggregation. Current sidebar is current-account only; legacy unassigned data stays unassigned and coverage stays explicit.
      [Contract](docs/architecture/account-isolation.md).
- [ ] Accept combined sell-verification paths through #94: delayed reads, missing sell identities, ACK partial races, retained-price incompatibility and quiet-market limits. Initial Sell guidance was owner accepted; full maintenance/partial/cancel/relist/restart remains open.
      [Contract](docs/architecture/sale-guidance-delivery.md).
- [ ] Finish calibration provenance/cancellation censoring before strategy changes. Preserve immutable prospective advice, deduplication and unknown/censored labels; complete compatible runtime forecast loader, frozen challenger/training, chronological return/loss/calibration gates and explicit promotion/rollback/drift controls. Current cohorts do not establish improvement.
      [Contract](docs/architecture/frozen-execution-evaluation.md).
- [ ] Finish manual cancel → collect → fresh revalidation → exact replacement and partial-fill protection as a complete production workflow, including original acquisition target/stop/duration through restart.
      [Contract](docs/architecture/trade-continuations.md).
- [ ] Finish account chronological replay/qualified frozen benchmarks for 100M GP members, eight slots, separately at 5m/30m/2h/8h. Include cash/limits/tax/losses/partial fills/missed visits/terminal inventory. Complete real foreground/editor/transport/action-order parity; synthetic scenarios and price touches are not returns.
      [Contract](docs/architecture/parity-account-evaluation.md).
- [ ] Finish prospective cadence/attendance/full-account return measurement. Session cursors/elapsed clocks need installed player acceptance; historical CSVs lack cadence/session clocks and cannot establish GP/hour.
      [Contract](docs/architecture/panel-performance-and-latency.md).
- [ ] Measure eligible real execution calibration, then gate challenger learning with chronological frozen evidence and clearly separate paper data. Runtime stays PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION.
      [Contract](docs/architecture/execution-observation-intervals.md).
- [ ] Close the project-direction parity matrix and user documentation. Still deferred: validated forecast bands/actionable dump feed/opt-in local-sound-webhook delivery, bank tags/badges/slot colors, paginated investigation/export and optional cross-device sync. No full-parity claim.
      [Contract](docs/architecture/parity-account-evaluation.md).

## Historical evidence

Completed receipts and the full prior requirements are preserved in
[the PR #131 task archive](docs/history/tasks-through-2026-10-20-pr131.md), which links older history.
Historical session/cadence labels remain unavailable; do not infer them from execution timestamps.
No account-data edits, replay, adoption, outbox clearing, risk reset, game input or forced client termination.
