# Preserve workflows while clocks are ahead

21 October 2026. HANDOFF's delivery receipt distinguishes verified source, merge, installation and
player acceptance. This batch fixes reproduced loss of retained buy/sell continuations after a
local clock rollback, plus a corrected late-fill remainder defect independent of clock changes.

Creation, original first-seen and accepted sale-source times are evidence, not deadlines to reset.
A well-formed retained hint whose evidence is ahead of the current clock stays pending. Clock
rollback alone cannot close it, rewrite it, acknowledge cancellation or return an actionable
collected remainder. Legacy hints missing creation time still require their original first-seen
time to qualify. Ordinary future expiry deadlines are distinct and do not suspend current evidence.

Current observations still matter while guidance waits. Account-scoped copies processed in the
current login generation continue tracking exact-episode fills, cancellation and collection.
Completion, full counters with no remaining quantity, genuine counter regression and replacement
episodes retain their existing workflow closure behavior. These are observed changes, not disposal
inferred from time or absence. No financial completion, acquisition, profit or event is created by
closing a workflow hint.

A corrected callback can arrive after a saved collection. Increasing its highest fill counter now
also narrows the saved remainder; it never enlarges the original cap. Previously that update could
leave an impossible document that failed every later commit. The corrected cap and observed
closure facts survive failed-write retries, restart and a stale ordinary offer snapshot, so clock
recovery cannot resurrect a larger rebuy promise. Newly observed collection can commit while clocks
are ahead, but cannot authorize guidance until the original times qualify.
Missing legacy totals use the matching current observation only to narrow the cap; original identity
fields remain unknown, and another collection callback cannot enlarge a smaller retained receipt.

Pending buy protection remains item-specific during suspension. Independent current hints continue
through the queue. Legacy BUY advice-expiry removal occurs only after its evidence time qualifies;
creation, acceptance, first-seen and source identities are never rebased. The existing forced-atomic
commit and account/file conflict checks remain mandatory. Uncommitted observations still cannot be
guaranteed to survive power loss after persistent write failure.

Shared chronological replay is now `account-replay-13`; older frozen bundles require their retained
evaluator. Sale policy remains `companion-sale-5`: pricing, original stops/targets, exact pooled cost,
canonical source/physical quantity and final authority are unchanged. Runtime learning remains fenced.

Behavioral regressions simulate timestamps without changing the system clock. They cover future
creation/acceptance/legacy first-seen, clock recovery, ordinary expiry, partial-stock protection,
independent hints, failed writes, profiles/restarts and actual tracker callbacks after collection,
including exact cost deltas and stale snapshot restart. Manual cancel/late fills/collect/replacement/
relist acceptance, sustained latency, qualified production replay/returns and validated learning
remain open. No account database, source/financial history, risk anchor or historical plan is repaired.
