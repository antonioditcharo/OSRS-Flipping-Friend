# Supported small sell adjustment installation

25 October 2026 (America/Anchorage). [PR #173](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/173)
is installed; [contract](../architecture/supported-small-sale-adjustments.md). Root HANDOFF/TASKS
retain current direction and every open release gate.

- Tested head: `4dd7925fd1a6ab4c05fdb84da9c28d3bbc6140ae`.
- Merge: `b715e78654faa702ed1eb0020a08cf3b258a6c93`.
- Both exact-head CI platforms passed in run 40268248069.
- Clean build: 1,150 root + 970 companion + 247 core = 2,367 tests; zero failures, six existing skips.
  Tooling: 121 passed. Own Java classes target Java 11.
- Plugin SHA256: `c899f1f23b343a4c713700865d5b1b6c70459f1c2188b7ef0e88a17298beed70`.
- Companion SHA256: `8f62254987c3d112e87ebc3c22fbc02c97af733a51dae884ccde52f81acfbab8`.
- Installed at 2026-10-26 04:30:25 UTC (20:30:25 AKDT, 25 October) using the supported unattended
  paired updater. RuneLite 1.13.4 cache/API checks passed. Existing protected task/ACLs are unchanged.

## Preservation and rollback

Verified backup retaining #172 and current account data:
`C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261025-203020-b34f3bc3`.
All 12 checked original source/financial/projection tables match exactly across installation.
Disposable restoration verifies 149 files and one explicitly scoped account database; five separately
captured configuration files restore byte-for-byte on a disposable copy. Previous/current runtimes
read identical ledger profit bounds, managed stock, reconciliation scope, continuation dispositions,
bounded exposure and the present original risk window. Runtime rollback preserves subsequent trades;
restoring an older database remains a separate disaster-recovery operation.

No source replay, financial repair, stock adoption, original-plan rewrite or risk reset occurred.
Both desktop-control initialization paths still fail with os error 3, so the owner closed RuneLite
normally. The agent installed, verified and reopened it; all login and in-game actions stayed manual.

## Fresh observations and limits

After the owner opened the GE, all eight distinct authenticated slot sources precede the acknowledged
account frame and match current projections. Frame age was six seconds; pending financial events
were zero. Original immutable prefixes match the pre-update backup. GET-only pipeline passes:
631 recorded sale updates, 41 unresolved records and seven profit ranges retained. All slots were
occupied and WAIT guidance was logged. These historical counts are not new session profit.

The subsequent copied seven sells had no qualified routine change at that capture: four stood at
their exact non-loss floors; others lacked fresh buyer-paid trades or consecutive completed support.
Marlin had already been relisted at its exact floor before this update, so it cannot accept the
new small-adjustment path. The earlier frozen component changes its old STALE_PRICES result to a
supported 3,548 GP limit with unchanged original cost, quantity, target and stop; that frozen result
is not current order authority, placement or controlled player acceptance.

The initial client delivery took about 4.4 seconds with a five-second canonical retry; sampled later
refreshes were about 1.1–2 seconds. Render p95, sustained latency and complete player-operated
small reduction/cancel/late-fill/collect/relist/completion/restart remain open. Public-price forecasts
remain unvalidated; the newly requested market-informed loss/hold policy is separately pending.
Hosted shadow stays on; hosted advice, coordination and learning promotion remain off.

Private receipts: `local-data/hosted-pilot/walkdown-guidance-delivery.json`, associated installed/
restoration/rollback/status/launcher/pipeline/live-frame files, and the fresh/frozen sale diagnostics.
Older receipt: [#172 checkpoint](handoff-pilot-through-pr172.md).
