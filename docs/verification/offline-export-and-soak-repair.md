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
