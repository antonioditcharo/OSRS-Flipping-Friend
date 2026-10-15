# Project direction

Active development/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.
Read `HANDOFF.md` for merged PRs, verified behavior, source locations, and unfinished work.

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
  workflow and regression gate. PR #45 is merged and installed; ledger HTTP reads now pass.
  Startup/UI and controlled trading acceptance remain pending; reconciliation guards remain active.
  Read the newest HANDOFF checkpoint before historical entries.
- Current-slot recovery requires copied authenticated observations and durable ordering proof.
  It must preserve historical gaps and exposure guards; never book a recovered cumulative baseline
  as a new execution. See docs/architecture/offer-observation-recovery.md. Runtime remains PR #45
  until the next supported paired update; source recovery alone does not establish buy readiness.
- Scope trade uncertainty only through recorded source events in the requested account. Keep
  unassigned rows and unknown money intact; affected items remain paused, while provably unrelated
  acquisitions can remain manageable. Unknown scope stays global. Item scope is not exposure value
  or permission to buy; see docs/architecture/trade-gap-scoping.md for the separate remaining gates.
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
- Optimize and evaluate whole-account returns over elapsed time with realistic manual visits,
  capital, tax, buy limits, partial fills, losses, and remaining inventory. Slot-hour rates are
  diagnostic inputs, not a performance claim.
- Keep real executions separate from simulated price-touch evidence. Promote learning changes
  only with chronological evaluation against a frozen baseline and loss/return guardrails.
- Prefer complete player workflows and behavioral regression tests over new inactive scaffolding.
  Preserve manual game interaction while preparing feature parity.
- The owner authorizes agent-managed routine backup verification, paired updates and launching.
  Batch fixes and acceptance checks; do not require a response, pasted log or INSTALL confirmation
  for each routine step. Use tools/apply-update.ps1 -Unattended for a verified existing installation.
  Collect status, compatibility, pipeline and logs directly. Tooling/docs-only changes need no JAR
  reinstall when runtime source is unchanged. See docs/development-workflow.md. Login and in-game
  actions remain manual; never force-kill the client or change databases to unblock advice.
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
