# Accepted cancel/collect continuations and bounded replacement entries

Current changes: [durable plans and timely maintenance](trade-plan-maintenance.md) supersede the
same-second exclusion, mixed-plan erasure and full-horizon routine target veto described below.
The retained original contract explains earlier behavior; current delivery comes from HANDOFF.

19 October 2026. HANDOFF records actual source, installation and player acceptance separately.

The batch fixes related breaks in the manual sequence: buy reprice intent was session-only and
remembered the pre-cancellation quantity; subsequent fills could leave an excessive replacement
request. A normal fresh portfolio allocation could also be larger than the cancelled remainder,
causing repeated WAIT instead of an eligible smaller order. A background maintenance proposal was
enough to create a promise even when the foreground never accepted that instruction.

`OfferContinuation` now shares exact-episode state between live buy replacement, sell relisting
and replay. The account file retains the original offer key, initial cap, highest observed counter,
existing same-item listings and a bounded cancelled/collected receipt. Collection must be observed
for that episode; later fills reduce the cap, counter regression withdraws it, and full completion
or a new replacement ends it. Callback checkpoints precede temporary slot-record pruning. A buy
promise retains its workflow evidence for fresh revalidation; a receipt is never a price or funding guarantee.
Original buy waiting budgets and cancel-and-reassess restrictions now follow the
[no-trade-left-behind contract](no-trade-left-behind.md). Evidence retention does not extend a buy budget.
The sell file preserves its shipped field names and legacy default side without financial replay
or invented collection. Managed cost travels immutably with the returned quantity.

The client-thread guide acceptance boundary creates promises only for the exact accepted MODIFY
instruction, matching current account, slot, item, side, episode and remaining quantity. Unshown,
paused, foreign or superseded proposals cannot create them. Maintenance source identities also
participate in walkthrough context comparison. Explicit give-up, spent limits, skips/blocks and
sell-only withdraw the buy promise. Quiet, future or overflowing quotes cannot initiate an outbid
reprice; the shared `BuyRepricePricing` retains patience, deadband and profitability policy.

`GET policy/replacement?item=…&quantityCap=…` is authenticated, account scoped and read-only.
The cap is a preference, never old-reservation authority. The companion independently requires
current acknowledged cash/eight-slot proof, durable exposure and risk, real buy limits, member/
pause/block/skip/sell-only rules, quantity economics and portfolio resource/group constraints.
`ReplacementEntryPlanner` runs the production planner for that item with the bounded quantity
limit and rebuilds prices, tax/profit and leg forecasts. It does not trim or scale a larger plan.
Current evidence/revision is checked again before publication. The versioned response binds the
request, account, fresh plan and matching final entry decision; the client validates clocks, exact
candidate identity and cap before foreground acceptance. Refusals remain WAIT. Presented BUY
forecasts/context still commit only through the existing original-source/outbox qualification.

Replay v7 persists the same continuation state beside each new isolated synthetic ledger and
reloads it on restart. Logical display acceptance creates its promises; actual actions still need
a fresh comparison and a complete action delay. Shared bounded replacement planning precedes
selling a partially acquired holding, and relisting rechecks canonical stock, original context,
exact taxed costs and final sale authority. Results disclose replacement/relist action counts.
Distinct but overlapping public-volume windows are rejected so one volume interval cannot fund
multiple simulated fills. Old bundles require their retained evaluator.

Verification covers actual callback/engine/foreground queues, late fills, cancellation/collection,
slot reuse, restart, legacy sell files, version/account/request/clock/quantity/forecast guards,
real candidate generation and GET-only transport. Hand fixtures and the twelve 100M cadence/share
schema cases are not return evidence. Complete real editor/client/transport/manual-visit policy
coverage, fresh player trading acceptance, sustained latency and qualified chronological benchmarks
remain open. PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION, source/financial IDs, unknown money,
historical gaps, reserves, original risk anchors and manual game actions remain unchanged.
