# Supported small sale adjustments

25 October 2026. Source, verified artifacts, installation and player acceptance remain separate
in HANDOFF and `local-data/hosted-pilot/walkdown-guidance-delivery.json`.

## Reproduced maintenance gap

The shared half-percent deadband could exclude an entire narrow profitable spread even after
repeated buyer-paid trades immediately below a stalled listing. The client also filtered those
sales before shared pricing, and an old opposite-side trade rejected a non-loss ask adjustment.
Price smoothing and final authority repeated the coarse threshold. The result was a missed
maintenance candidate, rather than proof that every long-held sale was dropped by delivery.

## Working-sale decision

Known-cost, proven remaining stock can use a fresh buyer-paid last trade (at most five minutes
old) and two consecutive completed recent five-minute buckets with actual buyer volume. The
opposite-side trade must have a valid positive nonfuture clock; an aged bid cannot authorize a
loss, wind-down or unknown-cost exception. Market feed and current account/source checks remain
fresh at final authority. Existing placement and real-progress patience remain max(8m, cadence).

When the last buyer-paid trade is below the ask by less than the ordinary deadband, a small
adjustment additionally requires both completed bucket averages at or below that last trade.
The new limit reaches that supported level, covers the exact allocated acquisition cost after
tax and strictly lowers the working ask. The supported integer price bypasses smoothing and
requires exact final reauthorization; a coarse tolerance cannot add another concession.

Ordinary large reductions retain their threshold. Unknown-cost offers, fresh stops and explicit
wind-down retain their independent rules. Buy maintenance and its anti-churn rules are unchanged.
After repricing below a stable last trade, this rule cannot repeatedly cancel the new listing.

## Durable completion and rollback

The existing prospective receipt follows the exact offer through late fills, cancellation,
collection, cadence changes and restart. Saved small reductions may complete at their protected
integer limit if optional history disappears; price tolerance cannot restore the former ask.
Quantity/cost and original target/stop context are rechecked and unchanged. Quiet prices cannot
cancel a working listing or grant new loss authority.

No database migration, source replay or receipt-format change is introduced. Receipt policy
family remains `companion-sale-10` for the compatible rollback reader. Shared maintenance is
version 9; advised-price version 4; chronological account replay is version 25.

## Market-informed loss decisions: owner direction and remaining work

The owner explicitly accepts justified losses to improve whole-account GP/hour. Break-even is
not an indefinite holding strategy. A timed loss alone is not the requested policy: compare
holding, a small supported adjustment and a bounded loss exit using recent/historical price
levels, two-sided volume, spread, volatility, trend/recovery evidence, remaining quantity,
acquisition age, actual progress, attendance and available replacement opportunities.

This small-adjustment increment does not activate that new loss policy. The current engine
permits automatic losses at a confirmed original stop (or separate explicit wind-down), so
long-held positions above that stop can still wait at break-even. The next increment must carry
an explicit market-exit reason and immutable evidence through final authority and the complete
cancel/collect/relist workflow; preserve original stops, risk windows and exact costs; enforce
the existing durable account loss budget; and prove compatible rollback before activation.
Missing/quiet evidence cannot become a confident forecast, and replacement profit is estimated,
not guaranteed compensation for a realized loss. Existing experimental chart projections and
unvalidated learning models cannot supply disposal permission.

Regression gates include falling/recovering/range-bound markets, sparse/contradictory history,
volume-limited quantities, tax plateaus, partial fills, repeated advice, full slots, account
isolation, outages, restart, preserved original risk and prospective whole-account comparisons.
Evaluate 100M/eight slots at 5m/30m/2h/8h separately; no profit-superiority claim follows from
source tests or a single observed trade. Background analysis cannot consume the guidance budget.
