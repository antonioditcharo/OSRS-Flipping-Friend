# Forecasts attached to accepted guidance

15 October 2026. This closes a provenance gap in the prospective observation pipeline. It does
not fit a learned model, approve a strategy or prove that the player followed the advice.

## Reproduced faults

Regression `9471568` demonstrates that candidate lookup recorded buy advice before client-thread
delivery, sell proposals were recorded on the worker before delivery, a buy execution borrowed the
whole flip's probability, and a missing sell forecast borrowed the whole flip's duration.
Regression `8ffb3b2` additionally demonstrates that opening the sell editor replaced a managed
sale's price and forecast with a generic selected-sale quote.
The separate active entry-policy converter's missing leg metadata was also reproduced before
correction. It now shares the proposal conversion and is tested through acceptance and publishing.

## Actual guidance boundary

Candidate lookup produces a proposal with account, recommendation, expiry and quote age. The
client-thread delivery callback retains its existing account, generation, settings and evidence
checks. After StepGuide accepts or defers the proposal, only its current foreground instruction
can enter SuggestionLedger. Paused, rejected, expired and foreign proposals supply no evidence.
The guide also commits the foreground instruction after a selected-sale quote or pending-advice
transition. This proves acceptance for presentation, not an EDT paint, user attention or intent.

An identical order refresh updates the forecast payload without resetting entry progress. A
different trade deferred during entry does not acquire an earlier presentation clock. Repeated
submission of the same immutable Suggestion object does not restamp its forecast availability.
Fresh predictions for identical inputs retain their own availability timestamp as before.

Whole-flip confidence remains the displayed product of both leg probabilities. Execution evidence
uses only the actual leg's probability and duration. Missing probability or duration stays
unpaired, including personal-sale price guidance. There is no whole-flip duration fallback.

## Exact order and model claim

ExecutionForecast schema 1 records the configured PUBLIC_PRICE_FILL_V1 model, capture share,
evidence discount, wait weight, arrival formula, season multiplier, horizon, side, item, price,
quantity and the curve's scored/reachable bucket counts, window and reachable volume. These are
the estimator's sufficient inputs, copied at computation rather than inferred from later fills.
The semantic model version must change if the algorithm changes. This identifier is not a frozen
evaluation baseline or reproducible market-data manifest; those gates remain open.

Durations preserve raw waiting, quantity/throughput working time, applied wait correction and
minimum-duration overhead separately, in seconds. Their sum must match the leg's total. The
probability refers to full completion within the recorded horizon, not first fill. A known zero is
valid; absent nullable inputs, unsupported versions, invalid numbers or contradictory decomposition
are not valid forecasts. The content fingerprint is a SHA-256 over canonical inputs and outputs;
it identifies the claim, not its truth, availability or player intent.

CandidateFactory supplies both actual leg snapshots. The client forwards the buy snapshot with
the candidate it presents. Managed sales recompute the local model for the actual displayed price
and quantity after stabilization/quantity caps. Missing history produces an unknown forecast.
A generic personal-sale quote does not replace an exact managed item/quantity instruction. Other
quantities retain the separate unknown-cost personal-sale workflow; an item ID does not enroll
all owned units. No extra acquisition, disposal, profit or automatic game action is inferred.

## Durable source and readout

The existing account outbox stores the optional snapshot with its original binding and outgoing
event atomically. ACK, expiry and restart retain that original; changing actual inputs invalidates
comparability. No new file, table, migration or financial event identity is introduced. Legacy
missing context remains missing; older scalar evidence is not reconstructed into full snapshots.

The companion validates optional snapshots against the earliest qualified source's exact inputs,
leg probability and duration. Unsupported or inconsistent original context excludes its prediction;
it does not borrow a terminal snapshot or reject otherwise valid financial observations. Read-only
learning/current exposes qualified original snapshots and a fullForecasts count over the complete
source prefix, alongside the existing bounded 200-row diagnostic tail. Gaps and contradictions
continue to exclude prediction comparisons. Historical aggregates and counted-offer claims remain.

Runtime promotion remains HOLD_FOR_VALIDATION with the public-price baseline active. Censor-aware
fitting, frozen baseline/manifests, chronological held-out account return/loss/calibration gates and
100M-member eight-slot replay at separate 5m/30m/2h/8h cadences remain in
[issue #56](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/56). No measured advantage
over Flipping Copilot is established. See the [observation intervals](execution-observation-intervals.md)
and [original binding](execution-prediction-provenance.md) contracts.

Behavioral verification uses mock client observations, fixture account files and disposable SQLite
stores. It covers publication boundaries, paused/deferred/profile changes, leg probabilities,
configured model/order/horizon retention, missing/future schema, zero probability, decomposition,
generic sale override, publisher serialization, ACK/restart/expiry and canonical original readout.
No real-game input or manual live database change is used. Exact build/CI/install receipts belong
in [HANDOFF.md](../../HANDOFF.md).
