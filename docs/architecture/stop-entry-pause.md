# Stop rechecks and additional purchases

24 October 2026. Read `local-data/stop-entry-pause-20261024/delivery-state.json` for actual tests,
CI, installation and acceptance. This continues the [confirmed-loss contract](confirmed-stop-loss.md).

Two behavioral failures reproduced against installed PR #157: a collected cancelled-buy remainder
could become BUY when the original acquired stock's fresh price crossed its stop but completed
history was unavailable; final authority could also approve a cached READY entry in that item.
Insufficient evidence to realize a loss must not become permission to increase the same exposure.

A positive high-side public trade at most 120 seconds old, at/below a still-possible original
acquisition stop, now pauses additional purchases of that item. This pause grants no loss-sale
authority. A fresh recovery above every applicable recorded stop releases it; exhausted origin
ranges or fully disposed stock cannot pause later trades. Missing original context cannot invent
a stop. Mixed possible origins can pause additions using a known origin without authorizing a
liquidation of other/unknown origins. Future/stale quotes cannot create this signal.

Canonical planning excludes affected items; final ordinary-entry and capped-replacement authority
independently recheck even previously READY plans. The client retains the exact collected remainder
and checks other candidates. It cannot promise a replacement reprice while this addition is paused.
Existing buy-limit, overdue-abandonment, confirmed-stop and player wind-down paths remain separate.
Working offers are not cancelled merely by this new signal. An independently eligible non-loss sale
remains possible through the existing exact-source/final-authority workflow.

The restriction uses current account canonical remaining acquisitions and their original prospective
contexts. Client item plans, personal belongings and historical item appearances cannot create it.
It is merged with existing unavailable-item guards; it changes no risk reserve, loss budget, cost,
quantity, source event, original acceptance, accounting row or gap. Unrelated verified-cash entries
remain eligible under their existing guards. Recovery neither disposes of stock nor resets risk.

Replay uses the same restriction (`account-replay-20`). Sale authority remains `companion-sale-10`;
entry protocol stays compatible, with `RISK_CONSTRAINT / ENTRY_STOP_RECHECK` for the new refusal.
Tests cover refusal, exact continuation retention, fresh recovery, unrelated entries, merged guards,
unknown/possible/exhausted origins and unchanged canonical costs/effects. Controlled player
full-cycle/restart acceptance, sustained latency, qualified cadence benchmarks and learning remain open.
