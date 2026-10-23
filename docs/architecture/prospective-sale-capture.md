# Durable prospective sale acceptance at capture

23 October 2026. Actual verification, merge and installation:
`local-data/prospective-sale-acceptance-20261023/delivery-state.json`. PR #149 remains installed;
#150 and this work await one owner-deferred paired update.

A continuation file can become unreadable after valid maintenance acceptance, before the first
cancellation callback. Previously that economic observation was retained without its receipt,
permanently losing relist-policy context. Dirty-fact-save recovery alone did not cover this case.

Final client-thread presentation now also commits the verified workflow acceptance into the existing
account outbox. Failure withdraws guidance. New proof format 2 requires independent capture retention;
its marker cannot be omitted or downgraded within that format. Legacy format 1 remains supported and
never gains a default marker when serialized. Existing financial/source payloads are not rewritten.

The copied callback supplies its original CaptureOrder directly to the local outbox transaction.
The outbox binds its retained acceptance before or with the first cancellation, without reading a
cached continuation document or manufacturing a newer observation stamp. The same transaction writes
economic counters, the original event identity and first binding. ACK/restart and late fills reuse
that immutable binding. Continuation reads still fail closed for guidance when unavailable.

Retention is account/session/slot/episode/first-seen scoped. Original source sequences must already
exist in the outbox timeline. Bound acceptances cannot change; refused first cancellations cannot be
retrofitted. Repeated validation of a price retains the workflow's original acceptance. Pending
acceptance candidates do not survive failed writes as executable authority. Consumed prospective
metadata is removed only after the equivalent first binding is durable; no TTL discards live proof.

Canonical issuance/raw hashes/read sets, exact costs, counter continuity, current pricing/stops and
final sale authority still apply. No financial replay, historical repair, risk reset or learning
promotion is introduced. Sale policy 9 / replay 18 / evaluator 5 remain unchanged.

Regressions cover real callback/read-outage/ACK/restart/late-fill capture; failed staging; same-second
ordering; foreign/corrupt metadata; repeated prices and immutable bindings; legacy compatibility;
final presentation withdrawal; and the client-to-canonical exact-cost relist workflow. Controlled
player cancellation/collection/relist/restart and sustained latency remain pending.
