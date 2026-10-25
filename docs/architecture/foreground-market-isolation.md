# Public market isolation and disposable-restore headroom

Follow-on to installed PR #167. Its phase logs distinguished real HTTP stalls from client capture
and queueing. Current delivery is in `local-data/hosted-pilot/foreground-market-delivery.json`;
the installed #167 preservation and timing receipt remains `ack-latency-delivery.json`.

## Foreground request ordering

Public snapshot/series GETs return shared market information, but previously entered the caller's
serial financial queue whenever the plugin supplied an account header. A delayed cache read or
slow public response could therefore hold an offer acknowledgement behind optional feed work.

Use two dedicated public-feed workers with an eight-request bounded queue. Keep authentication
before dispatch, existing public payloads and market freshness rules, separate report workers,
and unchanged FIFO/one-running-task-per-account financial queues. Feed overload returns HTTP 503
without acknowledging a financial event. Global health retains its independent path. Slow API
logs distinguish queue/handler time and request category without logging credentials or payloads.

Regression coverage stalls a real public series request and requires a real offer ACK while the
feed remains blocked. A separate saturated-feed test preserves authentication, duplicate delivery,
financial writes and global health. No financial ordering, cost, risk, ownership or learning rule
changes. A basic isolated TCP probe did not justify a TCP-setting change; it remains unchanged.

## Disposable restoration

An actual disposable restoration exhausted disk space; original backup and installed account rows
remained intact, and a fresh 57-file restoration subsequently verified. Preserve the failed partial
copy as unverified evidence. Never treat a partial directory as a verified restore or overwrite it.

Before hashing or copying, budget all source files at their logical uncompressed sizes plus 768 MiB
of free runtime-volume headroom. Find the existing destination ancestor without creating it. Known
low space refuses before touching backup or destination; concurrent external disk use can still
invalidate a preflight. Original JAR/config/account backups and immutable trade evidence are retained.
Transparent NTFS compression changes storage allocation only; verify retained bytes separately.

Run the clean Java/tooling suites and exact-head Windows/Linux CI, then the supported paired update
with preserved #167 rollback. Verify current source/financial prefixes, restoration/rollback reads,
fresh eight-slot ACKs, useful guidance and measured foreground timing. Sustained player trading and
restart, hosted advice/coordination activation and learning promotion keep their separate gates.
