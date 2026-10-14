# Durable planning exposure

## Implemented scope

CompanionService reads durable position and offer projections together through
SqliteStore.planningProjections(), a synchronized database read transaction.
The returned collections are immutable copies.

DurablePlanningExposure derives per-item committed capital from known-cost,
READY positions plus price times unfilled quantity for BUY_OPEN and BUY_PARTIAL
offers. Terminal buys and sells add no outstanding buy reservation.
Unclassified unresolved positions, unsupported offer states, malformed inputs, and checked
arithmetic overflow refuse exposure calculation. Aggregate exposure across
different items is checked as well as per-item sums.

AccountSnapshot.withPlanningExposure() creates a planning-only copy with a
defensively copied exposure map. All other fields remain unchanged.
CompanionService passes that copy to PortfolioPlanner. A durable-read or
calculation failure replaces the cached plan with COMPANION_STATE_UNAVAILABLE.

## Explicit review of unpriced old stock

The player's account-scoped stock declarations are separate trading preferences. A declaration
matches the exact unpriced position quantity, cost flag/value, opening evidence and event/offer
identities. Personal declarations require source-free stock. No-longer-held declarations are the
player's statement, not an inferred disposal. Matching declarations can exclude those entries from
planning, while the original ledger rows, unresolved sales, costs and profit coverage remain intact.
Known-cost holdings are never excluded through this path. New evidence invalidates a stale declaration.

Declarations cannot conceal open buys, uncollected purchases, remaining sell stock or unknown offers.
The client and companion withdraw cached plans on review changes. Health keeps accounting warnings
visible and separately reports safe planning availability after fresh-market and durable-read checks.
Malformed/stale/foreign evidence fails closed. No database schema or correction endpoint is added.

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
plan that has no allocations. DurableExposureAllocationTest builds limits
with the planner's own constraint helpers and runs the real PortfolioOptimizer
on one candidate: client-snapshot exposure permits the buy, while durable
exposure at the item cap refuses it with PORTFOLIO_CONSTRAINT. A full
CompanionService test with live market data and candidate generation has not
been added. Pair installation, API compatibility and scoped read-only runtime checks have been
observed; controlled live trading acceptance remains incomplete. See HANDOFF.md for dated evidence.

## Prerequisite accounting repair

Separate commits prevent older snapshots from restoring disposed positions.
Equal-timestamp disposal snapshots are refused while same-timestamp initial
buy recovery remains supported. Regression tests cover full disposal across
restart and partial disposal at older and equal snapshot timestamps.
