# Recommendation Authority

## Status

This document defines the Phase 3 migration boundary. The policy contracts are additive in Package 3.1 and are not yet active in production.

## Final ownership

- The RuneLite plugin owns raw Grand Exchange and inventory observations and renders manual instructions.
- The companion owns durable offer, position, cost-basis, and buy-limit projections.
- The companion generates candidates and invokes shared-core portfolio optimization.
- Companion policies own entry, buy-maintenance, sell-maintenance, and exit decisions.
- No component performs automatic Grand Exchange interaction.

## Current split

The companion currently generates entry candidates with `CandidateFactory`, builds plans with `PortfolioPlanner`, persists plans through `CompanionService`, and exposes them through `CompanionClient`.

The plugin still uses `SuggestionEngine` for collect fallback, buy and sell maintenance, replacement intent, exit decisions, and an independent buy fallback when the companion has no fresh plan. Package 3.1 documents this split without changing it.

## Shared policy contract

All policy families return an immutable, versioned `PolicyDecision`. Manual actions reuse `OfferLifecycleAction`; no second action enum exists. Deliberate non-action is explicit: `WAIT` plus a non-`NONE` `PolicyAbstentionReason`.

The four policy responsibilities are:

- `EntryPolicy`
- `BuyMaintenancePolicy`
- `SellMaintenancePolicy`
- `ExitPolicy`

## Migration rules

- The companion must become the only opportunity-ranking authority.
- Companion degradation must produce explicit abstention, not an independent plugin ranking.
- Accounting-safe collection and reconciliation guidance may remain available during migration.
- Runtime migration occurs in later packages, one policy responsibility at a time.
- Phase 2 event identity, delivery, lifecycle, accounting, reconciliation, and crash-recovery guarantees must remain unchanged.

## Package 3.1 non-activation boundary

Package 3.1 does not change `CandidateFactory`, `PortfolioPlanner`, `CompanionService`, `CompanionClient`, `SuggestionEngine`, `FlippingFriendPlugin`, HTTP contracts, persistence, visible recommendations, or schedulers.

## Structured companion planning

Package 3.3 assigns every newly produced companion plan a machine-readable `PortfolioPlanOutcome`. Entry policy maps that outcome rather than display prose. Legacy plans without the field remain readable and map conservatively. The companion can evaluate the policy internally, but no persistence, plugin cutover, or visible recommendation change is introduced. Package 3.4 adds an authenticated read-only entry-decision endpoint that accepts only plan identity and allocation rank; the companion resolves candidate economics from its own current plan. Package 3.5 adds strict client retrieval and validation for entry decisions. Package 3.6 adds a pure, lineage-preserving presentation adapter for validated entry decisions. Neither capability is called by the plugin recommendation path, so visible recommendations and fallback behavior remain unchanged.
