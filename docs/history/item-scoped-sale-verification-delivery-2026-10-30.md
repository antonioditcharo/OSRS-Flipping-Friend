# Item-scoped sale verification and cache delivery

Local paired #203 installed `2026-10-30T16:04:47.2632616Z`, source
`7278eb95e5105e12ed2aefca1cd8ca8b17dc36b1`, merge `f89ee240173b04be88b2f46cb28194ba161cac1f`.
Final delivery-documentation CI/merge is recorded in the PR/currentDelivery; no further runtime
replacement is required for documentation. Hosted #194 and the $50/month ceiling are unchanged.

Initial/working sale decisions omit historical cancellation receipts they cannot consume.
Relists use item-specific receipt inputs while retaining complete canonical history. Relevant
receipts still prove their exact original source, account payload/hash, eight pinned sources,
ordering and first binding. Current stock, exact acquisition allocation, physical inventory,
original plans, tax/floors and market/source freshness remain checked. Corrected prices still
need a second complete authorization. No authority result or financial outcome is cached.
The full historical reader remains available for replay and equivalence.

Public-cache IO streams the unchanged gzip JSON, retaining clocks, TTLs, exact integer/null
values, duplicate ambiguity and cold-start fallback. No plugin production class differs from
#202; changes are confined to companion verification/cache code and its debug-line metadata.
No new financial endpoint, schema, source identity, acknowledgement, risk or pricing policy.

Clean 2,576 Java checks/six existing skips, 177 tooling, 110 actual paired-JAR Java 11 checks
and own bytecode 55 pass. Both exact-head CI platforms `41076599809` pass. Focused regressions
cover full/narrowed decisions, quantities/cost floors, original targets, corrupted and wrong-item
claims, receipt edits, first bindings and concurrent WAL progress. The first Java 11 diagnostic
classpath omitted the required plugin JAR; retain that failure. The corrected paired-JAR run passes.

Fresh uninterrupted synthetic hour: 3,614.234 seconds, 121 groups, 9,541 checks and 9,699
instrumented requests; zero test, transport or telemetry failures. Both completion markers,
all 1,302 frozen input hashes and packaged-own-class equivalence, including core, verify.
Client completed 60 rounds, companion 61. Retain 119 native resource samples, minimum 9 MiB
available memory and uncontrolled background load; the idle build daemon was stopped normally.
No retries, relaxed deadlines or filtering of failed/outlying observations. This does not qualify
native foreground latency, player operations or account profit.

The frozen account has 194,329 offer observations and 90 policy receipts. All 58 receipt-bearing
items preserve financial and first-binding results; database bytes are unchanged. Twelve
interleaved warm reads per mode have median full-reader 141.148ms, initial 34.519ms and item-relist
35.101ms. These are read-stage diagnostics rather than complete authority or native timings.
All 600 histories/172,800 buckets round-trip at 96 MiB; old #195/#200 read identical values from
unchanged bytes. Original #197 export/score proofs remain reusable; no evaluation fold was opened.

Installed hashes:

- Plugin: `48afd0e9bc830b65e6ee6e38b79f11d6ab036a029cf6a89797baa325539fc215`.
- Companion: `5134c7ac3e5ec0b8203ac8aa0ac636bf3128bca1d824bc7f89237e7b390be830`.

Normal RuneLite Close succeeded; no game input or force-kill. The supported unattended updater
verified backup `20261030-080437-7983929c` and the installed pair. Disposable restoration of 365
files and six supplemental configuration copies passes. All 12 original financial tables are
identical before new login. Old #195, old #202 and installed #203 return equal ledger, exact
stock, scope, exposure, continuation and original-risk reads on the new restored account at the
existing 768-MiB runtime ceiling; database bytes are unchanged. The unassigned legacy database
has a separate read-only snapshot/restore and remains unassigned. Protected task XML/permissions
are unchanged. Status, launcher CheckOnly and GET-only pre-login pipeline pass; fresh cash/slot
proof still requires the player. RuneLite reopened; login and every trade action remain manual.

#195 remains immediate code rollback, #186 the economic reference; #202 is retained. Code rollback
preserves subsequent trades. No live database restoration, financial replay, loss reset, new loss
authority or pricing/learning promotion. All 81 original requirement IDs remain, with 79 open
including the partial campaign. Fresh MAIN/editor latency, idle/mixed, sustained and controlled
trading acceptance continue on #203. Prior #202/#200 timing failures and unobserved live
post-cancellation late fills remain explicit; currentDelivery records the new session evidence.

Private proofs: primary `local-data/sale-authority-20261030`; earlier live #202 evidence remains
`local-data/live-202-20261029-2005`. See the [sale contract](../architecture/item-scoped-sale-verification.md)
and [cache contract](../architecture/streamed-public-history-cache.md).
