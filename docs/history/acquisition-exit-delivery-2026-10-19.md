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

## Verified delivery

PR #108 merges as fe8a33b9548559253968073e58d7aea69bd8078f. Both exact-head checks pass for
2e30f76c03653b7127a8a6daf5c2fc536497fea8, run 39279920742: Ubuntu 15:47:33Z and Windows
15:49:42Z on 19 October. GitHub's aggregate status remained stale after every step completed;
fresh individual check-run reads independently confirm completed/success for that exact head.
Cancel/retry requests were refused because the run had already completed; no rerun or check
override was applied. The clean primary/default checkout was fast-forwarded; main is untouched.

Installed through the supported unattended paired updater at 2026-10-19T16:13:02.7588664Z.
RuneLite was already closed; no game process was stopped or controlled.
VERIFIED backup 20261019-081258-a59c264b; process lifecycle remains 1 and task XML/SDDL are
byte-identical. Installed artifacts match the verified clean build:

- Plugin: 588,834 bytes; SHA256 7f2043b56d8c4a3c631da30af24375002aa083458a971f390455a97788208d6c.
- Companion: 18,086,624 bytes; SHA256 f832383acbbe47a99ed531feba46fc8bbda8786e72e6c90fbf2c768b456fbbd2.

Read-only comparison preserves all 61,527 retained event-log rows, 1,469 financial effects and the
sole original risk anchor, with zero effects created by this update. Status is PAIR VERIFIED /
COMPANION RESPONDING; RuneLite 1.13.2/cached artifacts match. Scoped GET pipeline passes
(86 sale updates/22 unresolved/one range); entry correctly waits for fresh client cash/slots.
Supported Start reopens RuneLite, with the installed JAR side-loaded in the actual launched
client's redirected start.log. No game input was sent.

The post-update read still has only an aged retained snapshot (over six hours old). Its eight IDs
match their recorded sources, but cannot establish current-login proof, a current sell-only
setting or current entry readiness. Fresh login/GE, initial/partial/cancel/collect/relist/restart
and player pricing-quality acceptance remain pending. Legacy plans stay unknown; this update
does not resolve issue #70 or backfill history. Private delivery-state.json, individual CI receipt,
installed artifacts, backup/task/prefix receipts and status/compatibility/pipeline/start logs retain
the delivery evidence. Later documentation changes require no JAR reinstall.

## Fresh post-login checkpoint

Subsequent manual login produces fresh authenticated eight-slot source bindings at age ten seconds;
all eight account read-set IDs resolve to accepted account/slot/capture-matching observations.
The account-scoped outbox has zero pending events at that read. Scoped Performance is readable.
Fresh portfolio READY and final PLACE_BUY / ENTRY_CANDIDATE_SELECTED GET pass in 156ms/250ms,
with the current account in normal mode and six free slots at the captured frame. Earlier
inconsistent-current-observation WAIT reads remain recorded; no guard was bypassed to obtain READY.

Actual launched Client-thread deliveries include 19 BUY cards (median 1,466ms/max 2,578ms), three
COLLECT cards (median 709ms/max 3,433ms) and nine WAIT cards (median 1,134ms/max 2,473ms).
These are an early, uncontrolled request-to-delivery sample with diagnostic reads also active;
they are not exact game-event-to-frame transitions, a comparable speed benchmark or sustained
acceptance. The separate learning read took 2,140ms and remains OBSERVING/HOLD_FOR_VALIDATION.
Do not attribute these timings to a new performance optimization in #108.

One legitimate subsequent financial append increases effects to 1,470; the original 61,527-row
source prefix, 1,469 effects and sole risk anchor remain unchanged. The immediate installation
receipt separately records zero new effects before launch. Private fresh-final-entry.json,
installed-108-read.json, fresh-runtime-details.json and delivery-state.json retain the source,
scoped responses, timing and boundaries. Controlled complete trade-loop/original-exit-context/
price-quality/partial/relist/restart and owner UI acceptance remain pending; fresh entry authority
does not establish those outcomes. No financial replay, adoption or learning promotion occurred.
