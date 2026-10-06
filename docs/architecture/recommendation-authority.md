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

## PACKAGE 3.24 BUY-REPLACEMENT INTENT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A versioned immutable shared-core contract now separates cancellation authorization from later replacement authorization. Pure fail-closed transitions require matching original offer identity and recommendation lineage, terminal cancellation observation, collection observation, and a fresh eligibility decision before replacement can become authorized. Partial-fill remainder is preserved exactly. Expiry, invalid ordering, and mismatched identity or lineage abandon or reconcile rather than authorize replacement. The plugin and CompanionClient do not consume this contract; transient SuggestionEngine replacement behavior remains production-active and unchanged. No persistence, route, service orchestration, client retrieval, presentation, selection, scheduler, automatic interaction, or Phase 2 behavior changes.

## PACKAGE 3.25 BUY-REPLACEMENT INTENT PERSISTENCE
Status: NON-ROUTED, NON-ACTIVATED DURABLE PROJECTION FOUNDATION.
SQLite now stores the Package 3.24 immutable replacement-intent state in an additive current-state projection. Strict restoration validates schema and remainder arithmetic; writes preserve immutable offer identity, recommendation lineage, and economics, are idempotent for exact replay, and roll back atomically at the injected checkpoint. The persistence seam is package-private and is not consumed by CompanionService, CompanionMain rehydration, any API route, CompanionClient, the plugin, or SuggestionEngine. No runtime replacement intent is created or advanced, and production behavior remains unchanged.


## PACKAGE 3.26 BUY-REPLACEMENT WORKFLOW FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private companion workflow now binds an exact companion-authored `CANCEL_BUY` decision to the matching canonical open-buy identity and recommendation lineage, creates a deterministic short-lived Package 3.24 replacement intent, and stores it through the Package 3.25 durable projection. Exact replay is idempotent. HOLD, malformed offers, and identity or lineage mismatches fail closed without writing. CompanionService, CompanionMain rehydration, API routes, CompanionClient, the plugin, and SuggestionEngine do not consume the workflow, so runtime replacement behavior and production authority remain unchanged.

## PACKAGE 3.27 BUY-REPLACEMENT LIFECYCLE ORCHESTRATION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure companion orchestrator now advances the Package 3.24 replacement-intent state only from accepted canonical lifecycle evidence. A matching open-buy to `BUY_CANCELLED_UNCOLLECTED` transition advances cancellation observation to `COLLECTION_REQUIRED`; a later accepted transition from that exact terminal projection to `EMPTY` advances through `ASSETS_RETURNED` and stops at `REPLACEMENT_ELIGIBILITY_PENDING`. The pre-clear terminal projection supplies the original offer identity, recommendation lineage, slot, item, price, quantity, and fill evidence that the cleared `EMPTY` projection intentionally no longer retains. Rejected, premature, idempotent, sell-side, expired, malformed, or mismatched evidence fails closed. The orchestrator never establishes fresh eligibility or reaches `REPLACEMENT_AUTHORIZED`. CompanionService, CompanionMain rehydration, API routes, CompanionClient, the plugin, SuggestionEngine, and SQLite transaction flow do not consume the orchestrator, so runtime replacement behavior and production authority remain unchanged.

## PACKAGE 3.28 BUY-REPLACEMENT ELIGIBILITY CONTEXT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private immutable companion context and pure fail-closed composer now retain authoritative inputs for a later post-collection replacement-eligibility decision. Composition requires an unexpired intent in `REPLACEMENT_ELIGIBILITY_PENDING`, a matching positive quote, a current bank-seen account snapshot with spendable coins and a free slot, and positive durable remaining buy-limit authority. Market and account observations use the existing inclusive 120-second freshness boundary. Exact identity, lineage, remainder, original economics, current prices, capacity, and timestamps are preserved. No quantity resizing, policy decision, intent transition, persistence, route, client, plugin, or production activation is introduced.

## PACKAGE 3.29 BUY-REPLACEMENT READINESS POLICY FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure readiness policy now evaluates Package 3.28 contexts into deterministic non-authorizing assessments. Exact identity, lineage, remainder, freshness, account capacity, and durable buy-limit coverage are revalidated. A fully covered exact remainder becomes `READY_FOR_PRICING`; insufficient buy-limit coverage is `INELIGIBLE`; missing, stale, expired, or inconsistent authority abstains. The assessment carries no replacement price or lifecycle action, does not implement `TradePolicy`, does not transition the intent, and cannot reach `REPLACEMENT_AUTHORIZED`. No service, persistence, API, client, plugin, SuggestionEngine, or production behavior is changed.

## PACKAGE 3.30 BUY-REPLACEMENT PRICING CONTEXT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private immutable pricing context and pure fail-closed composer now bind an exact Package 3.29 `READY_FOR_PRICING` assessment to its matching Package 3.28 authoritative input context. Schema, policy version, assessment identity, intent, original offer, recommendation lineage, slot, item, exact remainder, timestamps, freshness, account capacity, and durable buy-limit coverage must match. The result preserves the historical cancelled-offer price only as lineage plus current low/high prices for a later pricing policy. It contains no proposed replacement price, resized quantity, lifecycle action, `PolicyDecision`, intent transition, or `REPLACEMENT_AUTHORIZED` state. No service, persistence, API, client, plugin, SuggestionEngine, or production behavior is changed.

## PACKAGE 3.31 SHARED PRICE-OFFSET PRIMITIVE FOUNDATION
Status: ARITHMETIC FOUNDATION, REPLACEMENT-NON-ACTIVATING.
A pure shared-core `PriceOffset` primitive now owns the established fractional price-shift arithmetic: percentage movement uses the existing rounding rule, any nonzero offset that rounds to zero still moves one coin, and the result never falls below one. Companion `CandidateFactory` uses that exact primitive for its existing appetite-defined buy and sell offset loops without changing offset order, screening, quantity sizing, fill evaluation, tactic construction, ranking, or per-item trimming. The primitive does not select a replacement price, consume `BuyReplacementPricingContext`, resize the exact remainder, produce a `PolicyDecision` or lifecycle action, transition an intent, persist state, add a route, or activate replacement behavior.

## PACKAGE 3.32 BUY-REPLACEMENT PRICING-STRATEGY CONTEXT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private immutable strategy context and pure fail-closed composer now bind Package 3.30 pricing lineage to the matching authoritative `AccountSnapshot`. Account observation time, spendable coins, membership, bank visibility, slot capacity, lifetime, freshness, prices, exact remainder, and durable buy-limit coverage are revalidated. Risk is resolved through the same `RiskAppetite.forName(...)` mapping as `PortfolioPlanner`, the effective leg horizon reuses `PortfolioPlanner.horizonFor(...)`, and ordered buy offsets are defensively copied. No candidate or proposed price is generated, `PriceOffset` is not called, the exact remainder is not resized, and no action, `PolicyDecision`, transition, persistence, route, client, presentation, or runtime activation is introduced.

## PACKAGE 3.33 BUY-REPLACEMENT PRICE-CANDIDATE FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure composer now converts a valid Package 3.32 strategy context into an immutable ordered candidate set by applying the shared Package 3.31 `PriceOffset` primitive to the current low-price anchor. Every appetite offset produces one candidate retaining its original index and exact offset; order and duplicate resulting prices are preserved. Identity, lineage, timestamps, freshness, exact remainder, durable buy-limit coverage, appetite, and effective horizon are revalidated and retained. The set does not calculate affordability, resize quantity, evaluate fills, generate sell prices, calculate profit, rank or select a candidate, produce an action or `PolicyDecision`, transition the intent, persist, route, retrieve, present, or activate replacement behavior.

## PACKAGE 3.34 BUY-REPLACEMENT CANDIDATE-AFFORDABILITY FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure composer now evaluates every valid Package 3.33 candidate against authoritative spendable coins for the unchanged exact remainder. It revalidates identity, lineage, timestamps, freshness, candidate order, contiguous offset indexes, finite offsets, and shared `PriceOffset` arithmetic, then calculates exact `long` cost by widening the positive integer buy price before multiplication. Exact-balance cost is affordable; unaffordable candidates, duplicate prices, duplicate costs, order, and provenance remain present in the immutable result. The assessment does not filter, resize, rank, prefer, or select candidates, evaluate fills, calculate profit, produce an action or `PolicyDecision`, transition the intent, persist, route, retrieve, present, or activate replacement behavior.

## PACKAGE 3.35 BUY-REPLACEMENT AFFORDABLE-CANDIDATE ELIGIBILITY FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure evaluator now revalidates complete Package 3.34 affordability evidence and derives a separate immutable ordered affordable-candidate view without replacing or discarding the complete evidence. Price, exact remainder, exact `long` cost, recorded affordability outcome, identity, lineage, timestamps, freshness, contiguous original indexes, finite offsets, and shared `PriceOffset` arithmetic must agree. One or more affordable candidates yields `ELIGIBLE`; valid all-unaffordable evidence yields `INELIGIBLE`; malformed or unavailable evidence fails closed. Original indexes, order, duplicate prices, duplicate costs, and offset provenance are preserved. The evaluator does not renumber, rank, score, prefer, or select candidates, resize quantity, evaluate fills, calculate profit, produce an action or `PolicyDecision`, transition or authorize the intent, persist, route, retrieve, present, or activate replacement behavior.

## PACKAGE 3.36 BUY-REPLACEMENT AFFORDABLE-CANDIDATE FILL-INPUT CONTEXT FOUNDATION

A package-private immutable context and pure fail-closed composer now prepare the shared history-backed inputs required for a later fill evaluation of every Package 3.35 affordable candidate. Valid `ELIGIBLE` evidence, complete affordability records, original candidate indexes and offsets, duplicate prices and costs, exact remainder, identity, lineage, appetite, horizon, and timestamps are revalidated and retained. Supplied oldest-first item history is defensively copied, must not contain future or regressing timestamps, and produces one non-empty `FillCurve.overRecentHistory` shared by the preserved affordable candidates. The composer does not invoke `FillModel`, create a `FillEstimate`, query candidate-specific curve volume, derive seasonality, resize quantity, calculate probability, duration, throughput, profit, tax, margin, score, rank, preference, or selection, construct a `PortfolioCandidate`, action, or `PolicyDecision`, transition or authorize the intent, persist, route, retrieve, present, or activate replacement behavior.

## PACKAGE 3.37 BUY-REPLACEMENT AFFORDABLE-CANDIDATE FILL EVALUATION FOUNDATION

A package-private pure evaluator now applies the shared measured `FillModel` to every affordable candidate in a valid Package 3.36 fill-input context. It uses the single prepared `FillCurve`, each candidate's unchanged buy price and exact remainder, the unchanged effective horizon, and the model's established neutral-seasonality overload. Complete affordability evidence, affordable-candidate order, original indexes, offsets, duplicate prices and costs, identity, lineage, appetite, history timing, and context timing are revalidated and retained. Immutable replacement-specific evidence copies probability, expected hours, units per hour, and counterparty wait from each `FillEstimate`; weak or never-fill results remain evidence and do not remove candidates. The evaluator does not rebuild curves, retrieve history, derive seasonality, resize quantity, evaluate a sell leg, calculate tax, margin, profit, expected value, unwind cost, rank, score, prefer, trim, or choose candidates, construct a `PortfolioCandidate`, action, or `PolicyDecision`, transition or authorize intent, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.38 BUY-REPLACEMENT FILL VIABILITY FOUNDATION

A package-private pure evaluator now interprets complete Package 3.37 measured buy-fill evidence using the shared `FillEstimate.isPlausible` semantics: positive completion probability and finite expected duration. It preserves every fill evaluation while deriving a separate immutable ordered viable-only view with original indexes, offsets, prices, costs, exact remainder, probability, expected duration, throughput, and counterparty wait. Valid all-never-fill evidence yields `NOT_VIABLE`; malformed, mismatched, stale, or non-finite evidence fails closed. The evaluator does not invoke `FillModel`, rebuild or query curves, retrieve history, derive seasonality, resize quantity, evaluate a sell leg, calculate tax, margin, profit, expected value, or unwind cost, rank, score, prefer, trim, or choose candidates, create an action or `PolicyDecision`, transition or authorize intent, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.39 BUY-REPLACEMENT SELL-PRICING STRATEGY CONTEXT FOUNDATION

A package-private immutable context and pure fail-closed composer now bind a valid Package 3.38 `VIABLE` assessment to the authoritative `RiskAppetite` sell-offset ordering and an explicit current-high anchor. The appetite name must round-trip through the existing production mapping; ordered sell offsets are defensively copied and finite. Complete fill evidence, viable-only evidence, exact remainder, identity, lineage, horizon, fill-evaluation time, viability time, and composition time are retained. The package does not apply `PriceOffset`, generate sell prices, pair candidates, invoke `FillModel`, resize quantity, calculate economics, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.40 BUY-REPLACEMENT SELL-PRICE CANDIDATE FOUNDATION

A package-private pure composer now applies shared `PriceOffset` arithmetic to Package 3.39's current-high anchor for every authoritative sell offset and every viable buy candidate. It emits a deterministic buy-major, sell-offset-minor immutable raw pairing grid while preserving original buy indexes, sell-offset indexes, duplicate prices, exact remainder, exact buy cost, buy-fill evidence, identity, lineage, appetite, horizon, and strategy timing. Raw pairings are not filtered for spread or economics. The package does not invoke `FillModel`, resize quantity, calculate tax, margin, profit, expected value, or unwind cost, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.41 BUY-REPLACEMENT SPREAD ELIGIBILITY FOUNDATION

A package-private pure fail-closed evaluator now preserves the complete Package 3.40 raw pairing grid while deriving a separate immutable ordered positive-raw-spread view. It revalidates buy-major and sell-offset-minor ordering, canonical sell offsets, shared `PriceOffset` arithmetic, exact remainder, exact buy cost, and preserved buy-fill evidence. Valid all-nonpositive-spread evidence yields `INELIGIBLE`. The package does not invoke `TaxCalculator` or `FillModel`, calculate net margin, profit, expected value, or unwind cost, resize quantity, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.42 BUY-REPLACEMENT NET-MARGIN EVALUATION FOUNDATION

A package-private pure evaluator now applies the shared production `TaxCalculator` contract to every Package 3.41 positive-spread candidate, preserving ordered tax-per-item, net-margin-per-item, total-tax, and total-net-margin evidence for the unchanged exact remainder. Complete raw pairings and the positive-spread view remain available and immutable; zero or negative net margins are retained rather than filtered. The package does not evaluate sell fills, resize quantity, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.43 BUY-REPLACEMENT NET-MARGIN ELIGIBILITY FOUNDATION

A package-private pure fail-closed evaluator now preserves the complete Package 3.42 tax-aware evaluation list while deriving a separate immutable ordered positive-net-margin view. It revalidates every evaluation against its Package 3.41 positive-spread source and the shared production `TaxCalculator`, including per-item tax, per-item net margin, total tax, and total net margin for the unchanged exact remainder. Valid all-zero-or-negative evidence yields `INELIGIBLE`. The package does not evaluate sell fills, resize quantity, calculate expected value or unwind cost, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.44 BUY-REPLACEMENT SELL-FILL INPUT CONTEXT FOUNDATION

A package-private pure fail-closed composer now binds Package 3.43 eligible positive-net-margin evidence to defensively copied oldest-first market history and one shared nonempty `FillCurve`. It preserves exact remainder, original pricing indexes, tax and net-margin evidence, buy-fill measurements, identity, lineage, appetite, horizon, and timestamps. It does not invoke `FillModel`, create `FillEstimate`, resize quantity, calculate combined probability, expected value or unwind cost, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.45 BUY-REPLACEMENT SELL-FILL EVALUATION FOUNDATION

A package-private pure fail-closed evaluator now applies the shared production `FillModel.estimateSell` neutral-seasonality overload to every Package 3.44 positive-net-margin candidate using the shared `FillCurve`, unchanged exact remainder, and effective horizon. It preserves ordered sell probability, expected duration, throughput, and wait evidence, including weak and never-fill results. It does not rebuild curves, retrieve history, filter viability, combine probabilities, resize quantity, calculate expected value or unwind cost, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.46 BUY-REPLACEMENT SELL-FILL VIABILITY FOUNDATION

A package-private pure fail-closed evaluator now interprets Package 3.45 sell-fill evidence with the shared plausibility semantics: positive probability and finite expected duration. It preserves the complete ordered evidence while deriving a separate immutable viable-only view; valid all-never-fill evidence is `NOT_VIABLE`. It does not invoke `FillModel`, rebuild curves, combine probabilities, resize quantity, calculate expected value or unwind cost, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.
