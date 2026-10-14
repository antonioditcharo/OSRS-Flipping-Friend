# Start here

Use these files from the **current rebuild folder**. An old desktop shortcut can still point to a
previous checkout; re-setup replaces it after installing the current pair.

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

The companion runs from `...\osrs-flipping-friend\runtime\flipping-friend-companion.jar`, independently
of the source `build/` folders. A later development build cannot replace that installed JAR.
The task runs at logon and checks hourly while that Windows user is logged in. Re-setup uses an
interactive limited principal; it does not promise operation before logon or after logout.

`3 - Status` is read-only and validates the JAR pair, task checkout and scoped health. A responding
companion can still have a cold/degraded market cache; status is not proof of market freshness or
correct live account accounting. After logging in, run this from PowerShell in the project folder:

Start also checks RuneLite's official artifact list and the API version in the installed plugin.
An old or missing API declaration requires a paired update even when companion status passes.
It excludes older cached client/API JARs without deleting them. For a separate read-only compatibility
check before launching, run `tools/run-dev.ps1 -CheckOnly`. If RuneLite has updated beyond this
project's supported API version, a compatible source update is needed before launch.

```powershell
.\tools\pipeline-check.ps1 -SkipBuild
```

Record the output. It makes read-only requests and posts no synthetic trades. The in-game holding,
partial buy, cancel/collect, full sale and restart checks in [HANDOFF.md](HANDOFF.md) remain manual.
No live success or profit superiority is established by the automated build.

## Removing the installation

`5 - Uninstall` stops the companion, removes its task, both installed JARs, pair manifest and shortcut.
Account/legacy databases, backups and saved login are preserved. It refuses JAR removal if a remaining
listener cannot be stopped safely. Developer source builds also remain in the project folder.

## Project and preferences

See [README.md](README.md) for supported components and [TASKS.md](TASKS.md) for remaining work.
The live verification checkpoint is in [HANDOFF.md](HANDOFF.md). Settings cover membership, checking
cadence, spending/risk preferences and adoption of personal stock. Physical ownership remains visible
even for blocked items. Unknown cost and incomplete evidence must not become invented profit.
Explicit 2h/8h benchmark settings remain pending; do not substitute the 3h option.
