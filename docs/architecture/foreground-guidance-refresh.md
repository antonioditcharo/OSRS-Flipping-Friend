# Foreground action refresh

Continuation after installed #198 and merged offline #199. Live baseline pricing, canonical
financial delivery, original plans and risk authority remain unchanged. No learning activation.

## Measured problem and change

The first #198 session has 12 painted main cards (render p95 166.328 ms), four source-proven
distinct BUY decisions (post-ACK p95 751.156 ms) and no selected-sale samples. Five COLLECT
presentations have unproven decision identity and remain excluded from publication qualification.
The first worker waits 5,337.578 ms before execution; do not hide that pre-ACK player delay.
This is a small initial session, not sustained acceptance or a resolved startup outlier.

The EDT now updates the action, invalidation and controls first. Metrics and investigation work
coalesce into another event-loop turn and wait while an exact visible card paint is pending.
Retries are bounded by the existing paint timeout; hidden/superseded paints retain outcomes.
Optional work reads the latest context rather than retaining an old account/action closure.
Closing cancels pending UI work; reopening refreshes the action immediately.

Hidden Records views retain current snapshot references and populate on opening; account changes
clear their old data. Missed flips refreshes its table only while open or explicitly requested.
The existing EDT delivery marker follows card updating, so two optional diagnostic stages now
identify actual EDT entry and card completion; old traces cannot supply those missing stages.

The same serial worker labels MAIN, SELECTED_SALE and OFFER_REPLAY jobs. Queue-time metadata
records the active label, elapsed time and queued work count. This attributes startup contention
without changing executor ordering, event acknowledgement, replay retries or financial authority.
Do not introduce financial transport concurrency or authority-sensitive caches without the profile.

## Verification and remaining gates

Regressions cover immediate card/logout publication ahead of optional work, latest-context optional
refresh, cancellation on close, serial original-offer/frame order, hidden investigations, retained
records across logout, cadence controls, stale icon callbacks and the old delivery boundaries.
The fresh workflow hour includes the new foreground tests; fixtures never touch player accounts.

Require standard clean Java/tooling, bytecode 55, Java 11, exact-head Windows/Linux CI, uninterrupted
hour and paired preservation/previous-runtime reads before runtime delivery. Measure native MAIN
and SELECTED_SALE separately after installation, under idle/mixed conditions. Targets remain render
p95 <=100 ms and eligible publication p95 <=1 second after required ACK, with total/persistence/
queue/authority/paint timings, distinct versus repeated actions and every failed/unmeasured outcome.
Player sale revision, cancel/collect/relist and restart stay manual. Deterministic late-fill tests
plus monitoring satisfy only the selected construction gate; unseen live fills remain unverified.

Whole-account joint pricing/chronological qualification and the observational learner remain open;
the learner waits for delivery, latency and prospective-provenance gates. Keep original 60/20/20
folds, 5m/30m/2h/8h benchmark, fixed public stop and $50/month ceiling.

## Retained live outage and verification

The installed #198 scheduled companion stopped answering global health and account requests;
a 20-second thread capture did not complete. Root cause remains unproven. A coherent read-only
account snapshot and original outboxes were retained before same-JAR companion recovery. RuneLite
and both installed artifacts were unchanged. All 286 originally pending observations subsequently
commit with unchanged event/session/sequence/account/item/price/quantity/money fields; none are
missing or remain pending. No database was restored and no financial identities were regenerated.

The recovery exposed a CIM owner-query race after verified process termination. The helper now
continues only after a fresh PID-specific lookup proves absence. Live/reused/unreadable processes
and unknown listeners remain fatal. Four regressions cover both command-line identity paths.

The actual-JAR Java 11 suite passes 17 client/core and 21 companion checks. The controlled native
window proves that the new card paints before a 500-ms optional metrics task; first cold paint is
226.175 ms versus 565.685 ms on #198. Preserve that cold outlier: neither sample is RuneLite cached
p95 acceptance. The 30-second natural-workload JFR capture contains mostly native blocked samples
and does not identify the pricing bottleneck; no authority-sensitive cache or production transport
change is justified from it. Subsequent #198 sale/maintenance timing still exceeds the target.
