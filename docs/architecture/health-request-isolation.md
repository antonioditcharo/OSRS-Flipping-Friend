# Companion health and bounded account work

The starter's authenticated unscoped `/v1/health` checks process/protocol and public-market health.
It does not establish account readiness or permission to trade. Account health retains account
initialization and the same scoped service checks.

Live thread captures on 21 October showed both HTTP workers occupied by account requests: one was
initializing an account and scanning execution diagnostics under the account registry lock, while
the other waited for that lock. A later capture showed progress into the performance read. Health
requests timed out behind this work, including the new 60-second starter retry. This was observed
blocking, not proof of a deadlock or a measured query-duration bound.

HTTP transport dispatch now leaves unscoped health on its own path. All other requests, including
scoped health, pass authentication before entering the existing two-worker account concurrency.
The pending queue is bounded to 64. Overload returns protocol-tagged HTTP 503 without accepting or
acknowledging an event. The client must retain and retry its original durable identity. Existing
account path/header, canonical ordering, duplicate and financial authority checks remain in handlers.
Closing the server closes exchanges and stops both executors.

Routine calibration refresh only selects the already-required neutral public-price baseline. It
does not scan historical diagnostic evidence during account initialization or market refresh.
The explicit account learning read still freezes and evaluates its full coherent source prefix;
no evidence is deleted, relabeled, cached as fresh, promoted, or replayed financially.

Behavioral regressions hold both real account requests at initialization while checking health and
authentication, exercise bounded overload followed by exact-identity retries and canonical fills,
and prove that only explicit diagnostic reads scan execution sources. Existing scoped API and
source snapshot tests remain required. Runtime installation requires the standard verified paired
update; source tests and a successful global health probe do not establish controlled trade acceptance.
