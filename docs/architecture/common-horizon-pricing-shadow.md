# Common-horizon entry and exit challenger

This is offline construction of the approved following pricing release. Runtime constructors keep
the verified baseline. There is no HTTP/configuration switch for this challenger, no new loss
authority, model promotion or claim of measured GP/hour improvement.

## Shared calculations and final authority

`AccountHorizonPricing` reuses `AccountPriceScenarios` and its independent quantity-specific
completion/partial-fill paths, exact tax and conservative remaining-stock mark. Entry and exit
comparisons share min(48h, 2 x leg horizon + cadence/30 hours) at exactly 5m/30m/2h/8h. Unsupported
cadences/evidence retain the verified price. Strict improvement in expected terminal value replaces
the requirement to improve every individual completion/time metric; ties retain the current price.
Acquisition allocation, original target protection and exact non-loss bounds remain hard limits.

The shared sell engine enables this only through an explicit deterministic offline constructor.
Replay selects the same policy for proposals and final companion sale authority, including price
corrections, exact quantities, current acquisition sources and inventory. Live service entry points
use the default constructor; asynchronous/live requests cannot select the shadow policy. Shadow
decisions identify the distinct pricing policy. Financial receipt family 10, source acceptance,
original stop rules and durable risk windows remain unchanged.

Working-sale comparisons include the actual standing order over the same elapsed horizon. They
may preserve it after the existing patience/progress/completed-market checks. A cancelled sale
cannot be held: relist evaluation skips that standing-offer branch, retaining exact returned
remainders, accepted reductions, cost floors and current source checks. Unknown personal-item
costs never become known merely to enter the comparison. Stops and wind-down retain existing rules.

## Whole-account measurement and limitations

Replay 29 still uses one bankroll/eight physical slots, actual visit/action delays, shared buy
limits, partial fills, tax, cancellation/collection and conservative terminal stock. Static
comparisons credit zero replacement profits. Replay may reuse capital and slots only after the
simulated collection/placement actually makes them available; future profit is never guaranteed.
Public completion scenarios retain their unvalidated independence/tail/partial-throughput and
queue assumptions. Simulation remains distinct from real execution evidence.

Pricing qualification V3 binds `ACCOUNT_HORIZON_PRICING_SHADOW_V1` and the exact evaluator artifact.
Retained V2 cohorts/evaluators stay unchanged; use their retained artifact, never relabel their
old results as this exit policy. The chronological 60/20/20 slices, tuned-input exclusions,
calibration/coverage checks, three capture-share stresses, original loss bound and unopened-test
rules stay in force. Report each cadence separately at 100M/member/eight slots. Failed or
unsupported segments cannot activate the challenger or suppress baseline guidance.

The active hosted capture retains its original 2026-11-08T01:31:16Z stop and $50/month ceiling.
Do not tune on its growing tail or open the final held-out fold. Learning construction remains
behind delivery, native latency and prospective-source gates; promotion is separate.
