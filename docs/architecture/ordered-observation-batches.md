# Ordered observation persistence and retry wakeups

19 October 2026. This changes client persistence and retry scheduling, not canonical accounting,
financial identities, policy authority or game interaction. HANDOFF records actual delivery.

Two regressions reproduce a lost retry wakeup. Replay snapshots its current queue while a callback
may append another event. Finishing that snapshot previously declared the entire queue drained and
disabled the retry timer. The same loss was possible when a request arrived after the empty check
but before the in-flight replay released ownership. Ordinary later refreshes might hide the defect;
the lifecycle retry pump itself could remain dormant with durable events still waiting.

Replay now distinguishes a drained queue from a later tail, retains an immediate retry request
for that tail, and serializes completion/request scheduling under a small lifecycle lock. The lock
does not cover HTTP or file I/O. A concurrent replay still cannot send anything. Paused, switched
or superseded generations retain their data and cannot restore old timers or advice. A transport
failure keeps exponential backoff even if another replay request arrived while it was in flight.

A copied planning frame now persists all eight observations in one outbox-file replacement.
Every source keeps its own event/session/offer identity, consecutive sequence, captured timestamp,
slot, original prediction and exit-context binding. The public batch is bounded to eight distinct
slots; invalid factories, account switches, overflow or a failed replacement do not consume a
durable sequence or save part of the frame. The existing file format and legacy wire keys remain.
An authenticated EMPTY retains its existing binding-release rule. Physical evidence is not coalesced.

HTTP delivery remains one ordered request and one verified acknowledgement per source. Only the
identity-matched acknowledged prefix is removed in a single local replacement. A bad or missing
third acknowledgement removes the first two, retains the remaining six and prevents account proof
publication. Concurrent appends remain pending. If acknowledgement persistence fails or the
lifecycle changes, already accepted sources may be retried under their original IDs; the existing
canonical duplicate/transaction rules handle them. There is no financial replay or queue clearing.
Cash/eight-slot proof is published only after the durable queue drains, using the exact receipt IDs.

The work-budget regression compares the same eight observations and their acknowledgements:
sixteen local replacements become two. This does not claim that eight SQLite transactions become
one, nor a corresponding end-to-end latency ratio. Successful HTTP requests already reused their
connection in the controlled probe; connection behavior was retained after testing that hypothesis.

Tests cover original bindings through progress/restart, late invalid factories, duplicate slots,
profile changes, enqueue/acknowledgement replacement failures, prefix holes and foreign identities,
partial/lost acknowledgements, appends during replay and at completion, lifecycle/backoff and the
actual eight-POST-before-account loopback sequence. Final cash/source/risk/price/limit authority,
unknown money, historical gaps, original risk anchors and PUBLIC_PRICE_BASELINE/HOLD remain intact.

Installed source-to-ACK-to-guidance timing and controlled manual buy/partial/collect/sell/cancel/
relist/restart remain separate acceptance gates. Login and every game action remain manual.
