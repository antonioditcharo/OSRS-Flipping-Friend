# Account panel and prompt guidance

19 October 2026. The owner's Copilot screenshot and current source are the reference: expose useful
account information beside the action, then make suggestion transitions near-instant. This supersedes
the action-only sidebar restriction. Native Swing/RuneLite, manual gameplay, isolation and final
financial guards remain requirements. Delivery/acceptance: newest HANDOFF and linked receipt.

## Connected information

The 20 October [exit-profit update](exit-profit-and-replacement.md) changes primary unrealized
profit to expected after-tax exits at current listings/proven original targets. Instant-sale P/L
is separate; portfolio value retains bid estimates. Their unknown coverage and risk are distinct.

Current action/quantity/exact unit price, authentic item sprite and estimated after-tax profit/time;
graph, holdings, pause, block, skip; actual 5m/30m/2h/8h and visibly custom intervals; existing risk
controls; Records/settings and the wide native investigation windows.

Performance selects Session, Last 24 hours, Last 7 days or Recorded history. Every selected durable
DISPOSE row contributes; the investigation list's 100-row cap is never an aggregation denominator.
Source-offer sales are grouped and paged ten per page. A row opens its selected recorded sale evidence; the expansion
control opens Recorded sales. The graph remains available from sale details. Scope and unknown coverage stay visible.

| Metric | Definition / uncertainty |
| --- | --- |
| Profit | Sum of recorded after-tax bounds; unknown rows excluded and counted, never assigned zero |
| Flips made | Distinct full completed cost-matched sell orders with accepted disposal sources; partial updates contribute profit without inflating the count |
| ROI | Known realized net bounds / their exact applied acquisition costs; unavailable at zero denominator; unknown-cost rows excluded |
| Session time | Elapsed wall time including offline time from a separate durable measurement cursor; differs from Copilot's accumulated active-flipping clock |
| Hourly profit | Known realized net bounds / elapsed wall time; Measuring under one minute; other scopes have no invented session clock/rate |
| Available cash | Verified spendable coins from copied account evidence; includes only the existing conservative bank lower bound when enabled, never estimated resale proceeds |
| Stock purchase cost | Exact canonical acquisition cost of remaining managed units; remains available when resale prices are missing |
| Unrealized profit | Display-only last-quoted after-tax estimate on physically observed canonical stock; trade ages are explicit, and a star identifies the priced portion when quantities remain unpriced |
| Portfolio value | Display-only last-quoted managed-stock estimate excluding cash/personal items; approximate, with explicit missing quantities/cost, never a liquidation guarantee or input to entry risk |

These are recorded/display measurements, not whole-account terminal-adjusted benchmarks, validated
forecasts or profit-superiority claims. Historical coverage can remain incomplete.

The first complete authenticated account capture appends PERFORMANCE_SESSION with its current
position cursor. Reset appends an idempotent independent boundary. It preserves every canonical
source, financial effect, old trade and original risk anchor/confirmed loss; restart resumes it.
Late-observed rows enter by acceptance cursor; observation times are never converted into exact
execution times. Historical rows are not backfilled into a new session.

Authenticated scoped routes: GET performance/current?scope=SESSION|DAY|WEEK|ALL and POST
performance/session with requestId. Path/header must match. GET never creates sessions; reset needs
current account evidence. Client records use an independent coalesced worker and account/generation
fences, clearing on logout/switch. None of these measurements authorizes trading.

Current account is explicit. Cross-account catalog/selection/aggregation is deferred; do not infer
legacy database ownership or present current-account totals as all-account coverage. Copilot's own
community links/service login/logout are not Friend's identities. No Copilot private API dependency.

## Prompt decisions

Planning no longer has a five-second minimum spacing rule. Coalescing reuses an unchanged plan only
while account revision, market publication, series revision and TTL/risk validity permit. New
resources/preferences/sources/market/history trigger promptly. Immediate plan GET waits at most
400ms for queued work outside the service monitor; current proof and final entry still govern.

Client bursts coalesce to one calculation and one latest successor for changes during it. Ordered
offer events/acknowledgements are never coalesced away. Container changes trigger decisions after
invalidating stale advice. Account proof is published before client-thread instruction acceptance.
RuneLite logs time from refresh request through durable publication, plan/ledger, final decision and
client delivery. Deferred presentation starts a new measurement; no cached instruction is made
actionable to improve timing.

Validated canonical history stays in RAM as an immutable prefix; indexed tails validate new sources.
Scoped effects reuse an unchanged accepted position prefix, rebuilding on a new effect. External
SQLite commits (data_version), account changes, malformed/foreign tails and rollbacks discard relevant
proof. Accounts without historical gaps do not acquire an unnecessary full-history scan. Current
eight-slot projections/source receipts/cash are always reread; final risk/exposure/physical holdings,
funding, limits and expiry remain authoritative. No financial rows are rewritten/replayed.

Candidate history-derived features/context/fill curves use exact candle value keys (timestamps,
null sides, prices, volumes and contextual bid), not identity-only/hash proof. Size, quote, horizon,
appetite/model, season, cash, limits, filters, expiry and allocation are recalculated. Warm/cold
generation must agree. This changes computation, not strategy or runtime learning promotion.

## Acceptance

Require Windows clean build/tooling, exact-head Windows/Linux CI, native render QA, supported paired
delivery, then player-operated acceptance. Frozen timing alone cannot prove installed speed.
After login/open GE: inspect all metrics, change scope, reset the measurement; manually place or
collect one offer, check the next instruction and read delivered-stage timings directly. Retain
unknown money, missing stock, original anchors and genuine explained waits. Complete partial/cancel/
relist/restart acceptance and broader parity/learning/benchmark gates stay open in TASKS.
