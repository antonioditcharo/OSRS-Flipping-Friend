# Agent handoff

Updated 17 October 2026 (America/Anchorage). Read [AGENTS.md](AGENTS.md) and [TASKS.md](TASKS.md).
Root notes describe the current workflow; detailed prior receipts remain in linked history.

## Parity implementation candidate

The new candidate adds final companion sale authority, shared exact-cost sale maintenance,
durable recorded-sale investigation, item graph lookup, public market-drop investigation and
independent held-stock/local/webhook notifications. Offline account replay and frozen censor-aware
completion fitting are executable. Standard Windows clean build: 53s/18 tasks, 1,590 Java passed/
six existing skips; all 103 Python tests pass. CI, installation and manual acceptance are pending.
Runtime below remains #80 until a supported paired update is recorded.

Read [the implementation/consumer audit and player steps](docs/architecture/parity-account-evaluation.md).
Replay still lacks the complete local buy-replacement/relist/stability sequence; real four-cadence
market benchmarks and prospective session measurement remain open. Runtime model promotion/loader,
rollback and drift controls remain open; learning stays neutral/HOLD. This is implemented progress,
not parity or a performance claim. Existing historical gaps and reserves are unchanged.

## Installed recommendation fixes

Runtime through PR #80 is installed from rebuild merge 342aa523ca35ff10fbb1bdc42e9778c2762ca994,
including #79 sell maintenance/relisting. Both exact-head #80 checks at 8a666e5 pass in run
39069865905: Windows 117505107495 / Ubuntu 117505107647. #79's df3da1e checks also pass in run
39068966156. GitHub billing no longer blocks CI. Historical main remains untouched; ee0d3aff is retained.

Sell maintenance/relisting preserve fresh scoped acquisition evidence, exact taxed cost floors,
managed quantities and offer episode identity. Replacement BUYs require current final companion
entry authority and cannot exceed the same item's proposed cancelled remainder. An entry refusal
immediately withdraws buy guidance and invalidates queued player-key fills. Personal sale profit
stays unknown; old stock gaps/reserves are not repaired. Contracts:
[sell guidance](docs/architecture/safe-sell-relist.md),
[replacement entry](docs/architecture/replacement-entry-authority.md).

Final standard: 54s / 18 tasks / exit 0; 1,562 Java passed/six existing skips, zero failures/errors.
Prior 103 Python tests pass (tooling unchanged). Installed plugin 490,492 bytes/218 project classes;
companion 17,903,249 bytes/461 classes; all Java 11. Both hashes match the manifest; process lifecycle=1.
Backup 20261017-131556-67c07d16 VERIFIED: three companion/22 plugin account files, original pair,
manifest and task. Caller not elevated; task XML/SDDL unchanged and management access still read-only.
No UAC prompt, task ACL change, real database repair/replay or game input.

Independent native status, compatibility and scoped GET pipeline each exit 0. API 1.13.2 matches.
Pre-login ledger 11 sale updates/eight unresolved/zero ranges; WAIT/UNAVAILABLE awaited client coins/free slots.
The updater prints verified completion, but its private log helper lost the native exit code (null)
and exited 1. No native updater failure is established; independent installed/API checks pass.
Exact hashes, outputs, limits and retained prior checkpoints are in the
[delivery receipt](docs/history/recommendation-delivery-2026-10-17.md).

The previous client closed normally via the owner's authorized lifecycle request. Supported Start
relaunched one developer-mode client; side-loading and initialization are logged. Owner confirmed
login. Fresh installed-guard eight-slot checks pass at cursors 16,707/16,780 (seven/two seconds old),
all authenticated source IDs/times and reserved coins match; offer outbox drains one to zero normally.
Post-login HTTP samples are 188/16/203/672ms and 93/16/406/453ms for portfolio/action/ledger/learning.
Current plans refuse buys because a managed instant-sell quote is stale: trident lowTime 477s versus
the current 120s requirement. Later ledger 21 sale updates/nine unresolved; no unknown cost is priced.
No final buy eligibility, sustained timing or controlled trading/UI acceptance is established.
Repeated Swing layout exceptions lack plugin attribution; investigate/reproduce before a fix.
Read [the login receipt](docs/history/postlogin-verification-2026-10-17.md) for exact pipeline output,
read-only proof and limitations. This docs receipt requires no reinstall/restart. Continue source work from
the current rebuild branch; [portable instructions](CONTINUE%20DEVELOPMENT.md) explain verification.

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
