# Safe sell relisting

17 October 2026. This closes a bypass in the existing player-operated cancel, collect and relist
workflow. It does not migrate all sell selection to companion policies or resolve historical stock.

Regression 02d1c4b reproduces four actual decision-chain failures: the personal-sale helper could
relist managed units below their exact taxed cost floor, include personally owned extra units,
proceed without the durable ledger, and suggest affected stock despite recorded reconciliation.
Regression 8692ed3 reproduces offer identity confusion and malformed cancellation counters.

Relisting now requires a fresh, valid matching-account acquisition ledger. Pending delivery,
unavailable/old evidence and relevant or unbounded reconciliation produce explicit waiting advice.
An unrelated scoped gap does not discard clean acquisition evidence. Quantity is capped by the
cancelled unsold remainder, visible inventory and the unlisted durable position. Physical absence
does not alter accounting or prove disposal. Another open buy prevents selling around accumulation.

Managed relists use SellTimingEngine and the remaining exact pool cost. Subsets use
SaleAccounting.allocateCost. Price stabilization is followed by exact taxed floor revalidation;
only the existing stop or explicit sell-only mode authorizes a loss. Forecast/profit are calculated
for the final displayed order. A fresh market sale quote is required. This is a quote estimate;
recorded profit still comes exclusively from the durable account ledger.

New guidance intents retain the offer episode identity, original price/total and observed fill
counter. Cancellation/collection must match and cannot regress that counter. Legacy intents lacking
the additive fields retain their legacy matching only against legacy offers; they cannot match a
new UUID episode. A newly advised managed sale also retains a cost-required flag across restart;
missing acquisition evidence cannot silently downgrade it to a personal sale. An observed external
sale may still be relisted with unknown acquisition cost/profit after the evidence checks.

These changes write only normal account-scoped guidance JSON during normal runtime. No schema,
financial event identity, tax rule, historical row, database, reserve or learning key is rewritten.
Tests use disposable files and a mocked companion ledger; no real game input is generated.

Delivery and remaining live checks are in [HANDOFF](../../HANDOFF.md) and [TASKS](../../TASKS.md).
