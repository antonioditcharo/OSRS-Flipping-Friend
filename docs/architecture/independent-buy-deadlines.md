# Independent BUY maintenance deadlines

The independent accepted-plan envelope can carry a valid original BUY forecast after learning
attribution expires. The live current-offer cache previously required a recommendation ID, dropping
this evidence and substituting the longer fallback horizon. Replay already retained the original
duration. This fix retains authenticated exact current BUYs independently of learning eligibility;
the legacy recommendation policy still requires its original attribution.

Deadline freezing also distinguishes an unavailable source from a durably captured unknown forecast.
Missing cache evidence, an unsaved/malformed outbox, replaced session, changed episode/inputs/counters
or a future source defers new deadline metadata. Pending ACK does not erase a forced local source.
A matching durable BUY with no qualified original duration still freezes the existing fallback.
An available original duration freezes at placement plus that duration, even when first checked later.

The engine uses this guarded path before testing overdue cancellation. Existing frozen records stay
immutable; refresh, fills, settings changes and restart cannot extend them. Placement, forecast and
actual-progress clocks remain separate. Current no-progress patience and fresh two-sided prices
still apply. No identical rebuy promise is created for overdue cancellation.

Behavioral tests join actual callback/outbox capture, client timing and forced tracker metadata,
including expired learning, pending ACK, partial fills, restart, source-write failure/recovery,
unknown forecasts, changed counters, future clocks, replaced sessions and account isolation. Separate
5m/30m/2h/8h cases preserve placement-based deadlines and recent-fill suppression. Offer snapshots,
financial/source rows, exact costs, risk anchors and learning claims remain unchanged.

Actual verification/CI/merge/install: `local-data/independent-buy-deadlines-20261022/delivery-state.json`.
Sale policy 8 / replay 17 / maintenance evaluator 4 remain unchanged. Historical fallback metadata is
not repaired or inferred from later advice. Player full-loop acceptance, sustained latency and full
production replay remain pending. Saved-sale cadence changes still require prospective policy-setting
evidence before relaxing historical eligibility; this separate protocol gap remains open in TASKS.
