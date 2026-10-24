# Confirmed automatic loss exits

24 October 2026. Read `local-data/dragon-dart-loss-20261023/delivery-state.json` for actual source,
tests, installation and acceptance. These are separate states.

Canonical evidence records 11,000 Dragon darts acquired at 957 GP, a 1,000 GP listing, cancellation
216 seconds after placement, and an accepted STOP receipt proposing 806 GP. The original prospective
stop was 909 GP. Gross proceeds were 9,669,000 GP against exact cost 10,527,000 GP; the durable tax
range gives a realized loss of 1,040,381–1,051,380 GP. Original source, acceptance, financial rows
and the loss anchor remain intact. Historical receipts do not retain the full market read; a later
snapshot cannot establish the exact historical quote/buckets.

The previous shared policy could authorize loss from the latest high-side trade alone, including
without history, and let an unvalidated fill model search discounted exit limits. Four behavioral
regressions fail against that policy. A public last trade is not a current executable order book.

Automatic CUT now requires the original proven common stop, coherent positive two-sided prices
with each trade at most 120 seconds old and both below the stop, and the newest completed five-minute
bucket starting 300–900 seconds ago. The bucket must have positive volume on both sides and coherent
positive averages at/below the stop. Conflicting duplicate buckets refuse authority; incomplete and
future buckets cannot supply it. This is a conservative evidence contract, not a calibrated stop
model or a guarantee against genuine market losses.

Without confirmation, initial exits preserve the exact taxed non-loss floor and applicable original
target. Recent working sales stay listed. Confirmed stops still bypass routine placement/progress
patience. Explicit player wind-down retains its separate rules. Missing original plans cannot create
a stop. Client, final canonical authority and chronological replay share the check.

Automatic loss limits cannot go below the fresh reported instant-sell trade through a model discount,
price stabilization or final-authority tolerance. That bound is reapplied to collected relists too.
It does not claim a historical price is executable now. Current evidence must requalify after
cancellation, collection and restart; an accepted old STOP receipt is not permanent loss permission.

Canonical trading positions and active offers now receive background five-minute history priority
independently of the entry shortlist, refreshing after two minutes instead of the entry cache's
20-minute lifetime. The client also requests this bounded maintenance refresh. Reads remain
cache-only; existing per-cycle request budgets and failure backoff remain. Public history never
enrolls personal inventory, proves executions or changes accounting/risk.

New decisions/receipts use `companion-sale-10`, maintenance `supported-sale-maintenance-6`, replay
`account-replay-19`. Version-nine receipts remain readable and immutable; fresh client authority
requires a current-version decision. Financial keys and original acceptance identities are unchanged.
Controlled full-loop/restart acceptance, sustained latency, qualified benchmarks and learning remain open.
