# Protected exit comparison: offline verification

26 October 2026. PR #180 is source work; installed runtime remains #178. The owner is away.
Do not request login/game actions until they return. Native #178 guidance/chart acceptance,
sustained account-verification timing and player-operated trade/restart acceptance remain open.

## Frozen policy and preservation

The policy was frozen before reading the later comparison outcomes at source
`720c93bf682ede5675b510435efc4525e6360c60`. Companion SHA-256:
`de1a149e34c540d4c7332184344d645cac1c14834724d25ca70023f2df7b3981`.
Subsequent wording/receipt changes do not change the shared policy or companion artifact.
The comparison contract is [here](../architecture/common-horizon-exit-comparison.md).

Clean Java: 2,413 tests, six existing skips; 121 tooling; own classes target Java 11.
The initially failing replay-version literal was corrected from 27 to 28; the failed log remains.
Final exact-head Windows/Linux CI is tracked by PR #180 and private `verification-receipt.json`.
No new runtime may install on the strength of an earlier head's CI alone.

The installed #178 and new companion run under the client's Java 11 against the same disposable
restored account. Ledger profit bounds/sales, exact managed positions, source scope, continuations,
exposure and original risk-window hashes match. The database's before/after hash is unchanged.
No schema, financial writer, source identity, learning key or original loss anchor changed.
This is read compatibility; it is not new fill or controlled rollback acceptance.

## Later whole-account experiment

The sixteen-item catalogue was selected from earlier hourly prefixes before fetching new target
data. The evaluation suffix starts at 26 October 06:05 UTC, after the old development window ends
at 06:00, and ends at 17:30 UTC: 11 hours 25 minutes. No items were reselected after fetching
targets or inspecting new-policy outcomes. Input SHA-256:
`cb316144c43c5449451afd3a19f57fb36b89c34887f8d2b6d3808d84555694bc`.

Each case starts with 100M GP, membership and eight slots. Attendance is 5m, 30m, 2h or 8h;
missed visits, 15-second manual actions, a 120-second visit budget, restarts, buy limits, exact
tax/cost, partial fills and remaining inventory are retained. Capture shares below are assumptions
about reachable public flow. They are not observed personal fill rates.

Both exact artifacts reproduced all twelve cases on the existing Linux pilot. A Windows
disposable continuation-file replacement failure and its failed retry are retained; they are not
policy outcomes. The unchanged Linux baseline exactly matches all twelve Windows first-pass
results. Linux runs used a 0.75-core quota, 1.5 GiB memory maximum, 1 GiB Java heap, low priority
and 900-second timeout. The pilot service remained active with its original PID and JAR hash;
no live account, service runtime or hosting configuration was replaced.

All values below are simulated whole-account GP. Ending change includes open stock marked at
the same conservative low-side basis with a 2% haircut. `Delta` compares with installed #178.

| Attendance | Capture | Realized | Ending change | Remaining stock cost | Max drawdown | Realized/ending delta | Reprice-proposal delta |
|---|---:|---:|---:|---:|---:|---:|---:|
| 5m | 0.1% | 3,539 | -3,642 | 63,911 | 65,203 | 0 | 0 |
| 5m | 0.5% | 18,895 | -26,565 | 442,524 | 67,979 | 0 | 0 |
| 5m | 1% | 36,990 | -56,762 | 937,313 | 436,329 | 0 | 0 |
| 30m | 0.1% | 1,976 | -40,819 | 743,214 | 76,862 | 0 | 0 |
| 30m | 0.5% | 13,928 | -120,549 | 2,171,631 | 583,311 | +605 | -3 |
| 30m | 1% | 29,787 | -419,996 | 5,047,185 | 1,277,554 | +1,386 | -4 |
| 2h | 0.1% | 40 | -45,427 | 762,480 | 104,956 | 0 | 0 |
| 2h | 0.5% | 480 | -213,437 | 3,580,477 | 533,756 | 0 | 0 |
| 2h | 1% | 1,000 | -448,093 | 7,821,703 | 7,179,816 | 0 | 0 |
| 8h | 0.1% | 0 | -30,399 | 535,499 | 102,979 | 0 | 0 |
| 8h | 0.5% | 0 | -197,046 | 3,603,696 | 1,240,347 | 0 | 0 |
| 8h | 1% | 0 | -426,729 | 7,939,574 | 3,143,920 | 0 | 0 |

Ten cases match all economic decisions/results. Two 30m cases retain a 5,092 GP initial limit
instead of the old 5,081 GP alternative for item 20718, with 55/126 units respectively. Drawdown,
losing sales, remaining stock cost and interaction counts do not increase in any case. All terminal
marks are supported under the simulator's assumptions; missing source coverage remains explicit.

All conservative ending changes are negative. The short later window, small catalogue,
retrospective bucket-average quotes, unknown original publication/revision clocks and assumed
capture/marks prevent actual-return or predictive-superiority claims. In particular, 8h attendance
has too few visits for a mature series of round trips. The reused eight-item development fixture
also matches in twelve cases, but remains development evidence rather than untouched promotion.

Private inputs, manifests, failed/successful logs, frozen JARs and complete results are retained
under `local-data/exit-comparison-20261026` in the primary checkout. Do not publish account rows.

## Next implementation and release gates

Runtime activation remains held for installed #178's deferred player acceptance. Then require
the supported paired updater, backup/restore and rollback proof preserving later fills, fresh
ordered current-frame ACKs, visible guidance and player-operated sale/relist/restart acceptance.
An optional comparison must never delay or withdraw independent valid guidance.

The next market-informed loss/hold pass needs a distinct immutable evidence and budget contract:

1. Extend the shared comparison with qualified falling/recovering-market evidence and realistic
   price/quantity/time alternatives. A current-mark binary scenario cannot grant loss permission;
   age, a chart path or estimated replacement income cannot substitute for that evidence.
2. Add final verification through `CompanionSalePolicy` and durable, transactional reservation at
   `SalePolicyReceiptStore` issuance. Bind the original risk window, exact acquisition allocation,
   source/read set, market/model/settings versions, remaining quantity and maximum permitted loss.
   Two concurrent proposals must not reuse the same allowance. Existing marked/realized losses
   must not be counted twice; accounting remains driven only by canonical executions.
3. Carry that distinct proof through first player acceptance, cancellation, late fills, collection
   and exact relisting. A display refresh or timeout cannot release a working order's reserved
   risk. Later source evidence must retire or adjust it exactly once; uncertainty retains it.
4. Prove old-runtime reads and safe non-loss continuation when the new feature is disabled or code
   rolls back. Never relabel the existing `STOP` receipt as a new market-informed reason, clear a
   financial gap, change original stops/anchors or backfill learning samples.
5. Retain per-account fairness, bounded background work and immediate existing-policy fallback.
   Exercise falling/recovering/sparse markets and complete chronological accounts at each cadence,
   including cancelled/partial/late fills, restart, duplicate proposals and competing loss exits.

No new loss budget, loss authority, execution-learning model or public launch is activated here.
