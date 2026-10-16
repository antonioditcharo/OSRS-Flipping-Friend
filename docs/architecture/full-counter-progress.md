# Full cumulative counters before a terminal state

Live observations on 15 October 2026 contain BUYING with filled quantity equal to total,
followed by BOUGHT with the same cumulative quantity and spend. The old classifier rejected the
progress frame. Its subsequent authenticated recovery correctly preserved the newly created gap,
but the first rejection was unnecessary and prevented acquisition of the proven final delta.

BUYING and SELLING with positive counters up to and including the total are nonterminal progress.
The existing BUY_PARTIAL/SELL_PARTIAL states mean observed progress awaiting a terminal state;
they do not promise that fewer than all units have executed. Raw source types remain unchanged.
Only the explicit BOUGHT/SOLD/cancellation state authorizes the terminal collection action.

The existing reducer checks identity, side, session, ordering, quantity, spend and monotone counters.
A known prior observation allows only its incremental acquisition/disposal effect. Full progress,
duplicate delivery, restart, terminal confirmation and later heartbeats cannot recount that delta.
Existing exact cost allocation and sale tax/profit ranges remain authoritative. A cumulative average
does not prove homogeneous fills, exact tax or a terminal observation time.

First-seen filled counters still require reconciliation. An EMPTY before a terminal observation
still creates a gap even after all counters filled. Overfilled, regressing, wrong-side or unpriced
observations still fail closed. No new enum, wire version, source normalization, timeout change,
exposure bypass or game input is introduced.

Existing historical RECONCILE records, raw events, cost coverage and risk windows remain intact.
The fix prevents future false gaps; it does not replay old events into new financial effects or
repair live databases. In particular, the two live gaps already observed in the owner account still
pause their affected stock and retain conservative risk reserves. A separate auditable resolution
workflow is needed; installation alone cannot be claimed to restore their cost basis or buy budget.

Regression gate: the tests in FullCounterProgressTest cover acquisition delta/cost, nonterminal
actions, duplicate delivery, restart, sale cost/tax/profit conservation, missing terminals,
first-seen filled baselines, contradictory counters and unchanged old gap/source records. Tests
operate exclusively on disposable stores. The classifier contract tests cover full progress on
both sides without weakening contradictory side, incomplete terminal or range validation.
