# Start here

Use these files from the **current rebuild folder**. An old desktop shortcut can still point to a
previous checkout; re-setup replaces it after installing the current pair.

For the compact sidebar and combined trade changes, start with
[HOME TEST CHECKLIST.md](HOME%20TEST%20CHECKLIST.md). PR #43/#44 are merged and the 1.13.2 pair
is installed. Login exposed a ledger-read fault and startup search exception; the current follow-up
requires CI/merge and a fresh paired update. See the newest [HANDOFF.md](HANDOFF.md) checkpoint.
The numbered Update wrapper does not fetch Git.

| What you need | Double-click |
| --- | --- |
| First installation | `1 - First Time Setup.bat` |
| Previously installed, moved checkout, disabled task, or old shortcut | `1b - Re-setup Existing Installation.bat` |
| Everyday launch | `2 - Start Flipping Friend.bat` or the refreshed desktop shortcut |
| Read-only diagnostics | `3 - Status.bat` |
| Update after changing/pulling source | `4 - Update.bat` |
| Remove installed components while retaining data | `5 - Uninstall.bat` |

## Setup or re-setup

1. Close every RuneLite window yourself. Setup never closes the game or performs any game action.
2. Run `1` for a fresh installation or `1b` for an existing installation. Both use the same installer.
   An existing saved login is reused without reading or displaying it. File presence does not prove
   that the login is still valid. If there is no saved login, follow the manual login instructions.
3. The installer stops the companion, pauses its existing task and creates a dated backup. It shows
   every backed-up file's path and size. Review this output **before typing `INSTALL`**.
4. After confirmation, it builds and installs **both** JARs, configures the companion task for this
   checkout, verifies the installed pair and scoped health, and refreshes the desktop shortcut.
5. Run `2 - Start`, log in yourself, and open the Flipping Friend coin panel. Use `3 - Status`, then
   the read-only pipeline check below, before the manual checks in [HANDOFF.md](HANDOFF.md).

Run as your usual Windows user. If Windows refuses access to the old scheduled task, preserve the
error output; running re-setup as administrator for that same user may be necessary. Do not use a
different Windows account to work around it.

An installed JDK is needed to build; CI uses JDK 21 and local verification uses the existing JDK 23.
The installer selects an available JDK from JAVA_HOME, Java's directory, or that existing JDK 23 path.
It does not download a JDK or change system environment settings.

## What is preserved

Backups are under `%USERPROFILE%\.runelite\osrs-flipping-friend\backups\<date-and-id>\`. They contain
the installed plugin, the prior companion JAR when found, the prior task configuration/manifest, and
companion scoped account directories under `accounts/` when present, including SQLite sidecar files.
The plugin's separate profile records from `osrs-flipping-friend/accounts/` are copied as
`plugin-accounts/`, including positions, offers, journals, pending events and parked history. These
copies retain their existing directory names; no ownership is inferred or data imported. A prior
desktop shortcut is also copied there before it is retargeted.

Missing backup sources are reported as missing. Absence of the expected account directory does not
prove data is absent elsewhere. The installer never assigns, imports, moves, repairs or deletes the
unassigned `companion/flipping-friend.db`; it does not copy that legacy file into a live account.
Saved RuneLite login credentials are retained. Account directories containing links are rejected.

If you decline, building fails, or a copy fails, the companion remains stopped and its task paused.
Do not trade on a partly applied update. Preserve the output, resolve the cause, then run re-setup
again. Backups remain available; never edit or restore databases to make the check pass.

## Everyday start and updates

`2 - Start` checks the installed pair, starts or reuses a healthy companion, then opens RuneLite.
It does **not** rebuild or replace JARs. After fetching source changes, close RuneLite and use `4 - Update`.
If the old task is disabled or belongs to another checkout, use `1b` instead of `4`.

Setup, re-setup and Update first verify that the source API pin matches RuneLite's official release.
The current tested pin is **1.13.2**. A mismatch, unavailable response or invalid release metadata
blocks installation before backup, companion stopping or JAR replacement. Preserve that output
and obtain a tested source update; do not edit the pin just to pass. Status and companion-only
start do not depend on this installation preflight. See the
[compatibility contract](docs/architecture/runelite-compatibility.md).

The companion runs from `...\osrs-flipping-friend\runtime\flipping-friend-companion.jar`, independently
of the source `build/` folders. A later development build cannot replace that installed JAR.
The task runs at logon and checks hourly while that Windows user is logged in. Re-setup uses an
interactive limited principal; it does not promise operation before logon or after logout.

`3 - Status` is read-only and validates the JAR pair, task checkout and scoped health. A responding
companion can still have a cold/degraded market cache; status is not proof of market freshness or
correct live account accounting.

Start also checks RuneLite's official artifact list and the API version in the installed plugin.
An old or missing API declaration requires a paired update even when companion status passes.
It excludes older cached client/API JARs without deleting them. For a separate read-only compatibility
check before launching, run `tools/run-dev.ps1 -CheckOnly`. If RuneLite has updated beyond this
project's supported API version, a compatible source update is needed before launch.

After logging in, run this from PowerShell in the project folder:

```powershell
.\tools\pipeline-check.ps1 -SkipBuild
```

Record the output. It makes read-only requests and posts no synthetic trades. The in-game holding,
partial buy, cancel/collect, full sale and restart checks in [HANDOFF.md](HANDOFF.md) remain manual.
No live success or profit superiority is established by the automated build.

## Inventory and trade records

Personal supplies and equipment do not require classification. **More > Holdings** contains
remaining managed purchases with acquisition evidence; merely owning an item or seeing it in old
history does not enroll every owned unit. The old Review holdings controls are removed.

To get a current price for another owned item, select it yourself in a GE SELL setup. Opening that
setup records no trade. If you place a sell, its observed offer enters maintenance; unknown
acquisition cost/profit stays unknown. **More > Missed flips** opens incomplete/uncertain recorded
evidence separately. It does not repair accounting or change ownership.

If entries remain blocked for reconciliation, preserve the exact message and pipeline output.
Some existing faults still require investigation; the UI update does not guarantee new buys.
Do not edit a database or classify personal belongings to bypass a real exposure fault.

## Removing the installation

`5 - Uninstall` stops the companion, removes its task, both installed JARs, pair manifest and shortcut.
Account/legacy databases, backups and saved login are preserved. It refuses JAR removal if a remaining
listener cannot be stopped safely. Developer source builds also remain in the project folder.

## Project and preferences

See [README.md](README.md) for supported components and [TASKS.md](TASKS.md) for remaining work.
The live verification checkpoint is in [HANDOFF.md](HANDOFF.md). Settings cover membership,
checking cadence and spending/risk preferences. The compact panel has actual 5m/30m/2h/8h presets;
older custom intervals remain available with their real minutes. Physical ownership remains
independent of exclusions. Unknown cost and incomplete evidence must not become invented profit.
