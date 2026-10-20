# Replay exit priority and attended evidence

19 October 2026. `account-replay-8` extends the [v7 continuation model](trade-continuations.md).
This changes the offline evaluator; the installed player pair remains #116. Running the packaged
evaluator needs no RuneLite restart. HANDOFF and the linked receipt record delivery and verification.

Five regressions reproduced these v7 discrepancies against the live decision chain:

- An unrelated outbid buy was cancelled/replaced before collected stock was listed.
- The lowest item ID's healthy sale preceded another inventory holding's fresh stop-loss exit.
- A last free slot could be spent on a ninth buy while an unpriced acquired holding needed an exit.
- An attended visit with no initial action ignored newly arriving guidance until the next visit.
- A partial purchase crossing its stop kept accumulating instead of cancelling before selling.

V8 retains collection and accepted continuation priority, then examines canonical unlisted stock
before routine maintenance or new entries. A healthy active purchase is left intact; a fresh CUT
first cancels that same purchase. It does not sell around the working buy. Existing replacement
promises retain their prior semantics. Inventory exits rank cuts before winners, then expected
profit, matching those tiers in the live selector. Visible unlisted stock reserves the last slot;
a ready exit with no slot may cancel the largest remaining buy reservation, never another sale.
An additional sell listing must clear the live remainder-profit rule using exact allocated cost.

Routine maintenance orders both sides by proportional distance from the market, with a stable
slot tie-break, rather than processing every buy before any sale. Shared buy/sell quote policies,
patience, completed market support, original target context, exact taxed floors and final companion
sale authority still govern. The unconditional replay-only buy-maintenance prepass is removed;
the live identity-safe cancellation selector does not supply an independent higher-priority action.
Stabilized sale reductions must still clear the live deadband.

While a player is within an explicit non-missed visit, a newly published market/flow frame can
produce a display even when the previous decision was WAIT. An unchanged order retains its original
display clock and expiry. A changed or expired order is withdrawn and requires the complete manual
action delay again. If that delay does not fit the visit, no action occurs. A missed/finished visit
cannot be reopened by market data. Restart still withdraws pending instructions and preserves
canonical quantities, original context, financial effects and persisted continuations.

The new tests exercise multiple holdings, stop/winner ordering, mixed maintenance, the last-slot
case, evidence arriving during attendance versus absence/missed visits, a partial-stop restart and
quiet old prices retaining both stock cost and locked cash without a disposal. Existing delayed
action, expiry, continuation, original-context and four-cadence bundle checks remain required.

These are logical display/action and synthetic conservation tests. Real client-thread/editor/
transport acceptance, further source/feature/attendance parity and qualified chronological market
benchmarks remain open. No actual-fill, superior-return or validated-learning claim follows.
PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION, unknown costs, historical gaps, risk reserves and
the live account's original risk anchor remain unchanged.
