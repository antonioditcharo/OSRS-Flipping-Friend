# Qualified public-market stock risk marks

19 October continuation: [owner-approved verified-cash policy](verified-cash-workflow-recovery.md)
supersedes the global full-cost charge for valuation-only uncertainty in physically proven stock.
These quote/publication/volume/haircut rules still determine qualified risk marks; unseen stock
still reserves its exact cost and uncertain items remain unavailable for new entries.

18 October 2026. The owner explicitly approved this policy and the supported #101 pair is installed.
[Delivery and acceptance receipt](../history/qualified-stock-valuation-2026-10-18.md). This replaces
the strict 120-second-only rule from #98 with the qualified fallback below. It is not a history
repair, a guaranteed executable price or a learned calibration. Fresh source/valuation and READY
plan checks pass; sustained, final eligible entry and player acceptance remain pending.

## Problem and boundary

A last-trade timestamp measures when another player traded, rather than when the public feed was
retrieved. A quiet position could therefore move from a taxed market mark to a full acquisition-cost
reserve after two minutes, exhaust the whole account's loss allowance and freeze unrelated entries.
The #98 explanation/retry fix did not change this valuation. A failing regression reproduces the
freeze with synthetic two-unit stock costing 76M against a 100M account.

The implementation retains the direct mark for a supported bid within 120 seconds. For a quieter bid,
CURRENT_ACCOUNT_V2 can use a discounted public-market estimate only when all of these hold:

- The global feed is at most 120 seconds old; both low/high trade clocks are supported, no later
  than evaluation and at most 900 seconds old. The pair is ordered and its spread is at most 25%
  of the bid.
- A matching completed aggregate has its own exact, aligned publication timestamp. Five-minute
  evidence expires 600 seconds after bucket end; hourly evidence expires 7,200 seconds after end.
  Missing clocks remain missing. A fresh `/latest` poll cannot renew an old aggregate's clock.
- Exact numeric positive low averages and nonnegative integer low volumes support the quantity.
  Capacity is the larger of half the 5m low-side volume and one tenth of hourly low-side volume,
  rounded down. The overlapping aggregates are never added together.
- The mark uses the minimum of the last low and every qualifying aggregate low, then a 10% price
  haircut rounded down and current-rule tax. Quiet marks cannot credit unrealized gains. Evidence
  expiry also shortens the risk snapshot's validity.

The 900-second horizon, volume shares and 10% haircut are explicit owner-approved risk-policy choices,
not calibrated confidence bounds, order-book depth or a guarantee of liquidation. They have not
been established by the account backtester or prospective return evidence. No scenario/prediction
band or Copilot private endpoint supplies authority.

## Quantities, accounting and remaining guards

Only canonical acquired remainders physically present in copied inventory/collection evidence or
unsold current GE offers are eligible. Personal extras cannot increase them. Supported subsets
use `SaleAccounting.allocateCost` from the exact visible pool; every unsupported or unseen unit
still reserves its full allocated acquisition cost at zero terminal value. Total cost is conserved.

Missing/over-age/malformed/future prices, unsupported publication clocks, insufficient volume or
untrustworthy spreads cannot manufacture a mark. The original loss anchor, confirmed realized
losses and exact tax/profit bounds, 15% allowance, source/account isolation, live uncertainty,
funding, cash, slot, buy-limit and diversification guards remain. Final entry recomputes the same
valuation from the durable account; a favorable plan is insufficient authority on its own.

No position, financial event, historical unknown amount or sale price is rewritten. A quiet risk
mark neither proposes a loss cut nor cancels/reprices an existing offer. Public prices are never
converted into account executions or training samples. CURRENT_ACCOUNT_V1 remains readable;
new risk snapshots identify V2 and do not rewrite old snapshots or original forecast bindings.

## Ingestion, replay and verification

The aggregate payload and publication timestamp commit together in `MarketState`. Refresh timers
commit only after the whole refresh succeeds, so a failed later feed cannot suppress the retry
or put a fresh timestamp on retained data. HTTP snapshots preserve the additive publication clocks;
existing clients ignore them safely. Legacy feeds without clocks keep the old full reserve for
quiet stock. Future, misaligned, non-integer or incomplete supplied clocks fail ingestion.

Account replay version 4 passes explicit aggregate clocks through the same production risk path.
Old frozen inputs retain zero/missing clocks and cannot qualify quiet marks. New inputs reject
future/incomplete clocks before simulation. Older bundles retain their own evaluator. This does
not close the outstanding production replacement/relist/foreground coverage or establish a return
benchmark. Terminal inventory marks retain their separate conservative rule.

Behavioral tests cover the failing freeze, final unrelated PLACE_BUY, exact accounting preservation,
unchanged original anchor, partial volume/full residual reserve, unseen stock, genuine falling
prices, confirmed losses, no quiet gain credit, exact money, legacy data, bucket/quote/feed expiry,
future clocks, spreads, cached publication identity and refresh failure/retry. Player acceptance
must separately observe buy availability through quiet held stock and a subsequent real guard.
