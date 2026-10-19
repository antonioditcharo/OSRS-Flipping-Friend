# Panel information and guidance latency delivery

19 October 2026 (America/Anchorage). Owner's current screenshot/request is authoritative; no
Copilot private backend, crowd transactions or new game automation is involved.

## Implemented source

Native sidebar: estimated trade profit/time, holdings shortcut, session/period selector/reset,
durable realized bounds, conservative unrealized/portfolio marks, completed cost-matched sell-order
count, applied-cost ROI, elapsed session clock, known realized GP/hour and paged source-offer sales.
Records/settings remain native maximized investigation windows. Current account and unknowns are
explicit. New independent measurement/session routes preserve original financial/risk anchors.
The visible panel refreshes its clock/records independently of trade calculations. Pagination and
investigation stay at the bottom; short panels scroll without hiding the footer.

The five-second minimum plan spacing is replaced by revision/validity reuse and bounded immediate
plan completion. Client refresh bursts coalesce with latest-state/lifecycle fences. Validated
canonical prefixes and scoped economic prefixes stay in RAM, with external-change/rollback/tail
invalidation; live sources/cash/risk/limits remain fresh. History-derived candidate analysis uses
exact value keys; warm/cold policy output must agree. Delivered-stage timings appear in RuneLite
logs. Full contract: [panel/performance/latency](../architecture/panel-performance-and-latency.md).

## Verification and retained evidence

- New behavioral tests cover full sale aggregation beyond 100 rows, unknowns/ranges/denominators,
  partial/completed counts, durable idempotent session resets/restart, authenticated API isolation,
  external source deletion/corruption, rollback and new tails, current-source query budgets,
  warm/cold policy equivalence and coalesced latest cash captures.
- Native production Swing renders and real control tests cover scope/reset/paging/logout/unknown
  stock, long/short panels and source comparison. Root design-qa.md records visual evidence; earlier
  graph QA is preserved in market-graph-design-qa-2026-10-18.md.
- Actual loopback API fixture walks twelve coherent eight-slot frames through open/partial/full
  buy accounting, an immediate plan read and final PLACE_BUY. Earlier focused run: 422ms cold,
  61–104ms subsequent passes. Synthetic data, no game/client UI and no account-return claim.
- Initial fixed retained account comparison preserves the exact ledger and allocations. Both sides
  have eight occupied slots. Median repeated ledger/exposure/risk/plan time improves approximately
  1.17s -> 0.13s, with 95th percentile about 1.43s -> 0.17s. This is an isolated read-only probe,
  not an eligible live buy or installed UI latency. Final fixed-input receipts bind artifacts below.
- Tooling: 108 Python tests pass in 158.931s. Final standard Windows build, exact-head Windows/Linux
  CI, JAR identity and supported runtime delivery are recorded in the current handoff checkpoint.

Private receipts/logs/images/probes live in local-data/panel-parity-20261019 and local-data/panel-*.log;
older exact source/clock capture is stock-valuation-20261018/quiet-live-comparison. No row-level
account evidence is published. The probe uses a copied account read-only, fixed market clock and
completed public history only; no positions, slots or cash are manufactured in that comparison.

## Delivery / acceptance boundary

Source implementation and offline verification do not imply installation or player acceptance.
Installed source, backup/pair/task/financial preservation/status/compatibility and scoped reads must
be recorded after the supported updater. RuneLite closure/login/GE actions remain manual.

Player steps after delivery:
1. Log in/open GE; compare action/estimated profit/time, cadence/risk and account statistics.
2. Select session/history/rolling periods; use sales paging and a row's graph. Reset session once
   if desired: lifetime history and risk must remain intact, clock/prospective realized window restart.
3. Manually place/collect a suitable suggested offer and observe its successor. Agent reads delivered
   timings, outbox/slot/financial proof directly. Partial/cancel/relist/restart still need controlled
   player outcomes; no automatic game input or forced closure is permitted.

Deferred: cross-account catalog/aggregation, active-flipping-only clock, complete cadence/attendance/
terminal-adjusted benchmark measurement and other open TASKS parity/learning gates. Current-account
wall-clock realized rates are not full account returns. Runtime learning remains HOLD. No full
Copilot parity, private model reconstruction, validated bands or profit-superiority claim.

## Exact source verification checkpoint

PR #103 is merged as dd4926971fc7150cd2292b8a1d5686b94ad9752d. Both exact-head checks pass for
ff09c267bad383018c3e1809ec6860d5b90d922a, run 39223299513 (Ubuntu 08:33:07Z,
Windows 08:34:55Z). Standard clean Windows build: 126s / 18 tasks, 1,695 Java passed/six existing
skips, project classes Java 11; 108 Python passed in 158.931s. Native QA/control checks pass.

Verified artifacts: plugin 583,935 bytes, SHA256
b4fc5091226c95abbd558af7569c5584512a818a50718e4b2ea18ffd7970a871;
companion 18,078,136 bytes, SHA256
760cdf8c1c95171df4b65a1cdad9a1221b6b7477a9411134fd7c34dbcc6b4b13.

Final fixed-bar/source-clock probe (21 reads; first cold read excluded from repeated statistics):
median 1,470.671ms -> 121.425ms; p95 1,631.560ms -> 165.054ms, identical full ledger/allocations,
1,414 unchanged financial effects. Both plans are NO_FREE_SLOT; no final eligible buy is claimed.
The final loopback fixture returns final PLACE_BUY for all twelve frames: 201ms cold, then 65–93ms.
This is synthetic handler/decision evidence, not installed UI or measured whole-account returns.
A disposable SQLite 3.45.3 WAL probe also verifies snapshot-stable data_version during an external
commit and changed version on the next transaction (2 / 2 / 3), supporting coherent invalidation.

Private reproducible manifests: build-verification.json, ci-exact-head.json,
latency-artifact-manifest.json, latency-summary.json, loopback-latency.json and
sqlite-snapshot-version.log under local-data/panel-parity-20261019. The frozen database/market remain
hash-identical to their source capture; no source clock, cash or slots were changed.

Installed through the supported paired updater at 2026-10-19T09:17:24.8646686Z after the player
closed RuneLite normally. VERIFIED backup 20261019-011720-86754cee; both installed hashes match
the artifacts above. Lifecycle remains 1; protected task XML and SDDL are byte-identical. Read-only
comparison preserves all 59,535 canonical rows, 1,468 financial effects and the original loss anchor
with no new effects. Status reports PAIR VERIFIED / COMPANION RESPONDING, 1.13.2 compatibility
passes, scoped GET pipeline passes (85 sale updates/22 unresolved/one range). The logged-out
account correctly waits for current cash/slots. Supported Start reopens RuneLite and side-loads
the installed JAR; no game input. Fresh login/GE, UI and sustained delivery acceptance remain
pending. New sale-price-quality work is recorded separately in patient-sale-delivery-2026-10-19.md.

Fresh logged-in scoped reads subsequently pass: eight acknowledged account read-set IDs resolve to
authenticated durable slot observations at the capture clock, and Performance is bound to that
same account. New runtime timing evidence is from the actual redirected RuneLite process's Client
thread, excluding test-worker logs: twelve BUY deliveries median 1,038.5ms, maximum/p95 5,186ms
(first delivery: 4,154ms capture/durable publication); 62 WAIT deliveries median 606.5ms,
p95 963ms/max 1,436ms. These are refresh-request to client delivery, not exact game-event to frame
latency or placed-offer outcomes. Current explicit sell-only mode returns SELL_ONLY_MODE with five
free slots; its refusal is correct. Panel/reset/player and sustained/transition timing remain open.
Private source/timing capture: sale-patience-20261019/installed-103-read.json and timing-summary.
