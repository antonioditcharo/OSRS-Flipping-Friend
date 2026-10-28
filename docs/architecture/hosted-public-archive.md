# Hosted public capture with isolated resource limits

The approved optimization pass retains installed #190 for live guidance and #186 for the economic
benchmark. Offline qualification #191 is merged separately. This change collects public inputs;
it grants no pricing, loss, accounting or learning authority and requires no local client reinstall.

## Isolation and lifetime

`HostedPilotMain` optionally enables `publicArchive` in its existing private configuration. The
default is disabled. One minimum-priority worker and one queued tick perform capture, compression,
hashing and history requests outside the market-refresh, allocation, replication and account paths.
There are no additional listeners, device privileges, account inputs, servers or paid resources.
The worker sees only an allowlisted public market state. A stalled request never blocks guidance.

The worker reuses the existing bulk feeds every 60 seconds. It freezes up to 32 published-limit
items using initial hourly public turnover, with item-ID tie breaking, then attempts at most two
history requests per minute with separate seven-second call deadlines. Each history attempt has
its actual request/availability clocks and an explicit captured/failed result. Unknown hourly
history stays missing. New seed data cannot be backdated into earlier observations. Capture starts
after seeding; later aggregate revisions retain their actual observation times.

An immutable start/stop window bounds capture to 954,000 seconds by default; restarting does not
extend it. Storage is capped at 2 GiB with a separate 256-MiB disk reserve and 16-MiB component
limit. Only one cross-process writer owns a root. Malformed data, broken sealed provenance, disk
pressure or incompatible configuration pauses this archive alone. Nothing deletes old evidence
or consumes the foreground guidance budget to recover it. Disable the flag to roll back this
feature while leaving the archive intact. Existing financial backup scopes remain unchanged.

The standalone `tools/hosting/configure_public_archive.py` runs on the Linux pilot through its
existing encrypted connection. It changes only archive settings, retaining other authorization,
custom bounds and unknown options. Exact-byte backups and atomic replacement preserve the original
UID/GID/mode; concurrent edits or metadata-copy failures refuse replacement. Repeating the same
switch is a no-op. Restart the hosted service normally to apply a changed flag. This does not
restart RuneLite or replace the local companion.

## Format and recovery

Format 3 stores shared SHA-256-addressed gzip blobs and small immutable occurrence records.
Five-minute segments seal a record hash chain, occurrence count, first/last availability and the
previous seal's hash. Files are forced and atomically published. Repeated equal payloads reuse
blobs but remain separate timestamped occurrences. Failures stay explicit. No quote, missing
observation or exact execution is invented from a bucket average.

A crash leaves its open tail intact. Restart validates the sealed prefix and begins a new segment;
it never marks an interrupted tail complete or appends through it. The reader reports incomplete
directories and binds a selected immutable descriptor containing every included seal and seed
hash. Missing/duplicate segments, malformed clocks, changed records/blobs and late seeds refuse
preparation. Gaps and censored targets remain visible to qualification. Exporting a sealed prefix
while capture continues does not silently include newer data.

`prepare_pricing_archive.py` reads both completed format-2 captures and sealed format-3 prefixes.
The original format-1 tools, old frozen evaluators and incomplete 39/163-record captures remain
unchanged. Compact replay retains separate trade, snapshot, aggregate and availability clocks.
Seed prices, volumes and completed bucket times are checked before numeric conversion.
Raw public prices/volumes retain exact 64-bit values, including quotes above the current trading
model's integer range. Those items are excluded using initial information only and reported as
unsupported. A selected item crossing model bounds later refuses that cohort; it is never silently
removed retrospectively or narrowed into a false price. Original public blobs remain intact.

## Release evidence and remaining gates

Behavioral regressions cover interrupted tails/restart, conflicting writers, changed seals/rows,
clock regression, byte/disk bounds, failed/missing seeds, malformed numbers, bounded queues and
disabled archive isolation. A Java-writer/Python-reader interoperability fixture is explicitly
synthetic and cannot establish public forecast quality. Repeat the 1,000-connected/100-active
capacity experiment with capture enabled before production activation; retain the exact artifact,
measurement duration, public payload size and exclusions from local ACK/rendering latency.

Production activation and sustained capture are separate from code verification. The existing
60-minute mixed synthetic workflow receipt does not establish native rendering or player-operated
sale revision/cancel/collect/relist/restart. The chronological fit/validation/once-only test,
cadence-specific stressed account gains, distinct budgeted market-loss evidence and learning
promotion remain independent gates. Useful baseline suggestions continue throughout.

Local verification passes: clean 2,519 Java tests (six existing skips), 138 tooling tests and all
own bytecode at version 55. The exact packaged production artifact runs the writer on Java 11;
Python reads its five sealed segments across 1,740 seconds and explicitly reports the open tail.
This fixture is synthetic, saved separately and excluded from pricing qualification. Local
trading, financial and hosted evidence-store classes match installed #190. Exact-head CI and
Enabled hosted capacity and production activation pass in #192; sustained production coverage and
the first real seal remain open. See the [delivery receipt](../history/hosted-public-archive-install-2026-10-27.md).
