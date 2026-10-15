# Manual trade walkthrough and confirmation

14 October 2026. Originally developed on `codex/trade-ui-walkthrough`, now merged with automatic
trade scope through approved PR #43 at `440c44f`. The 1.13.2 pair is installed; live acceptance is
in progress. See the newest [handoff](../../HANDOFF.md) for the ledger/startup follow-up.

## Player workflow

| Editor state | Guidance | Normal Confirm |
| --- | --- | --- |
| Quantity and price differ | Highlight both input controls and display both target numbers | Locked |
| Only one value differs | Keep that control highlighted | Locked |
| Item, buy/sell type, quantity and price match; input is closed | Highlight Confirm | Available |
| A previously correct value changes | Restore the corresponding highlight | Locked |
| Another item or offer type is selected | Guide back to the recommended context; do not put its numbers on another item | Locked while actionable offer advice exists |
| No actionable offer advice, unavailable editor observation, or protection disabled | Do not infer a trade target | Native behavior |

Right-click the actual Confirm button and select its native **Confirm** option to deliberately
place a personal trade with different inputs. The normal menu choice says **Set recommended
values first** while locked; the painted button says **Locked - right-click**. The override does
not declare the recommended values complete, change cost/profit, or enroll personal possessions.
The `Protect trade confirmation` setting defaults on and is independent of overlay visibility.

The confirmation guard never fills fields, executes
client scripts, invokes a native menu action, or submits an offer. The guard only changes menu
priority, supplies a plugin no-op entry, and consumes an unintended normal confirmation. Native
Confirm is preserved for the player's context-menu selection.
The later [input assistance slice](trade-input-assistance.md) adds the owner's explicitly requested
key to fill one open numeric prompt; Enter and Confirm still require separate player actions.

## Observation and boundaries

- `GeWidgetResolver` resolves visible clickable quantity/price/Confirm controls by widget actions,
  including dynamic children. Captions and children of hidden subtrees are not controls. The
  generated Confirm container is a fallback, not a replacement for an observed dynamic button.
- `GeEditorValues` reads the selected item, offer side, slot, quantity, 64-bit price and active input
  state on the logged-in client thread. Price varp **5753** is the unnamed post-max-cash value in
  the pinned RuneLite 1.13.1 API. It is read as `long`; an integer wrap cannot satisfy the target.
- `StepGuide` derives progress from these committed values on each render. Opening an input box
  starts editing but does not establish completion. Existing deferred advice remains supported.
- `TradeConfirmGuard` recognizes the actual native Confirm operation. When widget identity is
  unavailable, both packed widget ID and dynamic child index must match. It does not intercept
  unrelated Confirm operations sharing the same parent.
- A real menu-open event arms one native override for that item, offer side and selected slot.
  A click consumes the permission; closing the menu, closing the interface, session changes and
  shutdown clear it. Changing item/side/slot cannot reuse an earlier menu's permission.
- New recommendation callbacks check their captured session generation and profile before
  restoring guide state. Reset withdraws old advice and progress.
- The instruction card contains the target numbers. Separate editor hints are suppressed while
  that card is enabled, preventing two overlays from printing over each other. The lock remains
  visible independently of number-hint settings.

The current strategy's supported unit-price range is unchanged. This UI reads long game values
for comparison; it does not extend the ledger's supported price range or repair old accounting.

## Primary reference and tests

Inspected Flipping Copilot commit `cb9251e8f46eb85762401ab1158e4caad272a424`:
[GrandExchange](https://github.com/cbrewitt/flipping-copilot/blob/cb9251e8f46eb85762401ab1158e4caad272a424/src/main/java/com/flippingcopilot/controller/GrandExchange.java),
[HighlightController](https://github.com/cbrewitt/flipping-copilot/blob/cb9251e8f46eb85762401ab1158e4caad272a424/src/main/java/com/flippingcopilot/controller/HighlightController.java),
and [MenuHandler](https://github.com/cbrewitt/flipping-copilot/blob/cb9251e8f46eb85762401ab1158e4caad272a424/src/main/java/com/flippingcopilot/controller/MenuHandler.java).
Its client reads actual GE variables and deprioritizes mismatched native Confirm while retaining
the context-menu action. This implementation uses the same manual interaction pattern.

Before implementation, commit `52513d1` reproduced eight failures on rebuild `5ac97048`:
matching values did not advance without clicks; wrong values advanced after clicking; both
incorrect fields were not highlighted; edits did not restore progress; wrapped prices matched;
dynamic Confirm was missed; captions and hidden descendants were selected as controls.

Behavior tests now cover those cases, wrong item/side, input still open, reset, menu lifecycle,
one-use override, native opcode/parameters preserved, shared-parent isolation, disabled protection,
unavailable client observation, and absence of production game-input writes. Synthetic images
exercise the actual overlay classes, including the duplicate-hint regression and lock visibility
for a personal item. These fixtures are not live RuneLite screenshots.

## Integration with PR #40

Standalone production revision: `b0ee05c8b0b68cb5ea570c510a7ed8de99a07508`; final rendering test:
`10c4fe4e6803bb2b61595822c0c3aa6bf8809bb2`. PR #40 remains unmerged at
`fd60c8be9b7313178ab9700018a02bfa34004c24`.

An isolated merge preview, `codex/trade-ui-scope-integration` at
`22985fa4955a2266fbcde4ae607334284e9237a0`, combines those source revisions without modifying
either original branch or the rebuild branch. Keep both quote-refresh and menu-guard event
handlers in `FlippingFriendPlugin`; session reset clears both advice/quote and the override.
Remove the redundant pre-read item/side return in `OfferEditorOverlay`: shared editor observation
already prevents inappropriate number hints, while the lock must explain the override even on a
personal item. Its loss in the automatic merge was reproduced by a rendering regression first.
The PR #40 quote test now supplies actual logged-in editor values instead of trusting a setter's
completion flag. An additional integration regression checks quote precedence over background
BUY advice, actual value validation, updated price relocking, unknown profit, and reset.

This preview is verification evidence, not an approved installation or merge target. After either
PR merges, reconcile the other with the current rebuild and repeat the combined checks. Do not
install directly from a temporary verification worktree or silently include another unapproved PR.

## Live acceptance after approved paired installation

The agent must not perform game input. The player performs each case and supplies observations:

1. Open a recommended BUY setup. With both values wrong, both controls stay highlighted and a
   normal Confirm cannot place an offer. Enter quantity only; its highlight clears while price stays.
2. Enter the matching price and close the input prompt. Confirm becomes highlighted/available.
   Change either value away again; its highlight and the lock return.
3. Right-click Confirm with deliberate personal values and select the native Confirm option.
   It places the player's chosen values; no recommendation completion or acquisition cost is invented.
4. Check SELL and cancel/collect/relist guidance, including partial fills. With PR #40, a player-
   selected personal item's sell quote is used instead of unrelated BUY advice; unknown profit stays
   unknown and the actual observed sell enters maintenance normally.
5. Close/reopen the editor, log out/in and change profile. Neither progress nor a prior override
   may authorize a different editor/account. Confirm outside GE remains unaffected.
6. Check normal/fixed/resizable layout and overlay/card/hint/protection settings. Verify placement
   against actual widgets; synthetic fixture bounds alone cannot establish live geometry.

Use only the supported paired update wrapper after fresh backup review. Do not change databases
to make any check pass. Preserve live exposure/accounting guards and report actual observations.
