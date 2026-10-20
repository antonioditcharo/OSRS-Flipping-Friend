# Fresh stops and retained trade continuations

A durable replacement promise protects a healthy partial purchase from being sold before its
replacement. It must not suppress a fresh, supported decision to leave that acquisition. Previously
the client and account replay skipped the held stock as soon as a buy continuation existed. A
working partial buy could keep accumulating through its stop, while a collected partial could
receive a new buy instead of a stop sale. Original acquisition cost/target/stop remain authoritative.

The exit decision now distinguishes these cases before applying the continuation hold. A fresh CUT
on a working buy requests cancellation first; no sale is proposed around an active accumulation.
A collected partial with a fresh CUT can be sold, bounded by current inventory and canonical
acquisition remainder. Healthy, stale, future-priced and unsupported decisions retain the original
replacement workflow. Merely proposing a stop does not discard its saved promise.

Client-thread acceptance of a managed stop sale durably supersedes that item's old buy promise
before recording/notifying the sale. A failed commit withdraws the instruction and retains the
existing durable image; later retries commit the removal. Stop cancellation already uses this
acceptance boundary. No old rebuy may return after a successful supersession/restart. This is
workflow state only: no fill, disposal, cost, profit or financial identity is created or rewritten.
The final companion authority still checks current source, quantity, price, tax and loss policy.

Fresh stop relists, unlisted exits/own-buy cancellation and listed sale stops precede ordinary
relists, replacement purchases and ordinary maintenance. A dedicated stop-only maintenance pass
does not perform buy housekeeping or reset ordinary price stability. It bypasses an earlier ordinary
write backoff when new fresh evidence now supports a stop. Ordinary relists still finish ahead of
ordinary exits/entries, and their exact retained quantities survive another item's urgent exit.
Healthy listings retain the patient-pricing contract; no quiet/old price becomes loss authority.

Account replay policy **account-replay-10** follows the same distinction, supersession and listed
stop priority through delayed actions, collection, partial fills, absence and restart. Its frozen
bundles remain synthetic public-volume-share scenarios, not measured account returns. Old V9
bundles require their retained evaluator. Complete real editor/transport/attendance coverage,
qualified market benchmarks and learning promotion remain open.

See [the consolidated verification and delivery receipt](../history/stop-continuation-delivery-2026-10-20.md).
