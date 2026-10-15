# Automatic trade scope and player-started sales

Accepted project direction, 14 October 2026. Implementation is merged through approved PR #43
at `440c44f`; the 1.13.2 pair is installed. The live ledger/startup follow-up and remaining acceptance
are in the newest [handoff](../../HANDOFF.md). The design below is not proof of live acceptance.

## Default workflow

The causal-scope continuation is documented in [trade gap scoping](trade-gap-scoping.md). Positive
source-event proof can limit an old item's uncertainty without editing the original item-zero row.
Unrelated fully observed acquisitions/exits and future cost matching may then remain available;
unbounded scope still withholds them. This supersedes the blanket ownership guard described in the
historical PR #45 paragraph below. Historical exposure and current cash/slot gates still block new
buys; item scope alone cannot release them. Source installation/acceptance is recorded in HANDOFF.

The live continuation at `ff2754b` handles an existing read boundary: rejected EMPTY observations
can record RECONCILE with item ID zero, quantity zero and no economic effect. The read-only scope
view preserves this as account-level uncertainty; Missed flips labels it **Unidentified offer**
with unavailable money. It does not infer the previous item or erase the record. Other effect
types still require a positive item ID. Unassigned uncertainty withholds managed exit advice and
new buys, and later sales cannot claim cost through that gap. Holdings availability reports
uncertainty rather than claiming all purchases were sold. Recorded bounded sale totals remain
readable independently. No schema change, database migration or live record repair is involved.

The plugin maintains its expected trading stock from observed executions. Inventory, equipment,
bank observations and collection boxes provide physical evidence independently. They do not
enroll personal gear or supplies into trading stock. Routine inventory classification must not
require a player to press Personal item or I no longer hold this.

Membership is an outstanding quantity with acquisition provenance, not an item-ID flag or an
item's appearance anywhere in a journal. A purchase of 100 nature runes permits tracking those
100 units; another 6,000 personally owned runes must not expand that position. Fully accounted
sales consume the matched quantity and cost once. Banked or uncollected purchased units remain
expected stock through restarts. Container absence is not proof of disposal.

All buy executions observed while the plugin is enabled are tracked, including manually chosen
buys. Recommendation attribution remains separate: observing a purchase does not imply that
the plugin recommended it. Missing recommendation lineage does not discard a real execution.

An item with no observed acquisition or outstanding matched quantity receives no unsolicited
holding/sell recommendation. Missing history is preserved as uncertainty; it is not proof that
a recorded purchase was sold, and it does not authorize erasing cost or ownership evidence.

## Selling other owned items

When the player selects an owned item in the GE sell setup, that selection establishes sale
intent for the selected item and quantity. Show a fresh, market-supported suggested sale price
there. Do not scan the account and propose selling its other belongings. Closing the setup or
changing its item/account invalidates that context.

The player types, places, cancels, collects and relists every offer. Observing the actual sell
offer enrolls that offer in maintenance through partial fills, cancellation/relisting, completion
and collection, with durable offer/event identities and account isolation. Merely opening a
setup does not create an execution, alter a position, or enroll every unit of that item.

For stock without a matched purchase, maintenance can advise on current sale price and progress.
Acquisition cost, break-even and realized profit remain unavailable. Sale proceeds cannot be
shown as profit. A partial sale of a mixed stack must preserve which quantity has matched cost
and which remains unpriced. A recent purchase price cannot price the entire personal stack.

Current quotes support a recommendation; they do not guarantee the best obtainable fill or
reproduce Flipping Copilot's private server model.

## Missed flips

Replace the main-panel Review holdings section and automatic adoption option with one compact
control opening a separate Missed flips window. Show incomplete trading records: unmatched
sales, expected purchases/sales with missing observations, and conflicting trade evidence.
Ordinary personal inventory is not a missed flip and must not become rows requiring classification.

Rows should show item, known times, bought/sold or unmatched quantities, known monetary amounts,
and what is missing. Keep unknown cost/profit and tax/profit bounds explicit. Distinguish recorded
quantity from confirmed physical holdings. Preserve durable identities, account scope, duplicate
handling and coverage. Start with read-only inspection under the owner's database rule; do not
add fabricated transactions, automatic backfills, database repair or mass-dismiss controls.

Historical incomplete rows should not automatically freeze unrelated new entries. Current cash,
slots, live reservations and relevant outstanding stock still need provable exposure. Where an
active trade's quantity/cost/exposure cannot be safely established or bounded, retain an explicit
abstention. Moving a warning to another window is not permission to bypass that protection.

## Implementation gate

Complete this against the single account ledger used by the planner, exits, maintenance, holdings
UI and reporting. Do not introduce a third independent position book to hide the present split.
Keep existing account/legacy files and historical events; no agent migration or live record edits.

Before production changes, reproduce the old workflow and establish behavioral cases for:

- Personal inventory/equipment/bank items and a saved adoption option produce no automatic sell
  suggestion, position enrollment or routine review requirement.
- A tracked purchase plus personal units of the same item manages only the remaining matched
  quantity; partial fills, sales, duplicate delivery and restart preserve its cost.
- Player-selected external sales get fresh price guidance; closing/changing the setup withdraws
  it. Observed offers enter maintenance and remain correctly scoped through completion/collection.
- Unknown-cost external sales never gain invented profit or borrow a cost from consumed units.
- Missed trades appear in the separate window; unrelated personal belongings do not. Foreign,
  stale, future or malformed responses cannot become current records or advice.
- Incomplete history stays visible while unrelated planning proceeds only with validated current
  exposure; an unbounded active trade still withdraws unsafe advice.

Use chronological real execution evidence, not simulated price touches, for later learning.
Measured account GP/hour and the separate 5m/30m/2h/8h benchmark remain unchanged.

## Reference and current gap

The owner's experience supplies the desired player-started sale behavior. The official
[Copilot MissedFlipsPanel](https://github.com/cbrewitt/flipping-copilot/blob/cb9251e8f46eb85762401ab1158e4caad272a424/src/main/java/com/flippingcopilot/ui/flipsdialog/MissedFlipsPanel.java)
confirms a separate incomplete-trade view at the inspected revision. Its server-backed correction
actions are not authorization to change this project's live databases.

Source implementation on `codex/automatic-trade-scope` reads accepted `position_event` execution
rows into an ephemeral `AutomaticTradeScope`; it creates no new ownership database or persisted
position book. Account ledger schema 2 adds trading positions with acquisition provenance and
read-only incomplete/uncertain update evidence. Past sale accounting and legacy projection rows
are not rewritten. Future observed executions in automatic mode account from this same view.
The plugin publishes automatic scope intent, stops snapshot adoption/repair in that mode, and
uses fresh durable quantities for unsolicited exits, Holding cards and unrealized risk marking.
A pending offer outbox or unavailable/old ledger withdraws these trade positions without using
local positions as a runtime fallback. Existing Performance still reads recorded ledger totals.

The adoption setting and main-panel review/dismiss controls are removed. Old saved records and
preferences remain preserved. Physical holdings cannot widen any trade quantity. Selecting a sell
editor yields current item/quantity price guidance; buy editors, closed editors, unsupported offer
prices, missing inventory, stale/future quotes and account transitions withdraw it. Only observed
offer executions enter accounting. Unknown-cost maintenance and relists do not publish invented
profit. `sell-relist-intent.json` stores guidance for an exact observed offer, not owned inventory;
it waits for the recorded cancellation and collection, then requests a fresh quote capped to the
cancelled unsold quantity. It survives restart and remains account scoped. Collected-offer proof
uses the existing 12-hour retained observation window; absent proof never invents collection.

Missed flips opens a modeless, read-only table. It displays individual durable update timestamps,
quantities, recorded gross, tax/profit ranges or Unavailable, and evidence reasons. Source-free
unpriced personal snapshots do not appear as missed trades. Legacy known-cost snapshots lacking
acquisition provenance are labelled legacy records; their quantities are not claimed as current
physical inventory. No corrections, database migrations or old sale backfills are offered.

Planning ignores source-free zero-cost snapshots and unrelated unpriced sale history when the
execution ledger and current offer projections prove safe exposure. Actual RECONCILE execution
records, ERROR offers, malformed money and unresolved known costs still abstain. Legacy positive
cost records remain conservative capital guards unless their source projection exactly matches
accepted execution evidence. This preserves uncertainty without asking the player to classify
routine belongings.

Local clean-build and behavioral validation are recorded in HANDOFF.md. Paired installation,
manual game acceptance and the original PR #31 checklist are still pending. Realized session-risk
and calibration inputs still need their durable-ledger migration; this change makes no parity,
profit-per-hour or learning-improvement claim. PR #38's observation fix remains required.
