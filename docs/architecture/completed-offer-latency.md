# Completed-offer acceptance latency

19 October 2026. This changes computation during prospective canonical offer acceptance. It does
not change the player workflow, execution identity, accounting, strategy or learning promotion.
Read HANDOFF for tested source, installed pair and fresh acceptance state.

Completing an advised offer previously selected and decoded every retained canonical source again
inside `executionPrediction`, even when the preceding accounting/ledger read had already validated
that same source prefix. With a long account history this repeated work delays the durable ACK,
and consequently the next collectible/sale/entry instruction. A 30,000-event fixture reproduces the
unbounded SQL rescan before the change using an actual SQLite instruction budget.

Qualification now reuses the existing immutable account prefix and validates its indexed tail
inside the same acceptance transaction. It still examines the earliest account/session/slot/offer
source, exact order, original prediction reference, authenticated zero-fill start, prior advice
time, intervening comparability and ordering. It uses the original prediction even if the terminal
payload claims a newer one. A cold prefix is fully validated; no unverified cache or historical
execution aggregate supplies authority. Malformed/foreign/incomplete sources leave the prediction
unknown. Fully validating the shared prefix can also conservatively exclude a prediction when an
unrelated older canonical source is malformed or foreign; real accepted fills still follow the
existing financial rules.

Existing `data_version`/account invalidation observes external commits. New sources must pass tail
validation. Rollback discards the entire cached prefix and economic scope, so failed source/sample
claims cannot poison a retry. Canonical source, projection, acquisition/disposal, tax, limits,
legacy counted-offer key and sample still commit or roll back together. No schema, index, financial
row, wire identity, original advice, old deduplication key or historical sample is rewritten.

Tests cover the warm 30,000-event query budget and exact quantity/cost/original context, external
original-source deletion, unrelated corruption, malformed new tails, failed learning writes,
retry and lost-ACK duplicates. Existing cold/restart/source mismatch, censored cancellation,
recovery and atomicity tests remain required. The small synthetic benchmark measures terminal
acceptance only; it is not event-to-frame latency, initial cold-publication speed, an in-game trade,
measured return or validated learning. Runtime remains PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION.

After supported paired delivery, fresh manual login/GE observations and a player-operated
partial/full buy or sale are still required to measure source-to-ACK-to-instruction transitions.
The agent collects logs/scoped reads; all game actions remain manual. Historical gaps, affected
stock, unknown costs/profit and original risk anchors remain intact.
