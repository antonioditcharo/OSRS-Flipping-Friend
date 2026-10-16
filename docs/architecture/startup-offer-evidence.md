# Preserve offer evidence through login and periodic captures

16 October 2026. Prevention fix; existing historical gaps are not resolved by this change.

## Failure and verified source contract

The live account had no eligible trading positions although buy executions were present. Recorded
sources include eight authenticated EMPTY observations followed immediately by the old nonempty
offer identities. This produces missing-terminal gaps and recovery baselines, withholding managed
stock. A cancelled offer also appeared active again in a later observation. The old payloads do
not distinguish a callback from a periodic capture, so their individual producer cannot be proven.

RuneLite explicitly documents initial EMPTY callbacks for every slot regardless of real offers,
followed by server updates:
[GrandExchangeOfferChanged](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/events/GrandExchangeOfferChanged.html).
[GameTick](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/events/GameTick.html)
runs after packets have processed. LOGGED_IN alone is not enough to authenticate an empty snapshot.

## Producer boundary

On plugin startup and each login, logout, hop or connection loss, array restoration and account
snapshot publication wait for the first GameTick observed while LOGGED_IN. Restoration requested
before that boundary retries on the tick, with the existing generation/profile/account-load checks.
This is an event boundary, not an arbitrary sleep or a claim that missing server packets arrived.

Initial EMPTY callbacks are ignored until that boundary unless a nonempty callback has already
been captured for the same slot in this login. Thus a real BOUGHT/CANCELLED/SOLD observation and
its subsequent clear remain ordered even if both occur before the first tick or before the
profile has loaded. The per-slot evidence resets with the login lifecycle. After the boundary,
actual empty observations retain the normal missing-terminal guard; offline absence never proves
cost, disposal or profit. Copied callback delivery remains independent of advice invalidation.

Periodic captures now confirm the processed offer book. All eight slots, identity, side, price,
size, raw phase, cumulative fills and cumulative spend must agree. EMPTY requires that the
processed slot is empty too. A stale or ahead-of-callback array cannot invent a clear, regress a
terminal phase or introduce an execution. It withholds account publication/advice until observations
agree. The normal copied callbacks remain the source of transitions; no terminal is synthesized.

## Verification and limits

Regression commits eedb4c2 and 7cced9d precede the corresponding fixes. Synthetic client/worker
tests reproduce premature array restoration, premature publication, default EMPTY callbacks,
relogin, stale cancellation phases, ahead-of-callback fills and mismatched spend. They retain
real terminal-then-clear delivery, offline completion restoration, copied observations and the
eight-event durable acknowledgement gate. All tests use mocks or disposable storage, never game
input or synthetic live API writes. Full verification receipts are in HANDOFF.md.

This does not remove recorded RECONCILE events, recalculate old acquisitions, supply old sale
costs or reset risk. Read-only diagnosis found duplicated-looking acquisition totals as well as
missing deltas in affected episodes; directly trusting aggregate positions is unsafe. A future
resolution must prove original source ordering, exact acquisition deltas, remaining quantity and
cost, and all possible intervening disposals. Preserve ambiguous EMPTY/session/identity evidence,
unknown profit and existing guards. No live database repair or fallback to personal inventory is
authorized. Current sell recovery and controlled live acceptance remain open.
