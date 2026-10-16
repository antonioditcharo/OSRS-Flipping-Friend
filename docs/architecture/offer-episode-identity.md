# Offer episodes remain distinct after collection

16 October 2026. Prospective accounting guard. Existing historical records are not repaired.

## Observed failure

The retained source prefix contains a terminal observation, EMPTY, then the same old terminal
identity with a new delivery event ID. The slot reducer had discarded the old episode when it
became empty, so its cumulative filled quantity became another acquisition or disposal. Delivery
UUID deduplication alone cannot prevent this. Restart must not erase the episode evidence.

An active zero-fill order after a zero-fill cancellation also reused the retired local identity.
Second-resolution slot/start/item keys can collide between genuinely new orders. A backend guard
must distinguish new episodes without renaming legacy history or recounting learning samples.

## Producer identity

Newly created tracked episodes receive a random `ge-<UUID>` identity once. It survives observation
copies, Gson persistence, account reload, terminal callbacks and late replays of settled offers.
An active zero-fill observation after a collected offer starts a fresh episode even if the retired
offer filled zero units. Identical settings or the same first-seen second do not reuse its ID.

Existing saved offers without the additive field retain their original slot/start/item wire key.
Existing outbox events and prediction bindings are unchanged. ExecutionRecorder's shipped learning
deduplication key remains unchanged; this feature must not recount legacy execution statistics.
First-seen time remains observational metadata, not proof of placement or exact fill timing.

## Durable acceptance boundary

After inserting a new canonical delivery inside the existing transaction, the store checks a
nonempty observation that would start or switch the current slot episode against retained canonical
source identities. SQLite JSON filtering and descending row order avoid parsing every source in
Java or following a randomly ordered UUID index. Ordinary unchanged continuations do not scan
history. There is no cross-request cache, new index/table, migration or background replay.

A previously recorded identity cannot start another episode after collection, in another slot,
or through current-slot recovery. A missing nonempty identity, malformed JSON or non-object source
cannot prove that a new identity is unique. Reuse or incomplete identity history produces normal
RECONCILE evidence with no acquisition, disposal, tax/profit or buy-limit increment. Its persistent
reason prevents a later authenticated baseline or counter update from bypassing that rejection.
Event, projection, guard evidence, accounting and limits retain their shared transaction/rollback
boundary. Re-delivering the same event UUID remains a no-op.

Scope follows all account-local items already associated with the reused identity, including across
a terminal/clear boundary. Contradictory metadata cannot clean the earlier item by changing its name.
Unknown source scope remains global. This scope is not an exposure valuation or buy permission.

Rejected transitions also withdraw that slot from the companion's active-offer advice cache. A prior
Collect card must not survive contradictory durable evidence. This removes cached guidance only;
it does not assert a collection, dispose stock or change the recorded source. Restart already
restores only valid active projections and must agree with the live service result.

## Verification and limits

Regression-first commits: a63a534 (five durable replay/accounting failures), cf94779 (two producer
identity failures), 86137e3 (contradictory item scope), and 1357b42 (stale Collect advice). Positive
service/restart coverage in 7d09a08 retains the existing rejected-terminal boundary. Disposable-store
and paused-outbox cases cover both trade sides, exact quantities/costs, tax/profit ranges, limits,
zero-fill cancellation, distinct identical orders, restart/retention, duplicate delivery, malformed
history, transaction rollback, copies/persistence and unchanged legacy keys.

Some older fixtures stored placeholder strings instead of canonical JSON or reused one identity for
an intentionally new episode. Their setup now serializes real source events and distinguishes new
episodes. The deliberately opaque-source fixture remains explicit and retains account-wide guards.
Financial/recovery assertions are preserved. No live data is normalized to match a fixture.

Read HANDOFF.md for standard verification, exact-head CI and installation status. A passing pure
read-only query probe is not live ingestion or restored trading acceptance. This change prevents
new duplication and stale advice; it does not correct old overcounts, backfill missing acquisitions,
price old sales, reset risk or release already affected stock. Those remain in issue #70. Batch the
paired runtime update with remaining recovery work; login and every game action remain manual.
