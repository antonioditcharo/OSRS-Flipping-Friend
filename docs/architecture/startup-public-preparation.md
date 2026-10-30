# Parallel startup preparation

The #200 live session after the owner's 29 October computer restart retains repeated five-second
offer/performance/public-feed timeouts before its first durable frame at 18:05:41 local. The owner
reports minutes of initial waiting followed by quick trading. A later thread capture is idle and
does not prove the earlier timeout's cause. A read-only frozen-account probe takes 12.47 seconds
(2.54s opening, 9.91s rehydrating, 29ms warm ledger) at a 768-MiB heap with unchanged database
bytes. It excludes live network/boot conditions and does not explain the entire observed delay.

One avoidable dependency is explicit in the entry point: its first synchronous public refresh
precedes API startup and existing-account preparation. Start the API and bounded account
preparation first, then schedule the first public refresh immediately on the existing background
scheduler. Subsequent refreshes retain their 60-second fixed delay and guarded failure behavior.
When public data arrives, refresh the account plans too. This removes serial waiting; it does not
promise a particular wall-clock boot time or permit orders without current market evidence.

The real-handler regression blocks that first public refresh while authenticated health and an
original offer acknowledgement complete. Duplicate delivery still commits once in its scoped
account; missing cash/frame/market evidence yields no entry. The test uses the existing bounded,
instrumented loopback client and releases every blocked task during cleanup. Financial ordering,
account opening, source IDs, costs, tax, original risk windows and final price authority are unchanged.

Normal-user process launch also retains the existing validated task's optional heap limit. The
installed task uses `-Xmx768m`; direct launch previously discarded that setting. Preserve its exact
configured value, quoted JAR path and no-limit behavior when absent. This changes no task XML,
permissions or new policy default. Invalid/foreign task actions continue to fail existing checks.

Installed #202 passes clean Java/tooling, Java 11, both source-head CI platforms, its fresh
uninterrupted workflow hour, paired restoration and previous-runtime financial reads. Fresh
manual/native startup acceptance and final documentation-head CI/merge remain tracked separately. Keep the initial timeouts and all slower
selected-sale samples. A 2.205s editor quote includes 1.155s behind MAIN plus 669ms final authority;
do not change financial concurrency or remove a required authorization to hide it. Idle/mixed,
distinct-action and MAIN/SELECTED_SALE timing qualification remain separate from this startup fix.
Learning and pricing promotion remain gated; baseline suggestions continue under current guards.
