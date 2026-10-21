# Trade-plan and maintenance delivery

Read `local-data/trade-plan-maintenance-20261020/delivery-state.json` for actual verification,
CI, merge, installed artifacts, backup and remaining acceptance. [Contract](../architecture/trade-plan-maintenance.md).

#133 merged as 6f1634f394befdd68556c1fb892c2249f7f00f1f after both exact-head checks. Its installation
is grouped with this runtime batch, avoiding an intermediate paired restart.

Reproduced causes: equal-second advice lost its exit context; ordinary partial-fill replacement
made mixed plans disappear; missing stops were recalculated from current risk settings; a changed
sell-editor quantity could receive a generic unprotected quote. Maintenance additionally had a
full-horizon target veto, no bounded stalled-buy review, progress-insensitive buy repricing and
non-improving stabilized buy quotes. Companion/client default horizons disagreed.
Deadline commits are isolated from execution snapshots, and account changes immediately withdraw
displayed and hidden price authority. Same-second accepted forecasts can time maintenance without
changing historical learning qualification.

The fixes preserve financial/event identities and exact pooled costs. Mixed remaining origins are
ranges, never assigned lots. The three reported bludgeons' old original values are unavailable;
their recorded disposal is preserved and no historical repair is claimed. The bolts' separate
original contexts remain readable without replaying money. Personal extras remain personal.

The owner selected earlier supported profitable sale reductions. The source receipt follows
cancel/late fills/collect/relist and is independently checked at final companion authority. Current
prices, exact subset floors, physical/source/account guards and CURRENT_ACCOUNT_V3 remain mandatory.

Frozen forecast evidence is at `local-data/trade-plan-maintenance-20261020/frozen-forecasts/`.
Its original receipt identifies an integration-build artifact, not an installed clean-head runtime.
At cursor 92121 it retains 269 observations, 173 eligible and 96 unqualified. The 53 qualified sell
claims average 92.23% modeled completion within legacy 3h; observed completion is bounded at
49.06–81.13%, retaining 17 ambiguous cases. Buy cancellation coverage prevents a comparable general
calibration claim. Exact configuration/order groups remain separate in the full audit. No multiplier,
training, learning promotion or superior-return claim follows from these observations.

Controlled manual full-loop/restart acceptance, sustained latency, complete production benchmarks
and validated forecasting remain open. Login and every game action remain manual. No financial
replay, data repair, invented disposal, risk reset, forced client termination or agent game input.
