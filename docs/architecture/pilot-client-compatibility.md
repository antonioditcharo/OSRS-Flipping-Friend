# Pilot client compatibility and historical risk-read latency

Follow-on to installed PR #163, 25 October 2026. Current delivery is recorded separately in
`local-data/hosted-pilot/compatibility-delivery.json`; the hosted pilot receipt retains #163's
original build, backups, source preservation, deployment and capacity evidence.

## Observed faults

The real RuneLite 1.13.4 launcher supplies Gson 2.8.5. Its optional GE history worker failed with
`NoSuchMethodError: JsonParser.parseString` because the core compiled against the companion's newer
Gson. Trading continued and actual COLLECT/BUY deliveries were logged, but history capture failed.
Client tests must run against the provided library; compilation alone cannot establish linkage.

Live delivery logs showed roughly 4–10 seconds per refresh. Thread snapshots caught reporting in
`currentPlanningProof -> DurableRisk -> ProspectiveGapExposure -> TradeGapEvidence.valid`, compiling
an account-scope regular expression for each retained event while holding the account monitor.
The unchanged source history was copied and validated repeatedly across health, reports and action
checks. Existing valid guidance and final current-state guards must survive the optimization.

## Change and proof boundaries

- Use the supported instance JsonParser API and exercise the real asynchronous collector, durable
  supporting snapshot and independent acknowledgement under Gson 2.8.5. A linkage error stays in
  the optional worker and leaves its source available for retry.
- Encode the identical SHA-256 UTF-8 account hash directly and validate the exact existing
  `rs-[0-9a-f]{64}` alphabet without recompiling a regular expression on each event. Account IDs,
  authentication, financial event IDs and profile ownership are unchanged.
- Reuse a validated immutable history when no source tail was appended. Appending creates a new
  list; an earlier read cannot observe later additions. External commits and every rollback still
  discard the canonical history, using the existing data-version fence.
- Reuse only the prospective gap funding calculation for that exact immutable history reference,
  original account/window/session/cursor/capital/eight-source binding, reconciliation-item set and
  opening-cost map. Changed evidence, unbounded scope, missing history, a different account/window,
  appended sources or discarded prefixes require qualification again, including unavailable results.
- Current offers, cash, holdings, buy limits, market marks, confirmed losses, risk availability and
  final order authority remain fresh evaluations. The memo cannot clear a gap, reset a loss anchor,
  repair history, adopt belongings or manufacture a profitable action.

## Release gates

- Reproduce the collector failure on the actual provided Gson, then pass capture and durable ACK.
- Budget repeated immutable proof reads; prove source append, cost/scope/window change and foreign
  replacement revalidation, and preserve earlier read snapshots.
- Run all source/financial/risk/continuation and account-isolation regressions, clean Java build,
  tooling suite and exact-head Windows/Linux CI; retain the verified #163 rollback runtime.
- Install through the paired updater, compare quiescent original data and verify fresh live history
  capture, cash/eight-slot acknowledgements and useful visible guidance.
- Measure retained-action reads separately from durable state-change delivery. Private shadow
  transport outage, sustained latency and player-operated trading/restart remain separate evidence.
  Hosted advice/coordination and learning promotion stay gated by the pilot contract.
