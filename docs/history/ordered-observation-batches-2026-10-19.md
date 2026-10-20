# Ordered observation and retry batch — 19 October 2026

PR #119 source 2fc548d29a267ffbb5ecd31cf5d376857fdaf686 merged as
65a45dd797432ca49277563c4a294934c8d05e4a at 2026-10-20T03:38:37Z after both exact-head checks.
The private local-data/loopback-latency-20261019/delivery-state.json records current acceptance.
The [contract](../architecture/ordered-observation-batches.md) describes source semantics and gates.

Two failing regressions reproduce dormant retries: a later queue tail and a replay request arriving
at completion. Both are fixed. A copied eight-slot frame and its verified acknowledgement prefix
now require two local outbox replacements instead of sixteen, while every source is still sent
and acknowledged individually in order. Original keys, predictions and exit context remain intact.

Sixteen new tests cover batching/provenance, invalid factories/duplicate slots, account switches,
failed enqueue/ACK replacement, partial/bad/lost ACKs, restart with identical IDs, concurrent appends,
retry completion/backoff, pooled/closed HTTP and actual eight-offer-before-account publication.
The real loopback third-ACK failure retains six events and publishes no account snapshot.
Successful HTTP connection reuse was already working and was retained after a controlled probe.

Final standard Windows clean build: 91s, 1,784 Java passed/six existing skips, zero errors/failures;
Java 11 bytecode. All 108 tooling tests pass in 127.483s. The first full build caught one old
source-shape assertion for the extracted EMPTY factory; it now follows both callback and copied
frame use, with actual typed EMPTY HTTP publication verified by the behavioral test.

No financial rows, old event IDs, unknown money, risk reserves or original anchors are rewritten.
The earlier replay v8 evaluator is included in the new package; it is not a benchmark or promoted
model. Runtime learning remains PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION. Fresh installed
login/eight-slot/ACK and controlled manual trade-loop/sustained timing remain separate gates.

Both platform checks passed: run 39364948266, Linux 118439534989 at 03:36:43Z and Windows
118439534698 at 03:37:53Z. Primary clean-fast-forwarded; runtime source matches the verified head.
Installed at 2026-10-20T03:40:04.2414847Z (19 October, 19:40 AKDT), VERIFIED backup
20261019-194000-1367235d. Plugin 606,984 bytes / SHA-256
6b8e68fbe3067e25bcf08b8ad6688406c6d9859aed67890188bd894169aa51a0; companion 18,120,947 bytes /
19cf0d5835d54eca76e57b2e23015e01e1d979aaa57f574a800b93d8729d5129.
The agent used normal titlebar Close and its ordinary Exit confirmation, verified process exit,
then ran the supported unattended paired update once. Task XML/SDDL and lifecycle 1 are unchanged.
Frozen 65,712 source rows/1,485 financial effects/original risk anchor are preserved with zero new
financial effects. Status, 1.13.2 compatibility and scoped GET-only ledger checks pass (88 sale
updates/23 unresolved/one range). WAIT correctly requires fresh client cash/eight slots after restart.
Supported Start reopened RuneLite and its log confirms the installed plugin and completed initialization.

Pre-update two-minute window: 21 WAIT cards, median 916ms/p95 1,275ms/max 1,636ms; one COLLECT
567ms. This is refresh-request-to-delivery, not exact game-event/frame latency or a controlled speed
claim. Post-update manual login and timing are recorded privately; local work counts alone cannot
close sustained latency or complete trade-loop acceptance.
