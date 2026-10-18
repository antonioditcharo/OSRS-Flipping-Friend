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

## Delivery and acceptance gates

The behavioral HTTP fixtures cover delayed acquired exits and entry authority, observed personal
sale maintenance, partial fills during verification, wrong source, cleared offers and pending
financial delivery. Existing exact-cost/tax/unknown-cost/relist/partial/cancel/source/duplicate tests
remain required. Candidate build/CI/install receipts belong in HANDOFF and a separate delivery note.

After installation, the agent reads fresh scoped evidence and timings directly. The player logs in,
opens GE, collects bought stock manually, and checks that a Sell card reaches the foreground. For
repricing, cancellation, collection and replacement placement stay manual. A subsequent partial
fill must never produce an obsolete quantity. Unknown costs/profit and paused historical stock stay
unknown/paused. Only actual player-operated outcomes can close these acceptance gates.
