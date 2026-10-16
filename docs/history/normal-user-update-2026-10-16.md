# Normal-user update delivery — 16 October 2026

Installed source: [#77](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/77), merge
`3dcd8313255235dcca13e3d56016386876811710`, from exact tested head
`fdcaa94c17cabd8f74643c4a8d796f924e901434`. It batches the already merged #72 episode guard and
#75 risk explanations with the startup/process lifecycle. This receipt changes documentation only.

## Source and CI

Standard Windows clean verification: BUILD SUCCESSFUL in 59s, 18 executed, exit 0; 1,534 Java
passed/six existing skips, zero failures/errors (647 plugin/181 core/712 companion). Tooling:
103 tests / 99.585s / OK / exit 0. PowerShell 7 process gate: ten / 25.351s / OK. Native sharing,
two isolated child JVMs, actual-main blocked startup and BOM/partial-pair checks pass.

Both exact-head checks pass in [run 38955937701](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38955937701):
Windows job 117160940451 / Ubuntu 117160942779. The first Windows attempt (117158685945) timed
out in the unchanged AccountApiIsolationTest.unidentifiedClearReturnsReadableScopedEvidenceInsteadOfHttp500
at its two-second HTTP read. That focused class passes locally. The failed Windows job was rerun
through the GitHub connector with no source or timeout change; the retry passes. This is not a
runtime latency or trading acceptance claim. CI reads used the dedicated checks tool.

#76 had already merged as 51b69e4 after e158825 passed both checks in run 38953016899. Its optional
ACL repair was never applied; the owner rejected even an initial admin prompt and #77 removes
that dependency from routine updates. Historical main stays 0cb8ef8; ee0d3af remains an ancestor.

## Real installation

The primary rebuild checkout was clean and fast-forwarded to the #77 merge before installation.
RuneLite was confirmed closed. Current process elevation=false, task maintenance access=false,
task enabled=true. The only current installed-JAR JVM was confirmed to belong to this user.
The supported wrapper ran normally as:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\tools\apply-update.ps1 -Unattended -UserProcess
```

Observed output:

```text
RuneLite release preflight passed: 1.13.2 matches the source API.
BUILD SUCCESSFUL in 8s
8 actionable tasks: 7 executed, 1 up-to-date
Backup verified: companion account files=3; plugin account files=22; JAR/manifest hashes and task export match.
Backup verified: ...\backups\20261016-114457-2a290e0e
Protected startup task and permissions unchanged; companion maintenance uses this user process.
Companion ready (accounts-v1).
Update applied. Both installed JARs verified. Normal-user process lifecycle enabled.
```

The native updater exited after this completion; its outer log-capture host remained and was
cleaned up separately. Its native exit code was not retained; do not invent one. Independent native
verification exits below are observed. A first measurement command used a helper outside its scope;
the read was corrected with WindowsPrincipal directly. Neither issue changed runtime/account data.

Manifest installedAtUtc=2026-10-16T19:45:00.8484624Z, processLifecycleVersion=1. Installed artifacts:

| Component | Bytes | Project classes | SHA-256 |
| --- | ---: | ---: | --- |
| Plugin | 487,403 | 218 | 08aea899e918537ce44f10266a45e5acd3b376a6c18f75dcc80180c51288dbeb |
| Companion | 17,900,160 | 461 | 07c43f335a0de96e9429cd0c0b5671e3d898e460b2d074b07c9a33149ea4d24c |

Both hashes match the manifest; every project class is Java 11/major 55. These are actual primary
installed bytes, distinct from the earlier worktree JAR archive bytes. Backup root is
`%USERPROFILE%\.runelite\osrs-flipping-friend\backups\20261016-114457-2a290e0e`:

| Metadata/artifact | Bytes |
| --- | ---: |
| backup-verification.json | 335 |
| companion-task.sddl | 164 |
| companion-task.xml | 1,895 |
| original companion JAR | 17,878,455 |
| original installation.json | 458 |
| original plugin JAR | 486,494 |

Receipt status=VERIFIED, three companion account files and 22 plugin account files. Only relative
names/sizes were checked for account data; their contents were not opened/hashed. The unassigned
legacy database was not assigned, edited, moved or repaired. No financial source replay or risk
reset occurred. New app startup is ordinary operation, not an agent database repair.

Post-update and post-launch comparisons both report XML unchanged=true, SDDL unchanged=true,
elevated=false; task maintenance readiness remains false. The update succeeded without acquiring
management rights. The enabled protected startup task remains in place; no new task was created.

## Transport and launch

Each of these was run directly as a native PowerShell command and returned **0**:

- tools/status.ps1: PAIR VERIFIED / COMPANION RESPONDING; normal-user process lifecycle.
- tools/run-dev.ps1 -CheckOnly: RuneLite 1.13.2/API/cache match; no launch or changes by that check.
- tools/apply-update.ps1 -StartOnly: Companion already ready; no task write, build or reinstall.
- tools/pipeline-check.ps1 -SkipBuild with the explicit existing owner account.

Pipeline output, with the private opaque account ID replaced by `requested account`:

```text
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [requested account] Ledger: 11 sale updates; 8 unresolved; 0 profit ranges.
PASS [requested account] Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
```

The supported Start script was then launched with a hidden console; one RuneLite process is
observed. Launcher output confirms compatibility and developer-mode startup. Stderr has the
RuneLite ReflectUtil reflective-access warning; no plugin failure is shown by that output.
Login/UI/trading were not observed and no game input was sent. Fresh coins/eight-slot evidence,
controlled trades and the existing affected-stock issue #70 remain open. This delivery does not
clear old gaps, produce sell eligibility or establish superior GP/hour or learned-model promotion.

Private primary receipts: .gradle/process-task-before.json, process-task-after.json,
process-update-live-install.log, process-update-status.log, process-update-compatibility.log,
process-update-start-only.log, process-update-pipeline.log and process-update-launch.*.
Reusable source verification is in .gradle/process-update-*, process-lifecycle-* and
process-ci-http-timeout-focused.log. No personal source rows or credentials are committed here.
