# Accepted BUY plans independent of learning

An unchanged displayed BUY retains its original learning-attribution clock. Reaccepting that BUY
after ten minutes must not qualify a new learning sample, but it can provide a fresh prospective
acquisition plan. Previously the client looked up both through the expired learning entry, dropping
the target, stop and sell duration even when the player followed freshly accepted exact advice.

The current accepted BUY display has a separate immutable acceptance ID, acceptance time and
client-thread order. Its versioned proof contains the exact account/item/price/quantity, original
target/stop/sell duration and optional exact BUY forecast. Only acceptance preceding the copied
first zero-fill/zero-spend BUYING callback can bind. Publication can cross a second boundary;
later advice, first observations containing executions and mismatched inputs cannot qualify.

The account outbox commits the binding with the first source, including an explicit unknown receipt.
ACK, refresh, restart and collection do not remove it. One acceptance cannot bind another episode;
changed order inputs permanently withdraw the binding for that episode, even if changed back.
Failed writes retain the exact source and binding behind the existing durable-delivery barrier.
Known legacy episodes keep their original legacy evidence across upgrades. Versioned unknown or
malformed evidence cannot fall back to legacy authority or adopt later advice.

Canonical projection independently checks the earliest retained source and every inherited proof.
Acquired quantities retain their proven original context through partial fills and exact pooled
accounting; missing original sources remain unknown. No financial event is replayed or rewritten.
Original BUY maintenance timing may read the optional immutable forecast independently of learning;
without it the existing frozen horizon fallback applies. Independently qualified original learning
predictions retain their existing timing even when no exit plan is known; that gives no exit authority.
Workflow proof cannot qualify execution learning. Replay shares these rules; paired sale policy 8 /
replay 17 / maintenance evaluator 4.

Behavioral regressions cover actual client capture, durable retry/ACK/restart, legacy migration,
ordering, source refusal, canonical acquisition/partial disposal and replay. Source verification and
installation do not establish player-operated full-loop acceptance or validated forecast accuracy.
Actual delivery: `local-data/accepted-buy-plans-20261022/delivery-state.json`.
