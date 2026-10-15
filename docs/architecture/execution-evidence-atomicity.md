# Atomic real-execution evidence

15 October 2026. This closes a persistence gap in the existing calibration input. It does not
change strategy weights, sample identity, duration interpretation, or model-promotion policy.

Previously CompanionService committed canonical event acceptance, accounting and buy limits,
then separately claimed a terminal offer and updated execution_stat. A statistics-write failure
could permanently consume the claim. A failure after source acceptance was worse: retry returned
DUPLICATE before reaching the missing learning write. Reconnect/restart could not recover it.

## Transaction boundary

SqliteStore now records eligible terminal execution evidence before committing canonical offer
acceptance. The event ID, current projection, position effects, sale accounting, buy limits,
counted_offer claim and execution_stat update commit or roll back together. A lost response after
commit still returns DUPLICATE on retry, with the sample already present exactly once.

ExecutionRecorder computes the same completed/censored timing values as before. Its persistence
call joins the existing offer transaction. A standalone recording owns one transaction for the
claim and statistics, and never commits a caller's transaction. Checkpoints after the claim and
after statistics allow interruption tests at both boundaries. There is no post-commit learning
write in CompanionService.

Rejected transitions and offers with recorded accounting gaps do not become complete learning
samples. Recovered cumulative baselines remain incomplete on later terminal replay. Price-touch
paper evidence does not enter this path. No historical samples are replayed during rehydration.

## Preservation

No schema change or data migration is needed. Existing counted-offer keys and aggregate statistics
are retained. This deliberately preserves the legacy slot/first-seen/item deduplication key rather
than changing it and silently recounting old terminal offers. Historical missing samples are not
backfilled or repaired. Canonical identity/timing provenance and chronological model evaluation
remain separate audit work; this change alone does not establish unbiased learning or higher returns.

## Verification

An actual SQLite trigger in disposable test stores reproduces a statistics-write failure. Before
the fix, both standalone retry and canonical terminal retry lose the observation. The trigger is
test-only; it is never installed in a live account database.

Tests cover failure/retry, interruption after claim and after statistics, rollback of source,
positions and limits, close/reopen, lost-ack duplicate delivery, later terminal heartbeats and
compatibility with an existing legacy deduplication key. Existing recovery tests continue to
exclude incomplete offers. Standard Windows verification and exact-head CI receipts are in
HANDOFF.md and the delivery PR. No agent database modification or game input occurs in verification.
