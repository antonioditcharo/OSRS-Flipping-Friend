# Scope historical trade uncertainty from recorded evidence

14 October 2026. This is one step toward safe new-entry planning, not proof that the live account
is ready to buy. Historical exposure bounds and a coherent current cash/slot read remain required.

## Problem and outcome

A rejected EMPTY observation records RECONCILE with item zero. The original read model treated
every such row as uncertainty about every item forever. That also hid fully observed unrelated
purchases from holdings/sell guidance and prevented future unrelated sales from matching known
acquisition cost.

The new read model follows source event IDs into the same account's recorded offer history. When
that history identifies the possibly affected items, only those items lose managed ownership
guidance. An unrelated fully observed purchase remains manageable, and a future sale can use its
actual remaining cost. Original incomplete rows, quantities, costs, tax/profit coverage and already
recorded sales remain unchanged. The change never retroactively supplies old profit or cost.

## Evidence contract

`TradeGapEvidence` derives an ephemeral map from each recorded RECONCILE event ID to a conservative
set of possibly affected item IDs. An empty set means the scope remains unknown. It reads no
inventory preference, player classification, current price or container absence as proof.

Require the explicit requested account, matching stored/payload event IDs, supported offer schema,
valid account/session/event identities, canonical source type/side/item/counters, slot 0–7 and
ordered same-session observations. Missing or malformed payloads, foreign account evidence,
unseen predecessors, session/order/time ambiguity and missing source events remain unbounded.
Unreadable JSON or a missing state does not break recorded-total reads or invent scope proof.

For a rejected EMPTY, retain all possible items in that slot's unresolved offer episode. A changed
item includes both the previous possibilities and the incoming item; it cannot make the old
holding clean by replacing its name. A terminal followed by an unambiguous EMPTY separates later
episodes. An EMPTY itself recorded as RECONCILE retains the prior context for repeated rejections.
Separating contexts is not a disposal, collection or profit entry.

The source map is read-only and recomputed from the existing event ledger. No new database table,
migration, replay into a writer, startup repair, deletion or user declaration is introduced.
Account-scoped writes still use the original transactional event/projection/accounting path.

## Ledger and consumers

Ledger schema 2 adds optional `reconciliationScope` metadata. Every RECONCILE row has exactly one
matching key. Empty sets keep the account-wide guard; nonempty sets contain positive item IDs and
must include the recorded item when it is known. Published trading positions cannot contain any
affected item, and unbounded scope cannot publish apparently clean managed stock. Client validation
rejects missing/extra/contradictory map entries. Defensive copies protect both maps and sets.

The raw `hasUnassignedReconciliation()` flag remains true for original item-zero evidence. The new
`hasUnboundedReconciliation()` distinguishes genuinely unknown scope. Older payloads without the
map retain their original conservative behavior. Older clients continue to withhold advice from
item-zero rows; use the supported paired update for the complete new workflow.

Managed holdings, exits and future sale cost matching use this same scope. A later purchase of an
affected item does not clean it, absorb its unknown units or price the personal stack. Source-free
legacy holdings still do not become managed acquisitions. If only uncertain holdings remain, the
UI reports unavailable evidence rather than claiming all purchases have been sold.

## Separate gates still open

Scope identifies affected items; it does not establish their remaining quantity or capital value.
The planner still abstains on unresolved exposure. The next step must derive conservative exposure
bounds and fence the current cash/slot observations before unrelated new entries can proceed.
No guard is removed merely because a history row can be assigned to an item.

Current-slot recovery from PR #47 remains a separate prerequisite. Full companion sell/replacement
policy, durable realized session risk/calibration, account-level chronological replay for all four
cadences, and player-operated acceptance remain outstanding. No feature-parity or profit-per-hour
claim follows from these tests or a successful ledger read.
