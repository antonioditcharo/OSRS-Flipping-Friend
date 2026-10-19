# Durable acquisition exit delivery

19 October 2026 (America/Anchorage). Private receipts: local-data/acquisition-exit-20261019;
initial regression/build logs: local-data/exit-context-*.log. Current runtime state is in HANDOFF.

An old-source regression demonstrates final sale authority accepting a later item plan as an
older acquisition's original target. Source inspection also confirms live `TradePlans` expire
after six hours and repeated recommendations overwrite the item key. The fix moves live context
to the original exact prospective buy source; no old acquisition plan is backfilled. Full contract:
[acquisition context](../architecture/acquisition-exit-context.md).

Foreground acceptance supplies the exact displayed BUY target, configured stop and sell-leg
duration. The existing atomic outbox/prediction binding freezes that original context. Read-only
canonical qualification and pooling retain it through partial buys, ACK, overnight restart and
partial disposals. Mixed/unknown acquisitions stay unknown, including after a partial sale; full
disposal separates the next purchase. Live holdings/exits/relisting and final authority read that
same context. Client snapshots and legacy item files cannot create original plans. The paired
final-sale protocol is companion-sale-3. Exact subset costs, tax rules, all source/physical/risk
guards, original event/sample keys and learning HOLD remain intact.

The regression-first final-authority case fails on the preceding source, then passes. Behavioral
tests cover actual accepted advice/outbox publishing, mismatch/late attribution, >8-hour ACK and
restart, durable partial buys/sales, duplicate delivery, exact remaining cost/profit, mixed/unknown
origins, full disposal/new acquisition and deletion of an original source on disposable stores.
The existing managed initial/maintenance/cancel/relist tests now supply canonical context rather
than mocking the obsolete item-plan authority. Old sale protocols are refused.

Standard clean Windows build passes in 66s/18 tasks: 1,726 Java passed/six existing skips
(plugin 752/core 193/companion 787 tests), all packaged project classes target Java 11. All 108
tooling tests pass in 112.175s. build-verification.json retains exact candidate hashes. No UI
layout changes. Source verification does not establish installation or player-controlled trading.

The fixed read-only retained-account probe preserves identical ledger results and allocations,
with 1,414 unchanged financial effects. Baseline/candidate cold reads: 3,038/3,044ms; twenty warm
reads median 117/115ms and p95 189/162ms. Both return NO_FREE_SLOT. Concurrent isolated JVM probes
use the same frozen source/market/history clocks; these establish no latency improvement, eligible
entry or game-event-to-frame timing. New prospective context behavior is covered separately by
the canonical fixtures. Private probe outputs and latency-summary.json retain the measurements.

The initial direct runtime read had an account capture over five hours old. Its eight source IDs
match, but age makes it unusable as current proof; no current sell-only setting or entry eligibility
is inferred. Status independently verifies the installed #105 pair and responding companion.
Fresh manual login/GE and controlled buy/partial/collect/sell/cancel/relist/restart remain pending.
Replay v5's simulated context is not the production provenance boundary; complete replay and
qualified four-cadence returns/learning gates remain open. No price/return/learning superiority claim.

Exact-head CI, merge, supported update, backup/task/financial preservation and fresh scoped runtime
checks will be recorded after delivery. Until then the installed pair remains #105.
