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
