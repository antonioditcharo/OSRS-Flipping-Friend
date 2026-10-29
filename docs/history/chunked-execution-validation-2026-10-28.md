# Chunked offline evidence and workflow soak

28 October 2026, America/Anchorage. Installed local #195 and hosted #194 remain unchanged.
No financial/schema migration, account write, outbox acknowledgement, pricing promotion or JAR
installation occurs in this pass. #196 is merged at a4d37d6013dd722727cd01109b543c3d07bdc92c;
its tested head 1781a1a passes both CI platforms 40812821792 and all 155 tooling checks.

## Retained failures

The original #195 synthetic soak fails at 3,220.484 seconds (53.7 minutes) after 108 groups:
7,135 checks in completed groups, then 46 passes and one SocketException during fixture connect.
The original monolithic export fails at the 1-GiB heap in whole-snapshot Gson serialization.
Its 185,450 rows contain 276,793,276 raw payload bytes, already beyond the old 256-MiB cap.
Neither failure is acceptance. Three short instrumentation warm-ups are preserved separately.

A subsequent strict run stops after 18 group cycles per worker because the WMI/PowerShell
resource sampler exceeds its 25-second deadline. Its 2,394 tests and 2,790 fixture requests have
zero test/transport failures; incomplete resource telemetry still refuses this run's acceptance.
No retry removes any failure. Native read-only TCP/process/memory counters replace that sampler.
The original socket fault is not reproduced in production; production transport stays unchanged.
Native TCP tables do not establish unconnected Bound-socket counts or exact allocator pressure.

## V2 evidence and compatibility

New exports stream a single coherent read-only SQLite snapshot, with complete offer and RECONCILE
prefixes. Chunks hold at most 4 MiB or 1,024 rows; one eight-chunk cache serves both source types.
Disk-backed temporary indexes preserve identities, causal gap lineage and first-seen episode state.
The budget is 5 GiB with a 768-MiB free-space reserve; helper heaps remain capped at 1 GiB.
The manifest binds ordered chunk hashes/counts/cursors, account, capture, model/evaluator version
and retained artifact. It publishes last. Explicit exact metadata, corruption, foreign accounts,
duplicates, interruption, disk limits and concurrent WAL commits have refusing/equivalence tests.
V1 readers, raw payload strings, observation order, censoring and HORIZON_BOUNDS_V1 stay intact.

The initial full frozen-account export, independent verification, forecast audit and source audit
all pass at 1 GiB. Database size 886,173,696 bytes and SHA-256
0de8f764f714c09a6fc6a1179d2f347566c7631c736e60837035c18935cf17d8 remain unchanged.
All 185,450 offers and 1,389 reconciliation rows are retained. The old #195 artifact and V2 reader
agree on every source/observation/score digest: 917 observations, 485 eligible, 193 known complete,
38 known incomplete and 254 ambiguous. Unqualified and censored evidence is retained.
The source audit has 930 identities, 32 flagged and no unassigned problems; it authorizes neither
accounting nor trading. Counts cannot promote learning.

Final-source repeat (7f94d5a) passes again: freeze including verification 758.000s, independent
verification 115.250s, forecast audit 107.562s and source audit 260.047s. All use a 1-GiB heap;
the same database hash remains unchanged. The final reader also equals #195 on all four complete
source/observation/score digests. Retained evaluator artifact SHA-256:
37003879c7c66760bc6e0bf0d4f002ac5c900f4c72cab8da08c714e0de57b829.
All 357 plugin classes equal installed #195 byte-for-byte; no client update is required.
The final standard clean Java build passes 2,550 tests with six existing skips, including
cross-version chronological comparison. All 157 prior tooling checks plus four completion-guard regressions pass; the final full
161-test tooling run is part of the release gate. Own class bytecode is 55
(556 companion/core, 357 plugin/core); exact-artifact Java 11 passes 74 companion/core and seven
client checks. Source head 7f94d5a passes both CI platforms 40819785528. Delivery-head CI and merge identity are recorded in PR #197 and private currentDelivery.

## Player and latency evidence

The owner confirms sale revision and cancel/collect/relist work normally without stuck cards,
then performs a normal restart/login/GE check on unchanged #195. A read-only diagnostic observes
six partial-progress episodes among 2,258 new offer sources, with zero new source-linked
reconciliation rows. After restart all eight authenticated durable observations match the
account read set, slots and current session, again without new reconciliation rows.
No accounting, acquisition cost, target, stop or original risk anchor is reset.

Forty linked main-action presentations before restart have post-ACK p50 216ms, p95 878ms and
maximum 983ms. Repeated working presentations are included; healthy full-slot WAITs are separate.
The one SELL-to-WAIT is an explicit selected-editor override; its brief sale-verification sequence
is retained. Native cached rendering and selected-sale post-ACK timing remain unmeasured.
The first post-restart MODIFY_SELL takes 3,573ms total, 593ms capture/durable publication,
884ms plan/ledger and 2,094ms decision/delivery; post-ACK is 2,980ms. Keep this startup outlier.
The warm sample does not establish sustained/native acceptance. Live late fills remain unobserved.

## Strict hour and remaining acceptance

The replacement uninterrupted run PASSES after 3,601.062 seconds, started 29 October at
04:21:18 UTC: 60 cycles per worker / 120 group runs / 7,980 checks, zero test failures,
9,300 instrumented fixture requests and zero fixture transport failures. Both workers emit
matching completion markers; a zero exit without that proof is refused. A post-run audit verifies
the actual markers and raw log hashes, finding no socket/connection/resource-fault signatures.
All 119 native resource samples succeed (sampler p50/p95/max 31/62/93ms). Minimum available
physical memory is 12 MiB, maximum nonpaged pool 964,509,696 bytes and maximum committed memory
24,564,989,952 bytes; these pressure samples remain evidence, not a causal diagnosis of the original
socket failure. Whole test-group phase p95/max is 7,185/11,014ms client and 13,879/22,768ms companion.
These are aggregate regression-group durations, not player-visible publication latency.
The backend fixture worker uses one bounded test-only HTTP client, complete body cleanup,
two executor threads, a 64-task queue and eight idle connections. The client worker keeps the
actual production CompanionClient path; its requests are not counted by the fixture wrapper.
JDK HttpClient fixture retries and harness test retries are disabled. Production transport is
unchanged; its complete request time remains part of each test phase. Raw request counters, failures, phase times, sockets, handles and memory are retained.
No percentile filters erase cold work or failures. Delivery-head exact CI is tracked by the
linked PR. Native/selected-sale timing, sustained eligible latency and live late fills remain open; full-account repetition and the strict synthetic hour are complete.
Keep baseline suggestions; pricing and execution-first learning remain behind their promotion gates.

Private receipts are under primary local-data/optimization-pricing-20261026: chunked-full-account-v2-*,
chunked-full-account-v3-*, chunked-full-account-v4-*, prefix-equivalence-*, live-195-*, persistent-workflow-soak-v2 through v6
and the original exact-market-workflow-soak-* failure. strict-soak-v6-summary.json includes
resource ranges, coverage and the separate raw-log/completion-marker audit. currentDelivery in
local-data/hosted-pilot/delivery-state.json is authoritative over older private aliases.
[Format](../architecture/frozen-execution-evaluation.md),
[repair contract](../verification/offline-export-and-soak-repair.md).
