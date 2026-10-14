# Agent handoff

Updated 13 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## Current source and branch

Active development and the GitHub default branch are **`CoPilot's-Rebuild-from-Main`**.
`main` at `0cb8ef8` is an older cleanup/revert baseline; explicitly checking out `main` does not
include the work below. Start a new `codex/` branch from the current remote rebuild branch.
Do not reset or force-push the historical branches to make them look current.

The latest trading-runtime implementation is merge commit `6b4140109de6a63d93614740c87b32f72cfe171d` (PR #31).
The update-tooling continuation below does not add trading or learning features.

| Merged work | Result | Merge commit |
| --- | --- | --- |
| [PR #27](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/27) | Physical blocked holdings remain visible; personal stock adoption requires the existing opt-in. Added Windows/Linux CI, checkout portability fixes, and an evidence-based project direction. Removed the orphan reference gitlink that broke checkout. | `ab7aeb23488e17e59773bcf805c144fd85c12434` |
| [PR #28](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/28) | Account-specific databases/services/API scopes, cache and outbox fencing, recovery of offers older than four hours, read-only diagnostics, and Copilot CSV ingestion. | `6ec1a8f238a2232f31bd4906c48e4dafea7244bd` |
| [PR #29](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/29) | Atomic durable sale proceeds, proportional cost, tax/profit bounds, rule evidence, and live Performance reading that account ledger. | `bfef926d334a3374a7c390d595ec6eab0eba8cdc` |
| [PR #31](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/31) | Stale snapshots cannot restore sold positions; planner per-item exposure reads durable positions and open-buy reservations; unresolved state withdraws buy advice. | `6b4140109de6a63d93614740c87b32f72cfe171d` |
| [PR #33](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/33) | Guarded update rejects disabled/foreign tasks and incomplete artifact pairs; Java-on-PATH fallback; recorded incomplete live verification. Windows/Linux CI passed. | `7b98a19bf288ab259bb275c2efaf2669028b9482` |

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

No live RuneLite installation or live database migration was performed. Live verification against
actual holdings/collected coins remains outstanding. Profit superiority, feature parity, online
logistic learning, shadow trading, and automatic challenger promotion have not been established.
Historical package counts, roadmaps, and non-activation scaffolding are not runtime feature evidence.

## Durable planner exposure
Planning reads durable positions and offers together (`SqliteStore.planningProjections`) and
`DurablePlanningExposure` counts known held cost plus unfilled BUY_OPEN/BUY_PARTIAL reservations,
without double-counting fills. Unresolved positions, unknown offer states, or overflow replace any
cached plan with COMPANION_STATE_UNAVAILABLE. Only `committedByItem` changes; cash, committedCoins,
slots, preferences, and session drawdown remain client inputs. Snapshot reconciliation also refuses
snapshots older than the latest position event, or not newer than the latest disposal, so sold stock
cannot reappear. Concurrent read/write interleaving and a full live-market service test are untested;
no live RuneLite verification was performed. Details: `docs/architecture/durable-planning-exposure.md`.

## Live verification checklist (pending, do this next)
PR #31 passed Windows/Linux CI and a full local build, but has **never run against the real
account database or RuneLite**. Complete this before relying on it for trading.

1. **Build first.** From an up-to-date `CoPilot's-Rebuild-from-Main` (at `6b4140109de6a63d93614740c87b32f72cfe171d` or later):
   `.\gradlew.bat clean test jar :companion:shadowJar --console=plain` and
   `python -m unittest discover -s tools/tests -v`. Stop on any failure.
2. **Back up before installing.** Stop RuneLite and the companion. Copy (never edit or move) the
   account directory `companion/accounts/<accountId>/` and the currently installed plugin and
   companion JARs to a dated backup folder. Do not touch the unassigned legacy
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
