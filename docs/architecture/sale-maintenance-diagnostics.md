# Active sale wait explanations

21 October 2026. Delivery state: `local-data/sale-maintenance-diagnostics-20261021/delivery-state.json`.
Installed behavior and manual acceptance are recorded separately there.

Records → Recommendation explains each active managed sale using the same `SaleMaintenancePricing`
evaluation consumed by client selection, companion authority and replay. The existing `quote` method
delegates to that evaluation; pricing, patience, stops, floors and policy versions are unchanged.
The first blocking check is reported, not a claim that every later check has passed.

Rows show standing ask, exact observed remainder, age since placement observation and actual counter
progress, two-sided trade ages and a reason: missing/stale prices, immaterial change, quantity outside
the managed remainder, unproven clocks, placement patience, recent fills, completed-market support,
or exact after-tax cost-floor protection. Clock waits show their earliest clock-only reassessment;
market waits have no promised deadline. Eligibility is only the shared pricing result: final
companion authority, delivery, durable workflow persistence and foreground priority still apply.

The planning worker produces immutable results before foreground collection/wait returns. Swing
reads cached results and performs no history fetch, model evaluation, outbox read or authority call.
Account/settings/market/ledger changes and exact offer identity/counter/progress changes withdraw
results immediately. Results expire after at most 30 seconds and earlier at quote freshness,
ledger freshness, patience or five-minute bucket boundaries. Unknown managed scope is explicit;
confirmed personal sales are excluded and personal extras cannot borrow a managed pool's costs.

Overdue labels require a retained comparable immutable forecast for the exact original actual sell
order. Refreshed advice, acquisition estimates and legacy timing alone cannot supply it. Missing
forecasts remain unavailable; exceeding an unvalidated estimate cannot authorize repricing.
No source, financial, risk, workflow or prediction history is rewritten for these diagnostics.

Behavioral regressions cover all four actual cadences, recent fills, market support, stale/future
evidence, exact subset floors, unknown plans, extras, original forecast retry/ACK/restart, account
changes, slot reuse, logout, early foreground returns and UI expiry. Native player acceptance and
the existing controlled full trade loop remain separate release gates.
