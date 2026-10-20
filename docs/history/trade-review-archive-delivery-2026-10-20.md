# Trade review archive delivery — 20 October 2026

The owner is away until 17:30 local and requests continued offline work, pruning unresolved trades
that cannot be saved, and stronger prevention. The implementation preserves financial history and
uses reversible review annotations. A retained earlier purchase is protected for investigation,
without claiming that it proves a complete historical cost match or recovered profit.

The batch adds scoped, idempotent, exact-source archive/restore through the native Missed flips
window; preserves unresolved coverage; keeps new failures visible; and enforces durable automatic
accounting mode inside canonical acceptance. Fourteen new behavioral tests include reproduced wrong-mode and metadata-boundary failures. [Contract](../architecture/trade-review-archive.md).

Actual source/PR/exact-head CI, artifact/backup/task checks, full-prefix/anchor preservation,
annotation results and installed-versus-queued state are recorded in ignored
`local-data/trade-review-20261020/delivery-state.json`. Read it before claiming delivery. The prior
installed runtime is PR #128 until the verified paired update succeeds. Personal source IDs,
amounts and row-level review outputs remain in local-data; no financial rows are deleted/repaired.

Login and all game actions remain manual, deferred until after 20 October 17:30 local. Controlled
full trade-loop, sustained timing, complete production replay, qualified returns and learning
promotion remain open. Source tests and read-only/annotation HTTP checks do not close those gates.
