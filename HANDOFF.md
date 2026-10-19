# Agent handoff

Updated 18 October 2026 (America/Anchorage). Read [AGENTS.md](AGENTS.md) and [TASKS.md](TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

## Current source and installed runtime

Owner-approved #101/5260586 is INSTALLED. Quiet stock can use qualified completed public price/
volume support, a 10% price haircut/tax and quantity limits; quiet marks cannot credit gains.
Direct bids retain 120s; fallback trades must be within 15m. Missing/unsupported stock remains
fully reserved, with original accounting/anchors/confirmed losses and final guards intact.
Windows clean 121s: 1,680 Java passed/six skips, Java 11; 108 tooling tests pass. Both exact-head
Windows/Linux checks pass (5ec358e, run 39214094448). Verified backup 20261018-231405-0f47deda,
installed hashes, byte-identical task XML/SDDL/lifecycle=1, all 47,672 frozen rows/1,404 financial
effects/original anchor and status/compatibility/scoped GET checks pass. Supported Start reopened
RuneLite 1.13.2. Fresh authenticated eight-slot proof and live qualified quiet-stock valuations
pass; retained READY plans precede the current NO_FREE_SLOT/eight-occupied outcome. The same frozen
live evidence gives a negative allowance under #98 and a positive allowance under #101 without
financial changes. Over-15m unsupported quotes still retain full reserves. Final eligible entry,
sustained/owner UI and native-window acceptance remain pending; no gameplay was automated.
Contract: docs/architecture/qualified-stock-risk-marks.md;
receipt: docs/history/qualified-stock-valuation-2026-10-18.md.

#98's valuation explanations, prompt rechecks and native maximized Records/Missed flips frames
remain included. Its strict 120s-only fallback is superseded by the approved #101 policy above.
The owner reproduced a prolonged valuation pause after #98; that is not accepted sustained buy
availability. Native-window acceptance remains pending. Earlier verified installation/CI receipts:
docs/history/buy-readiness-windows-delivery-2026-10-18.md.

Merged #96/1ebe4f8 adds the wide native market graph, independent
public-history cache and experimental historical-return projection/evaluation. It is INSTALLED
with the accepted #94 sale fix. [Contract](docs/architecture/market-graphs.md), root design-qa.md
and [verification receipt](docs/history/market-graph-delivery-2026-10-18.md) record native QA,
1,653 Java passed/six skips, 108 tooling tests and reproducible public-only evaluation. All four
tested price horizons have worse known-target MAE than persistence; scenarios remain display-only.
Both exact-head CI checks passed (run 39190129423, source f1f0ce8). Paired update 02:37:56 UTC
used VERIFIED backup 20261018-183753-28a992cf; its delivery checks passed and it is now included
in the installed #101 pair above. Fresh graph/guidance acceptance is pending. No Copilot all-users
feed/private production dependency.

The combined sale fixes through #94/c8d3a18 remain INSTALLED in the #101 pair.
Quiet last-trade prices no longer strand verified unlisted stock: shared initial/relist pricing
provides an explained exact non-loss limit; stops and live sell cancellation still need fresh trades.
Current physical quantity, source, exact costs, scopes and TTLs remain checked. Current-account policy
from #88 remains active: historical unknown
money stays separate/affected stock paused; verified current funds support unrelated buys while
confirmed losses, unknown live exposure, cash/slots/limits and the original risk anchor still govern.
[Policy](docs/architecture/current-account-inventory-policy.md).

Installed fixes cover sale-read timeouts, retained authenticated sell identities, post-ACK partial-
fill source races, one consistent sale read, compatible displayed sell prices, and account/logout/
clock price fencing. Exact taxed floors and final source/quantity/price guards remain active.
[Contract/player steps](docs/architecture/sale-guidance-delivery.md).

#94's own build/CI, backup, financial preservation and normal-user lifecycle receipts remain in
[its delivery](docs/history/quiet-market-sale-delivery-2026-10-18.md) and
[earlier attempts](docs/history/sale-guidance-delivery-2026-10-18.md). Current pair identities and
verification are in the #101 receipt above. No elevation, task/ACL change, financial repair or game input.

The owner reproduced continued waiting on #91; fresh GETs identified SALE_QUOTE_STALE. Identical
frozen evidence changes WAIT to PLACE_SELL under #94 without accounting changes. Supported Start
reopened the new pair; owner confirms the fresh Sell-card check "Looks good." Post-update evidence
shows the ring sale and a two-unit acquired position listed, with fresh authenticated eight-slot
proof. Initial sell guidance is accepted; complete partial/cancel/relist/restart acceptance remains open.
Owner-confirmed #88 buy-guidance
availability and #86 Missed flips stability remain accepted, not the broader controlled trading
workflow. [Earlier policy delivery](docs/history/current-account-delivery-2026-10-18.md) and
[history display](docs/history/history-display-delivery-2026-10-18.md).

#83's investigation/graphs/market watch/alerts/evaluation paths remain installed. Replay v4 retains
shared live display-price stability/quiet-stock pricing and adds explicit aggregate clocks for risk.
It reproduces twelve synthetic 100M members/eight-slot
5m/30m/2h/8h cadence/share scenarios; retained older evaluators still verify their own bundles.
Complete replacement/relist/foreground coverage and qualified market benchmarks remain open.
[Product/deferred features](docs/architecture/parity-account-evaluation.md).
The earlier model's validation has zero paired Brier improvement. The fit from 20 eligible forecasts
now has a later 25-forecast cohort (13 known/12 uncertain): Brier-change bounds -0.0217 to +0.0125
still permit degradation. It remains inactive/HOLD; uncertain outcomes were retained.
[Frozen evidence/model receipt](docs/history/prospective-evaluation-2026-10-18.md). Runtime remains
public-price baseline/HOLD; promotion/rollback/drift and broader replay/measurement gates remain
open in TASKS. No full-parity, account-profit superiority or validated-learning claim is established.

## Routine updates

Use tools/apply-update.ps1 -Unattended from the clean primary checkout after both CI checks pass.
The verified process lifecycle handles companion maintenance without admin/INSTALL replies; leave
protected task XML/permissions unchanged. The optional #76 ACL repair was never applied and is not
needed. Never force-kill RuneLite or script game input. Login and all GE actions remain manual.
Read [the workflow](docs/development-workflow.md), [lifecycle contract](docs/architecture/user-process-updates.md)
and [first normal-user delivery](docs/history/normal-user-update-2026-10-16.md).

## Missing sells, independent evidence and latency

Keep [#70](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70) open. Prevention does
not repair old affected quantities/costs. A nine-unit offer has 11 acquired units in affected rows;
aggregate positions are not safe fallback holdings. Preserve original financial rows, unknown
cost/profit, historical gaps and the durable risk window. No database edits, financial replay,
adoption, outbox clearing or risk reset may be used to activate advice.

#73/cb25c1f audits a complete verified frozen source prefix without opening a database or client.
The retained owner bundle has 11,598 sources/cursor 13,221; 35 observed identities/23 with flags.
Audit/wrapper/retained-artifact verification all exit 0 and authorize neither accounting nor trading.
Raw maxima and unflagged identities are not current holdings or known costs. Complete hashes and
flag receipts are in [history](docs/history/handoff-through-2026-10-16-risk-pauses.md).
Private bundles: primary local-data/execution-evaluation/sell-gap-evidence-20261016 and
local-data/trade-audits/sell-recovery-20261016. The exploratory terminal-envelope calculation is
not a validated recovery policy or permission to reduce a reserve.

The already retrieved independent Copilot owner history contains 17,829 unique transactions,
latest 15 October 02:17:25 UTC. Only 16 overlap the retained Friend range; 3,037 nonempty Friend
observations are later. It cannot close the affected current-stock evidence gap. Raw response,
decoded rows/provenance manifest remain private in local-data/copilot-corroboration/20261016-093739.
No credentials were logged, other-account transactions retrieved, or records imported. Do not
repeat this export without new evidence; coverage, exact tax/fill timing and profit remain unproven.

At 10:07 local, authenticated portfolio/action/ledger/learning reads take 1,062/79/1,765/703ms;
outbox zero; ledger 11 sale updates/eight unresolved. Plan COMPANION_STATE_UNAVAILABLE has no
canonicalRisk, so earlier negative-budget results are historical, not this read's current result.
Installed native SQLite 3.45.3 uses an ordinary event_log scan; no index change is indicated.
Isolated read-only ledger probe: 753ms cold, 194/165ms warm, valid/same counts/zero eligible stock.
These are standalone JVM timings, not full HTTP acceptance. Private diagnostics and probe code
remain in primary local-data/diagnostics and .gradle. Sustained latency/final entry acceptance remain open.

## Remaining work and source map

The [17 October Copilot investigation](docs/research/flipping-copilot-2026-10-17.md) retains the installed
JAR/decompilation, complete client/protocol reports and current parity/learning matrix in ignored local-data.
One live price response matches the installed decoder; private backend/model internals remain unknown.
The research itself changed no Friend runtime code or ledger data; the implementation candidate
is described above. Trading and learning gates remain open.

[TASKS.md](TASKS.md) preserves every unchecked release gate. Finish safe evidence-based stock/risk
resolution, companion sell/replacement authority and the ledger consumer audit, then controlled
player-operated buy/collect/sell, partial-cancel/relist/restart and search/key/pause acceptance.
Complete chronological account replay and gated learning (#56); runtime learning remains the public
price baseline with promotion held. Benchmark 100M members/eight slots separately at 5m/30m/2h/8h.
Historical CSVs have no session/cadence labels; higher GP/hour remains unmeasured.

Active contracts: [trade scope](docs/architecture/automatic-trade-scope.md),
[gap scope](docs/architecture/trade-gap-scoping.md), [current planning](docs/architecture/bounded-current-planning.md),
[episode identity](docs/architecture/offer-episode-identity.md), [startup](docs/architecture/startup-offer-evidence.md),
[durable risk](docs/architecture/durable-risk-window.md), [ledger](docs/architecture/durable-profit-ledger.md),
[offline audit](docs/architecture/trade-evidence-audit.md), [compact UI](docs/architecture/compact-panel.md)
and [manual assistance](docs/architecture/trade-input-assistance.md).

Full prior receipts are retained in [task-access history](docs/history/handoff-through-2026-10-16-user-task-access.md),
[risk history](docs/history/handoff-through-2026-10-16-risk-pauses.md),
[source-audit history](docs/history/handoff-through-2026-10-16-source-audit.md) and
[earlier history, including PR #31's checklist](docs/history/handoff-through-2026-10-15.md).
