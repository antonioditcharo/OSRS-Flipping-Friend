# Durable offer persistence and retry

The local outbox previously replaced its file without a forced write or atomic rename. A failed
enqueue discarded the valid observation it had just built. Later progress/EMPTY could then receive
the lost sequence or a different session, omit a fill, or lose the original prediction/exit binding.
An already drained replay timer also stayed dormant after that local failure. These are distinct
from an HTTP failure after an event has committed locally.

Valid unsaved appends now retain their complete immutable events in an account-scoped in-memory
tail, including original IDs, session, sequence, counters and prediction/exit binding. Further valid
callbacks append behind it, even while storage is unavailable. Invalid later frames do not consume
sequences or replace that tail. Profile switches cannot write it into another account. The worker
retry can commit it without another game callback or a financial-history replay.

An outbox commit writes the whole document through a FileChannel, requests force(true), then
requires ATOMIC_MOVE/REPLACE_EXISTING. It checks the destination against the loaded committed image
both before writing and at replacement. A changed/missing/replaced destination is not overwritten;
the retained evidence stays blocked for verification. Unsupported atomic replacement or a failed
write preserves the previous document. No non-atomic fallback is permitted. ACK removal uses the
same forced atomic boundary; failed removal leaves the durable sources available for duplicate-safe
delivery under their existing IDs. File format and legacy delivery keys are unchanged.

Ordinary pending/session/eligibility reads cannot expose only the old prefix while a tail is unsaved,
and do not retry forced writes on their calling UI thread. A local enqueue failure invalidates cached
ledger/entry authority and rearms the existing bounded worker retry. Pause/profile/lifecycle and
transport-backoff fences remain. Companion collection reads check account/lifecycle and pending
observations before and after HTTP, allowing current local fallback collection rather than an older
companion slot instruction. Account cash/eight-slot publication still requires all sources ACKed.

Successful commits survive restart. An observation that has never reached writable storage remains
memory-only; process death during that outage cannot be claimed durable. Advice remains withheld
instead of claiming that unsaved evidence was delivered, inventing a baseline, or repairing history.
The existing durable document, acquisitions, historical gaps, original risk anchor and learning
identities remain intact. Login, placement, cancellation and collection remain manual.

Ten added behavioral tests cover failed first saves/fills, later EMPTY, original plan binding,
account switches, invalid later frames, destination changes, read-thread isolation, retry wakeups and
HTTP collection fences. Eight failures were reproduced before the corresponding fixes. Existing
batch/ACK/duplicate/forecast/account/source and financial-transaction tests remain required.
See [delivery](../history/offer-persistence-delivery-2026-10-20.md) for actual source/install state.
