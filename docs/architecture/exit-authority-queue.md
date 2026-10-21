# Complete exit validation and inventory availability

20 October 2026. Extends [continuations](trade-continuations.md),
[exact exit costs](exact-exit-costs.md) and [durable maintenance delivery](durable-maintenance-delivery.md).

The local engine previously offered one relist or holding exit. A refusal at final companion
authority could leave that item waiting without checking other eligible stock. The current pass
now retains all eligible exact-order proposals: stopped relists, stopped unlisted acquisitions,
listed stops, ordinary relists, then ordinary acquisitions. The existing stop/cancel and collection
priorities remain. A generic holding candidate cannot enlarge a saved cancelled-sale remainder.
All candidates keep exact pooled subset cost, original exit context, current physical/source guards
and independently calculated forecasts. Unshown proposals do not commit prediction evidence.

The client tries those candidates through the existing final authority. A refusal never authorizes
another price, item or quantity. Only the identical validated candidate can proceed. A changed
observation stops the old queue, and a proposal from another pass cannot consume it. If every
unlisted or urgent exit is refused, keep its WAIT; routine maintenance refusal can still fall
through to the independently authorized entry path. Refused continuation hints stay durable.
Before delivering a successor, withdraw every refused trade from foreground/pending/editor advice,
so an earlier rejected sell cannot remain actionable behind the last refusal.

Inventory is now distinct from equipment. AccountMonitor still observes worn items, collection
boxes and cached bank evidence independently of recommendation preferences. All remain owned
evidence; none enroll personal belongings into flipping stock. AccountState takes immutable copies
of container maps. Normal exits cap to the current inventory quantity and the canonical unlisted
acquisition remainder, with SaleAccounting.allocateCost for that exact subset. Banked/equipped
acquisitions retain quantity/cost and report preparation instructions in their holding status;
they do not produce a sell order for unavailable units. Moving units into inventory permits fresh
revalidation, without recording acquisition or disposal from the move.

Account captures additionally publish observedInventory separately from current physical holdings.
The companion checks that bound for unlisted exits when present. Legacy captures retain a missing
inventory field, rather than synthesising it from equipment/bank/collection evidence; the current
paired publisher always supplies it. Existing observedHoldings/risk semantics stay unchanged:
current equipment remains physical proof and cached bank quantities are not republished as fresh.
This is an additive companion-sale-3 boundary, not a financial or schema migration.

No source IDs, accounting transactions, financial rows, unknown coverage, held quantities/costs,
original plans, account scope or risk anchors change. CURRENT_ACCOUNT_V3, actual cash/eight-slot
proof, buy limits, exact after-tax floors, patient listing rules and all final guards remain.
Manual login, placement, cancellation and collection stay player actions. Controlled full-loop,
bank/equipment/inventory movement, partial exit/relist and restart acceptance remain open.
