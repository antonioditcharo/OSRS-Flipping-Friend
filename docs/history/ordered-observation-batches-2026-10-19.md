# Ordered observation and retry batch — 19 October 2026

Source/verification checkpoint. Installed runtime remains #116 until the supported paired update;
the private local-data/loopback-latency-20261019/delivery-state.json records actual CI/merge/install.
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
