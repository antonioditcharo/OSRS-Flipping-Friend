# Development storage

The installed service and player state live under `%USERPROFILE%/.runelite/osrs-flipping-friend/`.
Source worktrees are separate. Primary `local-data/` holds private frozen captures, release artifacts,
restoration checks, profiling and historical failures; these are evidence, not application dependencies.
Never commit their personal contents.

## Routine cleanup

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
are outside this cleanup. Do not deduplicate databases with hard links, vacuum them, replay financial
history, or discard unique frozen baselines/failure evidence. Retention of updater backups requires
a separate reviewed policy; this maintenance does not prune them.

11 November 2026 maintenance receipts: primary `local-data/runelite-1-13-6-20261111`.
The historical evidence remains at its original paths. This pass compressed 703 files with matching
SHA-256 before/after, reclaiming 26,887,397,867 bytes (25.04 GiB). Automatic approval review rejected
the broad stale-build deletion command with "blocked by policy"; no stale worktree build output was deleted.
