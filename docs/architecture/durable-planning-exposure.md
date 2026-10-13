# Durable planning exposure

## Implemented scope

CompanionService reads durable position and offer projections together through
SqliteStore.planningProjections(), a synchronized database read transaction.
The returned collections are immutable copies.

DurablePlanningExposure derives per-item committed capital from known-cost,
READY positions plus price times unfilled quantity for BUY_OPEN and BUY_PARTIAL
offers. Terminal buys and sells add no outstanding buy reservation.
Unresolved positions, unsupported offer states, malformed inputs, and checked
arithmetic overflow refuse exposure calculation. Aggregate exposure across
different items is checked as well as per-item sums.

AccountSnapshot.withPlanningExposure() creates a planning-only copy with a
defensively copied exposure map. All other fields remain unchanged.
CompanionService passes that copy to PortfolioPlanner. A durable-read or
calculation failure replaces the cached plan with COMPANION_STATE_UNAVAILABLE.

## Deliberately unchanged

Spendable cash, committedCoins, free slots, itemsOnOffer, preferences, account
identity, observation time, and marked session drawdown retain their existing
client-snapshot inputs. The calculated reservedBuyCoins is not substituted for
committedCoins. Equity and session-risk arithmetic are not migrated here.
The planner's separate active-offer input remains the in-memory tracker.

This is not complete durable ownership of planning, exits, inventory UI,
calibration, or session risk. No game-input automation is introduced.

## Verification and limitations

Tests cover partial fills without double counting, cancellation, partial and
full sales, restart, immutable reads, exposure-copy field preservation,
invalid exposure, and per-item and aggregate overflow refusal.

The storage read is transactionally grouped, but deterministic concurrent
interleaving has not been tested. Client cash and preferences are not captured
atomically with the durable projections.

The service failure test seeds a cached READY plan containing a buy
allocation and confirms a durable-read failure replaces it with an unavailable
plan that has no allocations. A successful service-level test
proving that durable exposure changes an optimizer allocation remains
outstanding. Live RuneLite installation validation has not been performed.

## Prerequisite accounting repair

Separate commits prevent older snapshots from restoring disposed positions.
Equal-timestamp disposal snapshots are refused while same-timestamp initial
buy recovery remains supported. Regression tests cover full disposal across
restart and partial disposal at older and equal snapshot timestamps.
