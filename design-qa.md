# Native account panel design QA

19 October 2026. final result: passed

## Visual truth and normalization

- Owner source: C:/Users/Antonio/AppData/Local/Temp/codex-clipboard-a1f96ed7-a22a-4f36-a3a8-13e594803b1a.png
  (276×1003 pixels), privately retained as local-data/panel-parity-20261019/reference.png.
- Compare the leftmost 239×1003 panel pixels, excluding the unrelated right RuneLite toolbar.
- Production Swing/RuneLiteLAF: build/ui/sidebar-parity-1003.png (239×1003, 1:1), retained as
  local-data/panel-parity-20261019/qa/friend-panel.png.
- Combined full comparison opened: local-data/panel-parity-20261019/qa/comparison-final.png.
- Combined focused action/controls/statistics comparison opened: qa/comparison-final-top.png in
  that directory. Short viewport opened: build/ui/sidebar-parity-499.png (239×499).
- Same dark native theme, Buy action, 5m/High controls, Session and five sale rows. Different item,
  quantities/prices and account coverage are synthetic fixtures. No CSS/browser/device scaling.
  Source and implementation were viewed together; the focused comparison makes small text legible.

## Comparison history and fixes

1. Initial render put paging directly after sales, leaving the footer midway down a tall panel [P2],
   and verbose estimate text wrapped unnecessarily [P2]. Initial capture is retained privately at
   local-data/panel-parity-20261019/qa/before-footer.png and earlier build captures.
2. Paging/investigation/funding now occupy the bottom region; short content scrolls. The concise
   estimate stays explicitly estimated; redundant action caption removed. High selection has the
   reference's red state; positive ROI/rate and elapsed-clock colors are present. A new render at
   the actual source height and combined full/focused comparisons above verify the fixes.
3. Real control tests verify scope/reset/paging, account exit, unknown money/unpriced stock and
   component heights. Previous long-text/compact/native regressions pass. No actionable P0/P1/P2
   visual defect remains within the supported current-account increment.

## Required fidelity surfaces

- Fonts/typography: actual RuneLite regular/small/bold fonts, readable statistics and amount hierarchy.
  Exact headline money/bounds wrap; they are not rounded into apparently exact abbreviated totals.
- Spacing/layout: narrow action, five shortcuts, cadence/risk, scope/reset, statistics and sale rows.
  Footer stays visible; smaller viewports scroll. Friend's funding/free-slot line is an intentional
  extra, describing the actual planning budget.
- Colors/tokens: existing RuneLite dark palette; yellow price/cadence, red High, green positive money,
  warm elapsed time. Friend keeps its own branding and Records/header control.
- Image quality/assets: runtime ItemManager supplies real item sprites. The fixture uses RuneLite's
  authentic Nature rune image. Icons are licensed Google Material PNGs. No Copilot logo is recreated.
- Copy/content: every requested current-account metric category is present. Exact money, unknowns,
  grouping, applied-cost ROI, conservative portfolio and elapsed clock are documented in
  docs/architecture/panel-performance-and-latency.md. Estimates differ from recorded money.

## Intentional product boundaries and remaining acceptance

Current account is explicit; cross-account catalog/aggregation remains an open feature gap.
Copilot's paid-service identity/community/login controls are not simulated. Friend's independent
clock includes elapsed/offline time; measurement resets preserve risk/history. These are documented
product/evidence constraints, not a complete-parity claim.

Native component/render/control QA passes. Installed player acceptance, actual live latency and
controlled trading outcomes remain separately pending in HANDOFF/TASKS. No game was automated.
Earlier graph QA: docs/history/market-graph-design-qa-2026-10-18.md.
