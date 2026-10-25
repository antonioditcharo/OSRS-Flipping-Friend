# Historical funding reuse and publication timing

Follow-on to installed PR #165. Actual delivery belongs in
`local-data/hosted-pilot/ack-latency-delivery.json`; source or offline timing is not installation.

## Observed work

Post-update profiling still found `performance -> currentPlanningProof ->
BoundedHistoricalExposure.calculate` walking the same retained source history repeatedly. This
is a conservative lifetime funding envelope, distinct from the prospective loss-window calculation.
The earlier prospective memo did not remove this separate work. Some refreshes also spent roughly
five seconds in the combined capture/publication phase, whose old log could not identify the cause.

## Safety boundary

Reuse only the historical funding map for the exact immutable validated history reference and
paused-item set. New sources, a replacement prefix or a changed paused set require calculation
again. An unavailable result remains unavailable. Per-store ownership, canonical data-version and
rollback invalidation still govern the source prefix; no cross-account global memo exists.

Every call separately applies current canonical stock, exact legacy costs, reconciliation scope,
current offer states, remaining buy reservations and aggregate overflow checks. Current cash and
the authenticated eight-slot read set, limits, prices, loss window and final order authorization
retain their existing independent checks. The memo cannot reduce a recorded gap, adopt inventory,
infer disposal, reset an anchor or create profit. It never caches a final planning decision.

The HTTP client closes its connection after failures while writing or obtaining response headers,
as well as after body reads. Failed requests retain exact durable offer IDs and payloads for retry;
they cannot acknowledge a frame. Route-only slow-request timing omits query strings, payloads,
account IDs and credentials. Existing protocol/account checks and timeout budgets remain unchanged.

## Timing and release evidence

Keep the existing total refresh log. Add separate client-queue, local capture/persistence,
worker-queue, entry-recovery and account-publication phases. These are diagnostic intervals;
publication-phase completion by itself is not proof of a successful durable frame acknowledgement
or UI paint. Preserve the separate current eight-slot/source ordering and outbox-drain checks.

Regression coverage requires an unchanged-prefix traversal budget; appended/replaced sources;
changed paused items; contradictory and overflowing historical funding; fresh current costs,
unknown holdings, uncertain offers and reservations despite a warm memo; external source changes
and rollback; and real loopback HTTP failure/retry preserving all original events.

Run the clean Java/tooling suites and exact-head Windows/Linux CI. Retain/verify the #165 runtime
and current account/config/outbox backup, then install through the supported paired updater.
Verify immutable prefixes, old/new runtime ledger reads, fresh guidance, independent background
recovery and current frame ACKs. Measure live latency separately from the frozen historical-read
comparison. Sustained latency, player trading/restart and hosted advice/coordination activation
remain separate gates; no learning promotion follows from this optimization.
