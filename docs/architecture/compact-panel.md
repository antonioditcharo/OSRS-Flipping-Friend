# Compact action panel

14 October 2026. Source on `codex/copilot-panel`; paired installation and player acceptance are
pending. The branch includes PR #40 automatic trade scope, #41 walkthrough and #42 input assistance.
It does not merge those PRs into the rebuild branch.

The sidebar is a compact Flipping Copilot-style action surface: one instruction, exact quantity
and unit price, graph / pause / block / skip, four cadence presets, risk, durable recorded profit
and spendable-budget/free-slot status. Flipping Friend retains its own name and assets. The old
onboarding, SuggestionCard and collapsible sections are removed from source.

## Player controls and connections

| Control / view | Connection and behavior |
| --- | --- |
| Current instruction | `StepGuide.getSuggestion()` and `getPending()`, matching the walkthrough |
| Graph | Displayed item's cached `MarketDataService.getSeries`, existing PriceGraphPanel |
| Pause / resume | `StepGuide.setPaused`, plugin refresh callback; fresh advice on resume |
| Block / skip | Displayed BUY item, `SuggestionEngine.skip` and existing blocked-items config |
| Cadence / risk | Existing ConfigManager keys, incumbent clear and worker refresh |
| Finish session | Existing `SuggestionEngine.setSellOnly`, cleared on login |
| Budget / account mode | Same spendableCoins bank/collection/commitment/cap contract as planning; free slots and F2P status |
| Recorded profit / Performance | Scoped fresh `CompanionClient.lastLedger`, StatsPanel |
| Holdings | Canonical `engine.tradingPositions`, trade evidence and position status |
| Plan | Existing PortfolioPanel and companion plan |
| Local history / Bank estimate | Existing journal consumers, explicitly separate from realized profit |
| Missed flips | Separate read-only scoped evidence table |
| Copy quantity / price | OS clipboard only; never sends input to the game |

Gear opens Settings. More opens Recommendation, Price graph, Plan, Holdings, Performance, Local
history, Bank estimate, Settings, or Missed flips. The profit-row icon opens Performance.
Recommendation retains full explanations, target sell, estimated after-tax profit, duration,
break-even and confidence outside the sidebar. RuneLite plugin configuration still contains the
optional input key and advanced options; Settings explains how to find it.

Cadence presets persist actual 5, 30, 120 and 480 minutes through the existing horizon/account
contracts. Existing 3/15/60/180-minute settings remain available in Settings and visibly custom.
An old three-hour value is never labelled as 2h or 8h.

Recorded profit uses only the fresh account ledger: exact GP, ranges, unresolved and unavailable
states. It is not called Session profit. There is no fabricated reset, GP/hour or journal fallback.
Physical personal inventory is not enrolled by this UI. Unknown-cost manual sales stay unknown.

Pause withdraws advice, editor quotes, search/input eligibility and advisory notifications.
Trade observations and accounting continue. Resume requests fresh advice rather than restoring
a saved recommendation. Account reset clears pause. A generation fence cancels queued key fills
even if pause, resume and identical fresh advice occur before the client-thread callback.
Existing item/profile/prompt/side/slot/key and offer-evidence guards remain. Enter and Confirm
remain manual. No placement, cancellation, collection or input sequence is automated.

## Source and verification

FlippingFriendPanel keeps the injected constructor, refresh callback and shutdown API.
PanelDetails reuses the existing consumer panels in a reusable modeless window. Swing work stays
on the EDT; recommendation work uses the plugin's existing background refresh callback. Shutdown
disposes both detail windows. Visible details refresh their selected consumer; account-ledger loss
clears old totals.

The public [Copilot screenshot](https://github.com/cbrewitt/flipping-copilot/blob/c8dbb5155c44156ed3050c50155b086c25815556/images/flipping-copilot-ui.png)
and [native controls](https://github.com/cbrewitt/flipping-copilot/blob/c8dbb5155c44156ed3050c50155b086c25815556/src/main/java/com/flippingcopilot/ui/ControlPanel.java)
were inspected at the hub-pinned revision. The layout is independently written with native Swing
and Google Material PNG icons (bundled Apache 2.0 LICENSE/NOTICE). No Copilot branding/code is
packaged. A public reference sprite is used only in a local synthetic screenshot fixture.

CompactPanelContractTest clicks production Swing controls: displayed-vs-engine item, all menu
destinations, exact cadences, external/custom settings, sell-only, pause, unknown-cost sales,
ledger loss, planner budget settings, F2P status and narrow labels. PanelShowsTheLedgerTest retains after-tax, holdings, history and
full-reason assertions in the opened views. CompactPanelRenderingTest uses RuneLiteLAF at 225/239px
and 730x540 details, checking text geometry. Input-generation and horizon tests cover model effects.
See root design-qa.md and HANDOFF.md for evidence and limitations.

## Player acceptance after review and paired update

Do not install from a temporary review worktree. After owner-approved merge, update the normal
rebuild checkout. Close RuneLite, use `4 - Update.bat` (or `1b - Re-setup Existing Installation.bat`
if setup needs repair), review fresh backup paths/sizes and type INSTALL to install both JARs.
Start with `2 - Start Flipping Friend.bat`. Preserve all account and legacy data.

1. Run the existing read-only pipeline check and record exact output. Existing reconciliation
   faults are not repaired or declared resolved by this UI.
2. The player logs in and opens the sidebar. Check one readable instruction matching the
   walkthrough/search/input item, quantity and price. No old accordion stack remains.
3. Open gear and every More destination. Check full reasons, copy controls, graph, close/reopen,
   shutdown disposal and normal/enlarged RuneLite scaling.
4. Change each cadence and risk; reopen config/restart to verify persistence. Legacy custom
   minutes must remain exact with no preset highlighted.
5. Pause during numeric input/search, then resume. The player tests assigned-key fills, manual
   Enter/Confirm, right-click override, held key and context guards from the earlier slices.
6. Check managed stock, personal belongings, a player-selected personal sale and Missed flips
   using the earlier acceptance lists. Unknown acquisition cost/profit must remain unknown.
7. Compare recorded totals/ranges to read-only evidence. No source test or screenshot proves
   session GP/hour, live accounting reconciliation or feature parity.

All in-game interactions belong to the owner. Record observations and reproduce a real bug in
an automated test before fixing it. No database repair, migration or direct editing.
