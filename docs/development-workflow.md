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
