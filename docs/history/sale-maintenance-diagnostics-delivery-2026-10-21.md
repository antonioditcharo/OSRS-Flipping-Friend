# Sale maintenance explanations delivery

Actual verification, CI, merge, backup, installed hashes and remaining acceptance are recorded in
`local-data/sale-maintenance-diagnostics-20261021/delivery-state.json`.
[Contract](../architecture/sale-maintenance-diagnostics.md).

Active managed-sale explanations now come from the pricing evaluator itself. Records → Recommendation
shows observed placement/progress ages, current blockers and only supported clock reassessment times.
Actual-order forecasts remain distinct from acquisition estimates and are visibly unvalidated.
Eligibility is not final companion authority or an instruction to cancel.

The evaluator preserves existing prices and authority; diagnostics do not create workflow promises,
persist financial events, change risk or promote learning. Expired or changed account/offer/market/
ledger results withdraw immediately. The UI consumes immutable worker results without model or HTTP
work. Previous #134 installation and fresh-login evidence remain in the
[prior receipt](trade-plan-maintenance-delivery-2026-10-20.md).

Native display/player acceptance, controlled full-loop/restart trading acceptance, sustained latency,
complete production replay, account-return benchmarks and validated forecasting remain open.
