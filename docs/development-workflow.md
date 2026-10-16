# Development with fewer player interruptions

Owner direction, 14 October 2026: the agent manages routine diagnostics, backup verification,
paired updates, launching and GitHub delivery. Batch related fixes and user acceptance checks.
The earlier one-response-per-step and manual backup-confirmation routine is superseded for this
assisted workflow. Login, GE actions and all other in-game input remain the player's actions.
Live databases and unassigned legacy data must never be edited, migrated or repaired.

## Routine agent delivery

1. Diagnose from logs, authenticated GETs and read-only evidence. Reproduce real bugs with tests.
2. Batch source fixes on a codex/ branch. Run appropriate tests and the standard verification once
   per candidate; repeat only for a new change, failure or unresolved concern. Capture concise results.
3. Require both exact-head Windows/Linux CI checks before merge, then clean fast-forward the normal
   rebuild checkout. Historical main stays untouched. Document outcomes and unresolved live checks.
4. If Java/plugin behavior changed, use the supported paired updater from the normal checkout:

   ```powershell
   .\tools\apply-update.ps1 -Unattended
   ```

   Use `-SkipBuild` only when both artifacts have already been built and verified for that candidate.
   Unattended mode requires a verified existing installation and matching existing task; it does not
   reconfigure setup, accept a broken pair or bypass the running-client/release/version guards.
5. The wrapper creates a fresh dated backup, checks JAR/manifest hashes and the saved task export,
   and compares account file names/sizes without opening or hashing their contents. It writes
   `backup-verification.json`, then installs both JARs. Failure prevents installation; never clear
   account state to get past it. No separate player confirmation or pasted backup listing is needed.
6. Run status, launcher CheckOnly and the GET-only pipeline directly. Fetch logs directly as well;
   do not ask the player to copy terminal output the agent can read. Launch through tools/start.ps1
   when needed, using a hidden console with output captured locally. The player logs in themselves.

Tooling/documentation-only changes do not require another JAR replacement when runtime source is
unchanged and the installed pair remains valid. Summarize the result and ask for user action only
when it requires login, a game action or an otherwise unobservable UI result. Keep personal output
in ignored local-data. Do not claim live UI/trading acceptance from offline or transport checks.

## One-time task permissions, then normal-user updates

An older companion task can run as the correct limited user but be owned by Administrators with
only read access for that user. It then requires elevation to pause/restart during every update.
Run `6 - Enable User Updates.bat` as administrator once, using the same Windows account. The agent
can prepare/check this without elevation; approval of an existing protected task repair remains a
Windows boundary. Do not use old ad hoc helpers that unconditionally demand administrator rights.

`tools/enable-user-updates.ps1` validates the exact Java action, runtime JAR, checkout and current
principal. Both native task properties and XML must establish an interactive, non-elevated task;
Windows may omit the default XML RunLevel, so absence alone is not accepted as proof. It backs up
the task XML and owner/group/DACL, verifies their bytes/hashes, and adds control for that one user
on that one task. Original rules, owner/group, action, triggers and enabled state are preserved.
Denied/conditional rules and foreign/elevated tasks require inspection instead of a forced repair.
A changed task aborts before writing; a failed postcondition restores the original DACL.

Fresh registration performs the same grant before the paired installer copies/enables the new
pair. Routine updates check access before creating a backup or stopping anything. Once enabled,
run `tools/apply-update.ps1 -Unattended` directly in a normal shell. No elevated background updater,
saved administrator password, global task-folder permission or UAC/security-setting change is used.
The metadata repair itself does not install JARs, stop processes or open account/database files.

This uses Microsoft's supported [task-specific permissions](https://learn.microsoft.com/en-us/windows/win32/taskschd/security-contexts-for-running-tasks)
and [SetSecurityDescriptor API](https://learn.microsoft.com/en-us/windows/win32/taskschd/registeredtask-setsecuritydescriptor).
Source tests and a read-only access check do not establish that a real repair/update has completed;
record the owner-approved repair and a subsequent normal-user paired update before claiming that.

For historical trade-gap investigation, [audit a verified source capture](architecture/trade-evidence-audit.md)
with tools/audit-trades.ps1. It creates a private reproducible observation report without opening a
database or client. The report cannot authorize accounting changes or release trading guards.

## Avoiding unnecessary close/reopen cycles

The launcher's full developer API is cached under
`%USERPROFILE%\.runelite\osrs-flipping-friend\developer-api\`, outside source build outputs.
Gradle clean therefore does not need to remove a JAR held by the running client. Old copies under
build/runtime-extras are not moved or repaired. Version/platform guards and saved login are retained.

Build/test while the client is open, then group runtime changes into one final installation/restart.
The installer still requires RuneLite closed before replacing the pair. Never force-kill the game
or inject input to make an update convenient. Some UI acceptance genuinely needs a new client load;
batch those checks rather than restarting after each test. The agent can start the client directly;
login and all offers remain manual. The numbered wrappers retain their interactive self-service path.

## Retrying an interrupted build

The builder retains native compiler diagnostics in redirected logs and judges Gradle by its exit
code. Windows PowerShell 5.1 otherwise treats a harmless javac deprecation notice as a terminating
error under Stop. Nonzero Gradle exits, a missing wrapper and a missing output JAR still fail.

An interruption after backup leaves the companion task disabled. Ordinary update/start/status
continue to reject a disabled task. If the agent has identified the exact verified pre-copy backup,
the supported updater can explicitly resume it:

```powershell
.\tools\apply-update.ps1 -Unattended -ResumeBackup '<absolute dated backup directory>'
```

Recovery requires that backup to be directly under this installation's backups directory, with a
VERIFIED receipt for this checkout. Installed JARs and manifest must still match both the manifest
and saved copies. The exported task must match the original enabled task except for Enabled;
account relative names/sizes must match the saved metadata. Links, missing proof, foreign/changed
tasks, changed account metadata and partially replaced pairs are refused. Original backup contents
are never rewritten. Checks repeat under the installation lock; release and closed-client guards
still apply. A fresh backup is verified, both JARs are installed together, then the task is enabled
and the companion health checked. Do not manually enable a task or use this option as re-setup.
