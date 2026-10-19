# Durable recommendation risk

18 October 2026 continuation: the owner explicitly approved
[current-account policy](current-account-inventory-policy.md). Historical unknown amounts remain
reported and affected stock stays paused, while verified current funds may support unrelated buys.
The earlier account-wide historical reserve charge below is superseded; original anchors, exact
losses, current unpriced-stock reserves and live exposure/evidence guards remain active.

18 October owner-approved continuation: [qualified quiet-stock marks](qualified-stock-risk-marks.md)
add timestamped completed public price/volume support, a 10% price haircut/tax and quantity limits
for trades within 15 minutes. Direct marks retain 120 seconds. Unsupported/unseen stock still has
zero terminal value/full-cost reserve. The original anchor, confirmed losses and allowance arithmetic
do not change; quiet marks cannot credit gains. This is a stated policy choice, not a calibrated bound.

Implementation contract, 15 October 2026, for the active recommendation breaker. Local
journal/session figures remain explicitly local estimates and cannot authorize new buys.

## Tracking window

The first fresh authenticated cash/eight-slot account observation with positive known trading
capital starts a prospective risk window for that explicit account and durable outbox session.
Its start time, starting capital and position-event cursor are recorded with the account snapshot
in the existing event log. There is no new database schema, historical profit backfill or inferred
session clock. A companion/client restart loads the same window; it does not reset realized losses.
Different accounts have separate stores/windows. Missing, malformed or contradictory window
evidence withholds entries. It never assigns the old unscoped database to an account.
The original eight authenticated source IDs are validated when the marker is reloaded. Financial
event identities and risk markers do not expire with old diagnostic account snapshots. No live
retention task or database repair is introduced.

This is a risk tracking period, not a benchmark session or a claim of measured GP/hour. Existing
unknown history remains in Missed flips. New manual trading before the first observable window
is not retroactively assigned a session. Once a window exists, its ledger cursor survives restart.

## Money and uncertainty

Realized profit ranges come from the authoritative position-event ledger after the window cursor.
Use the lower bound for the breaker; do not substitute journal profit, a rounded average or the
upper bound. An unrelated unpriced personal sale is not trading profit. An unresolved sale which
consumed observed trading stock retains unknown profit and reserves its possible lost cost for
risk, rather than manufacturing zero-cost profit. New reconciliation uncertainty reserves the
affected observed-capital envelope; old pre-window gaps remain exposure guards and paused items.
These reserves are possible losses, not reported realized losses or historical repairs.

The [history-tolerant continuation](history-tolerant-planning.md) separates lifetime historical
exposure from newly exposed risk-window capital. Old quarantined stock is not charged again as
new managed loss; new purchases of paused stock remain reserved. Source-order opening receipts
and new episode funding bound prospective gaps without clearing historical exposure or the window.

Open managed stock is marked to a fresh instant-sell quote after current-rule hypothetical sale
tax. These marks are estimates, not realized proceeds or a promise of immediate execution.
Absent/stale/malformed quotes cannot silently mark stock flat. Pre-existing personal inventory
is not enrolled. Historical capital upper bounds are not account equity or spendable cash.
Quotes must be numeric exact integers; fractional values and numeric strings are refused. The
published risk expires with the earliest cash/slot, global-market or individual quote deadline.

Known capital is observed spendable cash plus current buy reservations plus clean managed stock
at known acquisition cost. The 15% base is capped by starting capital and a conservative current
capital-before-recorded-profit estimate: subtract the bounded realized profit upper value from
currently known capital, floored at zero. This avoids counting a known loss twice, treats deployed
stock as capital, and lets external withdrawals reduce the base without granting value from
uncertain historical capital. Use checked integer arithmetic.

The conservative drawdown is the negative part of bounded realized profit plus marked open-stock
profit, with unresolved-loss reserves deducted. The remaining loss allowance is 15% of the risk
base minus that drawdown. Positive marks can offset observed losses, but unknown profits cannot.
Overflow or inconsistent provenance makes risk unavailable rather than clipping to a safe-looking
number. Preferences, item/group exposure, actual cash/slots and buy limits still gate each entry.

## Consumers and verification

Plan computation and final entry approval use the same fresh account/read-set/risk input. The
companion owns the arithmetic; client-supplied local drawdown is not an authority. The plan's risk
diagnostic/notification reads this result, and the compact sidebar remains an action surface.
Plan details show the loss bound, remaining allowance, uncertainty reserve and tracking start.
Unavailable money stays unavailable. The notification latch is scoped to the durable window;
missing evidence does not silently rearm it. The window has no automatic login/restart reset.
Missing risk affects new entries, not evidence ingestion, personal sell quotes or collection.

Risk explanations distinguish a conservative loss bound from an uncertainty reserve. A reserve
can exhaust the allowance with no recorded loss; it must not be described as confirmed loss or
a resettable local session limit. The waiting card may use a risk reason only from the current
account's fresh, blocking diagnostic. Missing/foreign/expired/nonblocking evidence keeps the
generic durable-account refusal. Notifications use the same durable reason. This changes no
financial value, risk-window lifetime, entry decision or exposure guard.

Regression gates include foreign local-session profit, persistent losses across restart,
realized tax/profit ranges, unknown historical versus new managed/untracked sales, missing quotes,
uncertainty reserves, account/session fences, arithmetic overflow and first-window transaction
rollback. Source tests are not live trading acceptance or a profitability comparison. Runtime
remains the last verified paired install until the normal close and supported update are observed.

## Evidence-read work budget

Current slot and original risk-anchor lookups use the existing event_log_event_id_unique partial
index. Their predicates explicitly require a non-null/nonblank identity, matching that index.
Keep the source predicate in the LEFT JOIN condition: a missing source must still withdraw the
whole current read set, rather than silently returning seven valid slots. No new index or schema
migration is required. Anchor validation, identity/account/session/order checks remain mandatory.

Gap scope and conservative capital bounds share a complete immutable history read within the
same planning operation/transaction. This is a lazy operation-local value, including unavailable
evidence; it does not survive another request. New, malformed or foreign source evidence must be
seen on the next read. Current eight-slot evidence is still read fresh from the durable store.

CurrentOfferReadLatencyTest bounds actual SQLite VM work over disposable 30,000-row histories,
without a machine-speed timeout or a copied query-text assertion. It exercises heartbeat/restart,
shared historical gap scope and capital bounds, broken source links/payloads, and foreign evidence
arriving after a successful read. Actual latency and post-install advice remain separate live
acceptance evidence. Preserve the client timeout, ordered acknowledgement/retry protocol and all
financial guards; do not mask expensive reads by accepting stale advice or resetting account data.
