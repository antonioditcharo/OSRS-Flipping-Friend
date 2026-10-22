# Relist market evidence

21 October 2026. Actual source/CI/merge/installation lives in
`local-data/relist-market-evidence-20261021/delivery-state.json`; player acceptance stays separate.

An accepted profitable sale reduction could survive cancellation, late fills and collection, then
silently return to its old acquisition target when completed market buckets became unavailable.
The engine, selected-sale editor, companion authority and replay all treated a missing reduction
quote as ordinary initial-exit permission. This was not proof that the market had recovered.

Collected managed reductions now use one shared relist evaluator. The existing supported reduction
still applies when eligible. Otherwise, a fresh two-sided market may support a current managed exit
only if its ordinary non-loss price is no higher than the current sell-side trade. Missing, stale,
incomplete or contradictory evidence cannot restore a protected target above that market. The
exact saved remainder/source remains available for later revalidation; unrelated stock can proceed.
Replay excludes pending collected-sale items from generic initial exits, matching client selection.

Fresh proven stops and explicit wind-down retain their separate rules. The original maintenance
non-loss floor also applies to wind-down relists after stabilization and final authority; a proven
stop remains the separate loss exception. Costs use exact pooled subset allocation. No source or
financial row is replayed, no missing stop/plan is created, and no personal extras are enrolled.
Legacy continuations with no accepted reduction source retain their target-preserving initial-exit
behavior; initial quiet-market limits remain a separate workflow.

Paired sale policy 6, replay 15 and supported sale-maintenance evaluator 4 distinguish these semantics.
Regression coverage includes lost/partial/zero-volume buckets, stale quotes, market recovery, fresh
stops, wind-down floors, exact selected subsets, unrelated exits, restored support and restart. Full
production replay, sustained latency and player-operated cancel/late-fill/collect/relist acceptance
remain open.

A separate reproduced boundary remains: two accepted continuations for the same item can coexist,
but the first new same-item successor closes both. Accounting remains intact while the other saved
cap/source can be lost. It requires explicit successor-to-origin evidence across persistence,
companion proof and replay. Unknown new orders must keep the existing supersession guard; this batch
does not infer that association or claim to resolve that separate target-loss path.
