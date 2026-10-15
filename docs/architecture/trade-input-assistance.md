# Suggested GE search item and player-triggered input fill

14 October 2026. Originally stacked on walkthrough PR #41, now integrated with #40/#41 through
approved PR #43 at `440c44f`. The 1.13.2 pair is installed; live acceptance is in progress. PR #42
is closed as integrated. See the newest [handoff](../../HANDOFF.md) for the ledger/startup follow-up.

## Player workflow

BeforeRender runs before login as well as in game. The startup follow-up at `ff2754b` checks
client-thread, account, logged-in state and editor readiness before reading search variables.
On a login/logout boundary it hides existing suggestion rows without reading unloaded varbits.
This addresses the observed pre-login NullPointerException; it adds no game input or selection.

When the player opens a BUY item search with an empty query, the current recommendation appears
as a **Suggested: item name** row with its item icon and a native **Select** action. The player
clicks it to select the item, then opens the quantity and price prompts normally. Native previous
search/results are preserved. Typing a personal query hides the suggested row; clearing the query
can show it again. WAIT/COLLECT/SELL advice, unavailable account/offer evidence, closed/hidden
interfaces and disabled `Suggested item in GE search` do not create a clickable buy suggestion.

In RuneLite's Flipping Friend Guidance settings, assign **Fill recommended price/quantity** to a
key such as E. It defaults to **Not set**, so enabling a shortcut is the player's choice. Enter
cannot be used. Open the GE quantity prompt, press the assigned key once, then press Enter yourself
to accept the number. Repeat those steps for the price prompt, then manually Confirm the trade.
The shortcut fills only the prompt already open. It does not open another field, accept the value,
confirm an offer, cancel, collect, relist, or continue a sequence. Holding the key cannot fill the
next field; release and press it again. Normal typing outside an eligible GE prompt is unchanged.

PR #41's confirmation guard still reads committed GE values, so filling text alone does not
unlock Confirm. The player must accept the prompt. Its native right-click override remains
available for deliberate personal trades. With PR #40 integrated, an eligible player-selected
personal SELL quote is the active price advice; its unknown cost/profit remains unknown.

## Primary implementation reference and interaction boundary

Verified the RuneLite plugin-hub marker on 14 October: Copilot commit
`c8dbb5155c44156ed3050c50155b086c25815556`. Inspected its
[KeybindHandler](https://github.com/cbrewitt/flipping-copilot/blob/c8dbb5155c44156ed3050c50155b086c25815556/src/main/java/com/flippingcopilot/controller/KeybindHandler.java),
[OfferHandler](https://github.com/cbrewitt/flipping-copilot/blob/c8dbb5155c44156ed3050c50155b086c25815556/src/main/java/com/flippingcopilot/controller/OfferHandler.java),
[GePreviousSearch](https://github.com/cbrewitt/flipping-copilot/blob/c8dbb5155c44156ed3050c50155b086c25815556/src/main/java/com/flippingcopilot/controller/GePreviousSearch.java)
and offer-setup config. Its quantity/price shortcut writes the open chatbox input string and
display text, rejects Enter and supports numeric input modes 7 and 30. Its selectable suggested
item uses the native previous-search selection listener (script 754, item ID, operation 84).
These are source observations, not a claim that this plugin has been approved by RuneLite/Jagex.

The new code follows that player-triggered pattern. The player's click invokes the registered
native item-selection listener; the plugin never invokes it programmatically. It appends its own
row only where it fits inside the native search container and leaves native children/query alone.
The input shortcut has one permitted write site, `GeInputAssist.fill`: `INPUT_TEXT` and its visible
prompt text, following a matching key press. There are no synthetic key events, menu-action
invocations, programmatic client-script calls, varp/varbit writes or background trading actions.
The assistant itself sends no input to the user's game during development or acceptance.

[Jagex's client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
prohibit new menu entries that send server actions and describe non-exhaustive restrictions.
[RuneLite hub review](https://github.com/runelite/plugin-hub#reviewing) checks Jagex rules and rejected
features before public hub inclusion. Copilot's reviewed source is the implementation precedent;
public submission review and actual player acceptance remain separate from this source verification.

## Source and safeguards

- `GeSearchSuggestion` owns only its appended button/icon/label. It detects native rebuilds by
  widget identity, hides its row on withdrawal, and does not move/hide/rewrite native results.
  Personal typed queries and SELL editors never receive unsolicited BUY selection shortcuts.
- `GeInputAssist` registers/unregisters a RuneLite key listener with plugin lifecycle. A copied
  eligibility target lets the AWT key handler avoid reading live client widgets. The fill executes
  on the client thread and rereads all guards before the single permitted input-string write.
- Eligibility requires a loaded account, complete offer observation evidence, a nonempty RS
  profile key, logged-in state, focused game canvas, visible GE setup/title/input, matching item
  and buy/sell side, a valid selected slot, actionable positive quantity/price advice, and the exact
  quantity or price prompt. Quantity supports mode 7; price supports mode 7 or the current mode 30.
- A queued request retains profile, editor item/side/slot, recommendation, configured key, prompt
  mode/text/widgets and generation. Changes cancel the fill. Loss of eligibility or a changed
  prompt advances the generation, so closing/reopening identical widgets cannot revive a key press.
  Session changes, config changes, editor close and shutdown also invalidate permission.
- Repeated key-down/typed events from one held key are consumed without another fill. Unrelated
  keys, normal chat, bank quantities, item-search text, hidden prompts and unassigned/Enter keys
  do not fill input. No acquisition, disposal, tax, profit or trade-scope record changes here.
- `ComplianceBoundaryTest` retains the ban on programmatic submission/input APIs and allows only
  this one input-string write in the tested controller. This is the narrow exception explicitly
  requested by the owner; it does not authorize agent-operated game input or offer automation.

The supported strategy/unit-price range remains unchanged. A 64-bit price prompt is recognized,
but the shortcut fills the supported recommendation value; it does not extend ledger price support.

## Verification and live acceptance

The initial test-first commit specifies these previously absent features. Its baseline cannot
compile because the controllers/config do not exist; that is not a reproduced historical bug.
During implementation, two behavioral regressions reproduced old queued key presses surviving a
prompt close/reopen or temporary loss of readiness. They precede the generation fix. The tests use
mocked widgets, a paused client-thread queue and synthetic keys, never the real game.

After approved merging/reconciliation of all three slices, install both JARs together using the
root supported wrapper and fresh backup review. The player performs each live case:

1. Empty BUY search shows the current suggested item; manually select it. Verify the native previous
   item/results remain usable, including fixed/resizable UI. Type another item and verify suggestion
   withdrawal, then clear the query. This does not place an offer.
2. Assign a key. In the quantity prompt, press/release it, verify the visible value, then manually
   Enter. Repeat for price in the current client. Confirm stays guarded until inputs are committed.
3. Hold the key across prompt changes; only one field may fill. Test Enter as the configured key,
   normal chat/bank input, a wrong item/side, no recommendation and a hidden/closed GE editor.
4. With PR #40, open a personal item's SELL quote; its price fills only on the requested key press,
   unknown profit remains unknown, and the actual observed sell enters maintenance normally.
5. Withdraw/change advice, close/reopen, log out/in, switch profile and disable/unassign the shortcut.
   Earlier permissions must not cross those boundaries. Preserve existing ledger exposure guards.

Live widget appearance, native selection execution, keyboard/IME dispatch and real game acceptance
are unobserved while the player is away. No performance or game-policy approval result is invented.
