# Acquisition-bound exit context

19 October 2026. Read HANDOFF for tested, merged, installed and player-accepted states.

Live positions previously read the latest item-keyed `TradePlans` entry. Its six-hour TTL could
erase the intended target/stop/sell duration before an eight-hour visit; another recommendation
could supply a different acquisition's plan. Matching the client snapshot's quantity/cost/opened
time did not establish the original plan. A regression reproduces final authority accepting that
later item context for a legacy acquisition.

The live path now freezes `AcquisitionExitContext` only after the client-thread guide accepts the
actual BUY advice. It carries target, stop and the sell leg's duration, separately from the buy
leg's immutable execution forecast. The existing account outbox binds it to the first exact,
authenticated, zero-fill offer observation, in the same file replacement as the source event and
prediction. Its original event ID survives ACK, partial fills and restart without a six-hour expiry.
Advice must precede placement at recorded-second resolution; same-second ordering, a mismatched
price/quantity, an already-filled first observation or missing original prediction leaves context
unknown. Association is observational; it does not establish player intent or validated learning.

The companion independently qualifies against the earliest retained canonical episode source.
Later advice or a newly supplied context cannot replace an unknown or different original. Accepted
acquisition deltas contribute context to the existing read-only quantity/cost pool. Additional
fills of the same qualified buy retain it; partial disposals retain it even though the latest
financial source is now a sale. A mixed acquisition pool retains context only when every remaining
pooled acquisition has the same proven origin/context. Different or unknown origins make it
unknown until that pool is exhausted. Average-cost disposal cannot identify which original lot
remains. A later purchase after full disposal starts a new pool.

The ledger supplies context to live holdings, initial exits, maintenance/relisting and final sale
authority. Client `PositionSnapshot` and legacy item plans cannot supply it. `TradePlans` remains a
legacy journal/standalone cache; its TTL no longer governs live exit policy. Legacy item files and
positions are preserved, with no retrospective claim of original intent. An unplanned healthy
listing retains its existing ask under the patient policy; initial stock uses current sell-side
pricing and the exact taxed cost floor. Missing context never supplies a stop or a remembered target.

The ledger field is additive; no database schema or historical financial/source row changes.
Qualification is part of the existing coherent, externally invalidated economic-prefix read.
Missing/corrupt/changed original evidence withdraws context, and never repairs quantities, costs,
profit, gaps, reserves or anchors. Original prediction keys/forecasts and learning promotion stay
unchanged. Targets remain policy limits, never risk marks. The paired final-sale protocol is
`companion-sale-3`, so old item-context semantics cannot authorize a new client's sale.

Verification covers accepted foreground advice, exact-order mismatch, later advice, partial/full
buys, overnight ACK/restart, partial sells, duplicate delivery, mixed/unknown purchases, complete
disposal/new purchase and source deletion on disposable stores. Existing patient/exact-cost/
unknown-personal/relist/progress/final authority tests remain required. All game actions are manual.

Player acceptance: after delivery/log-in/open GE, manually place an eligible displayed buy, observe
partial/full fills, collect, inspect its initial sale target, then cancel/collect/relist only when
appropriate and restart. Agent reads scoped source/ledger/authority proof and timing directly.
Old acquisitions without prospective context stay unknown; issue #70 remains open. Complete replay
coverage must additionally reproduce this production provenance boundary before any promotion gate;
replay v5 still retains simulated entry context independently and is not that evidence.
