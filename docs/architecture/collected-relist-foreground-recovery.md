# Quiet collected relists and bounded foreground reads

25 October 2026. The owner still reported collected-sale rechecks, account-confirmation waits and
periodic desktop slowdown after #170. Current installation and queued source are separate in HANDOFF
and `local-data/hosted-pilot/collected-guidance-delivery.json`.

## Quiet collected stock

A complete current feed can legitimately contain an old last-trade timestamp. Maintenance version 8
permits an exact accepted ROUTINE sale reduction to finish as a non-loss limit in that case. It
requires the actual latest-feed clock within 120 seconds, complete positive quote clocks without
future times, current inventory/source/cancel/collect proof and exact remaining acquisition costs.
The price cannot fall below either the saved accepted limit or the exact taxed floor, and remains
below the cancelled standing ask. Original targets, stops and acquisition contexts remain intact.
Old STOP/WIND_DOWN permission cannot authorize a loss through this path. Missing acceptance,
stale/future feed clocks, unknown costs and unproven quantities still refuse. Working sells retain
their existing fresh-print/patience/market-support rules; quiet data cannot request cancellation.
Canonical final authority repeats the checks. Chronological replay version 24 identifies the change.

Client snapshots now carry the actual latest-feed observation time separately from UI publication.
Mapping, hourly/history refreshes cannot renew it. Snapshot maps are copied at publication: clearing
and refilling a producer map cannot temporarily empty a recommendation's already captured market.
This changes neither an actionable quote's tax/tick checks nor canonical account freshness.

## Repeated historical work

Historical funding retains its slot predecessor state and immutable calculated result only across
SqliteStore's completely validated canonical append. The store owns that proof: the previous exact
prefix must match, each appended source passes its existing account/identity validation, and every
external change, account change or rollback resets the state. Other list replacements and changes
to paused scope require full qualification. Overflow/contradictory historic money remain unknown;
append cannot turn a failed prefix into a zero bound. Published old results remain immutable.
Current costs, slots, reservations, verified cash and risk checks still run on each calculation.

The validated prefix also retains a sparse index of eligible filled sale sources for continuation
dispositions. Ledger reads no longer construct a full event-ID map on each appended frame. The
same exact financial/source match and original-episode consistency rules still qualify the output;
the index cannot book an execution, erase a gap, reduce exposure or create a learning sample.

## Verification

Tests cover quiet collected limits, stale/future clocks, exact non-loss floors, legacy refusals,
working-sale stability, snapshot isolation and original feed times. Funding tests enforce tail-only
reads, immutable prior results, changed scope, invalid additions and resets. Existing canonical
transaction, external source changes, account isolation and rollback regressions remain required.
Private frozen-copy probes compare old/new ledger, dispositions, funding and original risk anchors;
an in-memory append benchmark is component evidence, never accounting or UI latency acceptance.

Require the clean Java build, tooling and both exact-head CI platforms, then one preserved paired
update. Local verification runs with one worker and lower process priority. Keep player-operated
entry/partial/cancel/collect/relist/restart acceptance and sustained latency open until measured.
Hosted recommendations, coordination and learning promotion remain disabled.
