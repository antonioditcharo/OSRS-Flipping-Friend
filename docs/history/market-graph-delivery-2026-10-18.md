# Native market graph delivery

18 October 2026 (America/Anchorage). Source branch: `codex/market-graph-projections`.
PR [#96](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/96) is merged as
`1ebe4f807854d21d9d8c42e4192fc58c9e763b6a`. Both exact-head Windows/Linux checks passed on source
`f1f0ce8efeeb67fcee1283e615455650e24014ff`, run
[39190129423](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39190129423).
The clean primary checkout fast-forwarded. Paired delivery awaits the requested normal RuneLite
close; installed runtime remains accepted #94. No installed graph/player acceptance is claimed.

## Implemented and verified

The production Swing Price graph now has selected-item search, explicit suggested-item switching,
low/high observations, separate volumes, UTC time/price statistics, Month/Week/Day/8h views,
hover investigation, wheel/button zoom, drag/keyboard pan, reset, expand/restore and an experimental
projection toggle. The genuine RuneLite item sprite and licensed Material controls are used.
The compact sidebar and its controls remain unchanged. [Architecture](../architecture/market-graphs.md).

Focused history has its own bounded cache/worker, uses recent companion history before an independent
public request, deduplicates requests, retains good bars during failure, and fences abandoned/restarted
work. Browsing does not replace strategy history or queue behind strategy warm-up. Missing prices,
history gaps, old/future timestamps and unqualified projection windows stay unavailable.

Windows standard `clean test jar :companion:shadowJar` passed in 98s, 18 tasks: 1,653 Java tests
passed and six existing skips (plugin 722; core 192; companion 739 passed/six skipped). All 754
project classes in the two artifacts target bytecode 55/Java 11. Tooling: 108 Python tests passed
in 142.854s. The first full build identified an outdated strategy-cache expectation in the records
navigation regression; its correction verifies the new chart cache and absence of strategy reads.

Native rendering uses the real Swing view/RuneLiteLAF at 1905x1000, 1000x680 and 730x540. It was
compared directly with the retained user reference. Narrow-axis overlap and selected-tab contrast
were corrected. [Design QA](../../design-qa.md) passes within that rendered scope; window scaling,
monitor expansion and installed owner acceptance remain separate. The previous sidebar QA receipt
is [preserved](native-sidebar-design-qa-2026-10-14.md).

Private receipts are in `local-data/market-graph-20261018/`, including original public response bytes,
reference/render captures, `build-verification.json` and `frozen-evaluation/`. Build/tooling logs
are `local-data/market-graph-clean-build-final.log` and `local-data/market-graph-python-tests.log`.
The final built-source render repeated in 19s; `render-final/` was compared at all three widths
with no additional rendered P0/P1/P2 findings. `final-visual-verification.json` records the source.

Built plugin: 564,522 bytes, SHA-256 `ac6ae523a68e814d323d6a38abca24a0efceb5ede700a58a9ea1a7226098a7ea`.
Built companion: 18,041,268 bytes, SHA-256 `aeb082bcb54853dc8a53d1a7c452bab9308f59c8f60e8a3ca78f9983d83254f1`.
These are candidate hashes, not a claim of installation.

Pre-update native status, API 1.13.2 compatibility and scoped GET pipeline all pass. The retained
account capture is over half an hour old; WAIT/no current plan is not live buy acceptance.
The read-only frozen financial prefix has 44,076 canonical rows, 1,393 financial effects and the
sole original risk anchor. Task XML/SDDL and installed manifest are retained for after-update
comparison. No source row, account database, loss anchor, task permission or game input was changed.

## Data reuse and projection evidence

The inspected Copilot client has own-account exports and aggregate prices/graphs; no supported
all-users transaction export was found in the inspected interface. Its private model/training data
were not recovered. Friend uses independent Wiki/RuneLite public market data. Own exports remain
separate historical evidence; aggregate observations never repair a player's ledger or qualify as
their executions. No Copilot private API is introduced as a production dependency.

`HISTORICAL_RETURN_QUANTILES_V1` uses at least 24 complete contiguous earlier hourly comparisons
per horizon, anchored to actual recent quotes, with median and central historical-return ranges.
It is display-only, outside entry/exit authority and execution learning. It is not Copilot's model.

The frozen public Huasca-seed input has 365 hourly buckets and captured clock 1792375005. The
packaged CLI and its independently retained evaluator both reproduce the exact report. These
retrospective tests assume completed buckets were available at bucket close; original publication
and revision clocks are unavailable. They are not prospective/as-of execution validation.

| Horizon | Known / unknown / abstained | Scenario MAE % | Persistence MAE % | Range coverage bounds |
| --- | --- | --- | --- | --- |
| 1h | 306 / 6 / 5 | 1.3306 | 1.3253 | 52.40–54.33% |
| 3h | 101 / 3 / 2 | 2.2294 | 2.2165 | 48.08–50.96% |
| 8h | 39 / 1 / 0 | 2.2786 | 2.2352 | 46.25–48.75% |
| 24h | 13 / 1 / 0 | 4.2734 | 3.0521 | 57.14–64.29% |

Price MAE averages the two sides on known targets. Unknown/censored targets are retained in the
coverage bounds, unqualified forecasts are abstentions. All four horizons have worse known-target
MAE than persistence in this one-item capture. No accuracy advantage, calibrated future coverage,
trading performance, model promotion or full parity is established. Production learning remains HOLD.

Input SHA-256 `008feadf85c01853b594adf7eab4f7b156a8f0ed42d9bc8166cc82357db9eb13`;
results SHA-256 `630282a63bae3adcffaaad0b49e082067fd504f809e4e11861fd013810cb1c33`.

## Player check after installation

Open Records → Price graph. Search an item and check that the title/statistics match; try the four
ranges, hover, zoom/pan/reset, projections on/off and expand/restore. Switch deliberately to the
suggested item. Check that ordinary buy/sell guidance and Missed flips remain available. Login,
offers, confirmation, cancellation and collection stay manual. Controlled partial/cancel/relist/
restart acceptance, validated forecasts, qualified 100M cadence benchmarks and learning release
controls remain visible in TASKS.
