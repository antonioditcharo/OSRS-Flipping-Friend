# Development storage

The installed service and player state live under `%USERPROFILE%/.runelite/osrs-flipping-friend/`.
Source worktrees are separate. Primary `local-data/` holds private frozen captures, release artifacts,
restoration checks, profiling and historical failures; these are evidence, not application dependencies.
Never commit their personal contents.

## Automatic management

Normal `tools/start.ps1` starts one hidden, non-elevated maintenance worker for this checkout when
Python is available. It checks hourly, loads the current checked-out tool on each pass, and never
starts/stops the game or companion or changes a scheduled task. `tools/status.ps1` reads its last
report and flags errors, refused cleanup, budget warnings or reports older than two hours.

Companion stdout/stderr and the supported developer launcher's duplicate client console output
each retain an 8 MiB active file and four 8 MiB archives: **120 MiB total across three streams**.
Files live under `%USERPROFILE%/.runelite/osrs-flipping-friend/diagnostics/runtime-logs/`.
Rotation handles oversized individual writes, restart and concurrent companion logging. Only exact
owned log names with the diagnostic header can be replaced; unknown files, links and directories
are refused. After a write/rotation failure, a single warning is emitted and further diagnostics
are discarded until restart; logging failure does not stop financial delivery or the client.
RuneLite's own rotating client logs are separate. Old launch/companion logs and frozen investigations
are preserved: the updater now redirects only pre-logger output to `companion-bootstrap.log`/`.err`,
leaving the former `companion.log`/`.err` files intact. Bootstrap output is replaced per launch;
normal service diagnostics use the bounded files. Capture investigations before their rolling window expires.

`tools/verify_pilot_backup.py` now budgets and registers **new** disposable restoration copies.
After 24 hours, cleanup rechecks every copy/source hash against the still-existing VERIFIED backup
and a permanent receipt outside the scratch directory. It removes only the exact registered copy.
Changed/missing sources or receipts, extra/changed files, overlaps and links retain the copy and
produce an attention report. An added `KEEP` file therefore pins an investigation. Interrupted
cleanup resumes only the previously verified remaining files. Full updater backups never expire.
Only the explicitly scoped SQLite `flipping-friend.db-shm` index in a disposable copy is treated as
derived cache: read-only integrity checks can change its read marks. Database and WAL bytes must
still match exactly; neither is ever treated as cache. See SQLite's [WAL file roles](https://sqlite.org/walformat.html#the_wal_index_or_shm_file).
Use this helper for routine restoration checks rather than retaining ad-hoc duplicate database copies.

Optional managed restores are limited to **5 GiB of scratch**. Optional restore/soak admission also
requires **10 GiB free** and a projected **50 GiB allocated local-data budget**. The workflow soak
checks a **1 GiB raw capture budget** at 30-second samples; crossing it stops only its own synthetic helpers,
records failure and retains the raw evidence. Completed/failed soaks are sealed for lossless
compression, never deletion. Capture bytes may overshoot between budget samples. Completed captures
and manual frozen investigations can also be sealed explicitly. After 24 hours,
maintenance compresses only unchanged registered evidence, at most 2 GiB/eight files per pass,
with SHA-256 before/after. Existing unregistered folders are protected, regardless of names/age.

Required evidence, live financial databases and updater backups may grow legitimately. Budgets
raise alerts and refuse optional work; they never silently remove that required data to meet a cap.
Unmanaged/ad-hoc writers are measured but cannot be forcibly capped by this tool. Use the managed
entry points below for new verification/capture work. Registry/status are private in `local-data/.storage/`.

```powershell
# Report; --apply performs eligible registered maintenance once.
python tools/storage_maintenance.py --workspace "$PWD"
python tools/storage_maintenance.py --workspace "$PWD" --apply
# Verify a preserved backup into a NEW scratch destination; writes a permanent external receipt.
python tools/verify_pilot_backup.py '<verified-backup-directory>' 'local-data/check-YYYYMMDD/restored'
# After all writers close, explicitly seal a completed investigation directory (compression only).
python tools/storage_maintenance.py --workspace "$PWD" --seal 'local-data/completed-investigation'
```

## Historical cleanup

Use a size inventory first. Old worktree `build/`, `core/build/`, `companion/build/` and generated
Python bytecode can be regenerated; remove only checked absolute paths within their checkout,
while no build is using them. Keep source, uncommitted changes and `.gradle/reference` inputs.
Do not remove worktrees just because their branches are old.

Large offline evidence can use Windows NTFS compression in place (`compact /C`). This preserves
all existing paths and logical bytes. Record per-file SHA-256 before/after and allocated bytes;
stop if contents differ. Limit this operation to explicit, non-linked historical `local-data/`
files, excluding current outputs and any active capture. Compression is reversible (`compact /U`);
no decompression step is required for normal reads. Explorer's logical Size may stay large: compare
Size on disk or measured allocated bytes. Keep the private receipt/index under `local-data/`.

Live databases, account outboxes, credentials, market caches, installed JARs and updater backups
are outside automatic deletion. Do not deduplicate databases with hard links, vacuum them, replay financial
history, or discard unique frozen baselines/failure evidence. Retention of updater backups requires
a separate reviewed policy; this maintenance does not prune them.

11 November 2026 maintenance receipts: primary `local-data/runelite-1-13-6-20261111`.
The historical evidence remains at its original paths. This pass compressed 703 files with matching
SHA-256 before/after, reclaiming 26,887,397,867 bytes (25.04 GiB). Automatic approval review rejected
the broad stale-build deletion command with "blocked by policy"; no stale worktree build output was deleted.
