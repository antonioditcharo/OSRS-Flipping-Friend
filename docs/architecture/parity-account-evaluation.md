# Sale authority, account replay and execution fitting

17 October 2026 (America/Anchorage). This increment implements working product and offline evaluation paths from the completed Copilot research. It does not establish functional parity, superior returns, controlled trading acceptance or a promoted model.

## Active product paths

- Managed SELL and MODIFY_SELL guidance now requires a final scoped `GET policy/sale` decision. The request binds item, exact displayed price/quantity, loss-cut flag, slot and canonical acquisition/offer source. The companion independently reads its accepted ledger, current quote and account policy. Old sources, changed partial fills, excessive quantities, paused items, stale evidence and violated exact taxed floors refuse the action. A refusal becomes WAIT before foreground acceptance. Personal initial-sale quotes retain unknown profit; maintaining their observed offer uses the same scoped maintenance boundary.
- `SaleMaintenancePricing` contains the existing pure maintenance pricing rule. The local workflow and companion call it; price stabilization is still re-floored by the client. Local orchestration owns patience, inventory/bank/collection routing and persisted relist intent. The companion's final check does not replace missing original duration context with a new patience estimate.
- Recorded sales opens separately from the compact sidebar. It shows the latest 100 durable incremental sale updates, not reconstructed flips. Filtering, sorting, selected-source/tax-rule investigation and an item-graph link are available. Missing gross/cost/profit/tax remains unknown; old rows are neither backfilled nor reclassified. This bounded display is not a training prefix.
- Graph lookup accepts an item ID or exact name, supports 5m/1h history and shows observed timestamp/prices/side volumes on hover. It does not invent forecast bands.
- Market drops scans the independent public market rather than the player's positions. It requires fresh two-sided quotes, a completed earlier liquid five-minute reference bucket and a 10% low-price drop. This is a watch list, not a validated dump-entry strategy or replacement for ordinary final entry/risk authority.
- Held-stock warnings use authoritative acquired quantities, have an independent opt-in desktop preference, suppress duplicate episodes per account/holding, and dispatch to an already configured Discord webhook. Blank webhook configuration no longer controls local notifications. Transport tests intercept requests; no external test message is sent. Invalid/non-Discord URLs are refused, mentions are disabled, response status is checked and URL/token-bearing exceptions are not logged. Actual desktop/sound/webhook delivery remains a player acceptance check.

## Ledger consumer audit

| Consumer | Authority and remaining distinction |
| --- | --- |
| Acquired quantity/cost and initial exit | Canonical acquisition remainder in scoped `AccountLedgerSnapshot`, fresh ACK-drained client evidence, exact subset allocation; final companion sale policy |
| Sell maintenance/relist | Same ledger and exact costs, current canonical offer episode/source and remaining quantity; local persisted manual sequence, final companion sale policy |
| New/replacement buy | Existing final companion entry policy, acknowledged eight-slot/cash proof, canonical exposure, buy limits and durable risk; unchanged by this increment |
| Recorded totals/Performance/sale investigation | Scoped durable sale rows and tax/profit bounds; unknown historical money stays unknown |
| Holdings and market charts | Canonical managed quantities; displayed target/market values remain estimates, not liquidation equity or realized profit |
| Local history/bank trajectory | Explicitly labeled journal estimates; never fallback financial authority |
| Execution evaluation/fitting | Complete verified canonical prefix and original qualified forecasts; no journal, paper sample or diagnostic-tail fallback |
| Legacy plugin `Calibrator`/aggregate timing totals | Diagnostic/legacy scorer only; runtime `refresh(false)` and neutral companion timing fence remain. No sample count enables them |
| Held-stock alerts | Fresh managed trade ledger, not every owned unit with a historical item ID |

## Offline account runner

Build the standard pair, then use the companion JAR as an offline classpath:

```powershell
java -cp companion/build/libs/flipping-friend-companion.jar com.flippingfriend.companion.AccountReplayMain run <frozen-input.json> <new-local-data-directory>
java -cp companion/build/libs/flipping-friend-companion.jar com.flippingfriend.companion.AccountReplayMain verify <bundle-directory>
```

`AccountReplayInput` is the version-one input schema. It requires 100M GP, members and eight slots, an explicit start/end, immutable mapping, chronological available-at/observed-at market frames, complete-before-availability 5m/1h history, distinct completed five-minute volume-flow buckets and optional missed-visit/restart schedules. Unsupported tax periods, future quotes/history, duplicate flows and overwrites fail. Inputs must identify their source; hand-authored fixtures must never be described as captured market evidence.

Each independent cadence (5m, 30m, 2h, 8h) runs at 0.1%, 0.5% and 1% counterparty-volume shares. An offer must predate the entire execution bucket plus the configured delay; touching a price cannot fill more than the scenario's available volume. Buy capital is reserved once. Partial fills, completed-but-uncollected items/proceeds, cancellation refunds, collection and later placement consume actual slots and sequential manual-action time. Missed visits delay those actions. Buy limits and risk use canonical projections in isolated synthetic databases. Restart reopens those databases without replaying financial rows or resetting the risk window. The synthetic identity/database names cannot be frozen by the real-account evaluator.

The runner calls production candidate generation/portfolio optimization with an explicit clock, canonical current-offer/exposure/risk checks, entry policy, collection selection, buy-limit maintenance, `SellTimingEngine` and shared sale-maintenance pricing. Version 2 added shared display-price stability and restart reset; version 3 added the shared quiet-stock non-loss exit rule. Installed version 4 adds explicit completed aggregate clocks for the approved stock-risk marks; legacy missing clocks stay missing and cannot qualify quiet marks. [Current receipt](../history/qualified-stock-valuation-2026-10-18.md). **Production-policy coverage is not complete:** local outbid-buy cancellation/replacement intent, sale-relist intent persistence and all foreground acceptance timing are not yet reproduced. Do not use this runner to pass a production-policy promotion gate. These remain explicit release requirements, rather than being hidden by a “same planner” claim.

Results report bag cash, locked remainder, collection proceeds, acquisition cost, conservatively taxed/haircut terminal marks, realized profit, equity-change lower bound, elapsed-hour rates, drawdown, losses, abstentions and interactions separately. Missing terminal marks contribute zero to a disclosed lower bound and set incomplete coverage; no sale/disposal is booked by terminal marking. Fixtures prove conservation, not market performance. A 10-unit 100 -> 120 GP example yields exactly 180 GP profit/20 GP tax in both simulation and the canonical ledger.

The bundle retains input/result hashes and the executing packaged JAR, and verification recomputes all twelve scenarios. Real benchmark returns are pending an appropriate frozen market capture and complete production-policy replay. Historical Copilot exports still cannot provide missing session/cadence clocks.

## Offline challenger

```powershell
java -cp companion/build/libs/flipping-friend-companion.jar com.flippingfriend.companion.ExecutionChallengerMain fit <verified-training-bundle> <new-local-data-model-directory>
java -cp companion/build/libs/flipping-friend-companion.jar com.flippingfriend.companion.ExecutionChallengerMain evaluate <training-bundle> <model-directory> <later-validation-bundle>
```

The fitter uses the existing complete-prefix verifier. Its frozen artifact binds account, manifest/cursor/capture, fitting-class hash, method/schema, per-side/exact-horizon parameters and known/unknown/exclusion counts. A bounded logit offset minimizes worst-case Brier loss plus fixed regularization. Unknown/censored outcomes remain in the objective; all-unknown groups retain the baseline parameter. This is conservative partial identification, not an independent-censoring survival estimator or exact timing fit.

Loading reproduces the parameters from training data; rehashing edited parameters cannot bypass it. Validation must extend the unchanged account prefix strictly after training. Later settlement cannot refit training or become a new validation offer. Paired loss-change bounds retain unknown labels and report side/horizon/confidence and UTC-period slices plus censoring outcomes. Completion calibration does not change wait/work time. An explicit offline scoring seam can compare a challenger through candidate ranking; live service never installs it and no original real forecast is rewritten.

**Runtime promotion, compatible calibrated forecast publication/loader, rollback and drift control remain unimplemented release gates.** The runtime's existing hard `HOLD_FOR_VALIDATION`/neutral timing behavior remains active, including when learning is enabled in settings. Fitting, favorable calibration or more observations cannot authorize promotion. Complete production-policy replay and qualified chronological return/loss evidence must precede that work's activation.

## Player-operated acceptance

After the tested pair is installed and RuneLite is launched normally:

1. Log in, open GE and wait for outbox ACK drain/current eight-slot evidence. Agent reads status/logs/scoped HTTP directly. A refusal is an outcome to investigate, never a reason to clear reserves or alter account data.
2. Open Records -> Recorded sales. Filter/select a known and an unresolved sale, inspect its source/rule and open its graph. Check an item lookup and both graph resolutions; profit/cost uncertainty must remain visible.
3. On an eligible displayed buy, inspect the exact item/quantity/price. Use the configured key only in an already matching price/quantity prompt if desired. Enter, Confirm and placement are yours. Verify skip/block/pause withdraw stale guidance/key fills.
4. Observe a partial fill, cancel only when the displayed workflow calls for it, collect manually, then revalidate the replacement/relist. No quantity may exceed the cancelled remainder or authoritative stock. A final authority refusal must show WAIT.
5. Complete buy -> collect -> sell -> collect. Include a personal sale selected manually; its acquisition profit remains unknown. Compare the durable incremental rows and exact totals, preserving existing gaps/reserves.
6. Restart through the supported lifecycle during a partial or relist sequence. Verify quantities/costs, pending intent, account scope and guidance survive without duplicate financial/sample rows.
7. Verify opt-in local warning/sound and the already configured webhook on a genuine future trigger; do not manufacture a market event or send an unsolicited external test. Market drops remains investigation only.

Pending conveniences are evaluated forecast bands, a validated actionable dump strategy/feed, portfolio bank tags/badges/slot colors, broader paginated history/export and optional cross-device synchronization. Prospective session/attendance/equity measurement is also still required. These are deferred capabilities, not claims of parity.
