# Exact acquisition cost in exit advice

15 October 2026. Regression f446c27 reproduces four failures in the actual exit decision and
canonical-to-guidance path. Regression fa31f49 reproduces the same floor/profit error in sell
maintenance. This fixes quote estimates and loss floors; recorded account profit remains the
durable ledger result.

A holding of two units costing 81 GP has an average cost of 40.5 GP. Rounding that to 40 before
estimating a sale at 42 reports 4 GP instead of 3. Treating 40 as break-even also authorizes an
80-GP sale against an 81-GP cost. The same omission scales with order size and survives into
partial-sale and reprice guidance.

Runtime managed stock already comes from the account ledger's remaining acquisitions. Exit price
ranking and whole-position quote profit now subtract the exact total acquisition cost. Displayed
and maintenance profits for a subset use the existing SaleAccounting.allocateCost proportional
allocation, the same overflow-safe convention used by durable disposal accounting. For three units
costing 125, two proposed units carry floor(125 * 2 / 3) = 83 GP, rather than 82 from an integer
unit average. This is an allocation convention, not proof that individual fills had identical prices.

TaxCalculator.breakEvenSellPriceForCost searches legal integer GE prices using current net proceeds
for the actual quantity. It returns the lowest price covering that cost, including tax floors,
exemptions and the existing cap. A zero result means no supported legal non-loss price or unknown
inputs; it is not a clamped price incorrectly labeled break-even. An unforced initial exit then
reports no available non-loss price. Existing stop/sell-only initial loss exits remain possible.
Sell maintenance retains its existing stop authorization and preserves unknown-cost personal sales.

Price stabilization is followed by revalidation of the exact cost floor. A previously held quote
cannot pull a healthy recommendation below its new floor. The displayed forecast is recomputed
after this final price/quantity choice, preserving the original forecast contract. Maintenance
uses exact remaining cost and marks unbacked/oversized personal sale profit unknown. No acquisition,
disposal, prior tax evidence, outbox identity, accounting row or historical result is rewritten.

Regression/behavioral fixtures cover actual initial sale and maintenance guidance from canonical
positions, mixed purchase-price totals, partial quantities, stabilized quotes, tax boundaries,
exemptions, maximum legal prices, large quantities and unreachable break-even. Existing explicit
loss/reprice tests remain required. All game actions remain manual. These are quote corrections,
not measured gains in net account GP/hour or completed companion exit-policy migration.
