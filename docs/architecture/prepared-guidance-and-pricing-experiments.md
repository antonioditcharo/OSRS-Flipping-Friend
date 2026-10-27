# Prepared guidance and pricing experiments

Continuation of the owner's approved optimization pass, 27 October 2026. Installed #188 remains
the fallback until this bundle passes paired release. The economic benchmark remains #186.

## Foreground preparation

`CanonicalOfferPrefix` keeps immutable ordered references in 256-entry blocks. Warm append copies
block pointers and the tail block, preserving earlier snapshots and all event occurrences. The
existing canonical account/data-version checks still invalidate every dependent index on external
edits, replacement or rollback. No source is replayed or rebooked.

Outbox getters reread and compare every committed byte before reusing a fully validated private
state. This is not an mtime/size/hash shortcut. Successful replacement invalidates reuse; unsaved
tails still withhold delivery, and legacy upgrades retain their original write path. Existing
cross-process locking, forced atomic replacement, identities and financial acknowledgements stay
unchanged. No mutable state is returned to a caller.

A separate low-priority startup worker prepares at most four recent, existing account databases.
It does not create accounts, adopt the unassigned legacy store or hold the registry lock during
historical reads. Current capture, funds, source, risk and order authority remain fresh.

## Optional prospective display context

Schema-1 `DisplayedPricingContext` captures actual accepted prices/quantities, settings, known
modeled profit, market/forecast versions, reason and up to four planned account allocations. These
are explicitly a bounded account-plan shortlist, not every rejected price alternative. Unknown
profit/model context remains unknown. Configuration attaches once to the displayed instruction.

Accepted/captured ordering and exact account/order/source checks bind this context once to the
first authenticated, unfilled observation. The shared association window remains 600 seconds.
Later callbacks cannot refresh the original context. Its presence does not create a forecast,
financial effect, proven intent, execution sample or learning promotion. Missing legacy context
is not backfilled. Existing financial/learning IDs, risk anchors and ACK semantics remain intact.

## Offline pricing and qualification

The shared common-horizon experiment integrates nine buy and nine sell completion-time quantiles,
partial quantities, exact tax, attendance and a conservative remaining-stock mark. Its exponential
tails, independent legs and linear partial throughput are unvalidated public-flow assumptions.
Reusable cash/slot hours are reported without hypothetical replacement income. Entry replay may
compare the combined constrained account objective without individual metric dominance. Live
services never enable this experiment. The shared sale comparison can describe a loss advantage
but grants no loss permission and does not change active maintenance/receipt family 10.

Public capture tools record actual snapshot/trade/availability clocks and preserve failures. They
never POST account data or substitute aggregate prices for latest quotes. The interrupted initial
capture remains incomplete. New frozen inputs exclude declared tuned files/windows and retain
their evaluator artifact, input hashes and fixed 60/20/20 boundaries. Prior fold receipts detect
changed results. Final testing uses CREATE_NEW and remains unopened if validation fails.

Cadence results are separate at 5m, 30m, 2h and 8h, each with all three retained volume-share stress
scenarios. Unsupported availability/coverage, unknown terminal values, insufficient sales, failed
loss checks or nonpositive held-out gain cannot qualify a segment. Passing simulation is not real
profit superiority or runtime promotion. Long-cohort referenced storage, independently qualified
activation, market-informed loss receipts/reservations and controlled live acceptance remain open.

## Verification boundaries

The hosted #188 synthetic hour completed: 1,000 connected clients, 100 active planners, 72,000
allocation decisions, zero errors, 127.02 ms decision p95. It excludes local financial ACKs and
client rendering and does not replace the mixed-workflow/manual acceptance gate. Repeat against
the final artifact before expanding hosted responsibility. The cold first-account timeout remains
retained until startup preparation is measured on the installed update.
