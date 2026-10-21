# Recorded sale disposition of buy continuations

20 October 2026. Extends [exit profit and replacement](exit-profit-and-replacement.md).

A cancel/collect rebuy promise can outlive a completed sale when that sale was collected before
the next worker pass, or before the guard was installed. Absence of stock cannot resolve it.
The Dual macuahuitl investigation found an exact two-unit saved remainder after a later recorded
sale of the three acquired units. Personal source details remain in ignored local-data.

The scoped ledger now supplies one latest proven sale placement per item, independent of its
recent hundred sales. Qualification reads existing DISPOSE rows and their exact canonical sources;
it does not apply a reducer to financial history or write accounting. Item, offer identity,
observation time and positive quantity must match the recorded financial row. The original and
sale sources must be authenticated, have a stable first-seen time, and have no recorded rejected
source in their episode. Missing, foreign, inconsistent or future evidence cannot retire a hint.
Validated-prefix, external-change, appended-tail and rollback fences protect the cached read.

Before considering replacements, the client consumes a fresh acknowledged account ledger. Only
a collected buy continuation with a distinct sale episode first seen strictly after both its
original buy and saved promise qualifies. An old personal listing filling later is insufficient.
The account's forced atomic continuation document retains the whole original hint and exact sale
source in retiredContinuations. Unrelated hints survive. A failed write withholds the old remainder
and retries across refresh/profile changes; restart requalifies against the durable ledger. Retired
originals cannot be re-advised. Changed files are preserved instead of overwritten.

Local tracking also checks retained collected sale receipts, including cancelled partial sales.
The check follows collection acceptance within the same pass, closing the race between collection
and later-sale observation. It does not require a current market quote.

Retirement affects workflow promises only. It does not dispose remaining units, enroll personal
extras, clear historical gaps/exposure, infer acquisition cost, calculate new profit, restore cash,
reset risk or authorize an order. Exact accounting, original plans, CURRENT_ACCOUNT_V3 and final
source/funding/slot/buy-limit authority remain unchanged. Rejected or unknown evidence stays unknown.

Behavioral regressions cover exact source qualification, collection/restart, more than a hundred
later sales, original-placement ordering, rejected/changed/foreign sources, rollback, stale/future
ledgers, failed durable retirement, account switches, unrelated hints and re-advice. Controlled
player cancel/late-fill/collect/replacement or partial-exit acceptance remains open.
