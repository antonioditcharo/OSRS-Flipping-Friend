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

## PACKAGE 3.11 BUY-MAINTENANCE CONTEXT COMPOSITION

Status: NON-ACTIVATED FOUNDATION.

A pure fail-closed composer now maps an open canonical `OfferEvent`, matching current market input,
authoritative remaining buy limit, recommendation lineage, and decision time into the Package 3.10
`BuyMaintenancePolicyContext`. Missing offer identity, mismatched item or recommendation lineage,
non-open lifecycle state, malformed quantities, and future timestamps produce no context. No API route,
service call, plugin retrieval, presentation, parity, or live maintenance behavior changed.

## PACKAGE 3.12 BUY-MAINTENANCE ORCHESTRATION

Status: NON-ACTIVATED FOUNDATION.

A pure internal orchestrator now selects the lowest-slot canonical open buy, requires matching current
market input and an authoritative remaining buy-limit value, composes the Package 3.11 context, and
invokes the Package 3.10 policy. Missing or mismatched inputs return no decision. This establishes the
companion-domain orchestration seam without adding an API route, `CompanionService` call, plugin
retrieval, presentation, parity path, persistence, or live authority change.

## PACKAGE 3.13 BUY-MAINTENANCE SERVICE INTEGRATION

Status: NON-ROUTED, NON-ACTIVATED FOUNDATION.

`CompanionService` now has an internal read-only buy-maintenance decision seam. A service adapter
extracts complete current quotes from the actual companion market state, obtains authoritative
remaining limits from `BuyLimitLedger`, preserves canonical active-offer lineage, and invokes the
Package 3.12 orchestrator. Missing or malformed state fails closed. No HTTP route, client retrieval,
presentation, parity, persistence, plugin call, or production authority changed.

## PACKAGE 3.14 AUTHENTICATED BUY-MAINTENANCE ROUTE

Status: ROUTED, NON-CONSUMED, NON-ACTIVATED FOUNDATION.

The authenticated loopback API now exposes `GET /v1/policy/buy-maintenance`. The handler uses the
existing token guard, captures the decision time at the service boundary, delegates only to the
Package 3.13 internal service seam, and serializes the typed `PolicyDecision` or `null` when the
companion cannot establish authoritative inputs. `CompanionClient` does not retrieve the route, the
plugin does not consume it, and local `engine.refresh(false)` maintenance remains production-active.

## PACKAGE 3.15 BUY-MAINTENANCE CLIENT RETRIEVAL

Status: RETRIEVABLE, NON-CONSUMED, NON-ACTIVATED FOUNDATION.

`CompanionClient` now has package-private authenticated retrieval for the buy-maintenance route and
strictly validates schema, type, action, abstention shape, timestamps, offer identity, and recommendation
lineage. `HOLD`, `CANCEL_BUY`, and explicit `WAIT` are the only accepted actions. Null, malformed,
mismatched, or incompatible decisions fail closed. The plugin does not call this method and local
`engine.refresh(false)` maintenance remains production-active.

## PACKAGE 3.16 BUY-MAINTENANCE PRESENTATION

Status: PRESENTABLE, NON-CONSUMED, NON-ACTIVATED FOUNDATION.

A pure identity-safe presenter now maps validated buy-maintenance decisions and the matching canonical
open offer into existing manual `Suggestion` objects while retaining the typed decision. `CANCEL_BUY`
becomes a cancel instruction for the exact remaining quantity without inventing a replacement; `HOLD`
and explicit `WAIT` become visible wait cards. Missing or mismatched lineage, unsupported actions, and
non-open offers fail closed. The presenter is not called by `CompanionClient` or the plugin.

## PACKAGE 3.17 BUY-MAINTENANCE CLIENT PRESENTATION ORCHESTRATION

Status: PRESENTABLE, NON-CONSUMED, NON-ACTIVATED FOUNDATION.

`CompanionClient` now composes the Package 3.15 validated retrieval seam with the Package 3.16
identity-safe presenter behind a package-private method. The same canonical open `BUYING` offer supplies
the exact offer identity and recommendation lineage used for retrieval and is then passed unchanged to
presentation. Missing, terminal, malformed, unavailable, or mismatched inputs fail closed. The plugin does
not call this method, no parity path exists yet, and local `engine.refresh(false)` buy maintenance remains
production-active. No replacement behavior, persistence, shared-core contract, API, service, or Phase 2
guarantee changes.

## PACKAGE 3.18 BUY-MAINTENANCE PARITY EVIDENCE

Status: PARITY-ONLY, NON-CONSUMED, NON-ACTIVATED FOUNDATION.

A pure comparison contract now records whether an existing local buy-maintenance presentation and a
Package 3.17 companion presentation match in action, item identity, source slot, source price, exact
remaining quantity, and presentation text. Results retain companion decision, policy, offer, and
recommendation lineage and classify missing or invalid evidence explicitly. Neither `CompanionClient` nor
the plugin invokes parity comparison. No shadow execution, production cutover, replacement behavior,
persistence, shared-core contract, API, service, or Phase 2 guarantee changes. Local
`engine.refresh(false)` buy maintenance remains production-active.

## PACKAGE 3.19 BUY-MAINTENANCE PARITY ORCHESTRATION

Status: PARITY-ONLY, NON-CONSUMED, NON-ACTIVATED FOUNDATION.

`CompanionClient` now composes the Package 3.17 presentation seam with the Package 3.18 pure parity
comparator behind a package-private method. The caller supplies the existing local maintenance suggestion
and the canonical open buy; the canonical offer alone supplies companion retrieval identity and lineage.
Missing or invalid shadow evidence remains explicitly classified by the comparator. The plugin does not
call this method, no background shadow request, logging, persistence, winner selection, or production
cutover exists, and local `engine.refresh(false)` buy maintenance remains production-active. No
replacement behavior, shared-core contract, API, service, SQLite, or Phase 2 guarantee changes.

## PACKAGE 3.20 CANONICAL BUY-MAINTENANCE OFFER CACHE

Status: IDENTITY FOUNDATION, NON-CONSUMED, NON-ACTIVATED.

Research for production activation established that live `TrackedOffer` data does not carry canonical
outbox offer identity or recommendation lineage. `CompanionClient` now retains the latest durably
published canonical open-buy `OfferEvent` by slot after acknowledgement and exposes the deterministic
lowest-slot valid open buy to package-level orchestration. Terminal and cleared events remove cached
authority, and unattributed offers remain ineligible. The plugin does not consume this seam; no HTTP
request, winner selection, displayed suggestion, policy behavior, persistence schema, or Phase 2
guarantee changes. Local `engine.refresh(false)` maintenance remains production-active.

## PACKAGE 3.21 BUY-MAINTENANCE CANCELLATION SELECTOR

Status: SELECTION FOUNDATION, NON-CONSUMED, NON-ACTIVATED.

A pure package-private selector can now choose an exact companion-authored `CANCEL_BUY` presentation
only over a same-offer local `MODIFY_BUY` or `CANCEL` result. It requires supported schema and policy
metadata, no abstention, exact canonical offer and recommendation lineage, and matching item, name, slot,
price, and remaining quantity. Every other local responsibility, companion `HOLD`, explicit `WAIT`, and
all missing or mismatched inputs preserve the existing local result. The plugin does not consume the
selector, `engine.refresh(false)` remains production-active, and no replacement, HTTP, persistence,
shared-core, SQLite, or Phase 2 behavior changes.

## PACKAGE 3.22 BUY-MAINTENANCE CANCELLATION COMPOSITION

Status: COMPOSITION FOUNDATION, NON-CONSUMED, NON-ACTIVATED.

`CompanionClient` now composes the Package 3.20 canonical open-buy lookup, the Package 3.17
validated retrieval and presentation seam, and the Package 3.21 pure cancellation selector behind a
package-private method. Missing canonical authority, unavailable or invalid companion evidence,
`HOLD`, explicit `WAIT`, ineligible local responsibilities, and all identity, lineage, or economics
mismatches preserve the exact existing local result. An exact companion-authored cancellation is
returned only when the Package 3.21 selector accepts it. The plugin does not call this method,
`engine.refresh(false)` remains production-active, and no priority, replacement, background request,
logging, persistence, API, service, SQLite, shared-core, or Phase 2 behavior changes.

## PACKAGE 3.23 BUY-MAINTENANCE CANCELLATION ACTIVATION
Status: NARROW PRODUCTION ACTIVATION.
The live plugin now invokes `CompanionClient.selectBuyMaintenanceCancellation(...)` only after
companion collection has returned no action and local `engine.refresh(false)` has produced an existing
`MODIFY_BUY` or `CANCEL` result. The Package 3.21 selector therefore remains the final authority gate:
only an exact companion-authored `CANCEL_BUY` for the same canonical offer and recommendation lineage
can replace that local result. Collection, recovery, `SELL`, `MODIFY_SELL`, `WAIT`, partial-position
protection, unrelated maintenance, and entry remain undisplaced. `HOLD`, explicit companion `WAIT`,
missing authority, transport failure, malformed evidence, and every identity, lineage, or economics
mismatch preserve the exact existing local object. The companion cancellation does not authorize a
replacement. No background request, scheduler, persistence, logging, metrics, API, service, SQLite,
shared-core, or automatic Grand Exchange interaction is introduced.
