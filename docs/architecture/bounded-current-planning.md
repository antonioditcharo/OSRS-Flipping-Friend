# Current observations and bounded old exposure

15 October 2026. This extends offer recovery and causal trade-gap scope. It does not repair old
records or claim that uncertain stock has been sold. Slot-hour ranking is unchanged; measured
whole-account returns and chronological evaluation remain separate unfinished work.

## Entry contract

The plugin captures cash, all eight physical GE slots and known offer identities on the client
thread. F2P still requires eight observations while counting only its three usable slots. Mutable
client objects never reach the worker. Missing identities, unsupported prices, invalid counters
or contradictory reservations/free-slot counts withdraw advice.

The durable outbox assigns an event ID, session and sequence to each copied observation. All eight
must be acknowledged before its account snapshot is posted. The additive `offerReadSet` maps each
slot to that exact event ID; the snapshot and observations share their capture timestamp. Cached
offer publication is not authenticated proof. An old publisher without this receipt cannot
authorize new entries.

The companion reads current projections, source payloads, trade scope and necessary history in
one database transaction. The receipt must match all eight current source events, account,
session, ordering cursor, timestamp, offer identity and economic counters. It rejects ERROR slots,
missing/altered/cached sources, future observations, age over 150 seconds and resource mismatch.
Market freshness is checked separately. The account marker is refreshed only by real client
observations, never by a diagnostic POST.

## Cash is a lower bound

A fresh bank-container event or a visible bank view establishes bank cash. A non-null cached
container alone does not establish freshness. The session-local bank floor is reduced by the full
possible funding of each subsequently observed buy episode. Same-offer cumulative updates do not
debit it again; fill, cancellation and collection do not restore it. Refunds and other unobserved
credits wait for positive bank evidence. Invalid observations/reset discard the bank floor.

This may understate funds when inventory funded the offer. It cannot silently resurrect spent
bank GP after the offer disappears. Inventory and collection cash remain independently observed;
the bank setting and spending cap still apply. Holdings presence is preserved independently of
this spending bound. There is no inferred disposal or player classification workflow.

## Uncertain capital is an upper bound

Only account-local recorded source history can scope an old gap. Unknown scope stays global and
blocks entries. With known scope, each affected BUY episode contributes its full price times total
quantity, not merely its observed fill. Repeated increasing counters within the same episode do
not recount it. A later episode, including one following an ambiguous EMPTY, contributes another
full envelope. Nothing is subtracted for an unproven disposal. Contradictory historical funding or
arithmetic overflow prevents a bound.

Clean proven acquisition costs and current buy reservations remain in exposure. Known legacy costs
remain guards; an unproven source/cost conflict fails closed. Affected open reservations are already
inside their full envelope and are not added twice. These values can conservatively overstate
capital at risk. They are not managed quantities, account valuation, tax receipts or profit.

Affected items are unavailable for new entries. Provably unrelated items may be planned only with
the complete fresh cash/slot proof and normal capital, group, item, loss, membership and buy-limit
checks. Client and companion buy-limit evidence use the stricter remaining allowance. The separate
Plan window displays the uncertain bounds; Missed flips retains the original evidence. No sidebar
classification controls are added and unknown sale profit remains unknown.

## Invalidation and final authorization

An economic offer/cash/preference change invalidates cached buy advice immediately, even during
plan spacing. An identical heartbeat changes its source receipt without forcing a structural
replan. It must not compute against the preceding account receipt while that rotation is in flight.
A new account publication retries a transient COMPANION_STATE_UNAVAILABLE result immediately,
even when its economic fields are unchanged; an old capture's failed computation cannot replace
the new state. Client advice revisions fence both queued work and final UI delivery; durable offer
delivery uses a separate lifecycle generation so a terminal observation followed by EMPTY is not
dropped. A displayed BUY becomes WAIT when account evidence changes.

Plan publication and final entry approval are serialized with account/offer invalidation. Expensive
candidate computation remains outside that boundary. Final approval re-reads current proof and
rechecks the selected item against current reservations, paused items, preferences, membership,
buy limits, spendable cash, slots, exposure and loss budget. Overflow cannot wrap an exposure into
an eligible value. This returns advice only: every game input remains a player action.

## Evidence and limits

Regression-first commits precede the cash, stale-plan, paused-entry, overflow, client-limit and
displayed-advice fixes. Disposable-store tests cover malformed source/receipts, current-read age,
F2P, uncertain episodes, legacy costs, partial reservations and conservative abstention. Paused
worker/client queues test real refresh orchestration without a game or network sender. Existing
terminal/collection, recovery, duplicate, tax and accounting transaction tests remain enabled.

See the newest HANDOFF checkpoint for exact build, CI, read-only and installed-runtime receipts.
Offline tests are not live acceptance. Never seed a live read-set, edit a database, resolve an old
unpriced sale from chat, or clear a historical record to obtain a BUY.
