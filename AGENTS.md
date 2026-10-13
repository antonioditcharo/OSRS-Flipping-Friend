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
- Standard verification: `gradlew.bat clean test jar :companion:shadowJar` on Windows;
  `bash ./gradlew clean test jar :companion:shadowJar` on Linux. Java bytecode targets 11.
  Save redirected clean-build logs outside `build/` to avoid Windows file locks.
- Copilot CSVs are flip summaries and execution exports, without session clocks or cadence labels.
  Preserve reported flip profit/tax, integer-average uncertainty, incomplete account coverage, and
  duplicate ambiguity. Transaction-export Tax is derived by the client, not observed historical tax.
  Run `python -m unittest discover -s tools/tests`; personal source/row-level outputs stay in `local-data/`.
