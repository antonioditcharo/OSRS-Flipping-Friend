# Pilot checkpoint through PR #172

25 October 2026 (America/Anchorage). Current instructions remain in root HANDOFF/TASKS.

PR [#172](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/172) was installed at
2026-10-26 00:16 UTC, tested source `6643a8b75cfc09e524e7a202e45fb76771c9daec`, merged
`0c13c4fbce7b120348ee6a215fd0569228e1ddf9`. It finishes accepted routine collected-sale reductions
at a saved non-loss limit with fresh actual feed clocks; immutable market maps avoid snapshot
flicker. Validated funding-tail reuse and a sparse sale-source index reduce repeated historical
work without changing financial/source/risk evidence. [Contract](../architecture/collected-relist-foreground-recovery.md).

Clean Java: 2,360 tests, no failures, six existing skips; tooling: 121 passed; Java 11; exact-head
Windows/Linux CI run 40246984309 both success. Plugin SHA256
`4a498e53acbf17e0da883289fd66e0e153f4f7c3aca0dc65f514b64cb055540d`, companion
`94c1c245704b4dbf6cc8b506423b26f7008dd2a0e6d7e58176202a44ca47c638`.

Supported unattended paired installation saved #170 and current accounts in
`C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261025-161603-9badb344`.
All 12 checked original financial/source/projection tables match exactly; 109-file disposable
restoration and five separately preserved configuration files verify. Old #170/current reads
match ledger profit bounds, managed positions, reconciliation scope, continuation dispositions,
bounded exposure and the present original risk window. Runtime rollback preserves newer trades.
No source replay, database repair, reset or forced client termination occurred.

After manual login, a fresh durable ordered eight-slot frame matches all eight projections,
pending delivery is zero and original immutable prefixes match the backup. Logged COLLECT/BUY/WAIT
guidance is delivered; a GET-only pipeline passes. Several hours of subsequent trading logs and
owner feedback say the workflow is working normally, while long-held sell maintenance remains
under investigation. This is not controlled full-loop/restart or sustained-latency acceptance.

Cold guidance still took about seven seconds; most sampled subsequent deliveries were roughly
0.7–1.6 seconds, with intermittent five-second financial ACK retries. UI rendering and sustained
p95 targets remain unmeasured. One sampled 15-second CPU window is not a controlled slowdown
comparison. A startup ConcurrentModificationException belongs to Flipping Utilities' Wiki fetch
stack, not Flipping Friend; no unrelated plugin was disabled or modified.

Private receipt: `local-data/hosted-pilot/collected-guidance-delivery.json`; live frame/pipeline,
restoration/rollback, original-row proof, CPU and frozen component diagnostics remain alongside it.
The frozen accepted-relay component changes STALE_PRICES to an exact non-loss ELIGIBLE quote;
it is not placement authority. Historical 41 unresolved rows remain separately visible/scoped.
Private hosted shadow remains on; hosted recommendations, coordination and learning are off.

Prior #170 receipt: `steady-guidance-delivery.json`; #168 history:
[foreground checkpoint](handoff-foreground-through-pr168.md). #170 owner feedback reported long
rechecking/account waits and periodic slowdown, motivating #172. Earlier installed/merged labels
must never be used as evidence that an unverified later artifact is active.
