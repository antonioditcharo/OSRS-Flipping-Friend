# No trade left behind

Owner approved 24 October 2026. Delivery is recorded in
`local-data/no-trade-left-behind-20261024/delivery-state.json`; source, installation and controlled
player acceptance are separate. This workflow pass does not establish guaranteed fills or returns.

## Coverage and presentation

`TradeRecoveryScanner` derives immutable `TradeRecoverySnapshot`/`TradeRecoveryItem` rows from
the canonical account ledger, copied offers, physical inventory, existing continuation queues and
read-only maintenance evaluations. Every occupied non-collected slot is represented, including
full-counter and terminal frames; canonical unlisted acquisitions and incomplete evidence remain
visible separately. Parallel episodes retain their exact identities. Personal extras are not stock.
Rows of different kinds can refer to the same units: their quantities must not be summed.

Records → Trade recovery uses the existing maximized native investigation frame. The worker scans
even when companion collection wins. UI reads perform no disk, HTTP or policy evaluation. Changed
account, offer counters/episode, ledger publication, market, physical snapshot or maintenance settings
withdraw previous eligibility; a 30-second expiry also withdraws it without removing the obligation.
Final refusals replace earlier eligibility with the exact refusal reason. Diagnostic failure cannot
suppress independently valid trading or collection guidance.

## Buy lifecycle and cancellation purpose

The existing account-scoped `buy-maintenance-deadlines.json` gains an optional predecessor identity.
Only a qualified canonical continuation-successor proof supplies it. New successor episodes inherit
the original deadline, including an already elapsed deadline; later advice/forecasts/cadences cannot
restart it. Retained legacy deadlines are not rewritten. Missing, cyclic, foreign or unreadable
predecessor metadata cannot create a replacement budget. Real recent fills still protect progress.

An expired collected continuation retires its exact unbought origin after its reassessment decision
is durable. Parallel same-item origins remain distinct. Acquired units stay in the canonical ledger
and remain eligible for their supported exit; no disposal or sale is inferred.

`BuyRecoveryJournal` records a prospective, client-accepted cancel-and-reassess reason in the new
account-scoped `buy-recovery.json`. Atomic writes and exact committed-image/account checks preserve
external changes and unreadable files. Repeated presentation retains the original decision clock.
This metadata neither books executions nor resets cash, risk, targets, stops or learning.

Guided reassessment is separate from the existing eight-hour manual-abandonment skip. A new entry
for the reassessed item requires max(8 minutes, original checking interval), a material change using
the existing price deadband, fresh coherent two-sided prices and two new consecutive completed
5-minute buckets with positive volume on both sides and sufficient taxed margin. These are
conservative policy defaults, not calibrated fill claims. A timestamp/ACK change alone cannot
release the pause. Release must itself commit; failure preserves the pause. Unknown journal scope
withdraws new entries, while working offers and independently safe exits remain managed. Temporary
reassessment restrictions do not erase saved replacement promises or masquerade as player skips.

Reassessment restrictions travel with existing canonical entry exclusions and are checked again
at client acceptance. Fresh source/cash/eight-slot, member, portfolio, limit and durable-risk guards
continue to authorize every actual replacement. Conditional guidance explains that the eventual
replacement price/quantity can change or be refused. Unknown original deadlines cannot initiate a
guided buy reprice. A healthy working buy is not cancelled just to lower its bid.

## Maintenance and scheduling

Existing exact pooled/subset costs, taxed non-loss floors, original acquisition plans, qualified
stop-loss confirmation and explicit wind-down rules remain authoritative. Routine sell reductions
retain patience, progress, materiality and completed-market support. Supported prices can recover
upward during an independently needed relist or before placement; healthy sells are not cancelled
just to raise the ask. New entries never gain permission from a recovery row.

Within working-offer maintenance, oldest observed no-progress time precedes displacement, with a
stable slot tie-break. Qualified urgent exits and collection retain priority. Final refusal of one
working buy triggers bounded traversal of other exact episodes before unrelated canonical entries.
Ordinary failed workflow writes back off for 30 seconds on monotonic time; urgent stop handling is
not delayed. Held-stock history warming uses oldest fetch attempt first, including failures and
unfetched items, and requests planning even when the entry shortlist is empty.

Replay version 21 shares buy reassessment qualification, original replacement budgets and working
offer ordering. Synthetic execution/price-touch evidence stays separate from account executions;
complete foreground/editor/transport replay parity and controlled trading remain open.

Sale-receipt retries revalidate current policy while preserving the original receipt ID and issue
clock. A retry decision can be newer by at most five seconds; client delivery also checks the
original receipt age, so retries cannot extend its acceptance lifetime. Expired requests return
WAIT and require a fresh request. Receipt/account/source/financial rows are not rewritten.

## Verification and remaining acceptance

Behavioral regressions cover eight-slot coverage during collection, canonical stock versus personal
extras, unseen stock, restart/late-fill cancellation, expired replacement budgets, exact remainder
retirement, changed source/counters/settings, read/write failure, immutable decisions, monotonic
backoff, native Records routing and bounded final-authority fallthrough. Existing loss, tax, risk,
source/duplicate, continuation and account-isolation suites remain mandatory. All four actual
5m/30m/2h/8h cadences are tested separately; these are workflow checks, not GP/hour benchmarks.

Controlled player placement/partial fill/cancel/late fill/collection/replacement or exit/completion
and restart, native recovery-view acceptance and sustained guidance latency still require evidence.
Preserve all earlier unchecked release gates. Do not repair historical gaps or promote learning.
