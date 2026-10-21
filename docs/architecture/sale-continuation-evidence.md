# Preserve accepted sale continuations

21 October 2026. Read the current HANDOFF receipt for verification, merge, installation and manual
acceptance; these are separate states. This batch fixes two reproduced cancellation/relist failures.

A retained sell hint could acquire its first verified maintenance source after a later fill. Its
progress clock was compared against the hint's original creation time, making the saved hint
invalid. The next observation could remove it, and restart could refuse its document. Additive
`saleRepriceAcceptedAt` now records the later acceptance independently. Original creation, episode,
placement and progress remain unchanged; progress must precede acceptance. A repeated binding
cannot move those clocks or replace its source. Serialization validates active and retired evidence
before atomic replacement; failed writes withhold guidance and preserve the committed document.

Compatible old bindings retain their existing creation-time bound without a new acceptance claim.
Source-free legacy hints can receive a newly accepted, verified source. Already malformed bindings
are preserved and refused; the update does not manufacture historical acceptance or repair files.

Final relist authority also mistook a heartbeat from an already-present second same-item sale for
a newly started replacement. It now snapshots the latest recorded slot observations preceding the
exact accepted source, using durable prefix order even within one second. Only authenticated,
valid sales on that board qualify. Their matching episode/session/slot/price/quantity/placement
may progress through the lifecycle reducer without replacing the first sale's remainder. EMPTY,
slot reuse, changed orders or invalid observations permanently revoke the exemption. Newly started
same-item sales still supersede the old promise, including before collection; new accumulation
retains its separate refusal. Missing evidence never grants an exemption.

Paired sale policy is `companion-sale-5`; old policy replies are refused by the client. Replay is
`account-replay-12`; old bundles require their retained evaluator. Current canonical acquisitions,
physical inventory, already-listed quantities, exact pooled/subset cost, original plans and current
price/final authority remain mandatory. The canonical regression preserves a 201-GP remaining pool
after the parallel sale disposes one unit; under-cost and excessive-quantity relists remain refused.

No financial/source identity, database row, acquisition, profit, risk anchor, historical unknown,
personal ownership or learning policy is rewritten. Login, cancellation, collection and placement
remain manual. Controlled trading/restart acceptance, sustained latency and full parity remain open.
