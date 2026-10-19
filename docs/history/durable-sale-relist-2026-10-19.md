# Cancelled-sale guidance recovery — 19 October 2026

Source increment on `codex/durable-sale-relist`; delivery evidence is recorded below as it completes.
Read HANDOFF first. Previous #111 remains installed until the supported paired update is verified.

Two tests fail on the prior source: a second already-working same-item listing erases the intent;
and collection evidence disappears after the temporary settled-slot record is removed. Both now
pass. The live callback saves the exact bounded collection receipt before pruning/deferred reads,
with the managed-cost requirement. New sale episodes end the intent; stale workers cannot recreate
one after their original episode leaves the slot. The real decision chain retains exact managed
cost/profit/target and current ledger/inventory/scope/price/final-authority guards.
The immutable relist request keeps its managed-cost requirement during concurrent intent changes;
later contradictory collection counters withdraw the retained receipt.

Private regression/build/delivery receipts: `local-data/relist-recovery-20261019/`.
Read-only pre-update capture: 62,073 source rows, 1,470 financial effects and the original risk anchor.
No database modifications, financial replay, historical gap repair or learning activation.

Windows standard clean build passed in 83s: 1,743 Java passed/six existing skips (plugin 759,
core 193, companion 797 including six skips), zero failures/errors; all class majors 55 (Java 11).
All 108 tooling tests passed in 128.448s. Exact-head Windows/Linux CI is pending at this checkpoint.
Candidate plugin: 591,766 bytes, SHA-256 ca0c49d234efc7bc39af7aef53ce711e77328b97d2522b190bd7db533af53bdb.
Candidate companion: 18,094,359 bytes, SHA-256 0381a900abec979e385e6a237200f9f6ba826d3c697b1b85664a6099bba29bf6.
Installation and fresh player acceptance are pending. Owner-authorized normal agent closure and
the supported unattended updater are the delivery path; login and every game action remain manual.

Contract: [durable relist](../architecture/durable-sale-relist.md). Whole-account production replay
still needs persisted client intent, replacement and foreground/transport coverage. Qualified
benchmarks, controlled trade-loop quality and sustained latency remain open; no parity or return claim.
