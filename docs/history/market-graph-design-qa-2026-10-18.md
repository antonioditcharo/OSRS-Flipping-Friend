# Market graph design QA

Reference: retained user screenshot `local-data/market-graph-20261018/reference.png`.
Prior sidebar receipt is preserved in [native-sidebar-design-qa-2026-10-14.md](docs/history/native-sidebar-design-qa-2026-10-14.md). Native window chrome is 26px; compare its 1905x1000 content with the actual Swing render, not a website prototype.

## Pass 1 — blocked

Source and `local-data/market-graph-20261018/render-1/graph-1905.png` plus `graph-730.png` were opened together in one comparison. Both show the native RuneLite dark graph. Data differ deliberately: the source search says Huasca seed but its statistics say Gilded 2h sword; Friend binds all values to the selected Huasca seed from independently captured public data. Copilot forecasts are not copied or represented as validated.

- [P2] Time-axis labels overlap at 730px; reduce tick count according to available plot width.
- [P2] Native selected-tab background loses text contrast; use the real records window dark-tab component and a visible selected underline.
- [P3] RuneLite small font is slightly denser than the reference. Keep the product font while checking readable narrow labels after the fixes.

Mandatory surfaces: fonts are genuine bundled RuneLite fonts; layout has the same search/ranges/legend/chart/volumes/statistics structure; blue/orange price and band tokens match the reference; Material PNG controls are original licensed source assets; graphs are quantitative Java2D plots rather than image approximations; experimental copy explicitly describes historical ranges. Item icon is provided by the real runtime ItemManager and absent in the standalone fixture. Primary controls are exercised as actual Swing interactions.

Fixes are applied; a new capture and comparison are required.

final result: blocked (pass 1)

## Pass 4 — native rendering passed

Source and the actual RuneLiteLAF implementation in `local-data/market-graph-20261018/render-4/` were opened together in one comparison, including 1905x1000, 1000x680 and 730x540. The 26px native source title bar is excluded from the content comparison; Java raster density is 1x. This is production Swing component rendering, not a mock web page.

Earlier P2s are resolved: adaptive time ticks have no overlap, dark native tabs retain readable selected text and an underline. Controls/legend align with the plot at wide sizes and remain available at narrow sizes. The right panel keeps its item name, genuine RuneLite item sprite and statistics consistent with the selected chart. Forecast/history and volume regions remain distinct. Wrapped status/source text is visible at 730px.

| Required surface | Evidence/result |
| --- | --- |
| Fonts | Actual RuneLite small/regular/bold fonts; native data labels readable at 1x. Slightly denser label style is retained as P3 product consistency. |
| Spacing/layout | Wide plot, lower side volumes, fixed information column, native search and range/zoom controls. Narrow view keeps all primary controls and uses fewer time ticks. |
| Colors/tokens | Charcoal plot/surfaces, blue low/orange high lines, translucent same-side ranges, dashed medians and contrasting selected tabs. |
| Images/icons | Real ItemManager sprite path exercised with an independently sourced RuneLite CDN item image; licensed original Material zoom/reset/expand PNGs. No generated/fake chart or icon artwork. |
| Copy/data | Selected-item binding correct; UTC times, partial volume, unknown fields and experimental historical ranges are explicit. No claimed Copilot model, fill guarantee or learning promotion. |
| Interactions | Native tests exercise name/ID search, explicit suggested-item switch, real time ranges, zoom/reset, keyboard navigation and projection toggle. Runtime expand/restore uses the current monitor work area. |

Intentional differences: own Friend navigation; range controls above rather than over price observations; source and experimental-method explanation; independently sourced Huasca data rather than the reference's mismatched Gilded statistics/private forecasts. No guide line is invented when the current instruction is for another item. These preserve the requested functional workflow while retaining evidence boundaries.

P0/P1/P2 findings: none remaining in the rendered scope. Live monitor scaling, window focus, current-monitor expand/restore and owner acceptance remain separate release checks. The existing compact sidebar receipt remains archived rather than overwritten.

final result: passed (native rendering scope; installed/player acceptance pending)
