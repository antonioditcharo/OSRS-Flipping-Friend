# Development with fewer player interruptions

Owner direction, 14 October 2026: the agent manages routine diagnostics, backup verification,
paired updates, launching and GitHub delivery. Batch related fixes and user acceptance checks.
The earlier one-response-per-step and manual backup-confirmation routine is superseded for this
assisted workflow. Login, GE actions and all other in-game input remain the player's actions.
Agents must never directly edit or repair live databases or unassigned legacy data. The owner's
24 October accepted [source-proven amendment workflow](architecture/ordered-account-frame-recovery.md)
is a narrow application-level exception: explicitly requested, fully qualified sale amendments
append separate immutable proof while preserving every original source, financial and risk row.

Owner direction, 19 October: batch multiple related improvements and focused regressions before
the final full verification, paired restart and consolidated receipt. Do not turn every small fix
into a separate documentation/build/install cycle. Repeat checks for new changes or failures.

## Routine agent delivery

Start reuses a healthy existing companion. Transient health connection/server failures wait up to
60 seconds on the same port-owning PID; credentials, protocol/body failures or a changed/disappeared
listener fail closed. It never starts a duplicate or stops a listener to recover health. Status stays
read-only and reports its current probe without waiting. Script-only fixes require no JAR reinstall.

Global health has a separate dispatch path from bounded account work; scoped health still requires
the account service. Overload returns HTTP 503 without acknowledging an event, so original outbox
identities must survive retries. Routine refresh uses the neutral baseline without scanning historical
execution diagnostics. See [health isolation](architecture/health-request-isolation.md). A successful
global health check is process/market evidence, not account trading acceptance.

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

## Updates without administrator approval

An older task may run as this limited user but allow only read access to its management. Do not
request a one-time elevation merely to continue routine work. After tests and both CI checks pass,
the supported wrapper can enable normal-user process management from the primary checkout:

```powershell
.\tools\apply-update.ps1 -Unattended -UserProcess
```

Root `6 - Enable User Updates.bat` invokes the same transition normally. It validates exact task
scope and current-user process ownership, leaves task XML/permissions unchanged, and requires
tested startup/singleton/pair guards before first cutover. Original JARs/manifest are verified before
changes; old processes are drained again after atomic guarded-code replacement, before account
backup. Backup verification and runtime-artifact-only rollback preserve all account data.
Read [the lifecycle contract](architecture/user-process-updates.md) for ordering and failure behavior.

The verified manifest's processLifecycleVersion=1 makes later ordinary Update and Start use that
workflow automatically, without INSTALL replies or task management. Reconfiguring/removing the
protected task is separate from routine maintenance. Fresh registration still grants its creator
maintenance access; #76's optional explicit ACL repair is not needed for process updates. No new
task, elevated background updater, saved administrator password or UAC/security change is used.
Record the actual first normal-user update, unchanged task metadata and installed/transport proof
before claiming delivery. Every login and game action remains manual.

For historical trade-gap investigation, [audit a verified source capture](architecture/trade-evidence-audit.md)
with tools/audit-trades.ps1. It creates a private reproducible observation report without opening a
database or client. The report cannot authorize accounting changes or release trading guards.

## Avoiding unnecessary close/reopen cycles

The launcher's full developer API is cached under
`%USERPROFILE%\.runelite\osrs-flipping-friend\developer-api\`, outside source build outputs.
Gradle clean therefore does not need to remove a JAR held by the running client. Old copies under
build/runtime-extras are not moved or repaired. Version/platform guards and saved login are retained.

Build/test while the client is open, then group runtime changes into one final installation/restart.
The installer still requires RuneLite closed before replacing the pair. Owner authorization,
19 October 2026: the agent handles normal RuneLite window closure for routine paired maintenance,
using the application's ordinary close controls. Verify that its process exits before updating.
Never force-kill the game or inject in-game input. Login and GE actions remain manual.
Some UI acceptance genuinely needs a new client load;
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
