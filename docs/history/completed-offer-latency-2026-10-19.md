# Completed-offer acceptance delivery

19 October 2026. [Contract](../architecture/completed-offer-latency.md). This receipt separates
source verification, synthetic timing, paired installation and controlled trading acceptance.

## Reproduction and change

The 30,000-event acceptance regression fails on the old source inside `executionPrediction` with
SQLITE_INTERRUPT after exhausting its actual SQLite instruction budget. The new source reuses the
existing validated account prefix and reads only its indexed new tail during terminal forecast
qualification. Earliest original/order/advice/identity checks and atomic financial/sample acceptance
remain. External commits, malformed tails and rollback invalidate the prefix. An inconsistent older
source can conservatively leave prediction evidence unknown; it cannot discard real accepted fills.

New disposable-store tests cover the query budget, original context/cost after partial/full buy,
external original deletion/unrelated corruption, malformed new tail and failed learning write with
rollback, valid retry and lost-ACK duplicate. Existing original-source, legacy/censored/recovery,
atomicity and durable-exit suites remain required. No live data, identity or financial schema changes.

## Verified candidate

Windows standard clean build succeeds in 91s/18 tasks: 1,736 Java passed/six existing skips
(plugin 752/core 193/companion 797 including skips); project bytecode Java 11. All 108 tooling tests
pass in 140.322s; both exact-head CI checks are required before merge. The runtime learning fence stays
PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION.

A private standalone probe builds new synthetic 30,000-source stores and completes eight advised
offers after the same prior partial-fill ledger reads. Median terminal acceptance changes from
173.327ms to 17.970ms; maxima 186.523ms to 36.189ms. Full ledger, original prediction statistics,
buy limits and sixteen financial effects agree exactly. This is scoped synthetic acceptance work,
not HTTP, cold first publication, game-event-to-frame latency, live trading or account returns.

Private evidence: `local-data/completion-latency-20261019/` contains regression/focused/standard/
tooling logs, build identities, source/paired-artifact-bound synthetic comparison, financial prefix,
task/installation snapshots and subsequent exact-head CI/delivery metadata. Original source/finance
before delivery: 62,073 retained rows, 1,470 effects and the sole original risk anchor. The supported
updater must preserve that prefix and every existing historical gap/reserve/unknown amount.

## Runtime delivery and acceptance

The source requires both exact-head CI checks, clean default-branch fast-forward and the supported
unattended paired updater before claiming installation. HANDOFF records the current installed pair;
source or benchmark results cannot establish installed behavior. No client is force-closed and no
game input is sent. Fresh manual login/GE and observed partial/full buy/sale-to-ACK-to-guidance
transitions remain required. Complete live trade-loop/price-quality, sustained latency, production
replacement/relist replay, qualified 5m/30m/2h/8h benchmarks and validated learning stay open.
