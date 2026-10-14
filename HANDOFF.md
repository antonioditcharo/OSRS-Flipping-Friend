# Agent handoff

Updated 14 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## Current source and branch

Active development and the GitHub default branch are **`CoPilot's-Rebuild-from-Main`**.
`main` at `0cb8ef8` is an older cleanup/revert baseline; explicitly checking out `main` does not
include the work below. Start a new `codex/` branch from the current remote rebuild branch.
Do not reset or force-push the historical branches to make them look current.

The durable planner/accounting foundation is merge commit `6b4140109de6a63d93614740c87b32f72cfe171d`
(PR #31). The setup and RuneLite compatibility continuations below are also required for the current
player workflow; strategy tuning and measured learning promotion remain pending.

| Merged work | Result | Merge commit |
| --- | --- | --- |
| [PR #27](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/27) | Physical blocked holdings remain visible; personal stock adoption requires the existing opt-in. Added Windows/Linux CI, checkout portability fixes, and an evidence-based project direction. Removed the orphan reference gitlink that broke checkout. | `ab7aeb23488e17e59773bcf805c144fd85c12434` |
| [PR #28](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/28) | Account-specific databases/services/API scopes, cache and outbox fencing, recovery of offers older than four hours, read-only diagnostics, and Copilot CSV ingestion. | `6ec1a8f238a2232f31bd4906c48e4dafea7244bd` |
| [PR #29](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/29) | Atomic durable sale proceeds, proportional cost, tax/profit bounds, rule evidence, and live Performance reading that account ledger. | `bfef926d334a3374a7c390d595ec6eab0eba8cdc` |
| [PR #31](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/31) | Stale snapshots cannot restore sold positions; planner per-item exposure reads durable positions and open-buy reservations; unresolved state withdraws buy advice. | `6b4140109de6a63d93614740c87b32f72cfe171d` |
| [PR #33](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/33) | Guarded update rejects disabled/foreign tasks and incomplete artifact pairs; Java-on-PATH fallback; recorded incomplete live verification. Windows/Linux CI passed. | `7b98a19bf288ab259bb275c2efaf2669028b9482` |
| [PR #34](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/34) | Shared paired setup/re-setup/update/start/status lifecycle, stable installed companion, hash manifest, backup review, saved-login reuse and refreshed shortcut. Windows/Linux CI passed. | `cb2c1d2dac01865374108339fbd1fcee43ee0093` |
| [PR #35](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/35) | Separate plugin profile records are included in the pre-install backup; linked records are rejected. Recorded actual backup review. Windows/Linux CI passed. | `6411f800e9a56d37de5dbd3b3194061ae262e910` |
| [PR #36](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/36) | Exact official RuneLite artifact selection, installed API compatibility guard, long spent counters and persistent pause for unsupported unit prices. Windows/Linux CI passed on final revision `10d1a4bc`. | `cb203a834401d4e32cc5791d07f0ade37df80d61` |
| [PR #37](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/37) | Explicit account-scoped review of unpriced personal/retired stock preserves ledger accounting; open offers and changed evidence remain protected. Windows/Linux CI passed on final revision `0e38e070`. | `49934e44590e0a484e0ddbd4857179102b03bdc2` |

## Implemented boundaries to preserve

- Physical ownership observations are independent of trading exclusions. Personal inventory,
  equipment, bank evidence, offers, collection boxes, and flipping stock are distinct concepts.
- Account identity is an opaque hash of the RuneLite profile. Account path/header/payload/response
  identities agree; requests use `accounts-v1` and `/v1/accounts/<accountId>/...`. A profile switch
  clears advice/ledger caches, and an earlier account's response cannot become the current ledger.
- Live SQLite files are `companion/accounts/<accountId>/flipping-friend.db`; shared market state
  uses `companion/market-state.db`. The previous `companion/flipping-friend.db` is preserved and
  unassigned. Do not infer its owner, prune it, or import it into a live account automatically.
- Durable offer projections restore active/uncollected offers independently of four-hour buy-limit
  expiry. Event IDs, exact acknowledgements, duplicate handling, and transactional rollback matter.
- Sale updates record gross counter deltas, removed blended cost, and tax/profit evidence in the
  same transaction as ownership. Cost remainders survive partial sales and the final unit.
- Mixed-price fills do not justify tax on a rounded average. First-seen time does not prove an
  offer's tax regime. Old unpriced sales and unknown cost/proceeds remain unresolved; current
  quotes or local journal estimates cannot manufacture exact historical profit.
- Performance uses the scoped durable ledger, including uncertainty and coverage. Missing/stale/
  malformed/foreign data says Unavailable. Local history and bank valuation remain labeled estimates.
- Game interaction is manual. The assistant does not place, cancel, or collect offers automatically.

See [account isolation](docs/architecture/account-isolation.md) and
[durable sale accounting](docs/architecture/durable-profit-ledger.md) for design and regression cases.

## Next work and claims that remain unsupported

The single-authority migration is **not complete**. Planner per-item exposure now uses durable state (see below); exits, inventory UI,
journal calibration, and local session-risk inputs still use local records. Migrate these consumers
to the durable account state with positive inventory/cost reconciliation and behavior tests for
partial fills, missed observations, crashes, duplicate delivery, profile changes, and restart.
Then finish the manual cancel -> collect -> revalidate -> replace workflow and companion sell policy.

The benchmark contract is 100M GP, members, eight slots, and separate 5m/30m/2h/8h checking scenarios.
Explicit 2h/8h settings and full-account chronological replay remain pending; do not substitute 3h.
Account GP/hour requires measured attendance/session time and conservative treatment of remaining
inventory. Candidate GP per slot-hour is diagnostic, not the success metric.

The [Copilot baseline](docs/benchmarks/copilot-2026-10-11.md) and importer document the supplied
exports' evidence limits. Raw CSVs and row-level outputs are intentionally gitignored under
`local-data/`; they are not available from a fresh GitHub checkout. Tests include synthetic fixtures.
Session/cadence labels are unavailable. Transaction-export Tax is derived by Copilot's exporter,
not an observation of historical tax. Do not infer a valid GP/hour comparison from these files.

The user-run paired installation at `6411f800` was followed by a RuneLite login freeze. After PR #36,
the user installed the compatible pair at `cb203a83`; read-only pair and launcher compatibility checks
passed. The user subsequently reported working login and sell recommendations, but no new buys.
The pipeline check passed its protocol/ledger checks and exposed a reconciliation-blocked buy plan;
controlled trading acceptance checks remain pending (see the latest continuation below).
No agent database migration was performed. Verification against actual holdings/collected coins remains outstanding.
Profit superiority, feature parity, online
logistic learning, shadow trading, and automatic challenger promotion have not been established.
Historical package counts, roadmaps, and non-activation scaffolding are not runtime feature evidence.

## Durable planner exposure
Planning reads durable positions and offers together (`SqliteStore.planningProjections`) and
`DurablePlanningExposure` counts known held cost plus unfilled BUY_OPEN/BUY_PARTIAL reservations,
without double-counting fills. Unresolved positions, unknown offer states, or overflow replace any
cached plan with COMPANION_STATE_UNAVAILABLE. Only `committedByItem` changes; cash, committedCoins,
slots, preferences, and session drawdown remain client inputs. Snapshot reconciliation also refuses
snapshots older than the latest position event, or not newer than the latest disposal, so sold stock
cannot reappear. Concurrent read/write interleaving and a full live-market service test are untested.
Installed-pair and read-only runtime evidence is recorded below; controlled trading checks remain
incomplete. Details: `docs/architecture/durable-planning-exposure.md`.

## Live verification checklist (pending, do this next)
PR #31 passed Windows/Linux CI and a full local build. It has now been installed and its read-only
account pipeline checked, but **controlled live trading verification remains incomplete**.
Continue the outstanding checks below before relying on it for trading.

1. **Build first.** From an up-to-date `CoPilot's-Rebuild-from-Main` (at `6b4140109de6a63d93614740c87b32f72cfe171d` or later):
   `.\gradlew.bat clean test jar :companion:shadowJar --console=plain` and
   `python -m unittest discover -s tools/tests -v`. Stop on any failure.
2. **Back up before installing.** Stop RuneLite and the companion. Copy (never edit or move) the
   account directory `companion/accounts/<accountId>/`, the plugin's separate `accounts/` records,
   and the currently installed plugin and companion JARs to a dated backup folder. Preserve the two
   account layouts separately; do not infer ownership or import records. Do not touch the unassigned legacy
   `companion/flipping-friend.db`.
3. **Install both JARs together** using the supported numbered update wrapper (the lifecycle that
   `UpdateLifecycleBoundaryTest` describes). Never install only one of the two artifacts.
4. **Start and read-check.** Start the companion and RuneLite, log in, then run
   `tools/pipeline-check.ps1` (read-only). Record its output.
5. **Manual in-game checks** (the player performs every Grand Exchange action; nothing is automated):
   - With an existing known-cost holding and no open buy: buy advice still appears for other items.
   - Place a buy and let it partly fill: advice continues; the item is not double counted
     (held cost plus only the unfilled remainder).
   - Cancel or complete that buy and collect: no leftover reservation blocks advice.
   - Sell a holding fully, restart the companion, log in again: the sold stock does not reappear.
   - If the panel says "Durable holdings or offers require reconciliation before new buys", record
     which item/offer caused it using read-only inspection only. Do not edit the database to clear it.
6. **Record results** here and in TASKS.md: date, revision, pipeline-check output, and each check's
   outcome. Any failure must first be reproduced as an automated regression test before a fix.

**Rollback:** stop both processes and reinstall the backed-up JARs. Under the owner's database
handling rule, do not restore, edit, move, migrate, or repair account databases or the unassigned
legacy database. Record any suspected database problem and investigate read-only.
The plugin pair must also match the current RuneLite API. An older backup without compatible API
metadata cannot be launched through the new guard; do not bypass it or mix old cached client/API JARs.

Still untested after this checklist: concurrent read/write interleaving and a full live-market
service test. Cash, committedCoins, preferences, and session risk remain client-snapshot inputs.

## 13 October 2026 checkpoint and update-tooling continuation

Development base: `ee0d3aff4ee8d83f7806358204d48e7af33f2597`, containing PR #31. The primary Windows
checkout was on the rebuild branch, clean and equal to origin after fetching. Historical `main`
at `0cb8ef8` was preserved. The three untracked historical dashboard directories were moved with
`Move-Item` to the sibling `OSRS-Flipping-Friend-dashboard-backup-20261013-154616` directory;
32,634 files / 950,774,032 bytes matched before and after. This is not an installation/account backup.

| Live checklist check | Observed outcome |
| --- | --- |
| Build at `ee0d3aff` | PASS: 1,163 Java tests passed, six skipped, zero failures/errors; both supported JARs built; eight Python importer tests passed. |
| Stop and back up installed components/accounts | NOT PERFORMED. Read-only preparation found RuneLite running, no listener on 37777, and a disabled `FlippingFriendCompanion` task configured for the older `.claude/worktrees/plugin-trade-recommendations-profit-a27519` checkout. The expected scoped accounts directory was absent. |
| Install both JARs through the numbered wrapper | NOT PERFORMED. Task configuration does not select this checkout's companion. No task or installed JAR was changed. |
| Start/login and run `tools/pipeline-check.ps1` | NOT RUN. There is no pipeline-check output for this checkpoint; no live success is claimed. |
| Known-cost holding permits advice for other items | NOT OBSERVED. |
| Partial buy counts held cost plus only unfilled reservation | NOT OBSERVED. |
| Cancel/complete and collect removes the reservation | NOT OBSERVED. |
| Full sale and restart cannot resurrect sold stock | NOT OBSERVED. |
| Read-only identification of any reconciliation message | NOT OBSERVED. No live database was opened or changed. |

The missing expected accounts directory does not establish whether account data exist elsewhere.
Locate backup sources read-only before any installation; preserve the unassigned legacy database
and never infer its account owner. The existing task is still disabled and points to its old checkout.

After the owner authorized autonomous development, branch `codex/update-task-checkout-safety`
reproduced the updater faults before production changes. Test commit `b9d5fdb` ran the actual script
against disposable files and mocked process/task/network commands: four failures, two positive
controls passing. The old script replaced the plugin before rejecting a disabled task or missing
companion JAR; a task for another checkout or JAR could answer health and report "Update applied."
A separate regression also reproduced unset `JAVA_HOME` preventing the promised PATH fallback.

Implementation revision `152db838700579eabf208d4589193c9d47be3994` changes `tools/apply-update.ps1`:

- Validate an existing root-folder companion task before stopping/building, and again before copying:
  it must be enabled, have one existing Java executable action, and name this checkout and companion JAR.
- Accept the supported installer's optional heap limit and single JAR argument; reject extra arguments
  that might select another data location. Unreadable task enumeration aborts rather than falling back.
- Require both supported JAR files before replacing the installed plugin. Use a literal source path.
- Preserve the direct hidden Java launch when no companion task exists; do not enable or retarget an old task.
- Allow Java discovery through PATH when `JAVA_HOME` is unset.

Seven behavior tests now cover invalid tasks/artifacts and valid scheduled/direct launches, including
paths with spaces and missing `JAVA_HOME`. They mock every process, scheduled-task and HTTP operation
used by the updater and run in temporary folders. CI runs these with the existing Python discovery
command on Windows/Linux. These tests do not certify real Task Scheduler, RuneLite or account behavior.
Task reconfiguration after the final validation and runtime binary identity remain unverified.
The first [PR #33 CI run](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38497703500)
passed Linux but failed the Windows harness's fixture-path boundary check. The fixture now resolves
temporary path aliases before invoking PowerShell; all 15 tests passed locally again. Use the latest
PR checks for the final remote outcome; the first run is retained as failure evidence.

## Verification and continuation

### Review panel visible; fresh lifecycle faults — 14 October 2026, 01:02 AKDT

Installed source remains rebuild `49934e44590e0a484e0ddbd4857179102b03bdc2` (PR #37).
On documentation branch `codex/stock-review-live-verification` at `882825915be66cb3db4df08ee35582abf6c02907`,
the player reported that **Review holdings** appears. Review choices and GE actions were explicitly
left unchanged for the fresh read-only check; no declarations have been observed or seeded from chat.

Agent ran `tools/pipeline-check.ps1 -SkipBuild` with bundled PowerShell 7, then the scoped
`ledger/current` GET (HTTP 200). Original output and ledger are private in
`local-data/diagnostics/stock-review-live-20261014/pipeline-before-review-20261014-010227.txt`
and `ledger-before-review-20261014-010230.json`. The opaque account identifier is replaced below
with `<account-id>`; all verdicts, counts and reasons are otherwise verbatim:

```text
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Durable holdings or offers require reconciliation before new buys.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_EXIT=0
```

Exit zero means the diagnostic completed; it does not establish buy readiness. The ledger still
has the seven earlier unpriced reconciliation entries and now also has three entries reporting
`invalid lifecycle transition`, two with known costs and one with zero quantity/unknown cost.
Item identities, quantities, costs and event/offer identities remain in private diagnostics.
The cause has not yet been established. Do not classify a known-cost conflict as retired, clear
records, or weaken the planning guard to unblock buys. Inspect evidence read-only and reproduce
any actual bug in an automated regression on a new `codex/` branch from current remote rebuild
before implementation. No live database or account record was edited, migrated or repaired.

Panel visibility is user-reported; saving reviews and post-review planning remain untested.
Known-cost holding/no-open-buy, partial buy, cancel/collect and full sale/restart acceptance remain
pending. The overall PR #31 acceptance checklist is incomplete.

### Stock-review pair installed — 14 October 2026 checkpoint (player login/review pending)

Installed source: rebuild `49934e44590e0a484e0ddbd4857179102b03bdc2` (PR #37).
Both [final Windows/Linux checks at `0e38e070`](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38527042939)
passed before merge. Documentation continues on `codex/stock-review-live-verification` from that
clean, fetched rebuild revision. Historical main and all earlier working branches remain preserved.

The owner supplied the new backup listing and paused at INSTALL. Backup:
`%USERPROFILE%/.runelite/osrs-flipping-friend/backups/20261013-233014-f45bc48b/`.
Read-only review passed: companion account three files / 6,225,568 bytes (database plus WAL/SHM),
plugin profile records 22 files / 1,502,156 bytes; every relative filename and size matched its source.
The prior plugin (393,350 bytes), prior companion (17,722,571 bytes) and manifest (458 bytes) each
matched their backup by SHA-256. Task XML was 1,895 bytes; action and current Windows principal
matched. Task Disabled/Enabled=False, listener count zero, observable RuneLite count zero at review.
No account/database contents were opened or changed by that review, and the unassigned legacy
database was not copied or assigned. The owner then confirmed INSTALL in the same updater.

The completion log supplied on 14 October reports:

```text
BUILD SUCCESSFUL in 4s
8 actionable tasks: 8 up-to-date
Built C:\Users\Antonio\OSRS Flipping Friend Rebuild\build\libs\osrs-flipping-friend.jar (397.5 KB)
Companion ready (accounts-v1).
Update applied. Both installed JARs verified. Restart RuneLite to pick up plugin changes.
Press any key to continue . . .
```

Agent read-only post-install checks: `tools/status.ps1`, `tools/run-dev.ps1 -CheckOnly`, installed/
build JAR hash comparison and the manifest's reviewed-backup path. Exact output:

```text
FLIPPING FRIEND - STATUS
Plugin: present
Installed companion: present
Pair manifest: present
Companion credential: present
Saved RuneLite login: present
Companion task: Running
Task checkout: C:\Users\Antonio\OSRS Flipping Friend Rebuild
Installed pair verified; companion responds with accounts-v1 (READY or DEGRADED).
Overall status: PAIR VERIFIED / COMPANION RESPONDING
The read-only pipeline check after login reports account coverage and market freshness.
STATUS_EXIT=0
RuneLite 1.13.1 and the installed plugin API match; cached artifacts verified.
Compatibility check passed. No client started and no files changed.
COMPATIBILITY_EXIT=0
INSTALLATION_BACKUP=C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261013-233014-f45bc48b
Plugin INSTALLED_BYTES=407086 MATCHES_VERIFIED_BUILD=True
Companion INSTALLED_BYTES=17737530 MATCHES_VERIFIED_BUILD=True
POST_INSTALL_REVIEW=PASS (no client launched; no game input)
```

Further process inspection found `OBSERVABLE_RUNELITE_PROCESS_COUNT=0`. The next player step is
numbered Start and manual login, then confirmation that **Review holdings** appears. Inspect a fresh
scoped ledger before guiding the player through the declarations previously supplied; do not seed
preferences from chat or change GE offers during this step. **Pipeline-check after this install:
not yet run.** Login/review controls, post-review plan availability, known-cost holding/no-open-buy,
partial fills, cancel/collect and full sale/restart acceptance remain unverified. The update itself
does not classify records or establish new-buy readiness or historical profit.

### Unpriced stock review workflow — 13 October 2026 (source implemented; live retest pending)

Base: rebuild `cb203a834401d4e32cc5791d07f0ade37df80d61`; branch `codex/stock-review-workflow`.
Regression commit `932ab1fda5717cfbe455b3fc449ce0d000362d33` preceded implementation
`1f728eb6cd3b6b90013ee36dd6eef2e3c79765e9`. The source branch incorporates the earlier installation
and blocked-plan checkpoints from `codex/pr31-live-verification`; that earlier branch is preserved.

The owner identified four of the five positive-quantity unpriced entries as personal inventory or
equipment. The fifth is no longer held; the two unpriced historical-sale items were also confirmed
fully sold. Those declarations describe current trading intent/ownership, not purchase prices or
historical profit. Personal item/quantity/event details remain in private local diagnostics.

The initial automated workflow fixtures reproduced three blocked planning cases: declared personal
stock, a retired old holding, and a retired unpriced sale entry. The unclassified unknown-cost control
remained blocked. Exact initial ending: `4 tests completed, 3 failed`, `BUILD FAILED in 9s`,
`REGRESSION_EXIT=1`; log `.gradle/stock-review-regression.log`. The sale fixture was subsequently
strengthened with SELLING followed by SOLD so it also verifies a genuinely unresolved sale update.

Implemented player workflow:

- **Review holdings** displays fresh scoped durable entries. The player can choose **Personal item**
  for source-free unpriced holdings or **I no longer hold this** for an unpriced old entry; **Clear
  review** reverses that preference. A stale/foreign/changed row is refused. Cost-bearing conflicts
  cannot be excluded through these controls and remain a reconciliation prerequisite.
- `stock-declarations.json` stores these choices separately in the plugin's existing account profile
  directory, with account identity and a position-specific evidence signature. It is included by the
  existing `plugin-accounts/` backup. Malformed/foreign saved preferences fail closed, and an attempted
  save does not overwrite them. No new database table, schema migration or ledger-correction endpoint
  exists; the normal snapshot carries the declarations as trading preferences.
- Durable exposure excludes only matching reviewed, unpriced entries. All original projections,
  accounting events and unresolved historical profit remain intact. Known-cost holdings and every
  buy reservation still count. Open buys, uncollected purchased items, remaining sell stock and unknown
  offer states cannot be concealed by a declaration. New quantity, cost, opening evidence or event/
  offer identity invalidates an old review.
- Local new-sell selection and the trading Holding list respect matching declarations without
  deleting PositionBook entries or physical holdings. Personal stock is also excluded from new entry
  candidates while the local evidence matches the review. Existing offer maintenance remains active.
- Review changes withdraw cached plans, fence earlier client responses and queued guidance, and
  invalidate the companion's cached READY plan before background recomputation. This does not complete
  the broader concurrent account-snapshot/read interleaving verification.
- Scoped health can remain DEGRADED to show accounting history warnings. Its separate planning
  readiness field requires fresh market data, a successful current durable exposure calculation and
  no other subsystem fault. The client also validates market/health timestamps before reading plans.
  Missing readiness evidence, unresolved active stock and stale/future market data still stop entry.

Verification: 1,186 Java tests passed / six capture-dependent skips / zero failures/errors (1,192
tests, 315 suites), both JARs built; all 48 Python tests passed (`Ran 48 tests in 29.907s`, `OK`,
`PYTHON_EXIT=0`). The clean build at implementation `1f728eb6` ended `BUILD SUCCESSFUL in 31s`,
`18 actionable tasks: 18 executed`, `GRADLE_EXIT=0`. A final clean build also verifies automatic
temporary-fixture cleanup; use `.gradle/stock-review-final-clean-build.log` for that ending.
The final clean ending also passed: `BUILD SUCCESSFUL in 31s`, `18 actionable tasks: 18 executed`,
`GRADLE_EXIT=0`. [PR #37 CI at `c188066f`](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38526589543)
passed Windows and Linux; check both final-head checks before merge.
Python log: `.gradle/stock-review-python.log`; all logs are gitignored, outside build outputs.
The broader first run caught a companion access before the unsupported-price guard and a holding-list
fixture mismatch; both were corrected while preserving the assertions. A later fixture compile failed
because the companion has no Mockito dependency; a paused executor replaces that mock without adding
a dependency. These failures are retained in the earlier `.gradle/stock-review-*.log` files.

**Not installed or live-tested.** Installed runtime remains `cb203a83`, where buys are blocked as
recorded below. No live database, position record, credential, game setting, installed artifact or task
was changed by this development. The agent did not perform game input or stop RuneLite; the owner
closed it and process inspection confirmed zero clients before the clean build. Reviews must be made
by the player in the new panel after a supported paired update and a fresh backup review. Do not write
the conversation's classifications directly into live files. Recheck pipeline output after review,
then continue each controlled manual checklist case. Unknown historical profit remains unknown.

### Live login and blocked buy advice — 13 October 2026, 22:27–22:29 AKDT

Runtime source remains `cb203a834401d4e32cc5791d07f0ade37df80d61`; documentation branch
`codex/pr31-live-verification`. The user reported login was working enough to recommend sells for
earlier manual purchases, but new buy recommendations were absent. This is user-reported UI behavior,
not a controlled sell or accounting acceptance result.

Agent ran `tools/pipeline-check.ps1 -SkipBuild` under bundled PowerShell 7, using only the supported
GET endpoints. Standard build/tests were already passed; no build or game input was performed during
this diagnostic. Original output is retained privately in
`local-data/diagnostics/pr31-live-20261013/pipeline-20261013-222706.txt`.
Output below substitutes `<account-id>` for the private opaque account identifier; all verdicts,
counts and reasons are otherwise verbatim:

```text
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Durable holdings or offers require reconciliation before new buys.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_EXIT=0
```

Exit zero verifies the diagnostic's protocol/ledger checks, not readiness for trading. A subsequent
scoped health GET returned HTTP 200 / DEGRADED with five position cost warnings and two
`disposal has no durable position` faults. A scoped portfolio GET returned HTTP 200 / UNAVAILABLE /
COMPANION_STATE_UNAVAILABLE with no allocations. A scoped ledger GET confirmed seven RECONCILE
entries: five positive-quantity unknown-cost holdings and two zero-quantity disposal entries.
Private item/quantity/cost details and event identities are retained only in
`local-data/diagnostics/pr31-live-20261013/ledger-20261013-222907.json`; they are not public fixtures.
No database was opened by the agent; no accounting event, synthetic snapshot, record edit, process
restart, or game input was performed. Shared market readiness is distinct from scoped account health.

Source trace: `DurablePlanningExposure.from()` fails closed on any unresolved position;
`CompanionService.refreshPlan()` withdraws the entire buy plan in that case. `CompanionClient`
publishes all local PositionBook entries, including unknown-cost entries. `syncHoldings()` only
adopts additional personal stock when the existing `adoptExistingItems` opt-in is enabled; its current
live setting and the origins of the saved entries have not been established. Turning that setting off
does not remove existing durable records. Do not guess which holdings are personal, claim historical
profit, clear warnings by editing records, or weaken the guard simply to show buys.

The user has been asked to classify the five held items as personal stock or flipping stock. Complete
the missing onboarding/reconciliation workflow using explicit evidence and regression tests before
source changes; preserve incomplete sales as unresolved and keep physical ownership distinct from
trading intent. Any real bug must first be reproduced on a new codex branch from current rebuild.

Checklist outcome: build, reviewed backup, paired installation and read-only protocol/ledger check
passed; login/sell display was user-reported. New-buy readiness is BLOCKED by reconciliation. The
controlled known-cost holding/no-open-buy case has not been established; partial fill, cancel/collect,
full sale/restart and conservation remain untested. Reconciliation blockers have been identified
read-only. Do not mark the overall live checklist complete. Review source workflow fixes separately
from final acceptance; the completed acceptance record still requires the owner's merge approval.

### Compatible pair installed — 13 October 2026, after the 22:09 backup (login still pending)

Installed source revision: `cb203a834401d4e32cc5791d07f0ade37df80d61` on the rebuild branch.
PR #36 merged after both [Windows/Linux checks on final revision `10d1a4bc`](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38519989011)
passed. Documentation continues on `codex/pr31-live-verification`; this is an incomplete acceptance
record, not evidence of successful login or trading.

The user ran `4 - Update.bat` and paused at INSTALL with backup
`%USERPROFILE%/.runelite/osrs-flipping-friend/backups/20261013-220953-f1ad1d84/`.
Read-only review found:

- Companion account backup: three files / 321,176 bytes, including the database and its WAL/SHM
  sidecars. Every relative filename and size matched the source. No database contents were opened.
- Plugin account backup: 22 files / 1,500,384 bytes; every relative filename and size matched the
  separate source tree. No account record contents or saved login were opened during the review.
- Prior plugin: 392,218 bytes; prior companion: 17,721,459 bytes; prior installation manifest:
  458 bytes. Each backup matched its installed source by SHA-256.
- Task definition: 1,895 bytes. The action matched the paused installed task and the principal SID
  matched the current Windows user. The initial diagnostic incorrectly tried to translate that SID
  as an account name and failed; reading it as a SID passed without changing the task.
- Task Disabled, Enabled=False; listener count on 37777 was zero; observable RuneLite process count
  was zero. The unassigned legacy database was not copied, opened, assigned or changed by this review.

After review, the user confirmed INSTALL. Exact reported build/install ending:

```text
BUILD SUCCESSFUL in 5s
8 actionable tasks: 8 up-to-date
Built C:\Users\Antonio\OSRS Flipping Friend Rebuild\build\libs\osrs-flipping-friend.jar (384.1 KB)
Companion ready (accounts-v1).
Update applied. Both installed JARs verified. Restart RuneLite to pick up plugin changes.
Press any key to continue . . .
```

Agent read-only post-install verification used `tools/status.ps1`, then
`tools/run-dev.ps1 -CheckOnly`, plus installed/build JAR hash comparison. Exact output:

```text
FLIPPING FRIEND - STATUS
Plugin: present
Installed companion: present
Pair manifest: present
Companion credential: present
Saved RuneLite login: present
Companion task: Running
Task checkout: C:\Users\Antonio\OSRS Flipping Friend Rebuild
Installed pair verified; companion responds with accounts-v1 (READY or DEGRADED).
Overall status: PAIR VERIFIED / COMPANION RESPONDING
The read-only pipeline check after login reports account coverage and market freshness.
STATUS_EXIT=0
RuneLite 1.13.1 and the installed plugin API match; cached artifacts verified.
Compatibility check passed. No client started and no files changed.
COMPATIBILITY_EXIT=0
INSTALLATION_BACKUP=C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261013-220953-f1ad1d84
Plugin INSTALLED_BYTES=393350 MATCHES_VERIFIED_BUILD=True
Companion INSTALLED_BYTES=17722571 MATCHES_VERIFIED_BUILD=True
POST_INSTALL_REVIEW=PASS (no client launched; no game input)
```

**Pipeline-check output: not run after this install; awaiting user login.** Build and backup/paired
installation checks have passed for the observed sources. Login, known-cost holding, partial buy,
cancel/collect, full sale/restart and any reconciliation message remain unobserved. The next user step
is `2 - Start Flipping Friend.bat` and manual login without making GE changes yet. Then run the
read-only pipeline check with `-SkipBuild` (the standard build already passed) and proceed one
manual checklist case at a time. Do not edit database/account records to clear a warning.

### RuneLite login freeze continuation — 13 October 2026 (source verified; live retest pending)

Base `6411f800e9a56d37de5dbd3b3194061ae262e910`; work branch `codex/runelite-login-classpath`.
Implementation `3ae5386d264d0a7055441033cf5709ed1271908f` was verified at source/documentation revision
`dcad648bc76efd7b358d57bfba847c0f3bbc2cd7` after the user closed RuneLite.
Regression commit `52e6fb965badd7d8591839afe4b03f51fee69b9c` preceded implementation. Historical main
and all live databases remain untouched. This source work has not been installed or tested in-game.

The user completed re-setup after an earlier lowercase `install` correctly cancelled it. Read-only
status at `6411f800` reported both installed JARs/manifest present, the task Running in the rebuild
checkout, `accounts-v1` responding, and `Overall status: PAIR VERIFIED / COMPANION RESPONDING`.
That verifies the companion pair, not RuneLite ABI compatibility or successful login. The user then
ran the numbered Start wrapper and reported a freeze during login at 20:38 AKDT.

Observed evidence, without game input or stopping either process:

- Launcher output included client 1.13.1 and client 1.12.39 resources on one classpath and warned
  that the plugin was built against 1.12.39. The Client error was
  `NoSuchMethodError: 'long net.runelite.api.GrandExchangeOffer.getPrice()'`, originating in the
  Flipping Utilities offer-event handler. This is an API linkage failure; do not blame or remove
  that plugin to conceal a mixed dependency set.
- Read-only javap confirmed 1.12.39 has int price/spent getters and 1.13.1 has long getters.
  All 31 Windows/amd64 artifacts selected by the official bootstrap version 1.13.1 were already
  present and hash-matched. Old client/API JARs were also present; no cache files were deleted.
- A read-only thread dump at 20:42 AKDT contained no Client game thread, while the AWT event
  thread remained waiting normally. That is consistent with the game thread ending after the
  linkage error; no successful login or UI recovery was observed. Private dump remains gitignored:
  `local-data/diagnostics/runelite-login-20261013/threads-24828.txt`.
- Automated actual-script regressions first failed all eight launcher cases (`Ran 8 tests in
  5.847s`, `FAILED (failures=8)`). The Java ABI test first failed `expected:<long> but was:<int>`.
  Logs: `.gradle/runelite-launcher-regression.log` and `.gradle/runelite-api-regression.log`.

Implemented and automatically verified source:

- `run-dev.ps1` selects the exact official bootstrap Windows/architecture artifact list, checks
  required current client/injected/API entries and each local size/hash, and excludes stale cache
  versions and other-platform natives. It reads but does not repair/delete/download cached JARs.
  Artifact names cannot escape the repository directory. The matching full API extra is appended
  only for normal developer launch. `-CheckOnly` validates without launching or downloading extras.
  Selection follows the [official launcher artifact flow](https://github.com/runelite/launcher/blob/master/src/main/java/net/runelite/launcher/Launcher.java).
- Source and the installed plugin JAR must both declare the bootstrap API version. Missing/old
  installed metadata stops launch and requires the paired numbered update; source pin alone cannot
  prove what is installed. Build pin is now 1.13.1 and JAR manifest includes `RuneLite-Api-Version`.
  Java bytecode still targets 11. Monetary test fixtures now provide long API values.
- Cumulative spent counters remain long. A targeted partial-buy regression passed with 3.5B GP
  recorded cost. Unit prices still use the existing 32-bit strategy model. A boundary regression
  first caught `ArithmeticException: integer overflow`; the source now records the raw unsupported
  observation as account-local `unsupported-offer-price.json` and pauses accounting/publication/
  advice. EMPTY and restart cannot erase the pause or manufacture disposal/profit. Cached/late
  companion advice is invalidated; other accounts remain independent. Full 64-bit unit-price
  support and positive reconciliation are future work; do not edit/delete the evidence to unblock it.

Initial verification checkpoint: all 48 Python tests passed (`Ran 48 tests in 31.241s`, `OK`, exit 0),
including eight launcher cases; isolated launcher run also passed (`Ran 8 tests in 5.510s`).
The full standard local Gradle command stopped at clean, before validating the final Java changes:

```text
Execution failed for task ':clean'.
java.io.IOException: Unable to delete directory '.../build'
Failed to delete .../build/runtime-extras/runelite-api-1.13.1.jar
BUILD FAILED in 5s
1 actionable task: 1 executed
GRADLE_EXIT=1
```

The then-open RuneLite process held that full API extra. No game process was stopped or locked file
forcibly deleted. The user subsequently closed RuneLite; process inspection confirmed zero clients.
Failure evidence is preserved as `.gradle/runelite-login-locked-gradle.log`; final Python output is
`.gradle/runelite-login-final-python.log` (gitignored, outside build).

Final local standard verification passed: 1,168 Java tests passed / six capture-dependent skips /
zero failures/errors (1,174 total across 308 suites), both supported JARs built. Exact endings:
`BUILD SUCCESSFUL in 48s`, `18 actionable tasks: 18 executed`, `GRADLE_EXIT=0`;
`Ran 48 tests in 43.965s`, `OK`, `PYTHON_EXIT=0`. Full clean-build log:
`.gradle/runelite-login-gradle.log`. Built plugin manifest declares `RuneLite-Api-Version: 1.13.1`;
plugin size 393,350 bytes; companion size 17,722,571 bytes. These are source artifacts, not proof of
the installed or running version. [PR #36 CI at dcad648b](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38515934145)
also passed Windows and Linux. Check the final PR revision's checks before merging.

The user also supplied a Java launcher popup naming missing `build/libs/flipping-friend-daemon.jar`.
Read-only Task Scheduler inventory found `FlippingFriendLearning` targeting that exact path in this
checkout. Its last recorded attempt was 21:00:01 AKDT and its next scheduled attempt was 22:00;
it was Running with the JAR absent. Supported source references to this obsolete component exist
only in tests that prohibit its activation. This popup is separate from the RuneLite API linkage error.

Task definition was exported and its action matched before cleanup:
`%USERPROFILE%/.runelite/osrs-flipping-friend/backups/legacy-task-20261013-213226-eef1efb7/FlippingFriendLearning.xml`
(1,907 bytes). A fresh action/path check and principal comparison confirmed the same missing JAR,
same checkout and current Windows user. Only that obsolete task was disabled/stopped, preserving
its definition; observed `LEGACY_TASK_STATE=Disabled`, `LEGACY_TASK_ENABLED=False`. The current
companion remained Running; observed RuneLite process count remained zero. No daemon JAR was
rebuilt and no cache/data file was altered to satisfy the obsolete task. A separate historical
forecaster task was observed Ready in the older checkout; this cleanup did not modify it.

After merge, apply both artifacts through the supported numbered wrapper with a fresh observed
backup and confirmation. No installed JARs, game settings, credentials or databases were changed
by this troubleshooting; the explicit live configuration change was retirement of the obsolete task.

**Pipeline-check output: not run.** Login, known-cost holding, partial buy, cancel/collect, full sale
and restart acceptance checks remain unperformed. Continue the checklist after compatible paired
installation and user-performed login; the read-only companion status is not proof of these outcomes.

### Backup review continuation — 13 October 2026

Base `cb2c1d2dac01865374108339fbd1fcee43ee0093`; implementation
`2b1f38d88256af35be5858ec7dbeb7c5c35978c3` on `codex/plugin-account-backup`.
Regression commit `9945dafe96cc6f3e74b5a5f9d02092e2f837b1ea` preceded the fix. The actual-script test
first failed with `Missing plugin account backup: default/session.json` (`Ran 1 test in 1.194s`,
`FAILED (failures=1)`). The wrapper backed up companion hashed scopes but omitted the plugin's
profile records under the sibling `osrs-flipping-friend/accounts/` directory.

The wrapper now copies that tree as `plugin-accounts/` before the INSTALL prompt, preserving directory
names, pre-login records, pending events and parked history without parsing/importing them. It rejects
linked directories/files before traversing or copying their targets. Companion scoped backups remain
under backup `accounts/`; the unassigned legacy database stays untouched. Tests exercise preservation
on declined installation and rejection of a nested junction/symlink using disposable files.

Local verification: 1,163 Java tests passed / six skipped / zero failures/errors (1,169 total,
306 suites), both JARs built; all 40 Python tests passed, including 32 lifecycle tests. Exact endings:
`BUILD SUCCESSFUL in 40s`, `18 actionable tasks: 18 executed`, exit 0;
`Ran 40 tests in 30.744s`, `OK`, exit 0. Logs outside build outputs:
`.gradle/plugin-account-backup-gradle.log` and `.gradle/plugin-account-backup-python.log` (gitignored).
Use the corresponding PR's Windows/Linux checks for remote results.

Live backup review at base revision `cb2c1d2d`:

- The user ran the numbered returning-user wrapper and stopped at its INSTALL prompt. Backup:
  `%USERPROFILE%/.runelite/osrs-flipping-friend/backups/20261013-193255-a12e2729/`.
- Prior plugin JAR: 324,999 bytes; prior companion JAR: 106,202,023 bytes. Both backup SHA-256 values
  matched their original JAR sources. Prior task XML: 2,113 bytes. Its principal resolves to the
  current Windows user, while its working directory/JAR point to the previous checkout.
- Missing `runtime/installation.json` was observed; the previous installation predates the manifest.
  The expected `companion/accounts/` was absent. This does not establish absence of other account data.
- Read-only filename/size inspection found 21 plugin account files totaling 1,501,532 bytes under
  the separate root `accounts/` tree. No database or credential filenames or links were found there.
  While installation remained paused, these records were copied to the same backup's
  `plugin-accounts/` directory; every relative filename and size matched (21 files / 1,501,532 bytes).
  No contents were parsed, ownership assigned, or source records edited/moved/deleted.
- At the copy preflight, the task was Disabled, listener count on port 37777 was 0, and observable
  RuneLite process count was 0. Installed JARs, saved credentials, account databases and the unassigned
  legacy database were not changed by this review. No game input occurred.
- The existing installer already passed its backup code before the source fix; the supplemental
  copy completes this particular backup. Keep that installer as the sole installation process.
  Resume only after backup review; do not start a second installer while it holds the runtime lock.

Checklist outcome: build passed; backup inspection/copy passed for observed sources; paired live
installation is still awaiting the user's INSTALL confirmation. **Pipeline-check output: not run**
(installation remains paused). Login, known-cost holding, partial buy, cancel/collect, full sale and
restart checks remain unperformed. No live planner/accounting success is claimed.

### Setup/re-setup/start continuation — 13 October 2026

Base `7b98a19bf288ab259bb275c2efaf2669028b9482`; implementation
`b5ea293686820d97df6f45e917cffb8efaf6741c` on `codex/paired-setup-start`. Regression commit `2d629cd`
preceded implementation: four failures reproduced daily start bypassing the guard, missing backup
review, and running the companion from a source build output; seven existing controls passed.

- Root `1b - Re-setup Existing Installation.bat` is the returning-user entry. Setup reuses a saved
  login without opening/displaying it, invokes the paired installer, and retargets the desktop
  shortcut only after successful installation. Fresh setup retains manual login configuration.
- `apply-update.ps1` is shared by setup, update, start and read-only status. It requires the player
  to close RuneLite, serializes installers/starts with an exclusive control-file handle, pauses and
  stops the existing companion task, then copies a dated backup before prompting for `INSTALL`.
- Backup includes prior installed JARs when present, task XML/manifest and scoped account directories
  with sidecars. Missing sources are reported; linked account directories are rejected. It never
  assigns, moves, repairs, deletes or backs up the unassigned legacy database as an account.
- The installed companion now lives at `.runelite/osrs-flipping-friend/runtime/flipping-friend-companion.jar`.
  Both installed JAR hashes and checkout are recorded in `runtime/installation.json`. Source clean
  builds no longer replace the running companion artifact. Missing/changed pairs cannot start.
- Re-setup registers a disabled Java task after confirmation, copies both artifacts, verifies the
  pair, then enables/starts it and requires an authenticated `accounts-v1` health response.
  The task uses an interactive limited principal, logon trigger and hourly watchdog while logged in;
  it no longer configures S4U/startup execution before logon or after logout.
- Everyday start builds/copies nothing and cannot open RuneLite after guard failure. Compatibility
  install/run helpers delegate to the same lifecycle. Status is read-only and refuses false readiness
  from disabled tasks or foreign/missing pairs. Uninstall removes both installed artifacts/manifest
  while keeping account/legacy data, backups and saved login; an unknown listener blocks JAR removal.
- Decline, failed backup/build/copy or task reconfiguration aborts without claiming success. The
  task stays paused on an interrupted install; rerun re-setup after resolving the cause. No automatic
  database rollback/repair exists. START HERE and README now distinguish fresh setup, re-setup,
  everyday launch, update and diagnostics.

Local verification: standard clean Gradle build passed, 1,163 Java tests passed / six skipped /
zero failures/errors (1,169 total across 306 suites); both JARs built. All 37 Python tests passed,
including 29 actual-script lifecycle tests using temporary files and mocked process/task/network/COM
boundaries. Exact endings: `BUILD SUCCESSFUL in 41s`, `18 actionable tasks: 18 executed`, exit 0;
`Ran 37 tests in 30.593s`, `OK`, exit 0. Full local logs: `.gradle/paired-setup-gradle.log` and
`.gradle/paired-setup-python.log` (gitignored). CI checks Windows/Linux; use the PR for remote evidence.

Review added an observable-task-registration guard before either JAR replacement; its regression
first reproduced a false success through direct-launch fallback when registration could not be read
back. The final Python suite has 38 tests (30 lifecycle tests). First PR CI also exposed harness
portability errors in Windows utility command discovery and Linux LOCALAPPDATA; both fixture
boundaries were corrected without weakening file-preservation or startup assertions. Windows CI
continued to fail utility hash-command resolution; JAR hashing now uses the .NET SHA-256 stream API
directly, preserving the same manifest/hash assertions without a PowerShell utility-command dependency.

**Live state is unchanged by this development.** No installed JAR, task, shortcut or live database was
modified; no game input occurred. The old disabled task/shortcut still need the user-run `1b` path.
No pipeline-check output exists for this continuation. Real Task Scheduler registration, desktop COM,
login validity, runtime binary identity and in-game accounting still require observation. The existing
PR #31 manual verification checklist remains outstanding. Do not treat mocked tests as live proof.

The 13 October local verification at implementation revision `152db838700579eabf208d4589193c9d47be3994`
passed 1,163 Java tests with six capture-dependent skips (1,169 total, 306 suites), zero failures/errors,
plus 15 Python tests, and built both JARs. It used Windows/JDK 23, Gradle 8.14, and RuneLite 1.12.39.
Exact Gradle ending: `BUILD SUCCESSFUL in 45s`, `18 actionable tasks: 18 executed`, exit 0.
Exact Python ending: `Ran 15 tests in 6.395s`, `OK`, exit 0. Local complete logs are
`.gradle/update-task-final-gradle.log` and `.gradle/update-task-final-python.log` (gitignored).
The seven updater cases also passed under PowerShell 7; the numbered wrapper uses Windows PowerShell.
[Post-merge CI for the runtime revision](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38197169733)
passed on Windows and Linux with JDK 21. Compilation targets Java 11.

```powershell
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
python -m unittest discover -s tools/tests -v
```

On Linux use `bash ./gradlew` for the same Gradle tasks. Redirect clean-build logs outside `build/`
to avoid Windows file locks. Supported artifacts are `build/libs/osrs-flipping-friend.jar` and
`companion/build/libs/flipping-friend-companion.jar`; update both together. `tools/pipeline-check.ps1`
checks the live protocol/scoped ledger using read-only HTTP requests, without synthetic trades.

Merged implementation working branches and superseded logs were removed. A reusable managed
worktree, original personal data, final verification evidence, the separate `copilot-rebuild`
history, and the pre-takeover stash were preserved. Compare divergent recovery/stash work deliberately.
Update this handoff and TASKS with each implemented boundary, evidence, and remaining limitation.
