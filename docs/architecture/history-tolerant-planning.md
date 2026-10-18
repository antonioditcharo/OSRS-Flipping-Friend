# Historical uncertainty and current buy eligibility

Superseded policy decision, 18 October 2026: see
[current-account inventory policy](current-account-inventory-policy.md). The owner chose to retain
historical uncertainty separately from current loss allowance. The calculations and original data
described below remain evidence; they no longer impose an account-wide historical buy block.

18 October 2026 UTC. The owner prioritized graceful incomplete-history handling. Unknown history
remains visible and is never assigned profit, ownership or disposal merely to obtain a buy.

## Distinguish history from newly exposed capital

The lifetime historical exposure envelope remains unchanged. It continues to pause affected items
and constrain their item/group exposure. Missing scope, current ERROR slots, contradictory funding,
overflow and missing/stale cash/eight-slot evidence still withhold new entries.

The prospective risk calculation previously reused that lifetime envelope for every new gap in
the same item. Thus 100M GP of old completed turnover plus a new 100 GP gap could create a new
100M GP loss reserve. It also treated an unpriced sale of stock already quarantined before the
window as a new managed loss, although that stock was excluded from proven starting capital.
Conversely, fresh positive-cost purchases of an already-paused item could escape the risk reserve.

`RiskLedgerEvidence` now captures clean opening acquisition costs at the original durable financial
cursor, then tracks only positively observed new acquisition cost for this risk window. Priced
disposals retain their original profit ranges and applied cost. Old unpriced stock does not become
new managed loss. Unpriced sales of newly exposed stock still reserve cost; ambiguous mixed stock
reserves the entire newly exposed pool once. A new purchase of a paused item adds its actual known
cost to that reserve. Repeated sales cannot erase it or repeatedly enroll historical belongings.

`ProspectiveGapExposure` finds the exact eight authenticated opening source IDs in the retained
source order. It includes clean opening cost, the full funding envelope of opening active buys,
and full funding for subsequent possibly distinct buy episodes. Same-episode increasing counters
do not recount funding. An intervening EMPTY separates envelopes even when ambiguous; reappearance
cannot be assumed to be a replay. Missing/foreign/malformed anchor sources refuse the bound.
Neither a timestamp nor raw cumulative maxima establish placement, remaining stock or known cost.

New-gap reserve uses that prospective bound rather than lifetime item turnover, taking the larger
of it and the qualified unpriced-cost reserve. No sale proceeds, current cash increase, personal
classification or mere container absence reduces a gap envelope. The original risk window/cursor,
recorded losses, gaps, event identities, tax/profit uncertainty and accounting rows remain intact.
There is no schema change, migration, financial writer replay, risk reset or optional bypass.

## Product behavior and evidence

Unrelated buy entry can pass the actual final companion authority with retained scoped historical
gaps, a finite exposure envelope, fresh account evidence and sufficient remaining loss budget.
Incomplete history by itself is not a global buy switch. A genuinely exhausted current risk budget
still refuses entries. Source verification does not establish a live eligible buy.

Missed flips remains a separate read-only window. It now filters by item, reason, date and exact
source/offer identity. Unknown money stays unavailable; the footer distinguishes update quantities
from current inventory. No repair, classification, adoption or dismiss-to-enable-trading control
is provided.

Regression-first tests reproduced three incorrect states before the change. Behavioral tests cover
old quarantined sales, lifetime turnover versus a small new gap, actual final unrelated entry,
new purchases of paused stock, repeated counters/ambiguous new episodes, missing/foreign source
receipts, large new reserves, persistent losses and restart. Existing transaction, tax, account,
stale-price, current-source and SQLite work-budget gates remain enabled.

The read-only candidate check against the explicitly scoped account has valid eight-slot evidence
and finite historical scope, but its prospective uncertainty reserve still exhausts the allowance.
No real account was changed to alter that result. Exact private diagnostics remain under
`local-data/parity-evidence-20261017/`. The owner's separate policy clarification is pending;
no change to the reserve guard is inferred from silence or a request to explain Copilot's behavior.
The inspected Copilot client separates missed categories from normal statistics, sends current
bag/bank/GE state and excludes items awaiting synchronization. No client-wide historical reserve
breaker was found; its private server risk rules are unknown. These facts do not establish that
its server ignores unresolved capital or authorize clearing Friend's existing reserve.
See HANDOFF for CI/install/acceptance.
