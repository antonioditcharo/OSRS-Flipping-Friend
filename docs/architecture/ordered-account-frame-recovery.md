# Ordered account frames and source-proven sale cost amendments

Owner approved 24 October 2026. Delivery is tracked separately in
`local-data/ordered-frame-recovery-20261024/delivery-state.json`.

## Incident and prevention

A copied eight-slot planning frame was appended on the worker after a newer client callback.
Its older capture timestamp received newer durable sequences. The reducer correctly rejected
the regression and retained a recovery baseline, but the scope reader made the gap account wide.
That blocked entries and cost matching for unrelated post-restart sales with recorded purchases.

The client now commits the complete copied frame to the existing forced atomic outbox on the
client thread, before worker dispatch. Its immutable cash/slots capture carries the eight original
source IDs. Publication only drains/acknowledges those sources; it cannot append the copied frame
again. A later capture supersedes the frame even when its economic counters are unchanged.
Account/profile, logout, pending-source, raw/callback agreement and final authority fences remain.
Capture does local persistence only. No HTTP or game input runs on the client thread.

Actual recorded UTC clocks are not clamped or restamped. A regressing clock still records the
normal rejection/baseline. Scope alone can retain the causal item set when both adjacent sources
are authenticated, the durable session/sequence order is strict, and the episode, item, side,
event type, price, size, filled count and spent counter are identical. It is not a recovered fill.
Changed counters/episodes, missing sources, foreign accounts/sessions and ambiguous ordering
remain unbounded. Original gaps and item pauses remain; current cash and risk are separate gates.

An additional read-only proof pairs the rejected unchanged frame with its immediately following
unchanged authenticated recovery baseline. Both exact reasons, the original same-episode counters,
ordered sequences and restored original clock are required. Every gap affecting an item must have
this proof; an independent gap or malformed acquisition defeats it. This does not clear the gaps,
item entry pause, prospective funding bound or full cost reserve. A current sell whose aggregate
remaining listings fit the recorded acquisition pool may coexist with unrelated entries under
those reserves; live uncertain buys and excessive/unsupported sells still block. Future observed
sale deltas can consume that pool's exact cost without becoming another unmatched-cost incident.

Ledger construction attaches item scope before validation. Otherwise an item-zero legacy gap
could incorrectly reject unrelated managed holdings before their scope proof was installed.

## Explicit accounting amendments

The owner accepted source-proven correction of the affected recent sales. This is a narrow
exception to the prior no-correction workflow, not permission for generic historical backfills.
No startup/read path repairs financial rows. The authenticated account endpoint
`POST /v1/accounts/<accountId>/ledger/sale-cost-amendments` accepts an exact list of sale source IDs.
Only the companion writes the supplemental `sale_cost_amendment` table in one transaction.
No agent edits a live SQLite row or replays an execution through accounting.

Qualification requires the original coverage-refused, unpriced disposal; complete matching account
source history; its ordered authenticated sale delta; a complete earlier unpaused acquisition pool;
matching earlier financial/source deltas; the exact remaining pooled/subset allocation; and unchanged
original gross, tax bounds and rule profile. Later purchases cannot supply earlier cost. Missing,
excessive, mixed personal or contradictory coverage remains unknown. Requests are bounded to twenty
distinct sources and are atomic; a failed member rolls back the entire request.

The immutable amendment stores the original sale, source/effect prefix SHA-256 digests, derived
gap scopes, pool quantity/cost and effective cost/profit bounds. Its original source ID is unique;
identical retries are no-ops and changed proofs are refused. Original event, financial, buy-limit,
learning, risk-anchor and session rows remain unchanged. There is no new acquisition, disposal,
cash movement, tax interpretation or execution-learning sample.

The authoritative ledger's `effective_position_event` view overlays the amendment on the original
sale. Ledger totals/history, Performance, active incomplete-sale review and prospective risk read
that same effective record, preserving the original sale ID/time/session and counting it once.
Original unassigned data and genuinely unknown legacy sales remain untouched. Existing read-only
offline databases without the supplemental schema retain the original read behavior.

## Verification and delivery gates

Tests cover overtaken unchanged callbacks, forced frame persistence/ACKs/restart, refusal diagnostics,
scope acceptance and rejection, exact pooled remainders, immutable originals, duplicate requests,
rollback, authentication/account isolation, risk-window preservation and unrelated final PLACE_BUY
authority. The full trading/loss/continuation suites remain required.

The opt-in `:companion:probeSaleCostIncident` task takes `-PincidentArgsFile=<JSON array path>`;
arguments are frozen source database, new destination database, account ID and exact sale IDs.
It permits only a new disposable copy under this checkout's `local-data`, never a live writer.
Frozen cash/slots can already be superseded: a passing historical reproduction cannot authorize
current entries. Fresh post-update eight-slot evidence, actual suggestion delivery, manual trading
and sustained capture/guidance latency remain separate acceptance gates.
