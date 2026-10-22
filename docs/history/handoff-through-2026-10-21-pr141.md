# Handoff checkpoints through PR #141

Historical delivery notes; current HANDOFF and actual receipts govern.

## Current delivery

Clock/remainder batch: read `local-data/continuation-clock-preservation-20261021/delivery-state.json`
first for actual verification, CI, merge and installation. Future evidence times preserve pending
workflows without authorizing guidance; fresh observations still persist corrected remainders and
closure facts. Late callbacks after saved collection narrow caps without invalidating the document.
Replay 13 / sale policy 5. [Contract](docs/architecture/continuation-clock-preservation.md).

Restoration/acceptance batch: read `local-data/continuation-restoration-20261021/delivery-state.json`
first for source, CI, merge and actual installation. Current-login observation copies preserve durable
remainders across stale offer snapshots; cached EMPTY predecessors cannot invent collection. Exact
accepted cancellations retire rebuy promises only after commit, not during worker proposals or from
a delayed same-item card. [Contract](docs/architecture/continuation-restoration-boundaries.md).

Sale continuation batch: read `local-data/sale-continuation-evidence-20261021/delivery-state.json`
first for source/CI/merge and actual installation. A first verified source added to an older hint
after later fills now has its own acceptance clock; it cannot invalidate/delete the original hint.
Existing parallel same-item sales no longer invalidate relist proof on a heartbeat or valid fill;
new/changed/cleared orders remain refused. Paired sale policy 5 / replay 12; original financial,
source, risk and exact-cost guards persist. [Contract](docs/architecture/sale-continuation-evidence.md).

Runtime health follow-up: `local-data/health-request-isolation-20261021/delivery-state.json`
records actual verification/installation. Live captures reproduced both HTTP workers blocked by
account initialization/reads; the 60s starter retry alone did not resolve that occurrence. Global
health now has a separate dispatch path, account work stays at two workers with a bounded queue,
and routine refresh no longer scans diagnostic history. [Contract](docs/architecture/health-request-isolation.md).
Global health is not account trade readiness. Read the receipt before assuming this runtime is installed.
Subsequent manual login drained all 228 saved observations and supplied fresh authenticated eight-slot
evidence. Genuine accounting effects arrived normally; this does not close controlled full-loop acceptance.

Starter tooling: `local-data/starter-health-retry-20261021/delivery-state.json` records the existing-
listener health retry fix and verification. Start waits at most 60s for transient failures on the
same port-owning PID, rejects credentials/protocol/body failures and never starts a second copy.
This script-only fix is merged as #137; the subsequent runtime blockage and delivery are separate above.

Newest workflow slice: read `local-data/ordinary-sale-queue-20261021/delivery-state.json` for source,
CI, merge and installed state. Ordinary sale reductions now retain every later eligible sale through
final authority; only the presented instruction commits its durable continuation. Pricing, buy/stop
priority, exact costs and risk are unchanged. Controlled acceptance remains open.
[Contract](docs/architecture/ordinary-sale-maintenance-queue.md) /
[receipt](docs/history/ordinary-sale-queue-delivery-2026-10-21.md).

Newest diagnostic batch: read `local-data/sale-maintenance-diagnostics-20261021/delivery-state.json`
for verification, CI, merge and actual installation. Records → Recommendation adds active managed
sale wait explanations from the shared evaluator, with observed clocks and unvalidated original
order estimates. Pricing/authority and accounting are unchanged. Native acceptance remains separate.
[Contract](docs/architecture/sale-maintenance-diagnostics.md) /
[receipt](docs/history/sale-maintenance-diagnostics-delivery-2026-10-21.md).

Previous workflow batch: `local-data/trade-plan-maintenance-20261020/delivery-state.json` records source/tests,
CI, merge, installation and manual acceptance. [Current contract](docs/architecture/trade-plan-maintenance.md) /
[delivery receipt](docs/history/trade-plan-maintenance-delivery-2026-10-20.md).

The new batch preserves same-second accepted-advice ordering independently of prediction scoring,
retains original acquisition plans with possible remaining origin ranges, and removes invented stops.
Selected managed sell quantities use canonical subset costs and final authority on a worker. Earlier
supported non-loss maintenance follows the exact cancellation/collection source into relisting;
original targets remain context. Buy progress/deadline/stabilization and shared default horizons are
corrected. Forecast diagnostics remain observational and unvalidated; learning stays fenced.

The reported three-bludgeon original context is unavailable and stays unknown; those three units
subsequently sold. The newer two-unit acquisition is a different episode. The bolts' original and
replacement contexts survive corrected read-only projection, without financial replay or re-enrollment.

#133 is merged as 6f1634f394befdd68556c1fb892c2249f7f00f1f after both exact-head checks. Its runtime
installation is batched with this delivery. It tries every eligible exact exit through authority,
withdraws refused advice and separates actual inventory from equipment. Bank/collection evidence,
exact quantities/costs and personal stock remain distinct. [Contract](docs/architecture/exit-authority-queue.md) /
[receipt](docs/history/exit-queue-delivery-2026-10-20.md).

Previous #132 is merged as a1e70c1c0e0b8630ce52fb8aa61bb9b4ab3c584b and installed. Fresh manual
login/eight-slot/empty-outbox proof passes. The Dual macuahuitl two-unit rebuy hint is now retired
with its exact recorded sale receipt; other hints and the original source/financial prefix/risk
anchor survive. Subsequent genuine acquisitions changed the stock totals; they are not update
effects. Actual proof: `local-data/continuation-disposition-20261020/delivery-state.json`.
[Contract](docs/architecture/continuation-sale-disposition.md) /
[receipt](docs/history/continuation-sale-disposition-delivery-2026-10-20.md).

Included #131 separates intended-exit unrealized profit from instant-sale P/L, tries all eligible
buy remainders through final authority and supports verified partial exits after refusal.
[Contract](docs/architecture/exit-profit-and-replacement.md). Controlled full-loop acceptance remains open.

Owner is home and actively trading. Earlier login deferrals are superseded. Login and all game
actions remain manual. Batch routine diagnostics/update/launch; do not request pasted logs or
repeated confirmations.
