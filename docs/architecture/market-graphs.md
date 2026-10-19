# Market graphs and independent price projections

18 October 2026 (America/Anchorage). User reference: Copilot's wide price graph with low/high
observations, forecast bands, volumes, range/zoom controls and item statistics. This extends the
existing production Swing details window; it does not substitute a website or change the compact
sidebar. Delivery state and receipts belong in HANDOFF and a linked history checkpoint.

## Data boundary

The inspected Copilot client exposes an authenticated **own-account** transaction export and
aggregate price/graph responses. It exposes no supported all-users transaction-log export. The
private model, training pipeline and global training data were not recovered. Source availability
does not establish permission or a supported production data service. See the existing
[protocol research](../../local-data/copilot-reverse-engineering/20261017/protocol-analysis.md) and
the public [request handler](https://github.com/cbrewitt/flipping-copilot/blob/main/src/main/java/com/flippingcopilot/controller/ApiRequestHandler.java).

Friend already uses the independent OSRS Wiki market API. A bounded public capture for Huasca seed
returned 365 buckets at each of 5m, 1h and 6h. Prices/volumes from other players are market evidence,
not the current account's acquisitions or disposals. They never fill missing ledger history, book
profit, remove a reconciliation gap, mark unseen stock, or qualify as real execution samples.
Own Copilot exports remain separately sourced historical evidence with their original cost/tax,
duplicate and timing uncertainty. No Copilot private endpoint is a production dependency.

## Working chart

`MarketGraphView` replaces the small graph only in Price graph; holdings retain their sparkline.
Search accepts IDs, exact names and distinctive text, with a choice menu for multiple matches.
The graph and statistics stay on that selected item. Switching to current advice is explicit;
unrelated suggestion refreshes cannot silently change the chart. A guide line appears only for a
matching currently displayed action, never a generated price scenario.

Month uses 6h buckets, Week uses 1h, Day/8h use 5m. Time is scaled by actual UTC timestamps;
missing buckets/sides break their lines rather than compressing or filling gaps. Low/high reported
volumes are shown separately. Observed daily coverage is labelled partial when incomplete.
Last side prices/times and 24h/week changes use the selected item's observations. Unknown, future
or absent evidence is not replaced by acquisition cost or another item's data.

Range buttons, wheel/button zoom, drag/keyboard panning, Home/reset, projection toggle and expand/
restore are functional native controls. Hover distinguishes historical average buckets from
future medians/ranges and shows sample counts. Dark tabs and responsive tick counts preserve
readability at 1905, 1000 and 730px. Real ItemManager sprites and licensed Material PNG controls
are used; the quantitative plot is painted from data, not a generated image.

`GraphHistoryCache` has a focused background worker and a bounded independent cache. It first
uses sufficiently recent companion history, then public Wiki history for the selected item.
Graph browsing cannot overwrite production strategy history or queue behind its bulk warm-up.
Pending requests deduplicate; switching items withdraws abandoned pending work. Empty/malformed/
failed responses release reservations, back off and retain earlier usable bars. Stop/restart
generation checks prevent old completions from resurrecting cleared state. No network call runs
on the Swing thread. The compact panel, ledger, risk window, offers and manual game-input boundary
are unchanged.

## Projection meaning and verification

`MarketPriceProjection` is `HISTORICAL_RETURN_QUANTILES_V1`, an **experimental display-only** model.
At each horizon it collects complete, contiguous, same-length past return windows, separately for
low/high prices, from completed hourly buckets. At least 24 comparisons are needed. The median
and 25th/75th empirical quantiles become price scenarios anchored to actual recent timestamped
public quotes. These are historical-return ranges, not validated future probabilities or fill
estimates. Missing/conflicting/future buckets, insufficient history, old anchors and unsupported
price ranges withhold the projection. No execution calibration or learning setting promotes it.

Rolling-origin evaluation uses only earlier completed prefixes and non-overlapping test horizons.
Unknown/censored targets and unqualified forecasts remain counted separately. Known-outcome
price error, persistence-baseline error and range coverage are reported alongside coverage bounds
that retain unknown targets. These are retrospective bucket-clock comparisons: original historical
publication/revision clocks are unavailable. They do not prove as-of live availability, calibrated
future quote probabilities, whole-account performance or live execution validation.

The explicit offline CLI freezes inputs/results/artifact hashes without opening a database:

```powershell
java -cp companion/build/libs/flipping-friend-companion.jar com.flippingfriend.companion.MarketProjectionMain run <frozen-public-input.json> <new-local-data-directory>
java -cp companion/build/libs/flipping-friend-companion.jar com.flippingfriend.companion.MarketProjectionMain verify <bundle-directory>
```

The source URL, item, captured clock and exact hourly source prefix accompany every report. Edits,
missing artifacts, unsupported resolution/source and overwrites refuse verification. Existing
execution forecasts retain their separate model identity, immutable bindings and HOLD gate.
Graph scenarios cannot authorize buys, rewrite original advice or make a validated-learning claim.

Native rendering can be repeated with the actual Swing view, RuneLite look and feel and a frozen
public-only capture, without starting a game or sending input:

```powershell
.\gradlew.bat renderMarketGraph -PgraphCapture=<capture-directory> -PgraphOutput=<image-directory>
```

Manual acceptance after a supported paired update: open Records/Price graph; find an item; check
that its title/statistics match; try Month/Week/Day/8h, hover, pan/zoom/reset, projection toggle and
expand/restore. Switch to suggested item deliberately. Continue trading manually; ledger gaps,
unknown historical money, actual cadence settings and entry/sale guards must stay intact.
