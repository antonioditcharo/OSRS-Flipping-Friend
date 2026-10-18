# Agent handoff

Updated 17 October 2026 (America/Anchorage). Read [AGENTS.md](AGENTS.md) and [TASKS.md](TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

## Current implementation and runtime

PR #83 is merged at b04a8d189f9d1f6f5914558739c1d72d04ef8a3b and installed through the supported
paired updater. Final-head Windows/Linux CI pass; local clean build: 1,590 Java passed/six existing
skips, all 103 Python tests passed. Backup 20261017-185122-b93e2a1e is VERIFIED, both installed hashes
match, Java 11, lifecycle=1, protected task XML/SDDL unchanged. Independent status, compatibility and
scoped pipeline pass. See [delivery and evidence limits](docs/history/parity-delivery-2026-10-17.md).

Installed additions: final companion sale authority/shared exact-cost maintenance, durable recorded
sales investigation, graph lookup, market-drop investigation and independently configured held-stock
alerts/webhook dispatch. Executable account replay and frozen censor-aware completion fitting are
included. [Implementation details and player steps](docs/architecture/parity-account-evaluation.md)
retain every deferred capability. The sole new known out-of-time outcome has zero Brier improvement;
learning remains public-price baseline/HOLD. No performance or full parity claim is established.

Supported Start relaunched RuneLite; owner confirmed login at GE. A fresh read-only eight-slot proof
passes with eight free slots and finite scoped exposure. The installed plan still refuses buys on
an uncertainty reserve, not a generic missing-history flag. Historical gaps, costs/profit and risk
anchor remain intact. New [history-tolerant planning](docs/architecture/history-tolerant-planning.md)
source passes standard clean verification (89s, 1,596 Java passed/six skips; 103 Python passed).
It corrects lifetime/new-risk mixing and preserves fresh-cost reserves; CI/install remain pending.
The candidate's fresh scoped read still has an exhausted prospective allowance. Owner asked for
Copilot's discrepancy behavior; client Missed flips/current-state separation is documented, private
server risk rules are unknown, and no reserve override is inferred. Controlled trading, sustained
latency, UI and webhook acceptance remain open.

Earlier #80 installation and post-login details are retained in
[the recommendation receipt](docs/history/recommendation-delivery-2026-10-17.md) and
[the earlier login receipt](docs/history/postlogin-verification-2026-10-17.md).

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
