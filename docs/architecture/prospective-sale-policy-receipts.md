# Prospective sale-maintenance acceptance

22 October 2026. Actual source, verification, merge and installation are recorded in
`local-data/sale-policy-receipts-20261022/delivery-state.json`. Source is not player acceptance.

A sale reduction accepted at the five-minute cadence must not become permanently ineligible when
the player changes to eight-hour checks during collection. Conversely, today's shorter interval
cannot establish that an old cancellation qualified. Historical settings alone do not prove an
accepted instruction.

Authenticated account-scoped POST `policy/sale-maintenance` first applies final CANCEL_SELL
authority. It then transactionally issues an immutable, idempotent request receipt, recording the
exact account source row/raw hash/read-set/settings, original offer/counters/progress, proposed
price/quantity, decision time, routine patience and routine/stop/wind-down mode. GET `policy/sale`
remains read-only. Issuance failure withdraws the instruction; no accounting event is created.

The client separately commits acceptance before displaying guidance. A later actual callback or
coherent periodic capture carries its copied process/order evidence into the forced atomic outbox.
Same-second acceptance must precede that capture in the same process; another process requires a
strict later timestamp. Restored objects and ordinary copies cannot mint capture ordering. The
first binding must precede or accompany the first cancellation; a later receipt cannot repair an
already receipt-free cancellation. ACK/restart retain bindings and refusal tombstones.

Every new SELL source carries sale-policy evidence version 1 independently of its placement.
Missing receipt evidence cannot downgrade a new source to legacy authority. Unsupported versions
and contradictory bindings fail closed. Existing unmarked immutable sources retain their prior
conservative behavior; historical evidence is never backfilled.

Canonical relist authority independently proves issuance, pinned account metadata, later source
ordering, first cancellation, collection, late counters, successor/remaining origin budgets and
each parent's accepted policy. Routine acceptance freezes only its original patience. Current
two-sided prices, completed volume buckets, material reductions, exact taxed subset floors and
all potentially remaining original stops are still rechecked. An old stop or wind-down receipt
cannot authorize a current loss. Accepted reductions cannot restore an unsupported original target.
Client selection, selected-sale editor, companion and replay use the same pricing evaluation.

This pair uses sale policy 9, replay 18 and maintenance evaluator 5. Receipt/source/account evidence
is additive; financial rows, legacy identities, pooled costs and CURRENT_ACCOUNT_V3 are unchanged.
Pinned account sources survive ordinary pruning. Bounded relationship/issuance retention remains
unfinished: even a never displayed receipt pins its source, so safe reclamation needs proven
acceptance/outbox reachability. No blind TTL is introduced.

Behavioral coverage includes cadence changes, independent sibling origins, late fills, exact subset
costs, editor quantities, ACK/restart/outages, first cancellation and version fences, transactional
rollback, corrupted metadata, authentication, POST validation and read-only GET. Controlled manual
full-loop acceptance, sustained latency, complete production replay and validated learning remain
open. The old three-bludgeon plan stays unknown; this protocol does not repair historical plans.
