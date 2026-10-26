# Account verification and predictive pricing

25 October 2026. Work in progress; HANDOFF and the private delivery receipt distinguish installed
runtime, verified source and future pricing activation. Existing trades and original risk anchors
are retained. Workflow source does not activate a learning model or a new loss policy.

## Measured workflow work

A 120-second live JFR capture found repeated prospective-gap/continuation scans, background
amendment/history decoding and native session lookups. An absent event-type index made finding
the old performance-session row scan later account captures while the account lock was held.

Add backward-readable indexes for metadata lookup, qualified terminal observations and earlier
acquisitions. Native SQL instruction-budget tests reproduce the unindexed scan and preserve
duplicate terminal occurrences, full live counters and partial cancellations. Missing earlier
purchase evidence rejects an impossible repair before full source decoding; positive repairs keep
their existing complete source/tax/account proof. Bounded negative background results expire on
external edits or new amendments. History support rereads only qualified terminals and reuses an
unchanged evidence/terminal prefix; malformed source JSON withdraws support.

Prospective-gap funding and continuation indexes advance only through SqliteStore-validated exact
canonical appends. External changes, replacement prefixes and rollback reset them. New episodes,
rejected sources, contradictory origins, changed scope/cost/window and overflow retain their guards.
Risk effect inputs may be reused only for the same effect cursor, account, original risk window
and reconciliation scope; every consumer receives a detached copy. Current cash, eight-slot proof,
physical holdings, prices, marks and final risk/entry authority are re-evaluated separately.

An accepted cancelled loss exit whose market confirmation vanishes can complete at a new exact
non-loss limit from a fresh feed. It cannot reuse stale loss permission or rewrite the acquisition
target/stop. This also provides the safe continuation behavior needed for later policy rollback.
Maintenance version 10/replay 26 identify this change; receipt family 10 remains unchanged.

## Appended forecast requirement

The owner supplied five Copilot examples: independently evolving low/high central paths, varying
uncertainty and an exact suggested order price, across rising, falling and recovering markets.
The owner's sixth image shows our existing nearly horizontal historical-return projection.
These images specify the desired behavior and interface, not a verified Copilot algorithm.

Next pricing work must produce horizon-dependent public-price forecasts and feed the actual entry,
hold, maintenance and exit comparison. Keep order prices distinct from point forecasts and their
uncertainty. Use completed price/volume history, time-of-day behavior where coverage supports it,
recent trend/recovery, spread, quantity, expected attendance and exact after-tax account outcomes.
Future trade learning is separate. Existing unvalidated graph/legacy forecasts cannot become
disposal permission by being renamed or drawn as curves.

Evaluate 5m/30m/2h/8h independently with chronological training prefixes and untouched later targets.
Compare point errors with persistence and seasonal baselines, and assess interval coverage/width;
then compare whole-account results under the existing 100M/eight-slot contract, including losses,
inventory and manual interaction time. Preserve missing outcomes and original availability clocks.
[Rolling-origin evaluation](https://otexts.com/fpp3/tscv.html) and
[distributional scores](https://otexts.com/fpp3/distaccuracy.html) describe the methodological basis.
No outsmarting/profit-superiority claim follows from an attractive chart or a price-touch replay.

Required delivery: bounded cached background analysis; immediate verified local fallback; one shared
pricing/forecast contract for client, companion, charts and replay; preserved prospective order/
source evidence and risk anchors; no unsupported loss, same-price cancel loop or preference veto.
Qualified market-informed losses need their own explicit evidence and durable budget check, plus
compatible rollback and full cancellation/collection/relisting proof before activation.

## Public price path increment (installed in #176; retained in #178)

`PublicPriceForecast.PUBLIC_PRICE_PATH_V1` fits the low and high sides separately. Candidate rules
are persistence, damped recent trend, slow median reversion, relative daily movement and a damped
daily combination. Selection uses 24 earlier disjoint rolling origins and checks the chosen rule
against persistence on 12 later completed outcomes. Directional rules require improvement on both
parts. Missing timestamps/outcomes are retained as missing; prices and volume are never forward
filled. Sparse, conflicting, stale, oversized or unsupported input falls back without vetoing
otherwise eligible legacy entries. This is public statistical analysis, not execution learning.

The 5m/30m fits use five-minute history; 2h/8h fits use hourly history. Interpolation preserves the
actual horizon. The #176 chart showed at most eight hours of qualified forecasts. Installed #178
uses a separate display-only model/cache for qualified horizons through 24h; strategy horizons and
authority remain unchanged. It reanchors on timestamped public quotes and keeps action-price guides
separate from the paths. A bounded single-worker queue performs chart fitting off the EDT; a
128-entry public-only exact-input cache shares analysis without sharing account state. Each input
has at most 2,048 rows and retains at most 720 completed buckets. Model/public-input fingerprints,
forecast estimates and exact candidate orders are optional prospective plan analysis; legacy
financial IDs and immutable fill-model fingerprints are unchanged.

Live strategy calls perform lookup/submission only; a single low-priority model worker has a
64-task queue and deduplicates matching requests. Cold/full/malformed requests immediately use the
legacy grid or limit. Snapshots are copied before submission. Explicit offline SeriesSource and
exit-harness modes fit synchronously to remove worker timing from deterministic account replay;
the mathematical and financial decision rules are shared.

Forecast values add at most two bounded prices per side. Actual-order historical flow, quantity,
tax, cash, buy limits and final authority still apply. The first unrestricted frontier failed an
eight-hour development scenario at 5m attendance: higher model scores accumulated more inventory
and worsened conservative account equity. That frontier is rejected. The revised entry guard
requires a legacy tactic for the same item and quantity with no worse capital, worst/unwind loss,
leg probabilities, leg times and net/expected profit. It retains all legacy alternatives. The
local Scorer uses the same guard. A higher expected GP/hour alone cannot expand entry exposure.

Initial known-cost sells compare supported forecast limits with the protected original target and
exact taxed cost floor. Working sells retain placement/progress patience and two completed market
buckets before comparing quantity-specific non-loss limits. Fine-tick adjustments remain exact.
Quiet prices cannot gain new predictive or loss authority. Shared maintenance is version 11 and
replay 27; compatible sale-receipt family 10 remains unchanged. A behavioral regression demonstrates
a supported patient limit above the instant quote and immediate legacy fallback with insufficient
history, while preserving original targets/stops.

Recovery follow-up restricts cost qualification to the complete source/effect prefixes required by
the requested sale. Later purchases cannot price earlier disposals. An earlier directly recorded
item gap is a permanent negative under AutomaticTradeScope and rejects before source decoding.
Positive repairs retain source hashes, tax proof, immutable overlays and concurrent-append checks.

## Research receipts and remaining gates

Private frozen public input: `local-data/predictive-pricing-20261025/public-market-snapshot`.
The first untouched-final-quarter point-error check has the following mean absolute percentage
errors over known side outcomes. Unknown targets and abstentions remain separately counted in the
receipt; original publication/revision times are unavailable, so bucket-close availability is an
explicit retrospective assumption.

| Horizon | Price path | Persistence | Earlier-error interval coverage on known targets |
|---|---:|---:|---:|
| 5m | 2.57273% | 2.57271% | 58.8% |
| 30m | 5.88778% | 5.91113% | 51.3% |
| 2h | 5.46645% | 5.48577% | 50.7% |
| 8h | 9.48865% | 9.56994% | 39.3% |

These small point-error differences do not establish profitable trades. The 8h nominal central
50% earlier-error interval undercovers later outcomes; it is not calibrated loss permission.
The chart describes historical forecast errors without guaranteeing future coverage.

The account development fixture uses 100M/member/eight slots, all four cadences, 0.1%/0.5%/1%
volume-share scenarios, manual visit/action time, tax, open stock and conservative terminal marks.
It covers only eight items/eight hours and reconstructs quotes from bucket averages. It is not
actual execution evidence, and the 8h cadence has almost no opportunity for a completed round trip.
After using its initial failure to design the guard, it is a development regression fixture,
not untouched promotion evidence. The guarded decisions/results match #175 in all 12 cases.
Preserve both rejected and revised receipts; do not claim return improvement or broad capacity.

Exact-head Windows/Linux CI, paired backup/rollback and production Swing inspection pass through
installed #178; read [its receipt](../history/combined-chart-install-2026-10-26.md).
Still open: native chart/guidance acceptance, sustained
guidance timing under controlled load, player-operated maintenance/relist/restart, wider untouched
chronological account evaluation and an explicit budgeted market-informed loss policy. Original
stops, risk anchors and learning promotion fences remain in force.
