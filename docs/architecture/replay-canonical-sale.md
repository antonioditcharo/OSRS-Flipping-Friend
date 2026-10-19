# Canonical sale and prospective action replay

19 October 2026. `account-replay-6` is an offline evaluator increment. Installed player behavior
remains the #108 pair described in HANDOFF; running this evaluator needs no client restart.

Two regression-first failures exposed the version-5 boundary: its first synthetic buy source had
no qualified original exit context, and it could place an instruction computed at the action time
without first displaying it. The standalone simulated holding supplied the remembered target.
That could preserve a target even when production canonical qualification would leave it unknown.

Version 6 displays an immutable instruction when an attended visit starts, then waits the configured
manual action seconds. At the action time it captures all eight sources again, recalculates the
instruction and runs current entry or final sale authority. Changed order, quantity, cost, source,
original context, cut status or expired original entry/plan withdraws the instruction. A replacement
display consumes another full manual delay, within the same visit's time budget. Restart withdraws
pending foreground advice and requires a new display/delay. Completed/cancelled offers still need
separate collection, and later placement consumes another action.

The first exact synthetic buy carries the actually earlier displayed buy probability/duration,
immutable forecast when available, and target/stop/sell duration. Existing `withBoundPrediction`
semantics bind that source; the companion independently qualifies the earliest retained source.
Later synthetic observations retain the original binding. Restart reloads it by its durable source
ID from this runner's newly created synthetic database. No historical financial source is reposted.
The simulation world independently conserves quantity/cost/cash; it supplies no exit targets to
policy. Initial exits and maintenance read canonical acquisition context and exact remaining costs
through the same transactional `saleProjections` and `companion-sale-3` policy used by the service.
Progress comes from canonical disposal evidence. Missing original context remains unknown.

Results identify both evaluator and sale policy versions, authorized sale actions and withdrawn
instructions. `completedSales`/`losingSales` now count fully completed sell orders; separate
`saleUpdates`/`losingSaleUpdates` count incremental disposal updates, including partial orders.
Profit, tax and terminal marks still reconcile independently. A partial cancellation cannot become
a completed sell order merely because one profitable disposal was observed. This is not a count
of round-trip flips.

Behavioral fixtures cover hand-calculated tax/cost, partial fills/sales, duplicate-source protection
through canonical accounting, unknown original context, cold restart, an eight-hour visit, expired
plan/candidate and changed displayed orders. Bundle verification still reproduces all twelve
100M/member/eight-slot cadence/share scenarios: 5m, 30m, 2h and 8h, each at 0.1%, 0.5% and 1% volume.
Old bundles require their retained evaluator. Schema smoke inputs and hand-authored fixtures are
structural checks; they cannot establish market returns or actual fills.

Remaining production-policy requirements are explicit: local outbid-buy replacement intent,
persisted sale-relist sequencing, the real client-thread acceptance/transport lifecycle and qualified
chronological frozen market captures. The abstract display/action stages are not a Swing/RuneLite
acceptance test. Do not pass a full production-replay or learning promotion gate with version 6.
Runtime remains PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION. No live database, source history,
unpriced stock reserve, risk anchor or installed pair is changed by this offline increment.
