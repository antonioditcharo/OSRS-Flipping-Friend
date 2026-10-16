# Normal-user process updates

The owner explicitly requests unattended maintenance without even an initial administrator prompt.
The existing task already runs as this Windows user, Limited/Interactive; only its management ACL is
protected. Routine maintenance therefore leaves that task and its security descriptor unchanged.
No new task, elevated helper, stored password or UAC/security-setting change is required.

First transition from the clean primary checkout, after source tests and both CI checks pass:

```powershell
.\tools\apply-update.ps1 -Unattended -UserProcess
```

The numbered `6 - Enable User Updates.bat` calls the same wrapper normally. Source/API, closed-client,
enabled exact-task/action/checkout/principal and installed-pair checks remain mandatory. The candidate
companion must declare `FF-Lifecycle-Guard: 1` and contain its startup guard. Future updates and Start
recognize `processLifecycleVersion: 1` in the verified installation manifest and launch directly.
Ordinary Update in this mode also uses verified unattended backups. The optional #76 task-ACL repair
is unnecessary for this workflow; it remains available for separately authorized task maintenance.

## First cutover and failure behavior

1. Validate the task's native/XML limited interactive principal and current user. Identify companion
   JVMs only by the exact installed JAR argument and verified process owner. An unknown port listener,
   foreign owner/task, missing guard or running RuneLite aborts without stopping anything.
2. Build before stopping if necessary. Hold the installation lock. Back up and hash-verify the original
   plugin, companion and manifest; copy task XML/SDDL read-only. Never stop/disable/re-register the task.
3. Stop only those owned companion JVMs. Atomically install the guarded companion, then drain owned
   JVMs again. This second drain catches old code launched by the scheduler before replacement.
   No account backup starts until those processes and the listener are gone. New scheduled starts
   encounter the installation gate before credential/store reads or housekeeping.
4. Copy account trees using the existing backup routine. Verify JAR/manifest hashes against the frozen
   originals, task export, and account relative names/sizes. Account/database contents are not opened
   or hashed; the unassigned legacy database remains untouched and unassigned.
5. Atomically replace the plugin and write/verify the pair manifest. Recheck unchanged task XML/SDDL,
   release the install gate, then launch the normal-user companion and require scoped health.

A pre-commit failure restores only the original runtime JARs and manifest from verified copies.
Account files/databases are never restored, edited, migrated, replayed or repaired. Rollback failure
is reported with its backup path. New installed companion startup additionally checks both JAR hashes
against the manifest before opening credentials/stores, so a partial pair refuses startup. A post-commit
health failure preserves the verified new pair and reports failure; it does not roll back live data.

## Startup guards and verification

CompanionMain acquires the installation/startup barrier and a lifetime singleton file lock before
token handling, database initialization, retention or service work. Windows' exclusive updater file
handle and the Java native byte lock are tested together. Duplicate or unavailable-lock startup
returns without account work. Once the API starts, the startup gate is released; the singleton is
held until JVM shutdown. Start releases its own installation check lock before requesting startup.

Tests use disposable fake tasks/files/process boundaries, actual native Windows security parsing,
two isolated child JVMs and a disposable PowerShell exclusive handle. They cover first transition,
scheduler race drain, foreign owners/tasks/listeners, client guard, missing capability, rollback,
manifest-driven repeated updates/start and incomplete-pair refusal. None operates the real client,
real task or account databases. The new lifecycle tests specify a new capability, not a claim that
old runtime already provided it. The protected-task access failure was reproduced before #76.

Scope follows Microsoft's [task security model](https://learn.microsoft.com/en-us/windows/win32/taskschd/security-contexts-for-running-tasks).
Interop uses the [native Java locking contract](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/nio/channels/FileLock.html)
and [.NET file sharing](https://learn.microsoft.com/en-us/dotnet/api/system.io.fileshare).
Source checks do not establish delivery: record the first real normal-user update, unchanged task
metadata and installed hashes, startup/status/compatibility and GET-only pipeline results separately.
