# Replay exit and attendance batch — 19 October 2026

The [v8 contract](../architecture/replay-exit-attendance.md) covers the offline evaluator changes.
Installed player runtime remains #116; there is no paired installation or client restart for this
batch. Source changes are limited to AccountReplay and its tests. Real account reads remain GET/
SQLite query-only; no synthetic source, financial repair, model promotion or game input is used.

The first five new regressions fail on v7 (21s): ready stock waits behind buy maintenance; an
item-ID-ordered winner beats a stop; the last exit slot is spent; a visit ignores new guidance;
and a stopped partial purchase remains open. The combined implementation passes those cases plus
mixed buy/sell maintenance order, missed/absent visits, restart during the partial stop and old
trade-price refusal without disposal. Existing exact costs, original context, continuations,
expiry, tax, canonical reconciliation and all four cadence cases pass.

Standard Windows clean verification: 129s, 1,768 Java passed/six existing skips, zero failures or
errors. Plugin 767, core 197, companion 810 including six skips. Main class major versions are
55 (Java 11). All 108 Python tooling tests pass in 155.260s.
The packaged evaluator runs on RuneLite's Java 11 and writes/reproduces the complete twelve-case
5m/30m/2h/8h x 0.1/0.5/1% bundle, retaining its evaluator JAR and version account-replay-8.
This is a schema/synthetic conservation smoke check, not a qualified return benchmark.

New companion artifact: 18,117,974 bytes,
SHA-256 f957c660b5544f3a44d1cf323f867aaa66a71d3644db00c920558ef2021656b0.
Build artifacts and detailed evidence remain under private local-data/home-acceptance-20261019/.
Exact-head CI/merge facts are recorded in that directory's delivery-state.json and CI receipt.

The owner manually logged in with GE open. Current account-scoped reads and authenticated
eight-slot source bindings pass. The installed plugin logs BUY and SELL delivery. The first
post-login WAIT is 5,422ms; an initial trace of 33 BUY cards has median 1,449ms, p95 2,946ms,
maximum 3,222ms; four SELL cards have median 1,559ms/max 2,178ms. This includes concurrent local
verification load and repeated advice; it is not a game-event-to-frame latency sample or sustained
fill/collection acceptance. An investigation GET for learning took 3,766ms; it is not a routine
client polling route and is not established as the source of normal suggestion delay.

Read-only prefix verification preserves all earlier 62,073 source rows, 1,470 financial effects
and the original risk anchor, with zero new financial effects at that capture. This is preservation,
not evidence of a completed new flip. The latest account/plan may change while the player trades;
an earlier READY/BUY does not authorize later orders.

Controlled full buy/partial/collect/sell/cancel/relist/restart and pricing quality, sustained timing,
remaining real foreground/editor/transport/source-feature parity, qualified chronological market
evidence and return/learning promotion remain open. The runtime remains PUBLIC_PRICE_BASELINE /
HOLD_FOR_VALIDATION. Old replay bundles require their retained evaluator.
