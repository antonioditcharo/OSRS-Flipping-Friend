# Project direction

Active development/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.
Read `HANDOFF.md` for merged PRs, verified behavior, source locations, and unfinished work.
Keep root HANDOFF/TASKS concise and current. Preserve detailed older receipts in linked docs/history
files, keep every unchecked release requirement visible, and distinguish installed runtime from
queued source. Do not require future agents to reread all historical checkpoints to find current state.

Build an OSRS flipping assistant that matches Flipping Copilot's useful player workflows and
eventually exceeds its measured net account profit per hour. The current priorities and evidence
are in `docs/project-direction.md`; historical package notes are not proof of runtime activation.

- Benchmark contract: 100M GP, members, eight GE slots, checking every 5m, 30m, 2h, and 8h.
  Report each cadence separately. Do not silently substitute the existing three-hour option.
- Preserve durable event identities, duplicate handling, accounting transactions, recovery tests,
  and existing user data. Unknown cost or incomplete observations must not invent profit or disposal.
- Observe physical holdings independently of blocked items, skips, and recommendation preferences.
  Keep owned inventory, offers, collection boxes, bank evidence, and flipping stock distinct.
- Routine personal inventory must not require classification by the player. Track only the
  remaining quantity from observed buy executions, with acquisition provenance; an item ID's
  appearance in history does not enroll every owned unit. Do not auto-adopt or recommend selling
  pre-existing belongings. This supersedes the earlier adoption/review workflow as product direction.
- For other owned items, give a current price when the player selects a GE sell setup, then maintain
  the observed sell offer through completion. Unknown acquisition cost/profit stays unknown.
  Put incomplete trade evidence in a separately opened Missed flips window, preserving live
  exposure guards and account data. See `docs/architecture/automatic-trade-scope.md` for the target
  workflow and regression gate. Runtime through PR #67 is installed via the supported updater;
  ledger HTTP reads pass. Earlier fresh risk/final entry GET successes are recorded in HANDOFF;
  they are not controlled trading acceptance.
  UI and controlled trading acceptance remain pending; reconciliation guards remain active.
  Recommendation-latency fixes are installed; post-update acknowledgement drain and fresh durable
  eight-slot proof pass. Sustained timing/final eligible entry remain open; all slots were occupied
  and a stale managed-price guard also appeared. Read the newest HANDOFF checkpoint before history.
  Live full-counter BUYING frames before BOUGHT also created false gaps; the tested fix is installed,
  with fresh post-update/full-progress acceptance still pending. Existing gaps and risk reserves
  stay intact; see docs/architecture/full-counter-progress.md.
  Startup prevention through PR #69 is installed: it waits for a logged-in server tick, ignores default EMPTY
  callbacks without a current-login nonempty predecessor, and requires periodic captures to agree
  with processed callbacks. Read docs/architecture/startup-offer-evidence.md and newest HANDOFF for
  delivery state. Existing affected stock remains paused; this is not historical gap resolution.
  Cleared-episode prevention source additionally persists IDs for new episodes while preserving
  legacy wire/learning keys, checks retained source identity inside canonical acceptance and
  withdraws rejected slots from cached actions. Read docs/architecture/offer-episode-identity.md
  and newest HANDOFF for delivery; never replay old financial rows to activate this guard.
  Offline trade-evidence audits use tools/audit-trades.ps1 with a verified frozen capture. They
  authorize neither accounting nor trading; raw counter maxima and unflagged identities are not
  remaining holdings, known costs or permission to reduce risk. See docs/architecture/trade-evidence-audit.md.
- Current-slot recovery requires copied authenticated observations and durable ordering proof.
  It must preserve historical gaps and exposure guards; never book a recovered cumulative baseline
  as a new execution. See docs/architecture/offer-observation-recovery.md. Runtime includes this
  recovery in PR #49. Fresh authenticated post-login observations and controlled acceptance remain
  required; source recovery alone does not establish buy readiness.
- Scope trade uncertainty only through recorded source events in the requested account. Keep
  unassigned rows and unknown money intact; affected items remain paused, while provably unrelated
  acquisitions can remain manageable. Unknown scope stays global. Item scope is not exposure value
  or permission to buy; see docs/architecture/trade-gap-scoping.md for the separate remaining gates.
- New entries require a fresh client cash/eight-slot capture tied to acknowledged durable event IDs.
  Known old gaps may contribute conservative capital upper bounds while affected items stay paused;
  unknown scope, current errors, contradictory funding and overflow still block buys. Bank cash is a
  session-local lower bound and must not return on fill/collection or cached-container reads. Keep
  queued advice invalidation separate from ordered offer delivery. See
  docs/architecture/bounded-current-planning.md; source tests do not establish live buy readiness.
- Keep the sidebar compact: current guided action, controls and durable recorded profit. Open
  explanations, holdings/history/plan and settings separately; do not restore the old accordion
  stack or label recorded totals as session profit. Preserve actual 5m/30m/2h/8h and visibly custom
  legacy intervals. See docs/architecture/compact-panel.md.
- Keep account-scoped API/database isolation intact. The old unscoped database is preserved and
  unassigned; do not infer its owner or merge it into a live account. One authoritative ledger
  for the planner, exits, UI, and reporting remains the next prerequisite for strategy tuning.
- Live Performance reads the durable account ledger. Do not fall back to journal estimates,
  backfill old unpriced sales, or infer placement/tax rules from first-seen timestamps. Preserve
  rule profiles and tax/profit ranges; a divisible average does not prove homogeneous fills.
  Details: `docs/architecture/durable-profit-ledger.md`.
- Initial exit and sell-maintenance estimates use exact acquisition pool cost and the existing
  SaleAccounting.allocateCost rule for subsets, not an integer unit average. Revalidate the exact
  taxed non-loss floor after price stabilization; no legal break-even price is not a clamped
  break-even claim. See docs/architecture/exact-exit-costs.md. Historical results stay unchanged.
- Optimize and evaluate whole-account returns over elapsed time with realistic manual visits,
  capital, tax, buy limits, partial fills, losses, and remaining inventory. Slot-hour rates are
  diagnostic inputs, not a performance claim.
- Keep real executions separate from simulated price-touch evidence. Promote learning changes
  only with chronological evaluation against a frozen baseline and loss/return guardrails.
  Eligible execution samples and their deduplication claims must commit with canonical source
  acceptance/accounting; never backfill old aggregate samples or change legacy keys to recount
  history. See docs/architecture/execution-evidence-atomicity.md.
  Freeze prospective prediction bindings with the existing account outbox; qualify against the
  earliest recorded source before pairing execution statistics. Unknown or mismatched original
  advice stays unpaired. This is observational association, not proven intent or exact fill timing.
  See docs/architecture/execution-prediction-provenance.md; censoring/evaluation remain open in #56.
  Prospective timing reads preserve placement, first-fill and full-completion intervals; cancellations
  censor at the last confirmed active capture. Do not turn offline time or historical aggregate
  ratios into exact fills or a waiting multiplier. Runtime uses the public-price baseline until
  chronological challenger promotion gates pass; counts alone cannot authorize learning. See
  docs/architecture/execution-observation-intervals.md. This promotion fence is not finished learning.
  Commit prediction evidence only after client-thread guidance acceptance, using the actual leg's
  probability and exact displayed order. Preserve immutable model/config/horizon/wait-work context
  through the original outbox binding; missing legacy context stays missing. Read
  docs/architecture/immutable-execution-forecasts.md. Forecast identity is not validated learning.
  Offline evaluation uses complete frozen source prefixes, reproducible manifests and conservative
  horizon-score bounds. Early cancellations and ambiguous intervals remain unknown; do not drop
  them or count them as failures. See docs/architecture/frozen-execution-evaluation.md. The CLI
  reads only explicitly scoped existing databases; unassigned legacy data stays untouched.
- Automatic runtime entry risk must use the durable prospective account window, realized profit
  bounds, uncertainty reserves and fresh taxed marks. Restart cannot reset its loss allowance.
  Client journal drawdown or wire-supplied canonicalRisk cannot authorize buys. Unknown profit stays
  unknown; preserve financial event identities/risk anchors under retention. Read
  docs/architecture/durable-risk-window.md. Source verification is not live acceptance.
- Prefer complete player workflows and behavioral regression tests over new inactive scaffolding.
  Preserve manual game interaction while preparing feature parity.
- The owner authorizes agent-managed routine backup verification, paired updates and launching.
  Batch fixes and acceptance checks; do not require a response, pasted log or INSTALL confirmation
  for each routine step. Use tools/apply-update.ps1 -Unattended for a verified existing installation.
  Collect status, compatibility, pipeline and logs directly. Tooling/docs-only changes need no JAR
  reinstall when runtime source is unchanged. See docs/development-workflow.md. Login and in-game
  actions remain manual; never force-kill the client or change databases to unblock advice.
  For a verified existing limited task which cannot be maintained without elevation, the supported
  wrapper's -Unattended -UserProcess transition leaves task XML/ACLs unchanged. It requires the
  tested companion startup guard, exact current-user process ownership, atomic runtime replacement,
  a second old-process drain before account backup, and runtime-artifact-only rollback. Later
  updates/start read processLifecycleVersion from the verified manifest. No initial UAC approval is
  required; do not substitute an elevated helper. Read docs/architecture/user-process-updates.md.
- The owner explicitly authorizes a configured player key to fill one already open, matching GE
  quantity/price prompt. Keep this narrow exception in GeInputAssist with lifecycle/context tests;
  Enter, confirmation, placement, cancellation and collection remain player actions. The assistant
  must never send input to the real game. See docs/architecture/trade-input-assistance.md.
- Standard verification: `gradlew.bat clean test jar :companion:shadowJar` on Windows;
  `bash ./gradlew clean test jar :companion:shadowJar` on Linux. Java bytecode targets 11.
  Save redirected clean-build logs outside `build/` to avoid Windows file locks.
- Copilot CSVs are flip summaries and execution exports, without session clocks or cadence labels.
  Preserve reported flip profit/tax, integer-average uncertainty, incomplete account coverage, and
  duplicate ambiguity. Transaction-export Tax is derived by the client, not observed historical tax.
  Run `python -m unittest discover -s tools/tests`; personal source/row-level outputs stay in `local-data/`.
