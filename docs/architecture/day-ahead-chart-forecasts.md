# Day-ahead chart forecast availability

26 October 2026. Recovered side-conversation change, **installed in #178**, including merged #177
and the separately caught startup scan fix. Combined clean Java/tooling, exact-head Windows/Linux
CI, backup/rollback and installation pass; native live acceptance remains pending. The original seven source files and source-base/hash
manifest are preserved privately in `local-data/hosted-pilot/recovered-chart-source` in the primary
checkout. No retained side-chat transcript was found; saved code, public captures and test receipts
are the recoverable evidence. The primary checkout and its unrelated deletions remain unchanged.

The owner reported missing forecast areas on Dharok's armour set and other graphs, with very
short forecasts when visible. Three source causes were reproduced: the public forecast stopped
at the first unsupported five-minute horizon even with usable hourly evidence; chart forecasts
were capped at eight hours (three in the 8h view); and background completion extended the allowed
canvas bounds without extending its untouched visible viewport.

`PublicPriceForecast.fitChart` creates `PUBLIC_PRICE_CHART_PATH_V2` display fits at 5m, 30m, 1h,
2h, 4h, 8h, 12h and 24h. It uses the existing rolling-origin candidate rules and comparison
requirements, fitting each side independently. Short horizons use completed five-minute history;
hour-scale horizons use hourly history. At 12h/24h, insufficient hourly comparisons can use
completed six-hour history with more disjoint comparisons. This changes neither the trading
model's horizons, cache, version, input fingerprint nor order-selection rules.

A quiet latest bucket can use the side's actual immediately preceding completed bucket. Missing
observations stay missing and their clocks are not compressed. A missing short horizon no longer
suppresses later independently qualified forecast points. Curves interpolate qualified horizon
estimates; interpolated points are not additional independent observations. Quotes must retain
their existing timestamp/freshness and legal-price checks. Unsupported history stays unavailable.
Persistence remains an honest forecast when earlier and later validation do not support a
directional challenger. No decorative drift or invented volume is added. Residual bands describe
the middle half of earlier forecast errors, not guaranteed future coverage.

Every historical range requests up to 24 forecast hours, matching the approximate next-day span
in the owner's supplied Copilot examples. Month/Week/Day/8h still describe the historical window.
The canvas follows its Home window when background data arrives, while preserving manual zoom
and pan. Chart analysis retains its bounded independent worker at minimum thread priority and a separate 32-fit cache; it
does not evict strategy fits or write account data.

Verification uses separate Gradle outputs and project cache under
`local-data/chart-forecast-20261026`, avoiding the main thread's build files. Focused tests cover
missing short history, independent quiet sides, full 24h coverage, stale/conflicting/future inputs,
unchanged live fits and identities, honest flat forecasts, late visible-window expansion, preserved
manual zoom and all four range controls. Real public captures for Dharok's armour set (12877),
Dragon dart (11230) and Ensouled dragon head (13511) retain original responses and SHA-256 manifests.
The production Swing renderer uses those captures; no game actions or account writes are involved.

The retained three-item final-quarter check is small and retrospective, with missing original
publication clocks. At 24h the known-target counts are six, six and five by item; earlier-error
band coverage is 0%, 0% and 20%. These are not calibrated future uncertainty intervals. Point
errors do not consistently improve on persistence. Preserve the failed coverage evidence and
the chart's explanation that shaded bands describe earlier errors; neither the longer horizon nor
a curved path grants trading, loss or learning authority. Private receipt:
`local-data/chart-forecast-20261026/public-forecast-check.json` in the primary checkout.

Installed delivery and its exact release-head CI are recorded in
[the #178 receipt](../history/combined-chart-install-2026-10-26.md). Live chart acceptance and forward
forecast accuracy remain open. Longer chart forecasts do not establish Copilot parity, improved prediction
accuracy or greater account profit. The main workflow/pricing release gates remain unchanged.
