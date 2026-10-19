# Cancelled-sale guidance recovery — 19 October 2026

PR #114 source 857f36e57c0403c0e8e9969b0b70dfe84e3514e5 merged as
61bf57e6ac862b1936fd4252aee44c9db1b42c11 at 2026-10-19T21:05:55Z; clean primary fast-forwarded.
The supported paired update is INSTALLED, including prior #111. Read HANDOFF first.

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
All 108 tooling tests passed in 128.448s. Both exact-head checks passed before merge:
run 39324055799, Linux 118305192070 at 21:02:59Z and Windows 118305192301 at 21:05:21Z.
Individual check-run API receipts verify the full source SHA, completed state and success.
Installed plugin: 591,766 bytes, SHA-256 ca0c49d234efc7bc39af7aef53ce711e77328b97d2522b190bd7db533af53bdb.
Installed companion: 18,094,359 bytes, SHA-256 0381a900abec979e385e6a237200f9f6ba826d3c697b1b85664a6099bba29bf6.

With explicit owner authorization, the agent used the normal native window Close control and
verified RuneLite's process exited before updating. No forced termination or game input.
tools/apply-update.ps1 -Unattended -SkipBuild installed the source-identical verified artifacts at
2026-10-19T21:06:37.6460622Z. VERIFIED backup 20261019-130634-180fa0d6; installed hashes and lifecycle=1
pass. Protected task XML/SDDL are unchanged. Read-only verification retains all 62,073 source rows,
1,470 financial effects and the sole original risk anchor, with zero new financial effects.
Status is PAIR VERIFIED / COMPANION RESPONDING; RuneLite 1.13.2 compatibility and scoped GET-only
pipeline pass (87 sale updates/23 unresolved/one profit range). Current WAIT/UNAVAILABLE needs a
fresh client cash/eight-slot capture; no new snapshots/offers/trades were posted by the check.
Supported Start reopened RuneLite at 13:07 AKDT. Fresh player acceptance is pending; login and
every game action remain manual. Availability is retained in the private delivery-state receipt.
The new client log confirms the installed plugin side-load and completed client initialization.
This establishes loading, not logged-in UI/trading acceptance or sustained event-to-advice timing.

Contract: [durable relist](../architecture/durable-sale-relist.md). Whole-account production replay
still needs persisted client intent, replacement and foreground/transport coverage. Qualified
benchmarks, controlled trade-loop quality and sustained latency remain open; no parity or return claim.
