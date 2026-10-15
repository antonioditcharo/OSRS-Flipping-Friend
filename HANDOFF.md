# Agent handoff

Updated 14 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## Streamlined delivery and installed ledger fix — 14 October 2026

**Current owner direction:** the agent handles routine diagnostics, automatic fresh-backup
verification, paired updates, launching and GitHub delivery. Batch related fixes and acceptance
checks; do not require pasted output, an INSTALL reply or a separate response for every step.
This supersedes the earlier manual handoff routine. Login and all in-game input remain player
actions. Do not force-kill RuneLite, modify databases or bypass accounting guards. See
[development workflow](docs/development-workflow.md) and the revised root home checklist.

[PR #45](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/45) passed both exact-head
checks at `b22db0d` in [run 38671474499](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38671474499)
and merged as `edf05a85c8ff5fedd07f598829845893341a86bc`. The normal checkout is clean and updated.
Owner completed its supported paired install after review of backup
`20261014-183810-89356679` (three companion account files / 7,671,456 bytes; 22 plugin account files /
1,601,253 bytes; names/sizes, prior JAR/manifest hashes and task identity matched). Build succeeded
in 6s with seven tasks executed/one up-to-date. Actual installed plugin 448,467 bytes and companion
17,783,446 bytes match their builds and manifest. The agent checked directly:

```text
STATUS_EXIT=0
RuneLite 1.13.2 and the installed plugin API match; cached artifacts verified.
Compatibility check passed. No client started and no files changed.
COMPATIBILITY_EXIT=0
Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
PIPELINE_EXIT=0
```

The prior ledger HTTP 500 is fixed in the installed service. A pre-login plan is not trading
readiness; existing reconciliation remains unresolved. Startup/UI and controlled trading checks
remain incomplete. No synthetic account events or game input were sent, and no database was changed.

Workflow source is `codex/streamline-update-workflow` from `edf05a8`. Regression/feature commit
`04261a2` demonstrates the missing unattended behavior and the launcher's build-directory cache
dependency. Production `06f9a9e08c92c616207b7169067e03a6f17e00e7` adds opt-in `-Unattended` to the
supported wrapper: require a verified existing pair/task, create a fresh backup, verify JAR/manifest
hashes and saved task export, compare account file names/sizes only, and write
`backup-verification.json` before proceeding without Read-Host. First setup/reconfiguration remains
interactive. Existing release, running-client, task, paired-copy and scoped-health guards remain.
The default numbered self-service update still has its manual confirmation.

The launcher now uses `%USERPROFILE%\.runelite\osrs-flipping-friend\developer-api\` for the public
full API JAR. It no longer makes Gradle clean delete a library loaded by RuneLite. Tests remove
disposable build outputs and verify all launched classpath files remain available; old caches and
credentials remain untouched. No Java runtime source differs from installed PR #45 (diff exit 0),
so this tooling-only batch requires no additional paired JAR installation.

Local verification at production `06f9a9e`: standard clean build successful in 57s, all 18 tasks
executed; all 61 Python tooling/import tests passed in 109.134s. The Java suite remains 1,288 passed
with six existing skips and no failures/errors. Logs remain outside build under ignored
`.gradle/workflow-*`. Tests cover no prompt, pending-data preservation, same-size JAR corruption,
incomplete account copies, altered installed pair, invalid modes/tasks and a running game, in
disposable fixtures with process/task/network operations mocked. No live unattended reinstall was
needed or performed for this batch. Final-head CI/merge and live launch/clean-build receipts belong
to its GitHub PR; require both checks before normal-checkout fast-forward.

## Historical live login: ledger read and startup guidance faults — 14 October 2026

**Current checkpoint; older "installation pending" statements below are historical.** Compatibility
[PR #44](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/44) passed both exact-head
Windows/Linux checks at `b51b54233cbf7720e439b8d1534cd09bb4882554` in
[run 38669254087](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38669254087)
and merged as `be77e67bb43e932496d2a2cccf4fe4d5e85186f7`. The normal rebuild checkout was
fast-forwarded cleanly; tested-tree diff and required `ee0d3aff` ancestry checks exited zero.
Historical `main` remains `0cb8ef8` and is untouched.

The owner completed another supported paired update after fresh backup review:
`C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261014-181307-eeed5a5b`.
Account metadata matched (companion: three files / 7,671,456 bytes; plugin: 22 / 1,502,023 bytes).
Prior JAR/manifest hashes, task action and owner matched. Owner output: BUILD SUCCESSFUL in 6s,
eight tasks (three executed/five up-to-date), companion ready (accounts-v1), both JARs verified.
Installed plugin 448,137 bytes and companion 17,782,905 bytes matched their builds and manifest.
Real status and launcher CheckOnly both exited zero with API 1.13.2. Full receipts are on PR #44.

The owner then launched/logged in manually. Screenshot shows the compact sidebar, cadence/risk,
Collect advice and "Recorded profit / Unavailable / Waiting for account ledger". Collection
highlight is visible; no collection action or other controlled trade acceptance is inferred.
The launch log records this plugin exception before login:

```text
java.lang.NullPointerException: null
    at client.getVarbitValue(client.java:3327)
    at client.getVarbitValue(client.java:62148)
    at com.flippingfriend.overlay.GeSearchSuggestion.refresh(GeSearchSuggestion.java:35)
    at com.flippingfriend.overlay.GeInputAssist.refresh(GeInputAssist.java:80)
```

The announced GET-only `tools/pipeline-check.ps1 -SkipBuild` failed. Output below redacts only the
opaque account ID; the exact output and route responses are private in the normal checkout's
`local-data/diagnostics/pr44-*` files:

```text
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
FAIL [<account ID redacted>] Response status code does not indicate success: 500 (Internal Server Error).
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_EXIT=1
```

Separate authenticated GETs: action HTTP 200 / WAIT; portfolio HTTP 200 / UNAVAILABLE with
"Durable holdings or offers require reconciliation before new buys."; ledger HTTP 500 with
`{"error":"malformed trade scope event"}`. SELECT-only diagnosis (`mode=ro`, SQLite authorizer
rejecting non-read operations) found 21 item-zero RECONCILE effects. These are recorded rejected
EMPTY observations with zero quantity/cost, not unidentified acquisitions. No live record was
edited, deleted, migrated or repaired. Old companion.log/err files date from earlier launches and
were not treated as current failure evidence. Owner confirmed RuneLite closed before clean build.

New branch `codex/live-ledger-startup` starts at current rebuild `be77e67`. Regressions `567132b`
reproduced seven failures across startup lifecycle, evidence validation, scope replay and HTTP.
Follow-up `d39ee73` reproduced a holdings availability error after the initial ledger fix. Source
`ff2754bda273e593657a639699cd7257d8332bb8` fixes both live failures and that UI boundary:

- Search guidance checks client/account/editor readiness before reading game variables.
- Item-zero RECONCILE with no economic fields remains visible as **Unidentified offer** in Missed
  flips. Economic effects still require a real item; unknown money stays unavailable.
- Unassigned reconciliation withholds managed exit advice and new buys. Raw acquisitions and
  recorded sale totals stay intact. Later sales cannot borrow cost through that unresolved gap.
- Holdings availability reports uncertainty rather than "No tracked purchases remain". No new
  position book, database schema/migration, input automation or guard bypass is introduced.

Standard clean verification on that production source:

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 37s
18 actionable tasks: 18 executed
CLEAN_BUILD_EXIT=0
Plugin: 568 tests; Core: 168 tests; Companion: 558 tests including 6 skips
Total: 1,288 passed; 6 skipped; 0 failures/errors
python -m unittest discover -s tools/tests -v
Ran 54 tests in 84.637s
OK
PYTHON_EXIT=0
Candidate plugin: 448,447 bytes; companion: 17,783,426 bytes
Project class bytecode: major 55 (Java 11), both JARs; plugin API 1.13.2
```

A local probe used the candidate's **SqliteStore.openReadOnly** on the existing scoped database,
without starting a service or running a writable constructor. It returned a valid schema-2 ledger,
four sale updates (two bounded/two unresolved), 1,028 missed rows including 21 unidentified rows,
and planning unavailable. Position-event count remained 1,034 before/after. Exit zero. This confirms
the candidate can read the observed data; it is not an installed HTTP/UI acceptance result.
Logs/scripts remain outside build in ignored `.gradle/`; private row/probe evidence stays local.

Next: both exact-head CI checks, merge this candidate, fast-forward the normal checkout, then a
fresh reviewed backup/paired update. Installed runtime remains **PR #44 at be77e67** until that
update; its live ledger still fails. Retest login/startup log and GET-only pipeline after installing.
The candidate intentionally preserves existing reconciliation blocks. Source tests or a valid
ledger read do not establish buy readiness, complete PR #31, or validate trade/restart workflows.

## Historical RuneLite 1.13.2 source continuation — 14 October 2026

**This checkpoint supersedes the source/runtime status in the older entries below.** PR #43 was
approved by the owner and merged as `440c44fd115e7ab016a000f5c71d79d8b2a7f4c9`. Both checks passed
on approved head `860cad83970cf401242db7e3672de17a55205b14` in
[run 38660675992](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38660675992).
The normal checkout was fast-forwarded cleanly to that merge and its tree matched the approved
head. PR #40/#41 are merged; #42 was closed as integrated into #43. Historical `main` remains
`0cb8ef8aab9114f02b16d2eca4433c0f7e010894` and must not be changed.

The owner completed the supported paired update from the normal checkout after review of backup
`C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261014-174522-d5cd8522`.
Companion account metadata matched: three files / 7,671,456 bytes; plugin account metadata matched:
22 files / 1,502,023 bytes. Prior JAR/manifest hashes and the task action/owner were verified;
database contents were not opened. Owner output reported BUILD SUCCESSFUL in 7s, companion ready
(accounts-v1), and both installed JARs verified. Read-only status subsequently exited zero.
Observed installed sizes: plugin 448,134 bytes; companion 17,782,902 bytes. This is the #43 pair
compiled for API 1.13.1, **not the new 1.13.2 candidate**.

The launch preflight then reported exactly:

```text
RuneLite is 1.13.2 but this source targets 1.13.1. A compatible project update is required. No client was launched.
COMPATIBILITY_EXIT=1
```

The owner reaffirmed autonomous project management and explicitly revoked the earlier rule to stop
on every failure. Continue diagnosis and regression-first source fixes without repeatedly asking
for management permission. Player-only game actions, backup review before paired installation,
and no live database modification remain in force. Never bypass compatibility/accounting guards.

Compatibility source is on `codex/runelite-1-13-2`, based on `440c44f`. Launcher regressions
`6eadb7b` reproduced three failures using the real build pin and synthetic current-release JARs.
Updater regressions `ccceff5` reproduced three failures: a newer official release, unavailable
release metadata, and malformed metadata all previously allowed installation to proceed.
Production source `c32e7289b1466b8348d581e7c75447714e497232` pins compile/test/manifest to
1.13.2, uses the new official `VarPlayerID.GE_NEWOFFER_PRICE` name for unchanged varp 5753, and
checks official release metadata before backup, task stopping or file replacement. Start/status
retain their separate paths; cached artifact/version guards are preserved. See
[compatibility contract](docs/architecture/runelite-compatibility.md).

Final local verification at source `c32e728`:

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 50s
18 actionable tasks: 18 executed
CLEAN_BUILD_EXIT=0
Root: 563 tests, 0 failures, 0 errors, 0 skipped
Core: 166 tests, 0 failures, 0 errors, 0 skipped
Companion: 555 tests, 0 failures, 0 errors, 6 skipped
Total: 1,278 passed, 6 skipped, 0 failures/errors
python -m unittest discover -s tools/tests -v
Ran 54 tests in 92.231s
OK
PYTHON_EXIT=0
Plugin candidate JAR: 448,117 bytes; RuneLite-Api-Version: 1.13.2
Companion candidate JAR: 17,782,885 bytes
Project class bytecode: major 55 (Java 11), both JARs
```

Logs remain outside build under `.gradle/runelite-1.13.2-*`. The owner updated RuneLite through
the official launcher and confirmed it closed; a subsequent process check observed zero RuneLite
processes. A separate disposable profile copied 31 public official Windows client artifacts and
the candidate plugin, with no login/account/database files. The actual launcher CheckOnly returned:

```text
Copied 31 official client artifacts into an isolated validation profile.
Only public JARs were copied; no login credentials, account records or databases were accessed.
RuneLite 1.13.2 and the installed plugin API match; cached artifacts verified.
Compatibility check passed. No client started and no files changed.
CANDIDATE_COMPATIBILITY_EXIT=0
```

Here "installed plugin" means the candidate in that isolated profile. The real installed pair is
still the earlier API 1.13.1 pair. GitHub checks/merge receipts belong to the compatibility PR;
require both checks on its final head, then fast-forward the normal rebuild checkout. Next owner
action is **4 - Update.bat**, fresh backup review at INSTALL, then paired installation. Do not
reuse the prior backup approval. Run real status and launcher CheckOnly afterward, before Start.

Live pipeline-check output for the 1.13.2 pair: **not run; pair not yet installed**. Compact UI,
search/key/Confirm, automatic scope, controlled partial-fill/cancel/collect/restart and PR #31 live
checks remain pending. Existing reconciliation faults are not presumed fixed. No client was
launched or game input sent, and no live database was modified by this compatibility work.
Publication gates are tracked in [release readiness](docs/release-readiness.md).

## Historical compact sidebar review — 14 October 2026

Completeness follow-up: root [HOME TEST CHECKLIST.md](HOME%20TEST%20CHECKLIST.md) gives the
approved-merge gate, guarded regular-checkout update, backup-review pause and ordered live checks.
START HERE no longer tells users to classify personal holdings or says 2h/8h are missing. No source
behavior changes accompany this follow-up. Final implementation head `e4dd4d2` passed both checks
in [run 38653571963](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38653571963).
Any later documentation head still requires both current checks and owner approval before merge.

**Newest review source: `codex/copilot-panel`; not installed or merged.** It starts from rebuild
`5ac97048c0fc44a513cc86564867406479b0d8db`, includes combined integration
`d0bbef8038341c80a25cd44fe7e43355d3465ca1` (PR #40/#41/#42 behavior), and merges PR #42's final
`8aebbed4636591458463311fe6207106a544e273` documentation boundary. Original PR heads remain
unchanged and open. Review this as one coherent candidate; coordinate overlap before merging
earlier slices. Historical main is untouched.

One Copilot-style instruction, exact quantity/price, graph/pause/block/skip, real 5m/30m/2h/8h
presets, risk, durable Recorded profit and spendable budget/free slots replace the old stack. Gear and More
open Settings and existing record consumers in modeless details; Missed flips remains separate
and read-only. SuggestionCard, OnboardingCard and CollapsibleSection are deleted. Plugin injection,
refresh and shutdown connections remain. See [workflow/source map](docs/architecture/compact-panel.md),
[native visual QA](design-qa.md) and [synthetic capture](docs/images/compact-buy.png).

Pause withdraws walkthrough/editor/search/input advice and advisory notifications, fencing queued
key fills across pause/resume even with identical fresh advice. Observation/accounting continues.
Resume refreshes; account reset clears pause. New 120/480-minute values reach existing horizon and
account contracts; legacy 180-minute settings remain visibly custom. Unknown-cost sales and durable
ranges/unavailable states remain intact. No fabricated session reset, GP/hour or journal fallback.

Regression trail: `e3ebc21` specifies the replacement contract (five failures on the old panel);
`f0fbf6e` implements it with the failing custom-selection case, corrected at `20a8b99`.
`f58790c` / `60f1010` cover native wrapping, including a corrected FlatLaf measurement fixture;
`f05dc4b` fixes the real 16px/48px long-item clip. `b3bd364` reproduces Settings alignment.
Source `a08eb43e56b25f0fb41499fc1d2ff1a9f855329d` fixes it and records icon attribution.
Source `d1191f891e518c7cf236e4640b4010ae48cff1dd` hides the obsolete intro preference,
preserving its saved key. No inactive intro-card toggle is left in RuneLite configuration.

A final connection review found inventory-only cash had replaced the prior spendable budget.
`33e5c96` reproduces lost bank/collection/cap and F2P display connections; final source
`7c213a54ee3f2290e62506a8f360d2c09948dd9e` restores the shared planner budget and account mode.

Final local Java verification on 14 October 2026, source `7c213a5`:

```text
RuneLite process count: 0
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 33s
18 actionable tasks: 18 executed
FINAL_CLEAN_BUILD_EXIT=0
Root: 563 tests, 0 failures, 0 errors, 0 skipped
Core: 166 tests, 0 failures, 0 errors, 0 skipped
Companion: 555 tests, 0 failures, 0 errors, 6 skipped
Total: 1,278 passed, 6 skipped, 0 failures/errors
python -m unittest discover -s tools/tests -v
Ran 48 tests in 78.292s
OK
Plugin JAR: 448,114 bytes
Companion JAR: 17,782,882 bytes
Project class bytecode: major 55 (Java 11), both JARs
```

Logs: `.gradle/compact-panel-budget-final-clean.log` and `.gradle/compact-panel-python.log`, outside build.
The Python tooling check ran during the earlier clean verification at `a08eb43`; tooling sources
did not change afterward. Earlier complete builds passed 1,277; the budget regression adds one passing test.
A preliminary unqualified `test --tests ...` passed selected root tests but failed because companion
had no matching tests; targeted invocations were corrected to `:test`. Final verification runs every
module without filters. No final result is inferred from that preliminary failure.

Live pipeline-check output: **not run for this slice**. Installation, scaling/focus and game
acceptance: **pending owner**. No game input, installation, scheduled-task/runtime change, database
repair/migration/edit or trade action occurred. Last recorded runtime remains PR #37 `49934e44`;
it was not reinspected by this source-only task. Existing durable reconciliation faults and PR #31
live checklist remain unresolved. After CI and owner approval, merge the candidate, update the
normal rebuild checkout and use its paired backup/update wrapper. Never install from this temporary
worktree or repair a database to make a check pass.

GitHub: [combined PR #43](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/43), open
against `CoPilot's-Rebuild-from-Main`, mergeable and awaiting owner approval. Both exact-head
checks passed at `5c6159d09ebeabadc5afb1546e09326abeb1ee89` in
[run 38652952390](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38652952390):

```text
Supported build (ubuntu-latest): completed / success
Supported build (windows-latest): completed / success
```

This earlier CI receipt predates the final budget-display correction. Require both checks on the
current PR head before merging; the authoritative latest-head status and receipt are on PR #43. Original #40/#41/#42 remain open
at `fd60c8b` / `9f254e0` / `8aebbed`, respectively. No PR was merged or installed.

## Automatic trade scope source implementation — 14 October 2026

Source commit `fe8e72c3170659923570f2f83954baa27d04f6f1` on `codex/automatic-trade-scope`, based on rebuilt/default
`5ac9704` after design PR #39 merged with both exact-head CI checks passing. Historical `main`
remains `0cb8ef8`; required `ee0d3aff` ancestor check exits 0. Regression-first commit `b25a358`
reproduces three existing personal adoption/recount failures before production changes.

Final source follow-ups: `1515fc3` corrects UTF-8 text with a failing-before-fix regression.
`1275bd7` reproduces delayed sell guidance writing under a newly selected account;
`b07f9cfd0809912726e3da86e94c8ae12014e43a` binds both intent creation and resumption to the
client-captured offer account. Final clean verification below includes both fixes; all original
account/offer observation regressions still pass. Final log: `.gradle/automatic-scope-final-account-clean.log`.

The current source replaces routine item classification with remaining quantities from accepted
purchase/sale deltas in the existing durable account event ledger. Source-free personal snapshots
never enroll belongings; matching an item name/history alone is insufficient. The automatic view
is read-only and ephemeral, not a third saved position book. Ledger schema 2 supplies these positions
and incomplete/uncertain evidence rows to the plugin; future observed executions use the same view
for accounting. No past sale profit/tax, legacy position projection or account database is rewritten.
Known unresolved costs, actual RECONCILE executions and ERROR offers still block unsafe entries.

The adoption setting, Review holdings panel and Not holding this control are removed. Old data and
saved preferences are preserved. Holding and unsolicited exits use fresh durable trade quantities;
pending delivery or old/unavailable ledger data has no local runtime ownership fallback. Unknown
cost remains unknown in actual manual sell maintenance. The player-selected sell editor receives
fresh item/quantity price guidance, cleared on close, buy setup, unsupported price, missing stock,
stale/future market data and account transition. Opening the editor records no execution. An exact
sell-offer reprice intent survives restart and waits for observed cancel/collect before offering a
fresh relist capped to the unsold remainder. It relies on the existing 12-hour collected-offer proof;
missing proof never creates collection or disposal. All game actions remain manual.

Missed flips opens a separate read-only table of recorded update dates, quantities, gross proceeds,
tax/profit ranges or Unavailable, and evidence reasons. Routine personal snapshots are absent.
Legacy known-cost snapshots without acquisition proof are labelled legacy records, not current
physical inventory. The main sidebar rendering was inspected in `build/ui/no-clipping.png` during
verification. Existing local journal/calibration and realized session-risk inputs still require
migration; unrealized risk marking now uses the same managed trade quantities. No profitability,
feature-parity or learned-policy promotion claim is made.

Verification on this Windows PC (14 October, 16:37:24 UTC):

```text
RuneLite process count: 0
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 28s
18 actionable tasks: 18 executed
FINAL_CLEAN_BUILD_EXIT=0
Root: 502 tests, 0 failures, 0 errors, 0 skips
Core: 166 tests, 0 failures, 0 errors, 0 skips
Companion: 555 tests, 0 failures, 0 errors, 6 skips
Total: 1,217 passed, 6 skipped, 0 failures/errors
python -m unittest discover -s tools/tests -v
Ran 48 tests in 80.074s
OK
PYTHON_EXIT=0
Plugin JAR: 418,626 bytes
Companion JAR: 17,753,394 bytes
Project class bytecode major version: 55 (Java 11), both JARs
```

Behavior checks cover saved adoption settings, personal extra quantities, observed buys, partial
sales, mixed tracked/untracked sales, duplicate delivery, restart recovery, safe historical gaps,
unsafe current exposure, canonical UI/exit ownership, selected sell context, fresh quote guards,
account-scoped relist intent, and read-only Missed flips. Intermediate failing logs remain under
`.gradle/automatic-scope-*` outside build; the entry presenter source hash was updated only for the
intentional profit-availability field, with its authority assertions preserved.

GitHub: [PR #40](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/40) is open against
`CoPilot's-Rebuild-from-Main`. Both exact-head checks at
`16222f0d2625c6f12726423d4075c65d5331b6f3` passed in
[run 38598173659](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38598173659):

```text
Supported build (ubuntu-latest): completed / success
Supported build (windows-latest): completed / success
Workflow: completed / success
```

The follow-up corrects Windows text encoding after a new failing regression demonstrates
UTF-8 mojibake in edited comments/documentation. It preserves the source behavior above, uses
explicit UTF-8 for text edits and rechecks the standard build. Its exact-head CI must also pass
before merge; keep PR #40 unmerged until owner approval. The checkout remains on the clean,
pushed implementation branch for review. No historical branch is altered.

**Runtime remains installed PR #37 (`49934e44`), not this source revision.** No installer was run,
no service was stopped, no game input was sent, and no live database was opened or modified during
this implementation. Pipeline output for this new pair is **not observed / pending installation**.
The previous live WAIT/UNAVAILABLE faults remain historical evidence and are not presumed cleared.
The PR #31 live checklist remains incomplete; this source verification does not complete it.

Next owner-operated acceptance: after source PR checks and review, use the numbered paired Update
wrapper, display/review its fresh backup, and wait for uppercase INSTALL before installation. Then
Start/login manually and perform one announced step at a time: GET-only pipeline; compact panel and
Missed flips; price for a selected personal-item sell without executing it; quote withdrawal on
close/item/account change; a small controlled observed buy/sell; partial cancel/collect/relist and
restart replay. Record actual dates/revision/pipeline output/outcomes without editing live rows.
If an existing fault prevents any check, preserve the exact output and reproduce the bug before a
new source fix. Completion/merge of the final live acceptance record still needs owner approval.

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
| [PR #38](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/38) | Authenticated, copied offer observations and ordered load replay; queued publication retains session/account identity. Windows/Linux CI passed on final revision `84159c6`. Existing live reconciliation faults are preserved. | `9a4c1ec8f8f2a05d34072c0f5e6cf6cbd34434ab` |

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
   `python -m unittest discover -s tools/tests -v`. Preserve failures and reproduce bugs before
   source fixes; follow the owner's current autonomous-management direction in the newest checkpoint.
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

### Search suggestion and configured input key — 14 October 2026 (stacked source; not installed)

Owner explicitly requested a recommended BUY item in game chat search and a player-assigned key
to fill quantity/price prompts. This narrow player-triggered prompt write supersedes the earlier
blanket ban on plugin input text writes; the agent must still never operate the real game, and
all offer placement/cancellation/collection/confirmation stays manual. Implementation details,
primary references and acceptance cases: [trade input assistance](docs/architecture/trade-input-assistance.md).

Branch `codex/trade-input-assist` started at fetched rebuild `5ac97048`, then merged the unchanged
PR #41 head `9f254e04e9c83f3d653b5ab5a514c4c144d62123` as a dependency. Its PR is stacked on
`codex/trade-ui-walkthrough` to keep the new review diff separate; retarget/reconcile with current
rebuild after PR #41 is approved and merged. PR #40 is still separate at `fd60c8be`, not included
in this source branch. Neither original PR/head nor the root checkout was changed.

- Test-first commit `b6bfe475e0ce57e44a66af7f2b898b00854b111f` specifies the new controllers/config;
  baseline compilation fails because the features do not exist (not a historical bug reproduction).
- Implementation/prototype `0abf74c` includes two failing behavior regressions for a queued key
  press surviving close/reopen or temporary readiness loss. Fix `648765b74505dfda114e831e5ad3cee4a2769c8f`
  advances the input permission generation on lost/changed eligibility; both regressions now pass.
- Empty BUY search gets a selectable suggested row/icon without replacing the native previous
  item/results. Typed personal queries, wrong side, withdrawn advice and hidden/disabled UI hide it.
- `Fill recommended price/quantity` defaults to Not set. One configured key press fills one visible
  matching numeric prompt; Enter is rejected as a binding. Held/repeated/typed events cannot advance
  another field. Enter and Confirm remain separate player actions; filling text does not unlock Confirm.
- AWT reads a copied target. The client-thread callback rechecks profile, item, side, slot, advice,
  key setting, numeric mode, exact prompt/widgets, focus, readiness and generation before writing
  only INPUT_TEXT/display text. Startup/shutdown/session/config/editor boundaries invalidate permission.

Standalone standard Windows verification at `648765b`: **1,239 Java passed / six skipped**
(1,245 total, 323 suites), zero failures/errors, both JARs built. Exact output:
`BUILD SUCCESSFUL in 34s`, `18 actionable tasks: 17 executed, 1 up-to-date`, exit 0.
Python: `Ran 48 tests in 75.971s`, `OK`, exit 0. Both project class targets are Java 11 (major 55).
Full logs outside build: `.gradle/trade-input-final-gradle.log`, `.gradle/trade-input-final-python.log`;
test-first/intermediate/queue regression logs remain under `.gradle/trade-input-*` (gitignored).

Combined preview `codex/input-assist-scope-integration` at
`d0bbef8038341c80a25cd44fe7e43355d3465ca1` incorporates the already verified PR #40/#41 merge
resolutions and this slice. Source merged cleanly; TASKS conflict was resolved retaining both
pending workflows. A new behavior test proves selected personal-sale quote precedence over a
background buy, unknown profit preserved, text fill on an explicit key, and normal Confirm still
blocked until copied observations show manual Enter committed the value.
Full standard preview: **1,260 Java passed / six skipped** (1,266 total, 330 suites), zero failures/
errors; both JARs built. Exact output: `BUILD SUCCESSFUL in 30s`,
`18 actionable tasks: 15 executed, 3 up-to-date`, exit 0. Evidence:
`.gradle/input-assist-integration-gradle.log`. The preview is not an approved installation target.

Reference is the current plugin-hub-pinned Copilot source `c8dbb5155c44156ed3050c50155b086c25815556`.
The input write and native selection listener follow that player-operated pattern; no claim of
official approval for this plugin or live acceptance is made. Native chat appearance/selection,
physical keyboard dispatch and price-mode behavior are still unobserved while the player is away.

**Pipeline-check output: not run for this slice.** Installed runtime remains PR #37. No installer,
live database edit/repair, task change, credential change or agent game input occurred. Do not merge
pending slices until their final-head CI passes and the owner approves. After merging/reconciling
the reviewed sources, use the root supported paired update with fresh backup review. Player then
checks search/select, assign key, quantity/key/Enter, price/key/Enter, manual Confirm, personal sell,
normal typing, held keys, disabled/unassigned/Enter settings, editor close/reopen and account restart.
Keep existing accounting exposure guards and complete the outstanding PR #31 live checks separately.

### Parallel trade UI slice — 14 October 2026 (source verified; not installed)

Owner requested a parallel slice before accepting the automatic trade-scope work. Standalone
branch `codex/trade-ui-walkthrough` starts from fetched rebuild
`5ac97048c0fc44a513cc86564867406479b0d8db`; regression commit `52513d1` recorded eight failures
before production changes. Implementation is `b0ee05c8b0b68cb5ea570c510a7ed8de99a07508`, followed
by personal-context rendering regression `10c4fe4e6803bb2b61595822c0c3aa6bf8809bb2`.

- Quantity and price highlights follow committed game values and return when those values change.
  A click opening an input field no longer counts as completing it. Both wrong fields are highlighted.
- Normal Confirm is guarded while item/side/quantity/64-bit price differ or input remains open.
  The player can right-click the original native Confirm to deliberately override for a personal
  trade. No field population, scripted game input or automated submission exists.
- Actual dynamic buttons are resolved by actions; hidden controls and mere captions are rejected.
  The visible lock explains the override. Duplicate number hints no longer overprint the card.
- Logout/editor close clears progress and override permission. Queued recommendation callbacks
  check session/profile. No accounting, ownership, database or installed-runtime changes occurred.

Standard Windows verification at the production revision: **1,221 Java passed / six skipped**
(1,227 total, 321 suites), zero failures/errors; both JARs built. Exact output:
`BUILD SUCCESSFUL in 37s`, `18 actionable tasks: 17 executed, 1 up-to-date`, exit 0.
Python output: `Ran 48 tests in 81.010s`, `OK`, exit 0. Final personal-context rendering assertion
also passed (`BUILD SUCCESSFUL in 5s`, exit 0). Logs outside build outputs:
`.gradle/trade-ui-final-gradle.log`, `.gradle/trade-ui-final-python.log`, and
`.gradle/trade-ui-personal-lock.log`. Synthetic render fixtures are not live screenshots.

PR #40 remains OPEN and unmerged at `fd60c8be9b7313178ab9700018a02bfa34004c24`; final-head
Ubuntu/Windows CI passed (run 38599815145). Its source is in a separate branch; earlier "not
implemented" entries below describe the prior checkpoint. This UI slice does not silently include it.
An isolated combined preview at `22985fa4955a2266fbcde4ae607334284e9237a0` on
`codex/trade-ui-scope-integration` passed **1,241 Java / six skipped** (1,247 total, 328 suites),
zero failures/errors, with both JARs built: `BUILD SUCCESSFUL in 28s`,
`18 actionable tasks: 17 executed, 1 up-to-date`, exit 0. A merge regression first reproduced the
personal-item lock disappearing; the resolution preserves both quote refresh and menu guard.
Integration details and required merge resolutions are in
[trade UI walkthrough](docs/architecture/trade-ui-walkthrough.md).

**Live pipeline-check output: not run for this slice.** Installed pair remains the prior PR #37
runtime; no agent game actions, installation, task changes or database inspection/repair occurred.
Neither PR is merged or installed. After owner approval and final-head CI, use the root supported
paired update with fresh backup review, then let the player perform the documented BUY/SELL,
override, field-edit, layout, restart/profile and maintenance acceptance checks. Existing live
account reconciliation and the PR #31 checklist remain outstanding.

### Workflow correction — 14 October 2026 (direction accepted; replacement not implemented)

The owner rejected routine player classification and main-panel clutter. The new target is
[automatic trade scope and player-started sales](docs/architecture/automatic-trade-scope.md):
track outstanding quantities from observed purchases; leave unrelated personal holdings outside
trading stock; offer fresh sale-price guidance for an item the player selects to sell, then maintain
the actual observed sell offer. Incomplete trades belong in a separately opened Missed flips view.
Unknown acquisition costs/profit, trade identities and physical ownership evidence stay intact.

This supersedes the PR #37 review/adoption workflow as product direction. Do not ask the player
to classify routine personal inventory or treat historical manual-review tasks below as the next
product acceptance target. Do not hide known-cost/active exposure conflicts merely to show buys.
Missing history and extra personal units of the same item require quantity/provenance matching,
not an item-ID membership check. The single-authority migration is still the prerequisite.

Documentation branch: `codex/automatic-trade-scope-design`, from fetched rebuild `9a4c1ec8`,
including the PR #38 merge checkpoint. AGENTS/TASKS/project direction now record the replacement
and behavioral gate. The installed pair and current Java code were not changed by this decision.
StockReviewPanel/adoption remain in current source until their tested replacement is implemented;
row-level incomplete trade evidence and player-started sell pricing are not yet wired end to end.
No new runtime test, installation, game input, account-file change or database operation occurred.
Pause the routine classification retest and the standalone PR #38 update instruction while
preparing the replacement workflow; PR #38's observation safeguards remain required in that build.

### PR #38 merged; paired update still pending — 14 October 2026, 01:41 AKDT

Current rebuild source: `9a4c1ec8f8f2a05d34072c0f5e6cf6cbd34434ab` (PR #38).
Both [final-head CI checks at `84159c6a421fcb93911efaba0448374cccd0715b`](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38543697812)
passed before source merge at 09:41:15 UTC. Exact final output:

```text
Supported build (ubuntu-latest)   pass   1m21s
Supported build (windows-latest)  pass   2m5s
FINAL_CI_EXIT=0
```

The primary rebuild branch was fast-forwarded to that merge and observed clean/equal to origin.
Historical main remains `0cb8ef8aab9114f02b16d2eca4433c0f7e010894`; earlier source/documentation
branches were preserved. This checkpoint is on `codex/offer-observation-live-verification`.
The standard local clean build and all Python tests passed as recorded below; this checkpoint
changes documentation only. No live account files, databases, tasks or installed JARs changed.

Read-only `tools/status.ps1` still exited zero with `PAIR VERIFIED / COMPANION RESPONDING`.
Installed plugin remains 407,086 bytes; installed companion remains 17,737,530 bytes (PR #37).
The new source pair is not installed. The player has closed RuneLite, confirmed by the corrected
client-main-class process check. Next player step: run the supported `4 - Update.bat`, stop at
INSTALL, and provide the fresh backup listing for path/size/hash/task review before confirmation.
Never install a single artifact or manually clear live records.

After that update, inspect the fresh scoped ledger before any review choice or GE action.
The prevention fix does not clear existing durable faults, so new buys may remain unavailable.
Positive authoritative reconciliation is still required for cost-bearing/offer conflicts; unpriced
personal/retired declarations cannot conceal them. **Post-PR #38 installation pipeline: not run.**
Controlled known-cost holding/no-open-buy, partial fill, cancel/collect and full sale/restart checks
remain pending. Do not mark the overall acceptance complete; its completed record still requires
the owner's merge approval.

### Offer observation boundary — 14 October 2026 (source verified; not installed)

Base: fetched rebuild `49934e44590e0a484e0ddbd4857179102b03bdc2`; branch
`codex/offer-observation-boundary`. Regression commit `b8c679f` preceded implementation
`f58a0cf`. This branch also includes the installation and blocked-pipeline documentation
from the preserved `codex/stock-review-live-verification` branch.
Source review: [PR #38](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/38), targeting
the rebuild branch. Both final-head supported-build CI checks must pass before source merge;
this is a prevention fix, not the completed PR #31 trading acceptance record.

After the panel visibility checkpoint below, scoped health GET returned HTTP 200 / DEGRADED /
`planningStateAvailable=false`, with five offer-slot lifecycle faults. HTTP does not expose past
offer observations. Agent inspected only the already identified scoped account database using
SQLite URI `mode=ro`, connection `query_only=ON`, and an authorizer denying SQL writes. No schema,
record, file location, WAL or legacy database was edited, moved, migrated or repaired. Private
evidence: `local-data/diagnostics/stock-review-live-20261014/read-only-offer-history.json`.

The 1,606 saved observations included batches of EMPTY followed about one second later by the
same five offer identities and fill counters. Some occurred before PR #37 was installed; this
does not establish that PR #37 caused the faults. The history also contained a SELLING observation
with filled quantity equal to its total, followed by SOLD. Historical events lack authenticated
game-state evidence, so the exact cause of every live fault cannot be proved from those rows alone.

Automated synthetic tests reproduced five source boundary defects before implementation: a login
screen EMPTY was processed as collection, a queued mutable record advanced before publication,
a buffered live API object changed its counters, the per-slot buffer dropped a terminal event
when EMPTY followed, and queued observations survived logout. Exact initial expanded ending:
`5 tests completed, 5 failed`, `BUILD FAILED in 8s`, `REGRESSION_EXIT=1`.
Log `.gradle/offer-observation-regression-expanded.log`. The first two cases had already failed
independently in `.gradle/offer-observation-regression.log`. An intermediate fixture compile failure
used the wrong GameStateChanged constructor; the corrected fixture produced the five failures above.

Implemented boundaries:

- Only LOGGED_IN offer events reach local accounting/publication. Non-authenticated resets are
  ignored; after account loading, a fenced client-thread read of the actual current slot array
  recovers offline completions. A missed terminal before an authenticated EMPTY remains unresolved.
- Raw API observations are copied immediately into immutable `OfferObservation` values. The load
  buffer preserves arrival order, including terminal then EMPTY in the same slot, and publishes
  each drained transition rather than only changing the local tracker.
- `TrackedOffer.copyObservation()` captures coherent state/quantity/spent/identity fields before
  either direct publication or the account refresh enters the worker. Login/logout/hop/disconnect/
  shutdown generations withdraw queued work from an earlier session. Observations not yet persisted
  before that boundary are not invented later; this does not establish crash-safe delivery before
  outbox enqueue or complete the broader concurrent account-snapshot migration.
- Publication retains the captured account ID. CompanionClient refuses mismatches before enqueue;
  the scoped payload and existing outbox write checks also refuse a profile change during event
  construction. Existing durable event/session/sequence identities, acknowledgements, duplicate
  behavior, accounting transactions, tax rules and fail-closed reducer remain unchanged.

Verification: 1,198 Java tests passed / six capture-dependent skips / zero failures/errors (1,204
total). All 48 Python tests passed (`Ran 48 tests in 41.964s`, `OK`, `PYTHON_EXIT=0`). The initial
full run caught a missing-account error-message regression; the original assertions were preserved
and the source message restored. A targeted command also propagated plugin-only filters into the
companion task and failed with no matching tests; the corrected `:test` run passed all 14 focused
checks. Logs are gitignored under `.gradle/offer-observation-*.log`, outside build outputs.

The player closed RuneLite. An initial overly broad process filter matched `.runelite` in the
companion JAR path; it aborted before clean. Read-only classification identified the installed
companion, and the corrected client-main-class check found zero RuneLite clients. The actual
standard command `.\gradlew.bat clean test jar :companion:shadowJar --console=plain` then ended:

```text
OBSERVABLE_RUNELITE_CLIENT_COUNT=0
BUILD SUCCESSFUL in 34s
18 actionable tasks: 18 executed
GRADLE_EXIT=0
```

Clean log: `.gradle/offer-observation-clean-build.log`; Python log:
`.gradle/offer-observation-python.log`. Both supported source JARs built, targeting Java 11.
Use the PR's final-head Windows/Linux checks for remote outcome.

Both [Windows/Linux CI checks at `6ce171ec55fca79587b66d1e9331cd577d6da8fe`](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38542984377)
passed on 14 October. Exact check output:

```text
Supported build (ubuntu-latest)   pass   1m11s
Supported build (windows-latest)  pass   3m19s
CI_EXIT=0
```

This subsequent evidence-only documentation commit changes no production code; require both
checks on the resulting final PR head before source merge as well. The full live acceptance record
has not been completed or approved for merge.

**Not installed.** Runtime remains PR #37 at `49934e44`; new buy advice remains blocked. Source
prevention does not repair existing projections, replay historical events into the live database,
establish historical profit, or authorize hiding cost-bearing conflicts. Review choices have not
been observed as saved. Continue read-only evidence and the authoritative reconciliation workflow
under the owner's database rule; preserve physical holdings, active offers and unresolved history.
Controlled manual partial-fill/cancel/collect/full-sale/restart acceptance remains incomplete.

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
