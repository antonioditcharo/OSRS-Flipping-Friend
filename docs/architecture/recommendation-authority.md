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

## PACKAGE 3.47 BUY-REPLACEMENT ROUND-TRIP COMPLETION FOUNDATION

A package-private pure fail-closed evaluator now applies the shared production `PortfolioCandidate.getCompletionProbability()` formula, buy probability multiplied by sell probability, to every Package 3.46 viable candidate. It preserves complete sell-fill evidence, the viable-only view, ordering, exact remainder, economics provenance, leg measurements, identity, lineage, horizon, and timestamps. It does not calibrate duration, calculate expected slot hours, expected profit, unwind loss, worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.48 BUY-REPLACEMENT DURATION-CALIBRATION INPUT FOUNDATION

A package-private pure fail-closed composer now binds Package 3.47 completion evidence to the production effective `FillCalibration.waitMultiplier(itemId)` choice. Learning-disabled or missing calibration uses `FillCalibration.NEUTRAL`; enabled learning uses the supplied calibration. The immutable context preserves ordered completion evidence, identity, lineage, exact remainder, horizon, and timestamps. It does not apply `correctedHours`, calculate expected slot hours or profit, unwind loss, worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.49 BUY-REPLACEMENT CALIBRATED-DURATION FOUNDATION

A package-private pure fail-closed evaluator now applies the production wait-only duration correction to both legs of every Package 3.48 ordered completion evaluation: unchanged expected hours when wait is nonpositive or nonfinite, otherwise `wait * multiplier + max(0, expectedHours - wait)`. It preserves raw measurements, completion probability, pricing and economics provenance, identity, lineage, exact remainder, horizon, calibration choice, and timestamps. It does not calculate expected slot hours or profit, unwind loss, worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.50 BUY-REPLACEMENT EXPECTED SLOT-OCCUPANCY FOUNDATION

A package-private pure fail-closed evaluator now applies the production `PortfolioCandidate.expectedSlotHours()` three-outcome formula to every Package 3.49 calibrated-duration evaluation. Never-bought evidence consumes one full horizon, completed evidence consumes calibrated buy plus sell hours, and stranded evidence consumes calibrated buy hours plus one full sell horizon; the result retains the production one-minute floor. It preserves ordered upstream evidence, pricing and economics provenance, identity, lineage, exact remainder, horizon, calibration evidence, and timestamps. It does not calculate expected profit, unwind loss, worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.51 BUY-REPLACEMENT UNWIND-LOSS INPUT FOUNDATION

A package-private immutable context and pure fail-closed composer now bind Package 3.50 ordered expected-slot-occupancy evidence to the exact external inputs needed by production `CandidateFactory.unwindCost(...)`: current low-price exit anchor, item volatility, candle bucket duration, effective horizon, item identity, and market timing. The context stores the volatility scalar rather than a mutable feature object and preserves upstream pricing, tax, net-margin, fill, calibrated-duration, slot-occupancy, identity, lineage, exact remainder, ordering, and timestamps. It does not calculate exit price, tax on exit, unwind loss, expected profit, worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.52 BUY-REPLACEMENT UNWIND-LOSS EVALUATION FOUNDATION

A package-private pure fail-closed evaluator now applies production `CandidateFactory.unwindCost(...)` arithmetic to every Package 3.51 ordered input: bucket-scaled volatility with the production ceiling, adverse-drift share and floor; the lower of candidate buy price and current low anchor; production exit tax; nonnegative per-item exit loss; and exact-remainder total unwind loss. It preserves complete upstream slot-occupancy and economics evidence, ordering, identity, lineage, market inputs, horizon, and timestamps. It does not calculate expected profit, worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.53 BUY-REPLACEMENT EXPECTED-PROFIT FOUNDATION

A package-private pure fail-closed evaluator now applies production `PortfolioCandidate.expectedProfit()` arithmetic to every Package 3.52 ordered evaluation: completed probability times exact total net margin, minus stranded probability times exact total unwind loss. It preserves complete unwind-loss, slot-occupancy, pricing, tax, fill, duration, identity, lineage, market-input, horizon, ordering, and timestamp evidence. It does not calculate worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.54 BUY-REPLACEMENT WORST-LOSS INPUT FOUNDATION

A package-private immutable context and pure fail-closed composer now bind Package 3.53 ordered expected-profit evidence to the production `RiskAppetite.lossCutPct` selected by its existing appetite name. The appetite must round-trip through the production mapping, and the immutable positive finite loss-cut scalar is preserved with complete expected-profit, unwind-loss, slot-occupancy, pricing, tax, fill, duration, identity, lineage, horizon, ordering, and timestamp evidence. It does not calculate worst loss, Kelly sizing, GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.55 BUY-REPLACEMENT WORST-LOSS EVALUATION FOUNDATION

A package-private pure fail-closed evaluator now applies production `CandidateFactory` worst-loss arithmetic to every Package 3.54 ordered input: positive buy price multiplied by the resolved production loss-cut scalar and unchanged exact remainder, cast to `long` with production truncation and floored at one coin. It preserves complete expected-profit, unwind-loss, slot-occupancy, pricing, tax, fill, duration, risk-input, identity, lineage, horizon, ordering, and timestamp evidence. Worst loss remains risk-budget evidence and is not folded into expected profit. The package does not calculate Kelly sizing or GP per slot-hour, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.56 BUY-REPLACEMENT KELLY-SIZING INPUT FOUNDATION

A package-private immutable context and pure fail-closed composer now bind Package 3.55 ordered worst-loss evidence to the exact production `CandidateFactory` fractional-Kelly inputs. For the unchanged exact remainder used as the fillable ceiling, it preserves full net profit, full unwind loss, round-trip completion probability, and production payoff odds (`fullNetProfit / fullUnwindLoss`, or full net profit when unwind loss is zero), together with the production 0.35 Kelly share, 0.1 minimum fraction, and defensive `{0.5, 1.0, 2.0, 4.0, 8.0}` size grid. Complete upstream economics, risk, identity, lineage, ordering, and timestamp evidence remain available. It does not calculate a Kelly fraction, generate or select quantities, recalculate fills or economics, calculate GP per slot-hour, rank, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.57 BUY-REPLACEMENT KELLY-FRACTION EVALUATION FOUNDATION

A package-private pure fail-closed evaluator now applies production `CandidateFactory.kellyFraction(...)` arithmetic to every Package 3.56 ordered input: full Kelly edge `(p * b - (1 - p)) / b`, multiplied by the production 0.35 Kelly share, capped at 1.0, then floored at the production 0.1 minimum fraction. It preserves full-profit, unwind-loss, completion-probability, payoff-odds, worst-loss, expected-profit, pricing, exact-remainder, identity, lineage, size-grid, ordering, and timestamp evidence. It does not generate, round, clamp, deduplicate, rank, or select quantities, recalculate fills or economics, calculate GP per slot-hour, construct `PortfolioCandidate`, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.58 BUY-REPLACEMENT QUANTITY-GRID FOUNDATION

A package-private pure fail-closed composer now applies the production quantity-grid arithmetic to every Package 3.57 ordered Kelly-fraction evaluation and every preserved ordered size share: `max(1, min(fillable, round(fillable * kellyFraction * share)))`. It retains the unchanged exact remainder as the fillable ceiling and suppresses repeated adjacent quantities exactly as production does after a clamp reaches a ceiling. It preserves source evaluation, grid index/share, prices, expected-profit and worst-loss evidence, identity, lineage, appetite, ordering, and timestamps. It does not invoke `FillModel`, recalculate fills or economics, calculate GP per slot-hour, construct `PortfolioCandidate`, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.59 BUY-REPLACEMENT QUANTITY-FILL INPUT FOUNDATION

A package-private immutable context and pure fail-closed composer now rejoin Package 3.58 ordered generated quantities with the existing Package 3.36 shared nonempty `FillCurve` and effective horizon plus an explicit fresh production seasonal multiplier derived at the market-context boundary. Exact intent, original-offer and recommendation lineage, item, appetite, exact remainder, quantity, price, size-grid provenance, history timing, market-context timing, and composition timing must agree. The package does not invoke `FillModel`, create `FillEstimate`, call `estimateBuy` or `estimateSell`, evaluate plausibility, apply duration calibration, recalculate economics, calculate GP per slot-hour, construct `PortfolioCandidate`, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.60 BUY-REPLACEMENT QUANTITY-FILL EVALUATION FOUNDATION

A package-private pure fail-closed evaluator now applies the shared production seasonal `FillModel.estimateBuy` and `estimateSell` overloads to every Package 3.59 ordered generated-quantity input using the shared `FillCurve`, generated quantity, effective horizon, and seasonal multiplier. It preserves ordered buy and sell probability, expected duration, throughput, and counterparty-wait evidence, including weak and never-fill results, with quantity, pricing, size-grid, identity, lineage, market-context, and upstream evidence. It does not evaluate plausibility, filter candidates, apply duration calibration, recalculate economics, calculate GP per slot-hour, construct `PortfolioCandidate`, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.61 BUY-REPLACEMENT QUANTITY-FILL VIABILITY FOUNDATION

A package-private pure fail-closed evaluator now interprets every Package 3.60 two-leg quantity-fill measurement using the shared plausibility semantics independently on both legs: positive completion probability and finite expected duration. It preserves the complete ordered evidence while deriving a separate immutable viable-only quantity view with unchanged prices, generated quantity, size-grid provenance, Kelly evidence, buy and sell measurements, identity, lineage, horizon, seasonality, market timing, and upstream evidence. Valid all-never-fill evidence yields `NOT_VIABLE`. It does not invoke `FillModel`, create `FillEstimate`, combine probabilities, apply duration calibration, recalculate economics, calculate GP per slot-hour, construct `PortfolioCandidate`, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.62 BUY-REPLACEMENT QUANTITY ROUND-TRIP COMPLETION FOUNDATION

A package-private pure fail-closed evaluator now applies the shared production `PortfolioCandidate.getCompletionProbability()` formula, buy probability multiplied by sell probability, to every Package 3.61 viable generated quantity. It preserves complete quantity-fill evidence, the viable-only view, ordering, generated quantity, size-grid and Kelly provenance, both-leg measurements, identity, lineage, horizon, seasonality, market timing, and upstream evidence. It does not invoke `FillModel`, create `FillEstimate`, reconsider plausibility, calibrate duration, recalculate economics, calculate expected slot occupancy or GP per slot-hour, construct `PortfolioCandidate`, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.63 BUY-REPLACEMENT QUANTITY DURATION-CALIBRATION INPUT FOUNDATION

A package-private immutable context and pure fail-closed composer now bind Package 3.62 ordered quantity-specific completion evidence to the production effective `FillCalibration.waitMultiplier(itemId)` choice. Learning-disabled or missing calibration uses `FillCalibration.NEUTRAL`; enabled learning uses the supplied calibration. The context preserves complete fill evidence, viable-only evidence, quantity-specific completion evidence, generated quantity, size-grid and Kelly provenance, both-leg measurements, identity, lineage, horizon, seasonality, market timing, calibration choice, and timestamps. It does not apply `correctedHours`, calculate expected slot occupancy or profit, recalculate unwind loss or worst loss, calculate GP per slot-hour, construct `PortfolioCandidate`, rank, select, authorize, persist, route, retrieve, present, schedule, or activate replacement behavior.

## PACKAGE 3.64 BUY-REPLACEMENT QUANTITY CALIBRATED-DURATION FOUNDATION
A package-private pure fail-closed evaluator applies production wait-only duration correction independently to each Package 3.63 quantity-specific buy and sell leg. Nonpositive or nonfinite wait retains raw expected hours in the arithmetic helper; positive finite wait uses `wait * multiplier + max(0, expectedHours - wait)`. Upstream negative and NaN wait evidence remains inadmissible. Immutable wrappers retain the complete calibration context, ordered completion and viable-only evidence, complete fill evidence, generated quantity, size-grid and Kelly provenance, raw measurements, identity, lineage, horizon, seasonality, calibration choice, and timestamps. Malformed, inconsistent, reordered, stale, or nonfinite derived evidence fails closed. No expected slot occupancy, downstream economics, candidate construction, ranking, selection, authorization, persistence, route, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.65 BUY-REPLACEMENT QUANTITY EXPECTED SLOT-OCCUPANCY FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure fail-closed evaluator adds expected slot hours to every ordered Package 3.64 quantity-specific calibrated-duration evaluation. It applies the production three-outcome formula: never-bought consumes one full horizon, completed consumes calibrated buy plus sell hours, and stranded consumes calibrated buy plus one full sell horizon. Both the production constructor's one-minute horizon floor and the result's one-minute floor are preserved. Original calibrated evidence is revalidated at its original timestamp without refreshing upstream authority; reordered, altered, stale, malformed, or nonfinite results fail closed. Immutable wrappers preserve the entire upstream context, complete fill and viable-only views, completion evidence, generated quantities, size-grid and Kelly provenance, raw measurements, calibration choice, identity, lineage, horizon, seasonality, and timestamps. Existing economics remain provenance only. No new profit, unwind-loss, worst-loss, sizing, GP-per-slot-hour, candidate construction, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.66 BUY-REPLACEMENT QUANTITY UNWIND-LOSS INPUT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private immutable context and pure fail-closed composer bind ordered Package 3.65 quantity-specific slot-occupancy evidence to explicit production unwind inputs: matching item identity, current low-price exit anchor, raw finite nonnegative volatility scalar, positive candle bucket duration, unchanged effective horizon, and market timing. The volatility scalar is captured without retaining a feature object. Market observation must not follow the source slot evaluation; both source and market observations use inclusive freshness checks with overflow-safe subtraction. Slot evidence is revalidated at its original timestamp and compared for exact ordered source linkage and occupancy values, without refreshing upstream authority. Complete upstream context, generated quantities, exact-remainder ceiling, size-grid and Kelly provenance, raw and calibrated measurements, completion, calibration choice, seasonality, identity, lineage, and timestamps remain preserved. Existing economics remain provenance only. No drift, exit price, exit tax, unwind loss, new profit or worst loss, sizing, GP-per-slot-hour, candidate construction, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.67 BUY-REPLACEMENT QUANTITY UNWIND-LOSS EVALUATION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure fail-closed evaluator applies production CandidateFactory unwind arithmetic to every ordered Package 3.66 generated quantity: bucket-scaled volatility with the 0.2 ceiling, 0.25 adverse-drift share and 0.002 floor, lower of buy price and current low anchor, production rounded exit price with one-coin floor, injected item-aware exit tax, nonnegative per-item loss, and checked long total loss using the generated quantity rather than the preserved exact-remainder ceiling. Immutable results retain complete input and upstream evidence, ordering, quantity/grid/Kelly provenance, raw and calibrated measurements, completion, seasonality, identity, lineage, and timestamps. Original slot evidence is revalidated without refreshing authority; composition and market freshness use inclusive overflow-safe subtraction. Malformed, reordered, altered, foreign, stale, nonfinite, or invalid-tax evidence fails closed without partial results. Existing expected-profit and worst-loss values remain provenance only. No new expected profit, worst loss, sizing, GP-per-slot-hour, candidate construction, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.68 BUY-REPLACEMENT QUANTITY EXPECTED-PROFIT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure fail-closed evaluator applies production PortfolioCandidate expected-profit arithmetic independently to every ordered Package 3.67 generated-quantity evaluation. It obtains completed-sale net profit from the injected item-aware TaxCalculator using each candidate's buy price, sell price, and generated quantity; derives completed and stranded probabilities from the preserved quantity-specific fill evidence; and subtracts stranded probability times that quantity's Package 3.67 unwind loss. The earlier expected-profit value remains explicitly named upstream provenance and is not scaled or reused. Package 3.67 evidence is revalidated at its original timestamp, including ordered source linkage, drift, exit price, exit tax, per-item loss, and total unwind loss, without refreshing authority. Immutable results retain complete upstream context, exact-remainder ceiling, generated quantity, grid/Kelly provenance, raw and calibrated measurements, completion, slot occupancy, market inputs, seasonality, identity, lineage, ordering, and timestamps. Negative expected-profit evidence is retained rather than filtered. No new worst loss, Kelly sizing, GP-per-slot-hour, PortfolioCandidate construction in production, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.69 BUY-REPLACEMENT QUANTITY WORST-LOSS INPUT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private immutable context and pure fail-closed composer bind Package 3.68 ordered generated-quantity expected-profit evidence to the production RiskAppetite loss-cut percentage selected by the preserved appetite name. The appetite must round-trip through the production mapping, and the positive finite scalar is retained without applying worst-loss arithmetic. Package 3.68 is revalidated at its original timestamp with the same injected item-aware TaxCalculator, including exact ordered source linkage, generated quantity, completed-sale net profit, completed probability, stranded probability, and quantity expected profit, without refreshing authority. Negative quantity expected-profit evidence remains valid. Immutable context preserves complete upstream pricing, fill, calibration, slot-occupancy, unwind-loss, generated-quantity, grid/Kelly, market, identity, lineage, ordering, and timestamp evidence. No worst loss, new expected profit, Kelly sizing, GP-per-slot-hour, PortfolioCandidate construction, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.70 BUY-REPLACEMENT QUANTITY WORST-LOSS EVALUATION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure fail-closed evaluator applies production CandidateFactory worst-loss arithmetic independently to every Package 3.69 generated-quantity input: positive buy price multiplied by the canonical production loss-cut scalar and generated quantity, cast to long with production truncation and floored at one coin. Package 3.69 is revalidated at its original timestamp with the same injected item-aware TaxCalculator, including exact source identity and quantity expected-profit economics, without refreshing authority. The exact remainder remains only the fillable ceiling and provenance; earlier worst-loss evidence remains explicitly named upstream provenance. Negative quantity expected-profit evidence remains valid, and worst loss remains risk-budget evidence rather than expected cost. Immutable results preserve the complete upstream context, ordering, quantities, grid/Kelly provenance, fill, duration, occupancy, unwind-loss, expected-profit, market, identity, lineage, appetite, and timestamps. No Kelly sizing, GP-per-slot-hour, PortfolioCandidate construction, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.71 BUY-REPLACEMENT QUANTITY KELLY-SIZING INPUT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private immutable context and pure fail-closed composer bind Package 3.70 ordered generated-quantity worst-loss evidence to the exact production CandidateFactory fractional-Kelly inputs for each generated quantity. Each input preserves that quantity's completed-sale net profit, unwind loss, completion probability, payoff odds (quantity net profit divided by quantity unwind loss, or quantity net profit when unwind loss is zero), and quantity worst loss, together with the production 0.35 Kelly share, 0.1 minimum fraction, and defensive {0.5, 1.0, 2.0, 4.0, 8.0} size grid. Package 3.70 is revalidated at its original timestamp with the same injected item-aware TaxCalculator and exact source identity, without refreshing authority. Complete upstream expected-profit, worst-loss, fill, duration, slot-occupancy, market, generated-quantity, grid/Kelly provenance, identity, lineage, ordering, and timestamps remain available. Finite nonpositive completed-sale payoff remains input evidence because production Kelly arithmetic maps nonpositive payoff odds to the minimum fraction; negative quantity expected-profit remains upstream provenance and is not itself filtered. No Kelly fraction is calculated, no quantity is generated or resized, and no GP-per-slot-hour, PortfolioCandidate construction, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.72 BUY-REPLACEMENT QUANTITY KELLY-FRACTION EVALUATION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure fail-closed evaluator applies production CandidateFactory.kellyFraction(...) arithmetic independently to every Package 3.71 generated-quantity sizing input. Finite nonpositive payoff odds map to the production 0.1 minimum fraction; otherwise the full Kelly edge `(p * b - (1 - p)) / b` is multiplied by the production 0.35 Kelly share, capped at 1.0, and floored at 0.1. Package 3.71 is revalidated at its original timestamp with the same injected item-aware TaxCalculator and exact underlying source linkage, without refreshing authority. Immutable results preserve complete generated-quantity profit, unwind-loss, completion, payoff-odds, worst-loss, pricing, fill, duration, slot-occupancy, market, grid, identity, lineage, ordering, constants, and timestamp evidence. No quantity is generated, rounded, clamped, deduplicated, ranked, or selected; no economics are recalculated; and no GP-per-slot-hour, PortfolioCandidate construction, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.73 BUY-REPLACEMENT QUANTITY EXPECTED-GP-PER-SLOT-HOUR FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
A package-private pure fail-closed evaluator applies production PortfolioCandidate expected-GP-per-slot-hour arithmetic independently to every Package 3.72 generated-quantity evaluation: quantity expected profit divided by that same quantity's expected slot hours. Package 3.72 is revalidated at its original timestamp with the same injected item-aware TaxCalculator and exact underlying source linkage, without refreshing authority. The production one-minute slot-occupancy floor is preserved upstream, finite negative expected profit remains valid negative rate evidence, and nonfinite division fails closed. Immutable results retain complete generated-quantity profit, slot occupancy, unwind loss, completion, payoff odds, worst loss, Kelly fraction, pricing, fill, duration, market, grid, identity, lineage, ordering, constants, and timestamp evidence. No quantity is generated or resized, no economics or occupancy are recalculated, and no PortfolioCandidate construction in production, ranking, selection, authorization, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.74 BUY-REPLACEMENT QUANTITY CANDIDATE ECONOMIC-INPUT FOUNDATION

Status: NON-ACTIVATED FOUNDATION.

A package-private immutable economic input and ordered context bind every Package 3.73 generated-quantity rate evaluation to the economic portion of the production CandidateFactory constructor contract. Target, equilibrium, and exit buy prices all retain the evaluated buy price; the three sell prices all retain the evaluated sell price, matching production construction. The unwind liquidation exit price remains separate upstream evidence, not the constructor exit-sell price. Inputs retain generated quantity separately from the exact-remainder ceiling, quantity net profit, worst loss, unwind loss, buy/sell probabilities, calibrated buy/sell hours, and the unchanged effective horizon. Expected profit, slot occupancy, and GP rate remain copied evidence, including finite negative economics.

The pure fail-closed composer revalidates Package 3.73 at its original evaluation timestamp with the injected item-aware TaxCalculator, requires exact ordered source linkage and unchanged finite rate values, and preserves the complete original evaluation set. Inclusive overflow-safe composition freshness does not refresh upstream authority. No new economic arithmetic or constructor normalization is applied. This is deliberately only an economic-input bridge: item-name/group metadata, durable capacity, expiry binding, and complete candidate construction remain separate responsibilities. No quantity generation/resizing, filtering, ranking, selection, PortfolioCandidate construction, authorization, intent transition, persistence, routing, retrieval, presentation, scheduling, runtime activation, or automatic game interaction is introduced.

## PACKAGE 3.75 BUY-REPLACEMENT QUANTITY FILL-SOURCE PROVENANCE FOUNDATION

Status: NON-ACTIVATED FOUNDATION.

The quantity-fill input context now retains the exact original BuyReplacementCandidateFillInputContext supplied to its existing composer. This additive final reference and package-private getter preserve the metadata-bearing source, including its original identity, name, lineage, and timestamps, without recomposition or authority refresh. The composer retains that reference only on its existing successful validation path; all previous copied fill scalars, quantity ordering, immutable inputs, and validation remain unchanged.

The existing direct constructor remains compatible and explicitly records absent source provenance as null. No equivalent context is reconstructed and no missing provenance is invented. Direct construction remains evidence construction, not validation; any later metadata-binding consumer must validate linkage and reject absent or inconsistent provenance. This package does not bind metadata to Package 3.74 economics, derive groups, bind capacity or expiry, calculate economics, construct candidates, rank/select, authorize replacement, persist, route, present, schedule, activate runtime behavior, or automate game interaction. It establishes only the missing provenance prerequisite for a later separately validated metadata bridge.

## PACKAGE 3.76 BUY-REPLACEMENT QUANTITY SOURCE-ACCESS FOUNDATION

Status: NON-ACTIVATED FOUNDATION.

Four package-private typed getSource accessors expose already-retained exact source references from the quantity duration-calibration input context, round-trip completion evaluation set, fill-viability assessment, and fill evaluation set. Together with existing later-stage source accessors, these close the access gaps on the path back to the Package 3.75 quantity-fill context and its retained original metadata-bearing context. No source is copied, reconstructed, refreshed, or substituted; constructors, final fields, immutable lists, measurements, ordering, quantities, economics, and timestamps remain unchanged. Absent source references remain absent, including legacy null metadata provenance.

Source access is not evidence validation or authority. Tests deliberately use empty wrappers to test identity access without claiming valid completion or economic evidence. A later metadata bridge must independently revalidate economic inputs at their original timestamp, check ordered source identity and wrapper horizon, and reject missing or inconsistent retained provenance. This package does not bind metadata, derive groups, bind capacity or expiry, calculate economics, construct candidates, filter/rank/select quantities, authorize replacement, transition intent, persist, route, retrieve, present, schedule, activate runtime behavior, or automate game interaction.

## PACKAGE 3.77 BUY-REPLACEMENT QUANTITY CANDIDATE-METADATA INPUT FOUNDATION

Status: NON-ACTIVATED FOUNDATION.

The package-private immutable metadata context and pure fail-closed composer bind the original item name and production ItemGroups.groupOf result to the complete supplied Package 3.74 economic context. The composer follows typed retained sources through the Package 3.76 access path to the Package 3.75 metadata-bearing context, without accepting independent caller metadata or reconstructing equivalent provenance. It revalidates economics at the original economic composition timestamp and checks ordered exact rate references and each economic wrapper horizon. Missing legacy provenance, malformed names, inconsistent item/lineage/appetite/remainder, foreign curves, altered history or horizon, and regressing wrapper times fail closed. Empty production groups remain valid; names are preserved unchanged.

Economic and original rate timestamps both bound metadata composition freshness inclusively with subtraction after nonnegative ordering checks, so a later economic wrapper cannot extend rate lifetime. Metadata timing is checked at its original quantity-fill binding, not promoted to fresh account or market authority. All original source references, economic inputs, generated quantities, exact-remainder ceilings, negative economics, ordering, and timestamps remain evidence. Tests use a dedicated coherent single-context chain with explicitly synthetic fill measurements; existing fixture helpers are not changed.

This package does not establish current capacity, durable buy-limit or intent-expiry authority, construct candidates, change quantities, filter/rank/select, authorize replacement, transition intent, persist, route, retrieve, present, schedule, activate runtime behavior, or automate game interaction. Later consumers must separately validate current authority before any recommendation. Direct construction is not validation.

## PACKAGE 3.78 BUY-REPLACEMENT QUANTITY CANDIDATE CAPACITY-AND-EXPIRY INPUT FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
The package-private immutable capacity context and pure fail-closed composer bind the retained eligibility buyLimitRemaining and a candidate expiry to the complete supplied Package 3.77 metadata context. The composer revalidates metadata by recomposing it at its original composition timestamp and requires the same economic, quantity-fill, and metadata source objects plus the same item name and production group. Capacity and expiry are read only from the exact retained metadata-bearing context; no caller value, ledger read, or refreshed account state is used. A limit of zero or less, a limit below the exact remainder, and any generated quantity that is not positive or exceeds the limit or exact remainder fail closed.
Expiry is the eligibility evaluation time plus a local 150-second lifetime, checked by reflection against the production plan lifetime, capped by the retained intent expiry, with overflow-guarded addition. It is anchored to the earliest pricing evidence so later wrappers cannot extend it. Composition is refused before the metadata timestamp, after expiry (inclusive at the boundary), or outside the inclusive original rate freshness window.
Retained eligibility capacity is evidence, not current durable buy-limit authority; live revalidation belongs to a later package. This package does not construct candidates, change quantities, filter/rank/select, authorize replacement, transition intent, persist, route, retrieve, present, schedule, activate runtime behavior, or automate game interaction. Direct construction is not validation.

## PACKAGE 3.79 BUY-REPLACEMENT QUANTITY LIVE BUY-LIMIT REVALIDATION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
The package-private outcome, immutable assessment, and pure fail-closed revalidator check every Package 3.78 generated quantity against a caller-supplied live buy-limit observation: item id, remaining allowance, and observation time. The revalidator does not read BuyLimitLedger or the clock; a later separately validated adapter must supply the observation. It revalidates the capacity context by recomposing it at its original composition time and requires the same metadata source, retained limit, and expiry. The live observation must match the item, be nonnegative, be observed no earlier than the original eligibility evaluation and no later than evaluation, and be inclusively fresh. Evaluation must lie between capacity composition and the unchanged candidate expiry, and the original rate freshness window still applies; live input refreshes no earlier authority.
The effective limit is the lower of the retained eligibility limit and the live value, so a higher live allowance never widens what eligibility established. Every quantity remains evidence; an ordered covered view with original indexes is derived without dropping, reordering, or resizing. At least one covered quantity yields COVERED; a valid all-uncovered observation, including zero remaining, yields NOT_COVERED. The exact remainder remains unchanged as ceiling and provenance, and negative economics are preserved.
This package does not read or update durable buy-limit state, construct candidates, select or rank quantities, authorize replacement, transition intent, persist, route, present, schedule, activate runtime behavior, or automate game interaction. Coverage is evidence, not authority.

## PACKAGE 3.80 BUY-REPLACEMENT LIVE BUY-LIMIT OBSERVATION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
BuyLimitLedger gains a package-private remainingAt(itemId, buyLimit, at) that computes one item's remaining allowance at an explicit instant using the existing anchored window, inclusive expiry, and zero clamp. It reads no clock and changes no ledger state. The existing remaining(mapping) now delegates to it with the same single clock read, so production behaviour is unchanged; a parity test compares both paths.
The package-private immutable observation and read-only observer take the ledger, the production item mapping, an item id, and an explicit epoch-second time. Missing ledger, mapping, or item, a nonpositive item id or limit, a negative or unrepresentable time, and an out-of-range result fail closed. The observation records item id, mapped limit, remaining allowance, and observation time, and is shaped to feed the Package 3.79 revalidator, which still takes the lower of retained and live values.
The observer never applies, reconciles, or restores ledger state, never reads the clock, and is not consumed by CompanionService or any runtime path. This package does not construct candidates, select quantities, authorize replacement, transition intent, persist, route, present, schedule, activate runtime behaviour, or automate game interaction. An observation is evidence, not authority.

## PACKAGE 3.81 BUY-REPLACEMENT QUANTITY CANDIDATE CONSTRUCTION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
The package-private immutable candidate set and pure fail-closed builder construct production PortfolioCandidate objects only for quantities that a Package 3.79 live buy-limit assessment marks covered. The builder revalidates the assessment by rerunning the revalidator with the same capacity source and live observation at its original evaluation time, requiring identical outcome, effective limit, covered indexes, and covered input identity. Construction is refused before that evaluation time, after the unchanged Package 3.78 expiry (inclusive at the boundary), or once the live observation or original rate evidence exceeds the inclusive freshness window.
All twenty constructor values come from the retained evidence chain: item id, name, and production group from the metadata binding; buy and sell prices, quantity, net profit, worst loss, unwind loss, both fill probabilities, calibrated hours, and horizon from the economic inputs; the effective lower of retained and live buy-limit remaining; and the capacity expiry. The display buy probability matches the production factory. After construction every stored field must equal its evidence exactly, and the candidate's own expected profit, expected slot hours, and GP per slot-hour must agree with the evidence chain within a tight relative tolerance; any disagreement rejects the whole set. Covered order and original indexes are preserved; nothing covered yields a valid empty set; the exact remainder stays ceiling and provenance; negative economics remain evidence.
Constructed candidates are evidence, not a plan or authority. This package does not invoke the optimizer or planner, rank, sort, select, authorize replacement, transition intent, persist, route, present, schedule, activate runtime behaviour, or automate game interaction.

## PACKAGE 3.82 BUY-REPLACEMENT QUANTITY CANDIDATE SELECTION FOUNDATION
Status: NON-ACTIVATED FOUNDATION.
The package-private outcome, immutable selection, and pure fail-closed selector record which constructed Package 3.81 replacement candidate the production optimizer's per-candidate rules would prefer for the single slot being replaced. The selector rebuilds the candidate set from the same live-limit assessment at its original build time and requires identical source indexes and field-for-field identical candidates. Selection is refused before the build time, at or after the candidate expiry (matching the optimizer, which treats an expiry not after now as expired), or once the live observation or original rate evidence exceeds the inclusive freshness window.
The gates mirror PortfolioOptimizer in order and are counted: net profit below the larger of one coin and the supplied minimum profit per flip, no capital required, zero completion probability, and expected GP per slot-hour not above zero. The objective is expected GP per slot-hour; the highest wins and ties keep the earliest candidate, matching the optimizer's stable descending sort. A losing candidate is never selected; no eligible candidate, including an empty set, yields NO_ELIGIBLE_CANDIDATE. The minimum profit per flip is a caller-supplied nonnegative setting; binding it to the account's stored setting belongs to a later adapter.
Portfolio-level constraints (coins, exposure, group limits, loss budget) are not applied, so a selection is preference evidence, not a plan, allocation, decision, or authority. This package does not invoke the optimizer or planner, create PortfolioPlan, PortfolioAllocation, or PolicyDecision, authorize replacement, transition intent, persist, route, present, schedule, activate runtime behaviour, or automate game interaction.
