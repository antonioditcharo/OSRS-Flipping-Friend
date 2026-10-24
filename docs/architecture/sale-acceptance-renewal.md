# Sale acceptance renewal

Fresh PR #153 post-login observations passed eight-slot/ACK/preservation checks and produced
new authorized maintenance receipts, but the panel withheld them at "Saving this trade workflow".
The retained first acceptance referenced an older observation of the same unchanged offer.
The prospective staging adapter incorrectly required source ID and sequence equality across renewal.

The current presentation must match a fresh well-formed receipt for its exact order, account and
current canonical source. Pending observations still withdraw presentation. Retaining an original
acceptance additionally requires its receipt to match the current economic episode: same account,
session, slot, offer identity, item, standing price, total and placement clock, with nondecreasing
source order, observation time, fill counters and proceeds. Equal source sequences cannot acquire
a different event ID. A future original decision cannot qualify a past renewal.

Unchanged GE editor cleanup and closing setup must not reaccept an already committed visible
instruction. Only a change in the displayed instruction triggers presentation acceptance there.
Normal engine delivery still validates and commits new instructions; cleanup does not renew the
original advice lifetime or bypass account/source invalidation.

Heartbeat sources and partial progress may advance within that episode. They do not replace the
first acceptance, receipt, accepted ordering, patience or cancellation provenance. Existing bound
outbox acceptance remains idempotent. A new quote still requires independent final authority;
this adapter cannot create a receipt, manufacture a stop, repair a receipt-free cancellation or
change any financial/source row. Slot reuse and old exact receipts remain rejected.

Behavioral regressions use real capture, durable continuation/outbox, ACK, heartbeat, partial
progress and client restart, then verify the unchanged original acceptance. Pending or superseded
receipts and reused slots refuse. Standard clean Windows/Java11/tooling and exact-head Windows/Linux
CI precede one paired update. Fresh actionable guidance and controlled cancellation/collection/relist
remain separate live acceptance; passing event delivery alone does not satisfy them.
