# Ordinary sale queue delivery

Read `local-data/ordinary-sale-queue-20261021/delivery-state.json` for source verification, exact-head
CI, merge, actual installation and remaining manual acceptance.
[Contract](../architecture/ordinary-sale-maintenance-queue.md).

Ordinary sell maintenance now retains later eligible reductions when final authority refuses its
first proposal. The existing authority/delivery path tries the queue and commits a continuation only
for the instruction actually presented. Each proposal retains its own slot/episode, exact remaining
quantity, subset cost and final pricing checks. No price, financial/source identity, risk anchor or
original acquisition plan is changed by candidate enumeration.

The previous installed #135 explanations and fresh eight-slot/empty-outbox proof remain in the
[prior receipt](sale-maintenance-diagnostics-delivery-2026-10-21.md). Native controlled trade-loop
acceptance, sustained latency, complete replay/qualified account returns and validated forecasts
remain pending independently of source tests and installation.
