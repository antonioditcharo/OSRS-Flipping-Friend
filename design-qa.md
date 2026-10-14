# Native sidebar visual QA

14 October 2026. Scope: production Swing components, synthetic data, RuneLite 1.13.1 RuneLiteLAF.
No live game screenshot, login or game input was produced. Product Design's reference-first and
comparison workflow is adapted to the requested Java plugin; no web prototype replaces it.

## Reference and comparable artifacts

Reference: public [Copilot screenshot at hub revision c8dbb515](https://github.com/cbrewitt/flipping-copilot/blob/c8dbb5155c44156ed3050c50155b086c25815556/images/flipping-copilot-ui.png).
Full image 1034x499; sidebar crop x=767..1005, y=0..498 is 239x499.
Local source: `.gradle/reference/flipping-copilot-ui.png` (ignored public reference).

Implementation: `build/ui/compact-buy.png`, 239x499 at 1x raster density; narrow stress 225x499;
opened detail views 730x540. Captures paint actual FlippingFriendPanel / PanelDetails components
with RuneLite fonts and RuneLiteLAF. A local mock ItemManager supplies a sprite from the public
reference only for comparison; production uses ItemManager images. CI without that local fixture
still checks the actual image callback.

Inspected full source/render together in `.gradle/reference/panel-comparison.png` (498x529 with
labels/gutter), then focused source/render in `.gradle/reference/panel-comparison-focus.png`
(498x240). These ignored local comparisons can be regenerated from the linked source and committed
target render. Review captures are under `docs/images/compact-*.png` and `details-*.png`.
Reviewed before/after, BUY, manual SELL, paused, waiting, long item, Recommendation, Settings,
Holdings and Performance. Fixtures contain no personal account data.

## Review and iteration

| Check | Result |
| --- | --- |
| Layout | Action, four-icon row, cadence, risk and recorded total fit 239x499; old accordion/onboarding removed. |
| Fonts | Native RuneLite regular/small/bold, exact quantity and full GP precision readable at 1x. |
| Spacing | Centered action caption, short rows, thin rules; detail content shares a 12px left inset. |
| Colors | Charcoal, bright instruction, gold amount/selected presets, green profit/red losses and disabled controls. |
| Assets | Real Material PNGs with LICENSE/NOTICE and ItemManager callback; no drawn icon glyph substitutes. |
| Copy | Actual quantity/unit price, cancel/collect/relist, Recorded profit, unresolved/unavailable coverage. |
| Connections | Production controls exercise graph, records, settings, skip/block, pause, cadence, risk and sell-only. |
| Overflow | Long item and max integer price wrap at 225px with geometry checks; explanations remain in details. |

Iterations fixed a preset remaining highlighted under a custom cadence, cached narrow line heights,
and mixed Settings alignment. Each has a failing automated case before its fix. An initial details
measurement included FlatLaf's default border instead of the component's actual border; the fixture
false positive was corrected before identifying the remaining long-item clip (16px for 48px).
The final geometry gate passes.

Intentional differences: own branding/Material icons; inline risk; exact durable Recorded profit
instead of an unsupported session/reset counter; records opened separately instead of mixing
estimated journal rows into realized totals. No invented cloud controls or recent-profit figures.
This matches the reference's compact workflow while preserving the project's accounting contracts.

No actionable P0/P1/P2 visual issue remains in the rendered scope. Live scaling, window focus,
real sprite loading and owner-operated GE acceptance remain pending and are not claimed observed.
Acceptance steps: `docs/architecture/compact-panel.md`.

final result: passed (native rendering scope only; live acceptance pending)
