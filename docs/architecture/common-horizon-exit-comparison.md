# Comparing protected exit limits over one horizon

26 October 2026. Source branch `codex/market-exit-comparison`; not installed. The owner is away;
manual login/GE, live timing and chart acceptance for installed #178 remain deferred. This pass
does not create new market-informed loss authority or promote execution learning.

## Decision and fallback

The existing non-loss forecast frontier compared estimated profit per hour. A positive ratio can
look attractive while a low completion probability leaves most stock unfilled. The shared
`ExitPriceComparison` also compares full-completion/unchanged-mark scenarios over the same horizon
for the same remaining quantity and exact allocated cost. It is a model scenario, not a calibrated
distribution of partial fills or a guaranteed liquidation bound.

For a candidate order, let `N` be its exact current-rule taxed proceeds, `M` the same current taxed
low-side trade mark for all unsold units, `C` the exact cost and `p` its existing public-flow
whole-order completion estimate. The scenario P/L is `(M-C) + p*(N-M)`. Comparisons use decimal
arithmetic for `p*(N-M)`, avoiding cancellation of large monetary values. Presentation rounds
whole GP half away from zero. No hypothetical replacement-trade profit is credited; future
prices, depth and partial-fill outcomes remain unpredicted.

An optional forecast limit must still improve the existing profit/time score by more than 10%,
and its marked horizon value cannot be worse than the baseline or retained best alternative.
New alternatives respect the exact taxed non-loss floor, original protected target where applicable,
the maintenance ceiling and the fresh reported low-side trade. An observed loss-making standing
order can be investigated but cannot grant an alternative order through this comparison.

The candidate frontier remains bounded and supplied by the qualified strategy forecast. It does
not use chart fits, create volume at unseen prices, expand entry exposure or fit a model on the
foreground path. Cold/stale/malformed/oversized/unsupported evidence immediately retains the
existing limit. The old initial quiet-market and accepted-relist paths still work independently.

Only completed public buckets participate; exact repeated buckets are counted once, conflicting
ones refuse the optional comparison, missing time is retained and future/incomplete buckets are
excluded. Both last trades must be within 120 seconds and the completed history must remain recent.
At most 2,048 input buckets and 16 supplied alternatives are accepted.

## Player workflow and boundaries

Initial listings keep their applicable original target and exact costs. Working sale maintenance
still requires placement/no-progress patience, two completed supporting buckets, material or
qualified fine-tick displacement, exact floors after stabilization and final companion authority.
Stops, wind-down, cancellation/late-fill/collect/relist receipts and original risk windows retain
their existing rules. Sale-receipt family 10 and every financial/execution/outbox identity remain.
Proposed maintenance 12 and replay 28 identify the changed optional comparison.

Records / Recommendation explains the standing order and an independently qualified routine
alternative: order price, estimated completion/time, P/L if fully sold and rounded scenario P/L.
It labels public-flow estimates unvalidated and states the unchanged-mark/partial-fill assumptions.
There are no action controls or new loss/cancellation permissions. Immutable worker results bind
item, remaining quantity, standing price and evaluation clock; current offer/account/settings/
ledger/market checks and quote/history expiry withdraw stale diagnostics. UI reads perform no
history lookup, model fitting, HTTP request or financial write. The main guidance panel does not
wait for an optional comparison or reporting view.

## Verification and evidence

Focused tests cover a misleading positive ratio with worse marked value, a supported patient
premium above the latest high trade, exact pooled subset floors, missing/conflicting/stale inputs,
large monetary values, negative half rounding, consistent quantity/horizon, expiry, estimator
failures, personal holdings and item/quantity mismatches in displayed diagnostics. Existing
patient-target, recent-fill, sale maintenance and relist tests remain required.

The reused eight-item/eight-hour development scenario matches the installed baseline in all
twelve cadence/capture-share cases. It is a regression fixture, not untouched promotion evidence.
A new sixteen-item catalogue was frozen from earlier hourly prefixes before fetching targets.
The later evaluation suffix starts after the old development window, retains the catalogue and
unknown/missing evidence, and includes missed visits, restarts, tax and remaining inventory.
Its 100M/member/eight-slot 5m/30m/2h/8h cases remain separate. Retrospective bucket-average quotes
and unknown original publication/revision clocks prevent actual-execution or profit claims.
Private inputs, source/artifact hashes and receipts are in `local-data/exit-comparison-20261026`
in the primary checkout. A Windows disposable-file replacement failure and its retry receipt
are retained; neither is a trading-policy outcome or a live account write. Exact frozen artifacts
are being checked on the existing Linux pilot under resource limits, without replacing its service.

Before delivery require the final full build/tooling and exact-head Windows/Linux CI, repeatable
whole-account comparisons, original financial/risk equality and old-runtime read compatibility.
Current #178 manual acceptance and the explicit budgeted market-informed loss/hold policy remain
separate open gates. No return improvement, predictive superiority or full Copilot parity is claimed.
