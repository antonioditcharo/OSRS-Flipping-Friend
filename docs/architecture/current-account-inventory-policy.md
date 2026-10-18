# Current-account inventory and buy policy

Owner decision, 18 October 2026: **Use current-account policy.** Incomplete historical trades remain
in Missed flips and affected stock stays paused. Verified current cash may support unrelated buys.
This explicitly supersedes the account-wide historical uncertainty charge described in the older
durable-risk/history-planning contracts. It is a product risk-policy decision, not evidence that
the old unknown amounts were resolved or that Copilot's private backend uses these rules.

## Financial and entry authority

The original durable risk window, starting capital, financial cursor, source receipts, exact
realized profit/tax bounds and confirmed losses remain unchanged. Restart cannot reset them.
The full prospective historical uncertainty calculation remains present as a separate reported
amount; it does not become recorded loss, profit, current holdings or spendable money.

AccountRiskSnapshot schema 2 identifies CURRENT_ACCOUNT_V1 and separates historical uncertainty
from current unpriced managed-stock cost. Old schema 1 arithmetic remains readable, while schema
and policy validation prevent a new-policy result being silently downgraded. The companion always
calculates authority from its own ledger; a client-supplied canonicalRisk never authorizes entry.

Entry still requires fresh acknowledged cash/eight-slot receipts, known causal scope, consistent
funding, no ERROR slots, supported money, membership, current prices, available slots, cash, buy
limits, diversification and sufficient durable loss allowance. Paused items remain excluded.
Any current nonempty offer in affected stock withholds new entries until finished/collected;
historical tolerance cannot hide uncertain live exposure. Existing finite historical bounds remain
item/group guards and investigation data, never the bankroll or displayed current held capital.

## Physical stock, price gaps and tracking

An additive observedHoldings map carries copied current inventory/equipment/collection quantities,
excluding cached bank contents. It travels with the same cash/slot capture and survives immutable
AccountSnapshot copies. These counts never enroll personal units or widen a tracked position.
Missing legacy physical evidence means no current physical proof, not zero historical ownership.

Risk marks at most the recorded remaining acquisition quantity physically observed now, plus unsold
units in current sell offers. It allocates exact pool cost with SaleAccounting.allocateCost.
Unseen units and units without a fresh supported price receive zero terminal value for entry risk:
their full exact cost is reserved. This can reduce or exhaust the allowance; it never fabricates
a current quote, realized loss, sale, collection or disposal. Future/malformed quotes, scope or
arithmetic still refuse evidence. Exits keep their own fresh-price requirements.

The sell engine retains an unseen purchase record and reports its unconfirmed location instead
of proposing a phantom sale/withdrawal or holding the last slot for it. Current partial buys still
retain their stop/cancel and replacement workflows. Positive observed holdings are capped to the
tracked remainder; pre-existing personal units do not expand it.

Player-started observed sales can receive maintenance despite historical item gaps, with unknown
cost/profit. Authorization binds the exact current authenticated SELLING source, accepted slot
projection, remaining quantity and supported current quote. It never borrows old paused cost.
Contradictory current frames refuse; unsolicited managed exits from paused stock remain blocked.

## Investigation and verification

Holdings displays last confirmed account-scoped purchase records through temporary delivery gaps,
with an updating/as-of notice and the label tracked remaining. Display retention never supplies
the engine's trading/ownership authority and clears on logout/account change. Incomplete history
is not shown as a claim that everything was sold. Holdings profit, pool cost and break-even now
use exact total cost rather than a rounded unit average.

Regression-first tests reproduce historical blocking, missing-price global refusal, unresolved
live-offer acceptance, phantom sale priority, rounded holdings profit and paused manual-sale
maintenance. Behavioral gates cover original loss persistence, final unrelated entry, paused item
refusal, immutable account scope, partial buys/cancel/relist, unknown costs, source/duplicate/order
guards, physical/personal quantity caps and read-only history/financial preservation. Delivery,
installed hashes and player-operated acceptance must be recorded separately in HANDOFF.
