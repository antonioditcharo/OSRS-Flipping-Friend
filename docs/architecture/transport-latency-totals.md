# Completed transport prefixes

26 October 2026. This client diagnostic closes a denominator gap in slow-request logs; it grants
no trading, financial acknowledgement, reconciliation, learning or loss authority. Installed
runtime and queued source remain separate in HANDOFF. Existing successful-guidance delivery
timings and slow-request logs remain distinct measurements.

## Coverage and meaning

Every request that passes existing account/credential preflight and creates its HTTP connection
object receives one in-memory ticket. Its finally block records completion, including fast
successes, fast HTTP refusals, connection failures and rejected scoped responses. Preflight,
connection-object creation, queued/in-flight/uncompleted requests, caller DTO parsing, durable
acknowledgement acceptance and panel delivery are outside this completed-transport prefix.

Rows partition completed attempts into body-read-and-scoped 2xx, failed 2xx, HTTP failures and no
response. Body-read-and-scoped means the body was read and the existing account/protocol checks
passed (public market/global health calls need no account protocol). It does not assert valid
typed JSON, an accepted financial event or displayed advice. A 202 `{}` can count in that category
while the original financial outbox remains pending. Retries remain separate transport attempts
of the same durable source, never new financial effects.

Latency runs from immediately before HTTP request configuration through disconnect. Nonnegative
durations, including failures, enter a fixed histogram; invalid timing is separately counted,
never replaced by zero. Reported p95 is a histogram bucket's upper bound, not an exact percentile.
An upper bound beyond 60 seconds remains unknown with an explicit overflow flag.

## Isolation and bounded work

One random measurement identity belongs to each CompanionClient instance. Windows carry a local
sequence and a diagnostic account epoch captured atomically with the checked scope. Account
switches, including A-to-B-to-A, use separate epochs; this epoch is neither the capture writer
generation nor a financial identity. Late responses retain their original ticket/window and
cannot replace or contaminate the current account's counters. Public health/market requests use
one separate GLOBAL window. Unsupported-price repeated invalidation does not repeatedly advance
an unchanged diagnostic epoch.

Only the active account window and GLOBAL window are retained, plus windows still referenced by
in-flight tickets. Nine fixed route groups and sixteen latency buckets bound each window. Report
rows are copied under the same short lock as increments. No arbitrary URL/query, body, credential,
item, quantity or cash value is retained by the diagnostic; account scope is the existing hash.
No per-account history/map, filesystem writes, network calls or scheduled worker is added.

Each window logs its first completed prefix, then at most one summary per sixty seconds when a
later request completes. These are cumulative prefixes: never sum overlapping reports. Compare
histogram/count differences only within the same measurement identity and window. A switched,
shutdown or quiet window's unlogged tail is unknown; this is not a whole-session denominator or
proof that no requests remained in flight. GLOBAL rows cannot establish account performance.

Optional diagnostic serialization failures are caught after preserving request cleanup. Existing
timeouts, retries, outbox writes/order, typed response validation, guards and UI waits are
unchanged. No cache is made authoritative and no stale advice is kept to manufacture availability.

## Verification

Deterministic tests cover outcome/timing partitions, rate-limited cumulative prefixes, p95 bucket
boundaries/overflow, invalid clocks, concurrent snapshots, fixed cardinality, defensive snapshots,
privacy and old-account completion. Real disposable loopback tests cover 200, bad scope, 503,
connection failure, missing-credential preflight and A-to-B-to-A isolation. Existing malformed-ACK
and retry tests prove transport totals do not discard original sources or publish an unproven
frame. Tests do not call the live companion or game.

Clean Java/tooling, Java 11 and exact-head Windows/Linux CI remain release gates. Sustained live
prefixes, fresh ordered eight-slot acknowledgements and actual useful guidance are still required
before claiming the verification-wait problem resolved. User-operated trading acceptance remains
deferred while the owner is away.
