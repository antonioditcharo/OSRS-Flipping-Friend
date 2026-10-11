# Account isolation and restart recovery

Implemented after the takeover assessment on 11 October 2026. The supported runtime now routes
live accounting to explicit account scopes, with separate SQLite files and service instances.
Public prices/history remain shared. This does not yet make companion positions the only source
for the plugin's local journal, position UI, or exits; that is the next migration.

## Scope and transport

`AccountIdentity.fromProfileKey` hashes the RuneLite RS profile key with SHA-256 and prefixes the
result with `rs-`. The opaque identity is stable, does not expose the profile key, and is validated
before constructing any filesystem path. It does not claim to synchronize accounts across devices.

Both `AccountSnapshot` and canonical `OfferEvent` payloads carry `accountId`. Account requests use
`/v1/accounts/<accountId>/...`, the existing loopback token, and `X-Flipping-Friend-Account`.
The path, header, payload, and selected service must agree. Account responses confirm the identity
and `X-Flipping-Friend-Protocol: accounts-v1`. Missing/mismatched scope fails closed.

The separate endpoint namespace is intentional: a newer plugin talking to an older companion
receives 404 before an accounting mutation can reach the old global routes. Update both supported
JARs together. An older plugin talking to the new companion cannot submit unscoped account events.

The public market routes remain shared. Health can describe the market before a player logs in;
when an account header is present it reports the selected account's health. Recommendation caches,
canonical offer caches, and in-memory advice are cleared when the profile changes. A response from
an earlier profile cannot prune the current outbox or become its plan.

## Persistence and legacy data

- Live accounting: `companion/accounts/<accountId>/flipping-friend.db`.
- Shared market/model housekeeping: `companion/market-state.db`.
- Previous unscoped file: `companion/flipping-friend.db`, preserved and unassigned.
- Existing plugin account JSON and journals retain their established locations.

Each account gets its own offer/position projections, buy-limit ledger, event deduplication,
replacement intentions, execution statistics, model tables, planning worker, and cached plan.
No SQL schema rewrite is needed to change every old primary key: physical database isolation
preserves the existing transaction and rollback semantics inside each account.

The shared service owns ingestion and the history cache. One warming worker consumes the union
of account shortlists. Account services do not close or independently refresh that shared feed.
Existing unscoped rows cannot be attributed to a player reliably, so startup does not migrate,
prune, or assign the old database. Explicit reconciliation/import is required for that history.

## Outbox and initial state

The existing per-profile outbox gains a durable account identity. Legacy pending events in that
already account-scoped queue gain the current profile's scope without changing event, session,
offer, or sequence identity. The upgraded queue is saved before delivery. Foreign scope, malformed
identity, duplicate identities/sequences, corrupt state, and unsupported event schema fail closed.
The directory is resolved from a single profile read and retained for the whole write; changing
profiles during enqueue cannot redirect an earlier account's state into the new account's file.

Before publishing an account snapshot, the plugin publishes current offer observations and waits
for pending offer acknowledgements. A freshly scoped database can establish the offer high-water
marks before reconciling filled stock from the snapshot. Unknown acquisition cost still requires
positive reconciliation evidence; it is never replaced with zero or a current quote.

## Restart behavior

Active and uncollected offers are reconstructed from durable current projections, including offers
older than the four-hour buy-limit window. Four-hour expiry applies to allowance, not ownership,
slot occupancy, or collection. Restoration retains original offer/session/sequence/source-event
identity, item, price, quantities, spent high-water, and recommendation identity. Missing historical
prediction/quote detail is not manufactured, and execution statistics are not replayed.

Durable reconciliation faults are restored too. Plans wait for a fresh account snapshot rather than
using a previous account's cash or positions. Current UI exits/journal still have their local source;
account isolation is a prerequisite for replacing that remaining split authority.

## Verification

Tests cover identical item/slot/event identities in two accounts, independent quantities and cost,
duplicate delivery, independent execution statistics, restart after nine hours, preservation of
unassigned legacy records, missing/mismatched HTTP scope, confirmed response scope/protocol,
profile-cache clearing, late acknowledgement rejection, outbox upgrades without identity changes,
and the initial-partial-offer/snapshot/subsequent-fill sequence without double-counting stock.

`tools/pipeline-check.ps1` now performs read-only checks of the supported artifacts, running protocol,
market health, and scoped account actions/plans. It no longer posts fictional live-account snapshots
or assumes the former global database is the active ledger.
