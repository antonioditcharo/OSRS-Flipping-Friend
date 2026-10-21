# Forecast diagnostics and shared holding horizons

20 October 2026. Source changes are distinct from installed runtime and player acceptance; read
HANDOFF for delivery. This work does not train or promote a model, edit accounting or authorize
repricing from a missed estimate.

## Corrected default horizon divergence

The companion's `PortfolioPlanner.horizonFor` claimed to mirror client `TradingHorizon`, but their
unset-target defaults differed. The companion used appetite leg defaults of 1.5/2.5/4 hours, while
the client used profile round trips of 90/360/480 minutes, halved to 0.75/3/4 hours. Explicit target
durations already followed the same formula.

`TradingHorizon` now owns the shared calculation and profile-name mapping. Companion planning
uses the existing client profile defaults. A positive requested round-trip duration takes
precedence; each leg receives half that time, subject to a floor of 80% of the actual checking
interval. Raw minutes preserve custom intervals. Tests compare every risk profile, default and
explicit targets, actual 5m/30m/2h/8h visits, and the visibly separate legacy three-hour interval.

This changes prospective sizing and horizon probabilities when the previously divergent default
applies. It does not rewrite an original acquisition's target, stop, patience context or immutable
execution forecast. In the current FillModel, expected duration itself is independent of horizon;
this defect alone cannot explain large duration errors.

## Distinct prospective claims

CandidateFactory uses risk-appetite capture share and available seasonal context for both planned
legs. Managed sell presentation recomputes the actual price and capped quantity with the local
SellTimingEngine model. Its current capture share remains 0.35 and season remains 1; recent Balanced
buy claims used capture share 0.55. These configurations remain unchanged and appear separately
in diagnostic groups. Their difference is not evidence for a replacement calibration. The smaller
sell capture share makes its throughput duration longer, so simply equating it to 0.55 would make
the optimistic timing problem worse at otherwise identical inputs.

The original acquisition's remembered sell duration is separate policy context for patience. An
actual displayed sell order has its own original immutable forecast. Records > Recommendation
labels the actual leg's full-order estimate, completion horizon and modeled probability, and says
that these public-price estimates have not been validated against account outcomes. A sell-model
duration is not a guaranteed execution deadline or permission to cut below acquisition cost.

## Reproducible frozen audit

Use `tools/evaluate-executions.ps1 -Mode Freeze -AccountId <explicit-account> -Capture <new-local-data-dir>`
to freeze a complete recorded source prefix through the existing SELECT-only scoped database
reader. The capture retains source/evaluation hashes and the evaluator artifact. It does not start
the companion, install a runtime or change any financial/source row.

`tools/evaluate-executions.ps1 -Mode Audit -Capture <frozen-dir>` first verifies the bundle and
reproduces canonical source qualification/evaluation. It then emits complete JSON diagnostics:

- Account source cursor, capture time and verified manifest hash.
- Every prospective observation, including excluded originals and their reasons.
- Full immutable forecasts, exact side/item/price/quantity, completion and first-fill duration
  intervals; absent upper bounds remain absent.
- Groups by exact estimator configuration and horizon, then by exact order inputs. Capture share,
  seasonal factor and wait correction are never silently pooled.
- Conservative horizon completion-rate bounds retaining censored and ambiguous episodes, alongside
  the mean advertised completion probability.
- Confirmed elapsed lower bounds beyond the estimate, completed intervals strictly before it, and
  unknown comparisons. These are diagnostics; an early cancellation is not a failed horizon claim.

Store account-level output only under local-data with explicit UTF-8 encoding. A cancelled or
unfinished offer has no inferred full-fill time. The bounded 200-row HTTP diagnostic tail is not
the input; the complete frozen prefix is. Configuration-specific results are observational and
cannot establish whole-account performance, intent, independent censoring or safe model promotion.

Immutable forecasts retain aggregate estimator inputs, not the original series publication clock
and each side's market-history ages. This audit cannot attribute misses to stale or one-sided
history after the fact. That requires prospective source-timing evidence; it must not be inferred
from later market prices or used to backfill old claims. Frozen outputs remain
`HOLD_FOR_VALIDATION`; no wait multiplier or learning promotion is introduced.

## Regression gate

Shared-horizon tests cover risk aliases, defaults, explicit targets and real visit cadences.
Frozen-audit tests preserve censored unknowns, exclude original gaps, distinguish configuration
and exact-order groups, retain the complete source list, refuse changed hashes and leave the frozen
bundle unchanged. Records tests distinguish leg duration from completion horizon and avoid
inventing a probability or horizon when the original forecast is absent. Standard clean builds,
exact-head CI and installed/player acceptance belong to the delivery receipt.
