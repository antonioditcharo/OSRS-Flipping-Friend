# Working BUY maintenance after refused sales

23 October 2026. Actual verification, CI, merge and installation are recorded in
`local-data/buy-maintenance-fallthrough-20261023/delivery-state.json`.

Previously a routine sell won local selection, collecting later sell candidates while deliberately
skipping lower-priority BUY evaluation. If all sales failed final validation, the client went
straight to new-entry planning or WAIT. An independently stale working BUY could remain hidden.

After every routine sale refuses, reassess only working BUY offers. Check at most eight distinct
source episodes, excluding each rejected identity before the next evaluation. A repeated or missing
identity ends traversal. Use the existing BUY pricing, original deadline, recent-fill, freshness,
limit and final cancellation selector. Account generation/revision changes withdraw the result.
An accepted later sale still wins; refused urgent stops and unlisted exits retain their priority.
If no BUY qualifies, retain the prior sale wait or ordinary entry path.

Evaluation does not create legacy rebuy promises for undisplayed candidates. Only normal final
presentation can commit durable acceptance for the selected order. Exact remaining quantities,
partial-fill/cancel/collect/replacement accounting, original plans, source identities, slot reuse,
account fences and CURRENT_ACCOUNT_V3 remain unchanged.

Regressions cover real engine selection with excluded episodes, recent fills/stale quotes, multiple
BUY refusals, repeated identities, urgent/unlisted priority and revision changes during validation.
Controlled player cancellation/late fills/collection/replacement and sustained latency remain open.
