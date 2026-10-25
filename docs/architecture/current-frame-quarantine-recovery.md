# Startup EMPTY prevention and current-frame quarantine recovery

24 October 2026. Follow-on acceptance work for the ordered-frame incident; delivery remains in
`local-data/ordered-frame-recovery-20261024/delivery-state.json`.

Fresh login exposed default EMPTY callbacks after the first logged-in server tick. The old guard
only rejected them before that tick. It could clear persisted nonterminal offers, then classify
their returning identities as reused. The callback guard now always requires a nonempty predecessor
in this login, including the restore pass. Genuine terminal/nonempty followed by EMPTY still flows.
Periodic captures still require copied raw slots to agree with the processed tracker.

A current ERROR projection cannot permanently hide independently proven current cash/slots.
Inside the normal account-capture transaction, `CurrentFrameBaseline` may restore current slot
metadata from the exact acknowledged eight-source read set. Every source must be authenticated,
valid, current, in one durable account/session, equal to its slot's retained sequence/time/source,
and agree with cash funding, slot counts and physical-quantity validation. A partial, stale, foreign,
superseded, malformed or contradictory frame restores nothing. The transaction rolls back together.

This is current-state recovery only. It writes no acquisition/disposal, cost, profit, buy-limit fill
or learning sample. All original gaps, source identities, financial rows, risk anchors and episode
reuse guards remain. Later increments in a quarantined reused episode stay unresolved rather than
being counted against an old episode. A current baseline never supplies missed offline executions.
Source-bound execution recovery for the affected newly interrupted episodes remains separate work;
their recorded terminal observations and unknown money stay available in Missed flips.

Under the owner's CURRENT_ACCOUNT_V3 policy, an affected current sell may coexist with unrelated
verified-cash entries only when aggregate remaining listings fit a known canonical acquisition pool
and complete prospective source evidence bounds at least that pool's full cost. The original full
reserve and item entry pause remain. This does not adopt, value, dispose or recommend selling any
units. Unknown scope, unsupported/excessive sells, unresolved active buys, malformed cost, funding,
cash/slot/limit and confirmed-loss guards still block. No financial gap is cleared to permit advice.

Canonical offer/account writes use the existing five-second account transport budget, separately
from the 750ms quote/action budget. A committed cold accounting scan must not time out every new
frame and prevent its cash publication. Original IDs, acknowledgement validation and retry backoff
remain; timeouts cannot make source proof fresh. Publication failures now have a concise log reason.

Tests cover after-tick EMPTY callbacks, genuine collections, complete versus partial/foreign source
frames, transactional rollback, no economic/learning replay, retained item pauses/full cost bounds,
unrelated final PLACE_BUY authority, continued quarantine of future counters and cold write ACKs.
Fresh installed login/GE acceptance, visible advice and sustained timing are separate delivery gates.
