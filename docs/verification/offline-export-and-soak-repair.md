# Offline export and soak repair

Installed #195 remains unchanged. Tooling #196 records these failures; neither is acceptance.

The frozen #195 Java 11 mixed-workflow run began 28 October 2026 at 21:59:40 UTC and failed
at 3,220.484 seconds (53.7 minutes), with 108 group runs. Completed groups have 7,135 passing
checks; the final 47-check group has 46 passes and one failure. PromptGuidanceLoopbackTest
fails during request-body connection setup with SocketException: No buffer space available
(maximum connections reached?). The helper opens/writes before its cleanup region. Production
transport has not reproduced this fault. The host resource snapshot taken later is not causal proof.

The 1-GiB frozen export failed with OutOfMemoryError in Gson/StringWriter serialization before
creating its output. All 185,450 offer rows have 276,793,276 raw UTF-8 payload bytes (maximum
row 4,679 bytes); the existing file cap is 268,435,456 bytes. Increasing heap cannot repair that cap.
The source database and failed receipts remain intact in primary local-data.

Required replacement: V2 chunked complete read-only snapshot; unchanged V1 reader; 4-MiB or
1,024-row chunks, eight-chunk cache, 5-GiB bundle limit, 768-MiB free-space reserve and 1-GiB
helper heap. Bind account, ordered chunk hashes/counts/cursors and evaluator artifact. Publish
manifest last. Stream validation with disk-backed identity, gap-lineage and episode indexes.
Prove V1/V2 observation, eligibility, censoring and score equivalence, WAL snapshot consistency,
corruption/interruption/disk refusal and full frozen-account export/verify/audit without DB changes.

Repair test cleanup independently of host pressure; one bounded reusable loopback client per
persistent worker. Record sockets, handles, memory, failures and phase timings throughout the new
uninterrupted 60-minute run. No retries may erase failures or improve measured percentiles.
Native rendering, eligible publication, player sale revision/partial/late-fill/cancel/collect/relist
and restart remain open. Pricing and learning stay in shadow. Preserve every prior acceptance ID.

## Replacement source and measurement contract

The persistent runner is tools/run-workflow-soak.py with tools/fixtures/WorkflowSoakWorker.java.
It freezes both test classpaths and the Java 11 worker, records their hashes, runs one reusable
bounded HTTP client per worker (two executor threads, a 64-task queue and eight idle connections), and retains every phase/failure plus socket, handle and memory
samples. Request counters and cumulative request time are included each cycle. JUnit has no
retry; any test or transport failure ends the run. Host telemetry failures also refuse acceptance.
The worker launch sets jdk.httpclient.redirects.retrylimit=1 and disableRetryConnect=true before
HttpClient initializes: the [OpenJDK 11 transport](https://github.com/openjdk/jdk11u/blob/master/src/java.net.http/share/classes/jdk/internal/net/http/MultiExchange.java)
otherwise permits automatic retries. Production transport remains unchanged.

Short instrumentation warm-ups are retained separately; neither satisfies the hour gate.
The acceptance run must use the complete request telemetry and explicit no-retry launch flags.
The full-account V2 export/verification/audits and previous #195 reader equivalence remain pending
until their actual receipts complete. Fresh #195 live main-action delivery sample: 40 linked
post-ACK observations, p50 216ms, p95 878ms, maximum 983ms; repeats of working actions are included.
One SELL-to-WAIT is the explicit selected-editor override. Native rendering, complete controlled
player workflows and sustained latency remain open. This sample cannot activate pricing/learning.


## Completed replacement gates

The final source passes the standard clean build: 2,550 Java cases, six existing skips, own
bytecode 55 and 81 exact-artifact Java 11 checks. Final-source full 185,450-offer/1,389-gap
export, verification and both audits pass at 1 GiB with unchanged database bytes and all old #195
source/observation/score digests equal. No runtime installation or production transport change.

The strict replacement hour passes after 3,601.062s: 120 group runs, 7,980 cases, zero test
failures, 9,300 instrumented backend-fixture requests with zero transport failures and 119 native
resource samples. The client worker retains actual production transport, outside the fixture
wrapper's request counters. Both completion markers and raw logs verify; a zero exit alone cannot
prove a full run. The original socket/OOME and WMI-sampler failures remain retained. Native
rendering, selected-sale timing, live late fills and sustained eligible-action acceptance remain
open. [Current release receipt](../history/chunked-execution-validation-2026-10-28.md).
