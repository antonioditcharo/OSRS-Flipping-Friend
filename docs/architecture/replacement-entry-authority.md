# Entry authority for replacement buys

17 October 2026. Replacing a cancelled offer spends available cash again. The old reservation
does not authorize a new order after cash, limits, holdings, loss allowance or settings change.

Regression 40a45d5 exercises the actual queued plugin refresh and client-thread delivery. Three
tests fail before the change: a local replacement bypasses companion refusal, the locally computed
order replaces the actual authorized order, and unrelated/oversized orders are not rejected.

Any BUY returned by the local recovery engine now passes through the existing active companion
entry boundary. The plugin temporarily excludes other allocated items while retaining player skips,
blocks and live-offer exclusions. These temporary exclusions do not alter saved preferences.
The companion still selects the allocation and performs final current-account/read-set, risk,
buy-limit, eligibility, market and optimizer validation. There is no local fallback on refusal or
transport failure. Existing client account/generation/settings delivery fences remain in place.

The final authorized order must match the proposed replacement item and must not exceed its
current proposed unfilled quantity. A smaller supported allocation is allowed. Price, quantity,
profit, target and forecast are taken from the actual authorized order; the plugin never clips a
candidate and reuses the original forecast. Missing/unrelated/oversized advice produces WAIT.
An explicit companion WAIT retains its reason. The old quote is not financial authority.

The runtime local engine no longer writes a replacement exit plan before companion authorization.
The authorized BUY supplies its plan, and an ordinary entry WAIT no longer writes an item-minus-one
plan. These are guidance hints, not executions or proof of user intent. Prediction binding still
occurs only after existing client-thread guidance acceptance. No accounting, risk window, database,
event identity or learning keys are changed or backfilled.

Regression b374b5a additionally reproduces two actual walkthrough/input-assistance failures:
an entry refusal was deferred behind an already open buy editor, and a queued player-key fill
survived refusal followed by the same buy returning. WAIT now immediately withdraws BUY/MODIFY_BUY
guidance, clears deferred advice/editor progress and advances the advice generation to cancel
queued key work. A refusal cannot retain a confirming highlight for the old buy. Different
actionable trade advice retains its existing mid-entry deferral behavior. Tests use synthetic
keys and mocked widgets; the agent never sends input to the real game.

This activates the existing entry policy for replacements; it does not activate the old replacement
candidate scaffolding. A fresh allocation larger than the remainder waits rather than being resized
without a new forecast. Exact remainder-constrained server candidate construction, durable buy
replacement intent through restart and complete manual workflow acceptance remain unfinished.
Sell-maintenance/exit policy consolidation also remains open. No game input is automated.
