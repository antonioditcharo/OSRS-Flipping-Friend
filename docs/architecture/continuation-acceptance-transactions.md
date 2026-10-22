# Continuation acceptance transactions

21 October 2026. Actual build/CI/merge/installation is recorded in
`local-data/continuation-acceptance-20261021/delivery-state.json`; source is not player acceptance.

A failed initial continuation save previously left the proposed BUY/SELL hint in the retry queue.
A later unrelated callback could commit it after its instruction had been withdrawn, producing an
unshown rebuy/relist promise. A failed first sale-source upgrade could similarly attach permission
to abandon a target when later collection progress committed. The original committed file survived;
the fault was publication of prospective acceptance mutations in the in-memory observation queue.

New hints and changes to accepted sale-source/managed-cost context are now staged separately. Only
a successful forced atomic write publishes them to the accepted queue. A refused write leaves the
prior accepted contexts and dirty observed progress/removals intact. Later callbacks cannot accept
the withdrawn proposal; a newly validated client presentation can retry it explicitly. Successful
acceptance includes any already pending observations in the same document. Existing identities,
format 2, legacy reads, exact caps, changed-file refusal and account isolation remain unchanged.
No historical hint is inferred to be unaccepted or deleted by this prevention change.

BUY workflow timing now shares the original-duration qualification between the live client and
account replay. Valid callback ordering can retain a matching immutable forecast for a same-second
placement even when second-resolution learning attribution remains unqualified. Replay retains
that binding through synthetic restart and freezes the same original deadline; missing/mismatched
proof retains the one-time horizon fallback. Replay 14 / sale policy 5. No forecast multiplier,
learning promotion, financial replay or production database change is introduced.

Regressions use real disposable tracker callbacks and file failures: withdrawn initial BUY/SELL,
source upgrades, unrelated callbacks, later fills/collection, renewed acceptance, profile changes,
restart and accepted dirty-progress retry. Timing tests keep learning unqualified and exercise the
original deadline, recent-fill suppression, frozen fallback and 5m/30m/2h/8h cadences.

Fresh authenticated eight-slot and empty-outbox reads passed before this batch, with all slots
occupied. Player-operated cancel/late fills/collect/exact replacement or relist/restart acceptance,
sustained latency and complete production replay remain open. Final source, physical holdings,
exact pooled cost/tax, risk, cash, slot and limit authority still apply to every resulting order.
