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

Package 3.3 assigns every newly produced companion plan a machine-readable `PortfolioPlanOutcome`. Entry policy maps that outcome rather than display prose. Legacy plans without the field remain readable and map conservatively. The companion can evaluate the policy internally, but no persistence, plugin cutover, or visible recommendation change is introduced. Package 3.4 adds an authenticated read-only entry-decision endpoint that accepts only plan identity and allocation rank; the companion resolves candidate economics from its own current plan. Package 3.5 adds strict client retrieval and validation for entry decisions. Package 3.6 adds a pure, lineage-preserving presentation adapter for validated entry decisions. Package 3.7 composes plan selection, decision retrieval, and presentation behind a non-activated client method. Package 3.8 adds a pure parity-evidence contract that compares an existing presentation with the shadow entry-decision presentation without executing either path. None of these capabilities is called by the plugin recommendation path, so visible recommendations and fallback behavior remain unchanged.

## PACKAGE 3.9 ENTRY AUTHORITY

Status: PRODUCTION ACTIVE.

The live plugin keeps higher-priority companion collection and local maintenance guidance ahead of
entry by continuing to call `engine.refresh(false)`. When a new entry is needed it now renders only
the authenticated, validated, lineage-safe companion entry decision through
`CompanionClient.nextEntrySuggestion(...)`. A missing, stale, malformed, unavailable, or mismatched
decision freezes new buying. The live plugin no longer calls `CompanionClient.nextBuySuggestion(...)`
or `SuggestionEngine.buyFallback()`.

This package does not complete Phase 3. Local collect, maintenance, reprice, sell, exit, and
partial-position protection responsibilities remain to be migrated. `SuggestionEngine` source is
retained for those responsibilities, while its independent buy ranker is production-unreachable.
Package 3.8 parity evidence remains pure and retained.

## PACKAGE 3.10 BUY-MAINTENANCE FOUNDATION

Status: NON-ACTIVATED FOUNDATION.

The companion now has an immutable identity-carrying buy-maintenance context and a pure versioned
`CompanionBuyMaintenancePolicy`. It deterministically HOLDs a current open buy, CANCEL_BUYs when
the authoritative remaining buy limit is exhausted, and explicitly abstains on stale or inconsistent
inputs. No API route, client retrieval, presentation adapter, parity path, or live orchestration calls
this policy yet. `engine.refresh(false)` remains the production owner of buy maintenance, repricing,
and replacement intent. Package 3.9 entry authority remains production-active.
