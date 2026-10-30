# Prepared outbox reads after acknowledgement

The 30 October #203 native session has fresh authenticated eight-slot evidence, an empty durable
outbox and visible MAIN/editor guidance. Initial paints reach 148.129 ms and price preparation
reaches 690 ms; these failures remain. Initial/unlisted sales lack existing recommendation/relist
identities, so the timing audit cannot classify them as distinct eligible decisions. A short warm
CPU profile did not capture an active revision and cannot establish the earlier delay's cause.

## Removal-only reuse

An offer acknowledgement changes only the pending list of an already decoded, validated private
outbox. Keep that validated state after a successful removal-only write. Both individual identity
acknowledgement and complete matched-prefix acknowledgement retain the same account, session,
sequences, original retained proofs and exact financial delivery semantics.

The write still takes the cross-process lock, checks the previous complete bytes twice, forces the
temporary file and atomically replaces the destination. A failed write cannot cache a removal.
Every subsequent read still compares every current byte, so same-size/time edits, another writer,
rollback and account changes cannot reuse an incompatible image. New observations, caller-supplied
proofs and legacy upgrades continue through the original cold validation; do not generalize this
optimization to all writes. Retained unsaved appends still withhold reads and acknowledgements.

## Diagnostic sale provenance

Timing schema 3 optionally records a version-one SALE_AUTHORITY source after the existing exact
final sale response and current-source recheck succeed. A corrected order gets evidence only from
its second successful authorization. Refused/restarted authorization clears the previous diagnostic
binding. This transient field never enters serialized advice, financial events, learning evidence
or the outbox. It supplies neither order authority nor a new recommendation/financial identity.

The timing audit continues to read schema 2 unchanged. Schema 3 requires matching account, sale
action, supported provenance version and a nonempty source. It can distinguish repeated initial
sales with identical source/order from changed orders. Missing provenance, unproven COLLECTs,
absent durable frames, hidden paints and telemetry losses stay unqualified. No old trace is repaired
or retroactively counted. Foreground financial delivery never depends on diagnostic success.

## Release requirements

Exercise individual/prefix ACKs, concurrent tails, failed replacement, same-size external changes,
legacy upgrades, account switching, malformed retained proof, first bindings and late fills.
Compare cold and prepared readers on disposable copies, preserving retained source payloads and
original file bytes. Require clean Java/tooling, actual paired-JAR Java 11 checks, exact-head
Windows/Linux CI, a fresh uninterrupted synthetic hour and verified preservation before install.
Native MAIN/editor latency and player operations remain separate acceptance gates; no pricing or
learning activation follows from faster decoding or better diagnostic attribution.
