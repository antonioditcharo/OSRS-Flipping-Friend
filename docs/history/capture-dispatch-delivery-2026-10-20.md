# Capture before dispatch delivery — 20 October 2026

This batch closes a pre-outbox loss window: copied authenticated callbacks could be discarded by a
logout generation change or rejected/stopped worker before local persistence. Original advice and
exit context now commit with the callback's event in the existing forced atomic outbox; only HTTP
delivery waits for the worker. Retry wakeups retain backoff and concurrent prefix ACK semantics.
Nine new tests include four reproduced old failures. [Contract](../architecture/capture-before-dispatch.md).

Actual source/PR/exact-head CI, standard build, artifact/backup/task checks, source/financial/anchor
preservation and installed-versus-queued state are recorded in ignored
`local-data/capture-dispatch-20261020/delivery-state.json`. Read it before claiming delivery.
Personal evidence stays in local-data. Previously installed PR #129 includes five reversible review
archives and 25 retained-purchase reviews; these and all original financial/risk data are preserved.

Owner defers login and GE actions until 20 October after 17:30 local; do not request them beforehand.
Controlled trade-loop/restart, sustained event-to-advice/local-disk latency, complete production-policy
account replay, qualified cadence returns and chronological learning promotion remain open.
