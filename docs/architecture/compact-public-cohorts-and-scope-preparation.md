# Compact public cohorts and incremental scope preparation

27 October 2026. Preserve installed #189 fallback and #186 economic benchmark. This pass changes
preparation and offline research storage; it cannot activate a new trading/loss/learning policy.

## Immutable scope snapshots

The original acquisition-context full reader remains the oracle. Prepared snapshots use a persistent
hash trie: an append/replacement/revocation copies its hash path, preserving earlier snapshots and
distinct colliding keys. Warm acquisition-context preparation no longer copies the entire result
map. A conflicting later prospective proof still revokes earlier associations. Scope memoization
observes revocations even when an unfilled order creates no new economic effect. Unknown original
context stays unknown; quantities, money, source IDs and original risk are unchanged.

Unchanged-frame recovery prepares its ordered before/rejected/baseline pairs from validated source
prefixes. Late changes/removals of reasons on consumed sources rebuild that preparation. Current
scope, independent gaps, unknown scope and unproven costs are filtered each time. Prefix replacement,
external edits, account changes and rollback reset it. This is the same unchanged-counter proof,
not new authority to invent executions, clear historical gaps or spend capital.

## Public capture segments

`tools/collect_pricing_archive.py` reads only the authenticated local public-market endpoint at
bounded cadence, with background process priority. It stores immutable content-addressed latest,
five-minute and hourly components, plus hash-chained occurrence records. Repeated identical data
may reuse a blob but never collapse captures. Failures retain their own records. Seed capture,
request, snapshot, availability and asynchronous trade times remain distinct.

Each run uses a new private segment. Interruption/budget exhaustion leaves `complete:false`; no
restart relabels the interrupted 39-snapshot v1 capture or another unfinished segment. Duration is
bounded to 31 days, cadence 60–900 seconds, records 50,000 and compressed public data at most 5 GiB,
with a disk reserve. The default 2 GiB limit is an experiment budget, not a capacity guarantee.

`tools/prepare_pricing_archive.py` requires a completed, intact chain and seed availability before
the first observation. Catalogue selection uses initial turnover only, never later returns. Gaps
stay gaps, revisions remain in the receipt and each completed flow bucket is simulated once.
Histories retain at most 720 buckets per item/resolution, in time-aligned immutable chunks of at
most 64 buckets. No bucket averages become invented latest quotes.

## Frozen replay and qualification

`CHUNKED_PUBLIC_REPLAY_V1` descriptors bind frame and history content hashes. Offline replay reads
frames lazily with one frame, at most 1,024 decoded 64-bucket chunks and 256 prepared histories
cached. Frozen candles reject mutation, so unchanged histories share their immutable preparation.
Expanded/compressed blob, item/history/frame and total
copy limits prevent unbounded materialization. Missing, linked, corrupt or mismatched blobs refuse
the experiment. Freeze retains independent copies; fold slicing retains past warmup without
serializing every history again. Legacy full-frame replay remains supported.

The unchanged 60/20/20 qualification gates, once-only final test, separate 5m/30m/2h/8h benchmark
and stress comparisons still apply. A 30-minute smoke capture can verify plumbing, not qualify a
cadence. With the current minimum final-fold coverage, the shortest qualifying elapsed cohorts are
five days for the shorter cadences and ten days for 8h, before considering executions, calibration,
stress or return checks. No promising curve or simulated result alone grants runtime promotion.

Require full-reader/frozen-account equivalence, old/new financial and original-risk reads, standard
clean Java/tooling checks, Java 11, exact-head Windows/Linux CI and paired preservation before a
runtime release. Public capture and offline evaluation do not substitute for the required local
mixed-workflow soak, cached render measurements or player-operated sale/restart acceptance.
