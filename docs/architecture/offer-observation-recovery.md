# Recover current offer tracking without rewriting history

14 October 2026. Source implementation; installation and live acceptance are separate gates.

## Observed problem

Read-only diagnosis found fresh account cash and eight free slots while six durable slot
projections still retained older ERROR_RECONCILIATION states. The reducer had no transition
out of that state, so subsequent real observations and fills could never reach accounting.
Repeated partial-fill observations also consumed the cumulative filled quantity again in the
durable buy-limit projection, including when only the delivery event ID changed.

These are distinct from missing historical executions. A currently empty slot proves that the
slot is free; it does not prove the old offer's final fill, collected coins, remaining holdings,
acquisition cost, disposal or profit.

## Observation contract

OfferEvent has an additive `authenticatedObservation` boolean, default false for old payloads.
Only the plugin's existing copied LOGGED_IN client-observation path calls
`publishObservedOffer` / `publishObservedOfferCleared`. Account refresh and cached local offers
keep using ordinary publication with this flag false. Existing account/profile/generation
fences, event UUIDs, persistent outbox session, sequence and exact acknowledgements remain.
The flag survives account scoping, serialization, restart and replay; replay is not fresh proof
by itself. This is producer provenance inside the authenticated account API, not a new security
credential or permission to send game input.

A flagged observation requires a valid account, slot 0–7, positive time/sequence and event/session
identities. Nonempty slots require coherent item, price, identity, state and counters; an empty
slot requires zero item/price/quantity/spend and no offer identity. It must have a newer sequence
in the same known durable session and a non-regressing timestamp to recover an ERROR projection.
Foreign sessions, stale events, malformed observations and cached/legacy events cannot recover it.
Same-offer recovery cannot reduce counters or change side/item/price/size.

Rejection retains the economic counters and identity while advancing only a newer same-session
ordering cursor. It cannot move that cursor backwards or adopt another session. Flagged stale
observations cannot roll an empty/current slot back to an older offer. A rejected observation
still requires fresh proof; the change does not silently accept a stale event.

## Accounting, limits and learning

Recovery appends the normal event and advances the current slot inside the existing accounting
transaction. Empty recovery has no economic effect. Nonempty recovery establishes a cumulative
baseline with RECONCILE evidence; it never books the baseline as an acquisition or sale. Later
valid fill deltas can be recorded once. Event/projection/accounting/limit failure rolls back
together, and duplicate event UUIDs remain no-ops across restart.

The original gap and all old sale/cost/tax/profit records remain untouched. Missed flips retains
the incomplete evidence. Current slot recovery does **not** clear unassigned or item-level trade
uncertainty, old known-cost conflicts, or the planner's exposure guards. It does not establish
that old stock was sold or allow new buys on this currently blocked account.

Buy-limit accounting uses increases in a matching offer's cumulative filled quantity. A first
filled observation still consumes the observed allowance once even when cost accounting is
unresolved; repeated counters do not consume it again or renew an expired four-hour window.
A fresh delta after expiry starts a new window. Arithmetic overflow aborts the transaction.
Existing limit rows are never recalculated or repaired by this change.

A recovered sell does not inherit another offer's placement-tax proof. Offers with recorded
RECONCILE evidence cannot become complete execution-learning samples on a later terminal replay.
Existing recorded learning statistics are preserved; this change does not promote a new strategy.

## Verification and remaining work

Behavior tests cover authenticated/cached publication, login/logout/profile fences, stale/session/
slot/counter rejection, missing-terminal recovery, initial partial baselines, later deltas,
duplicate delivery, restart, transaction rollback, tax provenance, learning exclusion and four-hour
limit expiry. All stores and game observations in these tests are disposable/synthetic.
No test or agent sends an offer, synthetic snapshot or recovery event to the live service.

Positive, bounded historical exposure reconciliation is still needed before unrelated planning
can resume safely. Loss of the durable outbox session, legacy identity gaps, timestamp/offer-ID
ambiguity and full 64-bit unit prices still abstain; this slice does not invent evidence for them.
One account ledger for all risk/calibration consumers, complete companion maintenance policies,
account-level chronological evaluation and player-operated acceptance remain release gates.
