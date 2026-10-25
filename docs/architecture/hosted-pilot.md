# Hosted pilot: continuous guidance and background recovery

Owner-approved implementation, 24 October 2026. This contract supplements all existing financial,
source, risk, maintenance and manual-game-action contracts. Delivery states are independent in
`local-data/hosted-pilot/delivery-state.json`; do not infer installation from source or staging.

## Authority and availability

The private pilot keeps the existing local companion as financial authority. Hosted hints are
asynchronous, short-lived, scoped to an authenticated device/account/installation/generation and
exact current-frame/configuration/market context. A malformed, unavailable or unusable hint retains
the current safe local plan. Optional refresh cannot invalidate financial evidence or delay an ACK.
Source/cash/slot/limit/loss and explicit user blocks remain final local requirements. Healthy full
boards receive keep-working guidance; fairness cannot cancel an order. Independent switches govern
hosted advice, coordination and automatic recovery. Pilot starts in shadow mode.

Profit/duration/cadence are preferences; supported positive-net substitutes retain all financial
constraints. Checking cadence does not delay advice. Existing prospective targets/stops/deadlines
and exact taxed costs remain immutable. Display-only interval midpoints use overflow-safe rounded
half-away-from-zero arithmetic, with unknowns preserved and estimates labelled. Order prices and
wire/accounting bounds are unchanged.

## Evidence and preservation

Before local installation, use the supported paired updater's verified backup. Verify hashes and
disposable restore of both JARs, configuration, account databases, amendments, outboxes and plans.
Code rollback retains post-update ledger activity; restoring a historical database is a separate
disaster-recovery decision. No destructive schema migration or financial event replay is permitted.
Capture uses a lifetime account OS writer lease and unique atomic outbox temporary files.

RuneLite's supported account-scoped GE configuration and naturally opened history widget are
copied read-only, outside the foreground budget. Integer average prices describe money bounds;
observation timestamps are not exact fills. Preserve repeated rows with occurrence/lineage.
History matches are supporting evidence only. Background accounting currently uses the existing
source-qualified sale-cost amendment path: unique quantity/money/source proof, unchanged retained
prefix, immutable amendment and one effective ledger. Ambiguous or missing acquisitions/disposals
remain unknown. FIFO orders compatible acquisitions; it cannot prove ownership. No personal items
are enrolled and no historical learning samples are manufactured. New live frames always win.

Replication has its own durable hash-chain checkpoints. Remote ACKs cannot clear local financial
delivery. Original financial/source IDs remain inside authenticated envelopes. Hosted evidence is
an isolated replica, never an alternate ledger. Learning remains the public-price baseline until
the existing chronological promotion gates pass.

Legacy source rows with no account identity retain an explicit `UNASSIGNED_*` replica namespace.
The envelope identifies the originating scoped file/device, not ownership of those rows. Their
original payloads remain unchanged; they cannot enter account allocations, accounting or learning.
They neither acquire an inferred owner nor prevent later qualified evidence from replicating.

## Hosted operation and fair demand

One DigitalOcean 2-vCPU/4-GiB server, daily provider backups and private encrypted object backups.
Approved base quote $36.20/month before tax, ceiling $50; no automatic size substitution/upgrade.
Loopback HTTP is carried through strict pinned SSH; credentials are provisioned for explicit
account scopes. Account hashes are identifiers, never authentication. Provider tokens/private
backup keys stay local. Public onboarding/billing/Plugin Hub packaging are later requirements.

Shared bulk feeds screen the eligible catalogue; bounded deeper discovery rotates beyond the
fixed shortlist. Capacity estimates use both reachable buy/sell legs, working orders, known stock,
future buy exits and expiring displayed-advice leases. Stock moving into a sell listing is not
counted twice. Disconnected real demand persists conservatively. Pooled capture defaults to 55%;
fair rounds operate by person then accounts. This estimates opportunity, not a GE reservation.
Refresh spam or more accounts cannot increase a person's round priority.

New demand frames enter a bounded coalescing queue. Allocation rounds commit at most 128 latest
frames in one FULL SQLite transaction before making them eligible for advice. Failed batches remain
queued; older frames cannot replace pending newer frames. Identical refreshes do not force another
disk flush. This avoids a synchronized user burst performing one foreground fsync per request.

## Private operator workflow

`tools/configure-hosting.ps1` saves the provider token encrypted for the current Windows user;
`tools/hosted-pilot.ps1 -Mode quote|provision|status|deploy|spaces|backup|tunnel` operates the private
pilot. Provisioning retains a resource receipt before subsequent steps and refuses duplicate paid
servers. First-login password change uses `tools/complete-hosting-bootstrap.ps1`; all password input
goes directly to SSH. Keep `local-data/hosted-pilot/private` private and outside Git/support exports.
Strict SSH host checks stay enabled, including Windows PowerShell 5.1 paths containing spaces.

The client config is copied to the installed companion's `hosted-pilot.json` only at verified
installation, initially `shadow=true`, `recommendations=false`, `coordination=false`. Generation
advances durably on each companion start. Removing/invalidating this optional file retains local
operation. Recovery can independently be disabled using `automaticRecovery=false` there or in
`companion/features.properties`. Feature switches do not erase evidence or outboxes.

The optional tunnel uses an OS lease, pinned key and bounded reconnect backoff. Normal Start can
resume it when the operator control `tunnel.enabled` exists. Removing that control stops only the
SSH helper. The provider API token is never inherited by this long-running helper. A stopped tunnel
cannot prevent the local companion or game from starting.

Hosted backups use consistent SQLite snapshots, verified disposable restoration, gzip and age
encryption to a separate local private recipient key. Hourly bundles retain two days; daily bundles
retain 30 days. The backup worker refuses uploads above a 200-GiB allowance without expanding paid
storage. `verify-object-backup.py <new-local-data-directory>` downloads/decrypts and verifies a
disposable restoration; private backup keys never go to the server. Backup timer activation waits
for this verification. `verify_pilot_backup.py <verified-install-backup> <new-local-data-directory>`
verifies runtime/config/account copies; neither command overwrites a live database.

`/v1/accounts/<id>/hosted/current` and `/trade-recovery/background` are authenticated report reads.
They expose optional-service status away from the primary action panel. Capacity uses a separate
test-only main/JAR, synthetic credentials/accounts/database and loopback port; no simulated account
observations or trades may be sent to live local account services.

## Release gates

- Clean Windows Java 11-targeted build, tooling tests and exact-head Windows/Linux CI.
- Existing offer/partial/cancel/late-fill/collect/relist/completion/restart preservation; no churn.
- Writer conflict, account isolation, original source/effect/risk preservation and replica replay.
- Unique and ambiguous recovery, rounded history prices, identical rows and truncated coverage.
- Extreme preferences retain eligible substitutes; financial refusals retain their authority.
- Missing/slow/malformed hosted service immediately retains local suggestions.
- Disposable local restoration and decrypted off-server SQLite integrity/hash restoration.
- Synthetic 1,000 connected clients/100 actively planning accounts, measured on approved server.
- Shadow compatibility and comparisons before any hosted advice/coordination activation.
- Fresh visible local guidance, outage fallback, sustained soak and player-operated trade/restart.
- Four separate 100M/eight-slot 5m/30m/2h/8h return evaluations. No superiority/promotion claim from
  server capacity or price-touch simulations; chronological whole-account evidence remains needed.

Warm targets are p95 100ms retained-guidance reads and one second from durable frame ACK to the
next eligible action. Report end-to-end latency separately. No scan/report/upload may consume the
foreground budget. Public launch waits for demonstrated reliability/capacity and later client
delivery compliance, authentication, onboarding and subscription requirements.
