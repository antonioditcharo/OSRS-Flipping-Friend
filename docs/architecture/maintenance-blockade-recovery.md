# Maintenance and durable-delivery blockade recovery

22 October 2026. Actual verification, merge and installation are recorded in
`local-data/committed-maintenance-acceptance-20261022/delivery-state.json`. Source fixes and server
responses do not establish controlled player acceptance.

The live investigation found a growing unacknowledged outbox, stale account evidence despite
client activity, and repeated replacement/entry waits. Thread dumps showed Performance aggregation
holding both account service and writer monitors while an offer update waited. A report response
could also occupy an account transport worker. Separately, a raw replacement BUY could win before
listed maintenance, then final replacement refusal skipped directly to entry waiting. Repeating
that path could indefinitely hide eligible maintenance on existing sell orders.

After all fresh replacement candidates and supported acquired-stock exits are refused, the client
now examines existing working offers for stops and ordinary maintenance. It does not retry the
refused entry or create an unseen continuation. Existing generation/revision, current prices,
exact subset costs, original stops and final sale authority still govern every displayed action.
Later sale alternatives retain their normal final-authority queue. Stale prices and recent fills
still withhold routine maintenance.

Read-only Performance, trade-review and learning HTTP requests use one separate report worker and
a bounded queue. Report overload refuses the read; it cannot acknowledge an event or occupy both
account workers. Authentication, account scope, event identity, duplicate handling and authoritative
entry/maintenance requests retain their existing boundaries.

Performance captures immutable source, financial, session and archive inputs under its read
transaction, then releases service/writer monitors before aggregation. Stock/account/market inputs
are captured before releasing the service monitor. A concurrent execution does not enter an already
captured report; a later report includes it. The completion index processes only appended source
tails while the account, source generation and financial cursor remain unchanged. It retains
disposal/completion metadata rather than all heartbeat records. Financial changes, rollback,
external writes and prefix replacement invalidate the relevant cache. Recorded-order terminal
completion, future clocks, unknown costs, partial updates and tax/profit ranges retain their prior
semantics. No learning or authority uses this reporting cache.

A separate reproduced callback failure is also fixed: after durable sale-policy acceptance, a
failed write of later counter facts must not hide the earlier committed proof from the outbox's
first cancellation. The reader exports only the exact verified committed document, never dirty
counter facts or a failed prospective acceptance. Unreadable/replaced documents and foreign accounts
still refuse. Original source identities, exact quantities/costs and first-cancellation/version
fences remain unchanged. Sale policy 9, replay 18 and evaluator 5 are unchanged.

Regressions cover actual client callback/outbox behavior, canonical acquisition through exact relist,
partial-fill write failure, ACK/restart, source/account refusal, replacement starvation, concurrent
report/event delivery, report backpressure, rollback and external cache invalidation. The completion
index is compared against the former full scan across mixed forward/rollback clocks.

Remaining gates include fresh authenticated post-update source/ACK/guidance evidence, controlled
player trading/restart, sustained latency and complete production replay. First-cancellation capture
through a genuinely unreadable acceptance document requires separate prospective evidence handling;
later acceptance cannot repair already published receipt-free cancellations. Reassessment of BUY
maintenance after every routine-sale refusal and safe reachable-proof retention remain separate
work. Preserve every financial/source row, existing gaps, unknown context and original risk anchor.
