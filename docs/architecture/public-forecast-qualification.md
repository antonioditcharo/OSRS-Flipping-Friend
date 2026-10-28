# Public forecast qualification before pricing promotion

27 October 2026. Offline qualification v2 adds a forecast report to the existing chronological
whole-account comparisons. It does not change live suggestions, financial evidence or learning.
Retained v1 bundles reproduce with their retained evaluator; changing the method requires a new
explicit frozen bundle. An insufficient smoke cohort never consumes the final test.

## What is measured

For each 5m/30m/2h/8h horizon, freeze predictions at the first actually available public snapshot
on or after the next nonoverlapping horizon cutoff. Use only that snapshot's available histories
and each side's actual, fresh trade price. Fit the production public strategy model, not the chart
model. Score low and high paths independently. Do not backdate the origin to a bucket start or a
nominal visit. These samples describe public price forecasts, not player intent or account fills.

Resolve a target using the first available snapshot within five minutes after its deadline that
contains a positive quote with an actual trade timestamp between the deadline and deadline +5m.
A newly received snapshot containing an older trade is not a target. Missing/stale sides remain
unresolved; low and high can resolve at different snapshots. Missing target windows and right
censoring at a fold's end are separate unknown outcomes, never zero error or failed account fills.
Every attempted origin has an unsupported-origin or forecast outcome; every forecast resolves,
misses its target window or remains right censored. Errors and counts cannot silently disappear.

Report mean absolute GP error, signed error, absolute percentage error, persistence GP error and
observed central-interval coverage with their resolved denominators. Wholly unknown metrics are
null. Flat/discrete prices can have 100% interval coverage; that is not automatically a failure.

## Qualification boundaries

Existing duration, recorded-availability, three volume-share stresses, complete terminal marks,
minimum completed sales, loss limits and positive conservative whole-account improvement still
apply. Add conservative provisional evidence requirements per side/horizon: at least 100 resolved
targets, forecast support on at least half of attempted origins, at least 80% of forecasts resolved,
and at least 40% observed central-interval coverage. Observation gaps over 15 minutes or any model
analysis exception refuse the experiment's qualification. The report retains all censored and
unsupported counts rather than removing them to improve a score.

Persistence comparison is reported without restoring per-candidate metric-by-metric dominance.
Passing calibration support is not return superiority; whole-account stress gains remain required.
These thresholds are an explicitly versioned research fence, not validated learning or automatic
runtime activation. Unsupported segments continue the installed baseline policy.

The evaluator uses at most 128 input items and a bounded pending forecast set. Capture/replay
failures cannot disable the live panel. Original market/canonical identities and current financial
authority remain unchanged. Distinct budgeted market-loss receipts, compatible runtime activation,
asynchronous hosted preparation and execution-first learning remain separate unfinished work.

Local verification: clean 2,512 Java tests (six existing skips), 132 tooling tests and bytecode 55
pass. The exact packaged evaluator runs freeze, fitting and validation on Java 11 against the
completed 31-observation public smoke. Every cadence refuses insufficient duration; the final test
remains unopened. All active trading classes are byte-identical to installed #190. This evaluator
change needs no RuneLite restart. Exact-head CI remains a release requirement.
