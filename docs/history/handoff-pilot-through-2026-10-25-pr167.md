# Hosted pilot delivery through PR #167

Historical checkpoint, 25 October 2026. Read current HANDOFF/TASKS and the latest runtime receipt
before treating any earlier source, installation or acceptance observation as current.

- PR #163 introduced the private hosted pilot, shared core, scoped replication, background recovery,
  flexible preferences, rounded estimates, writer fencing and fair allocation. Its staging server,
  restoration, capacity and deployment receipts are in `local-data/hosted-pilot/delivery-state.json`.
- PR #164 fixed the real RuneLite Gson 2.8.5 history linkage fault and repeated prospective risk reads.
  Fresh 1,024-row history snapshots were acknowledged; BUY, maintenance, COLLECT and CANCEL deliveries
  were logged. See `compatibility-delivery.json` and the
  [compatibility contract](../architecture/pilot-client-compatibility.md).
- PR #165 removed quadratic sale-scope copies and unnecessary acquisition/gap history scans. Clean
  Java/tooling, exact-head Windows/Linux CI, exact 12-table preservation, 51-file restoration and
  #164 rollback reads passed. See `guidance-hot-path-delivery.json`.
- PR #167 reused unchanged historical funding, cleaned up failed HTTP transports and split client
  publication timing. Clean Java/tooling, exact-head Windows/Linux CI, exact 12-table preservation,
  57-file restoration and #165/current rollback reads passed. BUY/SELL/COLLECT deliveries and a fresh
  eight-slot ACK were recorded. Some HTTP stalls remained. See `ack-latency-delivery.json` and the
  [latency contract](../architecture/acknowledged-guidance-latency.md).
- The first #167 disposable restore exhausted local disk space. Its original backup and installed
  financial rows remained intact; the failed partial copy was retained, space recovered through
  non-destructive compression, and a fresh restoration passed. Follow-on #168 adds a pre-copy guard.
- Encrypted off-server restoration verified 144,227 replicated evidence rows. This is separate from
  full local configuration/ledger restoration or trading acceptance.

Hosted advice, coordination and learning stayed off through these checkpoints. Sustained foreground
latency, controlled player trading/restart, hosted activation and all four chronological return
benchmarks retained separate gates. Original source/financial rows, risk anchors and working offers
remained authoritative. Private row-level receipts and credentials stay outside Git.
