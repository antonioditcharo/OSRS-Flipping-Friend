# Durable sale accounting

Implemented on 11 October 2026 after account isolation. Accepted sale updates persist gross
proceeds, removed blended cost, tax/profit bounds, the tax-rule profile, and an evidence reason
in the same SQLite transaction as event acceptance, offer state, and remaining stock. Performance
reads the scoped account ledger; an absent/stale/malformed/foreign ledger has no journal fallback.

## Cash flows and cost

Sold quantity and gross proceeds are the changes in the offer high-water marks. Missing or
inconsistent proceeds leave profit unresolved; no ask-price receipt is invented. Collection and
repeated observations do not create sales. Gross-counter semantics are an inference supported by
the pinned RuneLite 1.12.39 [trade-price calculation](https://github.com/runelite/runelite/blob/runelite-parent-1.12.39/runelite-client/src/main/java/net/runelite/client/plugins/grandexchange/GrandExchangePlugin.java#L458)
and [Received display](https://github.com/runelite/runelite/blob/runelite-parent-1.12.39/runelite-client/src/main/java/net/runelite/client/plugins/grandexchange/GrandExchangeOfferSlot.java#L222).
Live verification against collected coins remains required before a performance claim.

Cost is removed proportionally, preserving integer remainders until later sales. Quotient/remainder
arithmetic avoids overflowing `totalCost * soldQuantity`; the final unit receives the remaining cost.
Unknown cost or excessive disposal cannot manufacture zero-cost profit. Snapshot absence does not
create a disposal or realized profit.

## Tax evidence

[Jagex's 29 May 2025 notice](https://secure.runescape.com/m=news/yama-cas--more?oldschool=1)
states that existing offers retain 1% while new offers use 2%. First-seen time is not placement
evidence. `GE_2025_05_29` is assigned only when an observed empty slot dated on/after 30 May 2025
is followed by an accepted zero-filled sell offer. The next-day boundary avoids guessing the update
hour. The profile survives subsequent observations and restarts. Otherwise tax remains bounded
under an unproven placement/rule. Future tax changes require new profiles; do not redefine old ones.

Under the recorded 2% profile, tax is floored and capped per item. A divisible average does not
prove equal fill prices. Exact tax follows from a single unit, gross equal to quantity times the
minimum sell price, exemption evidence, or coincident bounds. Other aggregates retain ranges,
including possible mixtures across the cap. Missing item classification remains uncertain. Profit
bounds are gross minus tax bounds minus known removed cost; the fee is modeled from the recorded
rule/evidence, not independently observed from the game.

## Persistence, API, and display

Nullable sale columns are added to `position_event`, and a rule column to `offer_projection`.
Old disposals remain unpriced and unresolved; no backfill uses current prices or the JSON journal.
Columns are added only when absent, and migration failures are not swallowed. Duplicate identity,
rollback, and account isolation retain their existing guarantees.

`GET /v1/accounts/<accountId>/ledger/current` confirms scope/protocol and returns sale-update counts,
bounded/uncertain/unresolved profit coverage, separate tax coverage, profit/tax bounds, and current
durable positions including reconciliation state. Counts are observed updates, not completed flips
or individual exchange matches. Unknown sales are excluded from numeric totals and identified.

Performance shows full GP precision, ranges, unresolved counts, and positions requiring review.
Unavailable evidence says Unavailable. Local history and bank-value graphs remain accessible and
are labeled as estimates. GP/hour, win rate, and hold-time headlines await a canonical lifecycle
and measured session clock. This does not value unsold stock or claim whole-account returns.

## Verification and remaining work

Tests cover partial sales, last-unit cost, duplicate delivery, restart, transaction rollback,
idempotent old-schema upgrades, scoped API reads, foreign/stale/malformed responses, tax bounds,
and readable sidebar text. An exhaustive two-price check independently sums per-item floors.

Durable sale accounting and the Performance consumer are complete. Planner exposures, exits,
inventory UI, journal calibration, and session-risk inputs still use local records and must migrate
with positive reconciliation evidence and complete manual cancel/collect/replace workflows.
Development verification did not modify a live account database or install application JARs.

Local supported verification used Windows/JDK 23, Gradle 8.14, and the pinned RuneLite 1.12.39
dependency: 1,147 Java tests passed, six capture-dependent tests skipped, zero failures/errors,
eight Python importer tests passed, and both supported JARs built. The diagnostic PowerShell
script passed its syntax check. CI independently checks JDK 21 on Windows and Linux.
