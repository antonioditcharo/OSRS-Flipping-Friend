# Observed timing intervals and learning authority

15 October 2026. This extends the [original-prediction contract](execution-prediction-provenance.md).
It makes observation uncertainty explicit and stops historical aggregate ratios from activating
live timing corrections. It does not complete learning, prove better returns or validate the
uncorrected public-price model. [Issue #56](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/56)
remains open for forecast contracts, fitting and chronological account evaluation.

## Reproduced active-consumer fault

The account service previously loaded FillCalibration.from(executionStats()) every ten minutes.
Forty offers canceled after ten minutes against forty-minute forecasts made the actual planner
halve waiting time. Forty completions available only as totals did the same. Neither data set
established an original placement clock, completion interval, waiting/working decomposition or
held-out improvement. ExecutionCalibrationAuthorityTest reproduces both failures before the fix.

The historical ratio remains available to explicit diagnostics and existing comparison probes.
It no longer authorizes runtime calibration. The account service uses the public-price baseline
and reads prospective timing evidence from canonical source events. No count threshold alone can
promote a correction, including forty properly attributed completions. The report explicitly says
PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION. This is an interim promotion fence, not finished learning.

## Readout intervals

An authenticated observation says what was visible when captured. It does not prove exact placement,
server fill time or cancellation time. Whole-second timestamps describe a one-second interval.
The compiler therefore widens upper capture bounds by one second and preserves these observations:

- Placement is after the last authenticated EMPTY for that slot/session and by the first
  prospective authenticated active capture. Without that EMPTY, the placement lower bound is unknown.
- Completion is after the last authenticated non-complete active capture and by the first
  authenticated full terminal capture. Later heartbeat/reconnect messages do not move that bound.
- Cancellation with remaining quantity is right-censored at the last confirmed active readout.
  Time offline before a canceled offer reappears is not confirmed time on the market.
- First-unit fill has its own last-zero/first-positive interval, including partial fills. It is
  separate from full-order completion and is not reconstructed from total duration.
- A full observed quantity counts as completed even if its terminal label says canceled. A
  contradictory state, regressed clock/order, changing inputs or regressing cumulative fill yields
  unknown timing bounds. A clear without settlement ends observation; it invents no sale or cancellation.

For example, EMPTY at second 90, first active at 100, partial at 130 and first completed readout at
400 imply placement [90,101] and completion [130,401]. Full-order duration is bounded by [29,311]
seconds. A canceled readout at 400 instead supplies the lower bound 29, with no upper fill time.
With no intermediate active readout, the censoring lower bound can legitimately be zero.

These are conservative observation bounds, not a distribution fit. Forecast comparability means
original source and actual order inputs agree; it does not establish exact timing or training eligibility.
The same earliest-source predicate is shared with canonical execution-statistics acceptance.
Unknown original forecasts cannot be adopted by a later matching recommendation.

## Source and account boundaries

ExecutionObservationLedger reads existing account event_log rows in recorded order. Active/fill/
terminal captures require versioned prospective readouts. Earlier rows can disqualify late
attribution; an authenticated EMPTY can bound placement without becoming a historical fill.
Old aggregate totals are not replayed into new observations. Opaque account/session/
offer/slot identities remain distinct. Recorded accounting gaps keep forecasts excluded, while
unassigned source scope, malformed identity, foreign account or future prospective capture fails closed.

SqliteStore.executionLearningReport uses one SELECT-only snapshot and reports the canonical-offer
row cursor, excluding unrelated prunable account snapshots. No new table, schema migration,
deduplication claim, aggregate update, position ledger or financial effect is created by this read.
Existing source identities, statistics, uncertain sales and live exposure guards are retained.
The cursor identifies a source prefix, not a historical availability clock or frozen evaluation manifest.

Authenticated GET /v1/accounts/<account-id>/learning/current exposes the same evidence. Header and
path scope must agree; other accounts cannot read these rows, and POST returns 405. There is no
unscoped legacy equivalent. Counters cover the complete source prefix; only the latest 200 diagnostic
episodes are returned, with totalObservations and observationsTruncated explicit. That bounded list
must never be silently treated as a complete training data set. These episodes describe observations,
not current physical holdings, active GE capacity, realized profit or additional executions.

The compact sidebar is unchanged. No agent game input or live database alteration is used for
verification. The installed pair remains separately identified in HANDOFF.md; source delivery
does not imply runtime installation or controlled trading acceptance.

## Statistical basis and remaining gates

Incomplete follow-up supplies censoring, while a completion discovered between readouts supplies
an interval. Neither is an exact event time. This distinction follows the
[NIST discussion of censored and readout data](https://www.itl.nist.gov/div898/handbook/apr/section1/apr131.htm).
Player cancellations may depend on observed prices, advice and patience; treating their timing as
independent censoring is an unverified assumption. Evaluation methods also need their censoring
assumptions checked; [Kvamme and Borgan](https://www.jmlr.org/papers/v24/19-1030.html) show problems
with conventional weighting when prediction covariates contain censoring information. That paper
does not validate an OSRS cancellation model or justify inferring unobserved fills here.

Next: carry immutable forecast model/version, horizon and waiting/working decomposition through
the chosen candidate, displayed advice and original event binding; fit only a supported censor-aware
challenger; freeze training/validation source manifests and baseline; compare chronologically on
held-out calibration and realistic account return/loss gates before activation. Whole-account replay
must retain 100M GP, members, eight slots and separate 5m/30m/2h/8h visits, tax, limits, partial fills,
losses and terminal inventory. Real executions and simulated price touches remain separate.

## Verification

Disposable SQLite fixtures and isolated HTTP servers cover active-consumer promotion refusal,
forty genuine prospective completions, placement/completion/first-fill bounds, partial cancellation,
cached terminal exclusion, reconnect stability, missing starts, contradictory clocks/states,
full canceled quantities, source cursor stability, legacy preservation, opaque identity separation,
bounded output with complete counts, SELECT-only reopen and API account/authentication/method guards.
Existing atomic acceptance/recovery and #57 original-prediction tests remain required.
