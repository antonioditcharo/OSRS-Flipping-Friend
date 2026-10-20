# Offer-persistence recovery delivery

20 October 2026, America/Anchorage. Contract:
[durable offer persistence and retry](../architecture/offer-persistence-recovery.md).

Six old-source tests reproduce lost fill/order/first-save session/exit-context, hidden unsaved tail,
cross-profile retry loss, invalid later frame loss and overwrite of a changed destination. Two
client-level regressions additionally reproduce a dormant local-failure retry timer and collection
from stale companion state. Ten added tests cover the fixed boundaries and keep forced writes off
ordinary read/UI paths. A prior batch test now expects retained valid failed-frame events ahead of
the later callback, rather than silently reusing their sequences.

Actual standard clean/tooling, packaged replay, exact-head Ubuntu/Windows CI, merge, paired install,
VERIFIED backup, unchanged task, original source/financial/anchor preservation and native start state
are consolidated in local-data/offer-persistence-20261020/delivery-state.json. Personal records and
logs remain private. Current full-prefix verification uses complete ordered row digests instead of
another full JSON copy; older full captures remain intact. Archive compression verifies unchanged
content hashes and deletes no records or backups. No live database is modified by these checks.

Windows clean verification passed in 87 seconds: 1,864 Java tests passed, six existing skips and
Java 11 bytecode. All 108 tooling tests passed in 117.466 seconds. This establishes the tested
storage/retry behavior, not fresh controlled trading, sustained latency or a return improvement.

The owner deferred login/game tests until the 20 October home batch after 17:30 local. Do not ask
again beforehand. Controlled buy/partial/cancel/collect/replace/sell/restart, sustained timing,
remaining production replay, qualified return benchmarks and learning promotion remain open.
