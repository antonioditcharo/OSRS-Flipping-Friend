# Release readiness

14 October 2026. This is the ordered completion plan for a publishable RuneLite assistant.
The [handoff](../HANDOFF.md) holds actual revision, installation and test receipts; historical
package completion does not close these gates. No full parity or profit superiority is claimed.

| Gate | Current evidence | Remaining acceptance |
| --- | --- | --- |
| Supported client and installation | 1.13.2 candidate builds and passes isolated official cached-artifact checks; installer release preflight has regression coverage | Exact-head Windows/Linux CI, merge, fresh reviewed backup, paired installation, real compatibility/status and player login |
| Complete manual player workflow | Compact sidebar, automatic trade scope, selected personal sale quote, Missed flips, walkthrough/Confirm guard, search suggestion and one-prompt key fill are merged through #43 | Player-operated UI/focus/scaling checks, small intended buy/sell, partial cancel/collect/relist, pause/context changes and restart; record actual pipeline output |
| Accounting and one source of truth | Scoped durable events, proceeds/cost/tax bounds, duplicate/recovery tests, Performance and managed trade quantities implemented | Resolve observed reconciliation cases from evidence without database repair; finish calibration/session-risk authority and audit remaining local consumers |
| Reliable recommendations | Entry and buy-maintenance policies have tests and exposure guards | Complete sell/replacement authority and degraded-data behavior end to end; validate real readiness without bypassing unresolved exposure |
| Reproducible performance | Copilot exports imported with preserved uncertainty and coverage limits | Prospective session clocks and chronological whole-account replay: 100M members, eight slots, separate 5m/30m/2h/8h visits, limits/tax/losses and ending inventory |
| Learning that earns promotion | Real-fill timing calibration exists; simulated touches remain separate | Frozen baseline, chronological held-out challenger evaluation, return/loss/calibration limits and rollback before any promotion |
| Public distribution | Public source, BSD-2-Clause license and cross-platform build workflow exist | Follow current official RuneLite Plugin Hub submission/review requirements; resolve packaging, companion setup/update and input-assistance review before claiming approval |

Work in this order: restore the supported live client, finish observable workflows and accounting,
then measure/tune strategy. Do not add an unvalidated learning service or a new position ledger to
paper over a blocked live account. Routine personal supplies remain outside managed stock without
classification. Unknown cost/profit stays unknown, including manually initiated personal sales.

The [official Plugin Hub instructions](https://github.com/runelite/plugin-hub#creating-new-plugins)
are the publication reference. A working sideloaded build is not Hub approval. The owner-authorized
key fill remains confined to one open matching numeric prompt per player press; Enter, Confirm,
placement, cancellation and collection are manual. Competitor behavior is not approval of this
implementation. The agent never sends input to the real game.

