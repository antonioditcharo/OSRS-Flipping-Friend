# Sale verification and delivery

18 October 2026. After installed #88 restored buys, the owner reported "Waiting for sale verification"
on Dragon knives, a Masori helm and another item. This is not accepted sell-workflow behavior.

## Reproduced causes and changes

The installed account's read-only sale request exceeds the client's 750ms transport budget: two
Java HTTP attempts time out, while the longer-budget request returns in 1,955ms. GETs now allow a
bounded 5s read, including final entry/sale and ledger reads; connection and mutation/offer-delivery
budgets remain unchanged. Work stays off the client/UI threads. A longer transport budget never
extends quote, account, decision or displayed-advice validity. Sale responses are checked again
against their receipt time and current acquisition/offer source.

The canonical client cache previously retained only active attributed buys. Authenticated SELLING
observations now retain their exact event/episode identity too, including player-started sales with
unknown cost. Buy maintenance still selects only eligible buys. Terminal, EMPTY, rejected, logout
and account-change observations withdraw authority; pending acknowledgements still prevent advice.

A newly acknowledged partial fill could also replace the source while a request was running.
Drained delivery alone is not sufficient: the response must still match the latest retained source
and remaining quantity. Regression-first coverage reproduces acceptance of the obsolete source and
now requires WAIT even if that newer frame was acknowledged during the request.

The companion reads planning projections and its ledger from one SQLite read transaction, reusing
the complete scope calculation within that operation. It avoids the duplicate full-history scan
and mixed source prefixes. No result is cached between requests. A concurrent progress fixture
proves that both halves see the old prefix together, and the next read sees the new prefix together.
Failed reads roll back; verification cannot book an execution or alter financial rows.

Initial cold-history waiting now says "Loading item history" when no tactics exist and candidate
history is missing. It retains WAIT and does not turn an eligibility/refusal decision into a buy.

## Sale price stability

A separate production regression shows a 1% market move retaining the client's old sell price
under its 2% display tolerance, then failing final sale authority's existing 0.5% drift check.
The shared AdvisedPriceTracker keeps sell prices stable only inside that existing authorization
budget. Small noise remains stable; the six-hundred-second expiry and original buy tolerance are
preserved. Final exact taxed cost floors, source/quantity/quote checks are unchanged. Account
changes, logout and backward clocks cannot retain a price from another observation.

The chronological replay uses the same price tracker for exits/maintenance and clears its display
cache on restart. Replay policy version 2 distinguishes these changed action semantics; retained
version 1 bundles must still be verified with their original evaluator. This closes the display
stability coverage gap only. Complete production replacement/relist intent and foreground timing
coverage, qualified public market captures and four-cadence account-return benchmarks remain open.

## Delivery and acceptance gates

### Quiet-market exits (18 October continuation)

The owner still saw indefinite verification after the combined update. Authenticated GETs identify
`SALE_QUOTE_STALE`: the account and market snapshot are current, but the last trade on one side is
older than five minutes. Applying the entry/maintenance freshness rule to already acquired,
unlisted stock contradicts the exit workflow. A regression first reproduces PLACE_SELL becoming WAIT.

`SellTimingEngine.evaluateManagedExit` now supplies the same initial/relist pricing to the client,
companion and replay. Fresh trades use the existing exit strategy. Quiet trades use the greater of
the last recorded sell-side price and the exact full-pool taxed non-loss floor. This is a supported
limit price, with visible trade ages and no promised fill, rather than a claim of executable value.
It does not update the ledger, risk marks, realized profit or stock location. Missing/future prices,
unknown cost and an impossible legal floor still withhold that limit. Even sell-only mode cannot
use quiet evidence to cut a loss. Stops and cancellation/repricing of an existing sell still need
fresh trades; manual unknown-cost relists retain their existing freshness requirement.

Final authority also requires the requested units in the current physical holdings capture, capped
by canonical acquisitions and unlisted quantity. Exact source, scope, account/feed/decision TTL,
pending-ACK and foreground-generation fences remain. Smoothing rechecks the exact taxed floor for
quiet exits even in sell-only mode. Refusals explain whether repricing, purchase records, physical
stock or price need refreshing, and retain a diagnostic reason instead of one indefinite message.

Replay version 3 applies this same quiet-stock rule; old bundles require their retained evaluator.
This adds no simulated or measured performance claim. Complete production replacement/relist intent,
foreground timing, whole-account benchmark and learning-release gates remain open.

The behavioral HTTP fixtures cover delayed acquired exits and entry authority, observed personal
sale maintenance, partial fills during verification, wrong source, cleared offers and pending
financial delivery. Existing exact-cost/tax/unknown-cost/relist/partial/cancel/source/duplicate tests
remain required. Candidate build/CI/install receipts belong in HANDOFF and a separate delivery note.

After installation, the agent reads fresh scoped evidence and timings directly. The player logs in,
opens GE, collects bought stock manually, and checks that a Sell card reaches the foreground. For
repricing, cancellation, collection and replacement placement stay manual. A subsequent partial
fill must never produce an obsolete quantity. Unknown costs/profit and paused historical stock stay
unknown/paused. Only actual player-operated outcomes can close these acceptance gates.
