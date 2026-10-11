# Project direction

Build an OSRS flipping assistant that matches Flipping Copilot's useful player workflows and
eventually exceeds its measured net account profit per hour. The current priorities and evidence
are in `docs/project-direction.md`; historical package notes are not proof of runtime activation.

- Benchmark contract: 100M GP, members, eight GE slots, checking every 5m, 30m, 2h, and 8h.
  Report each cadence separately. Do not silently substitute the existing three-hour option.
- Preserve durable event identities, duplicate handling, accounting transactions, recovery tests,
  and existing user data. Unknown cost or incomplete observations must not invent profit or disposal.
- Observe physical holdings independently of blocked items, skips, and recommendation preferences.
  Keep owned inventory, offers, collection boxes, bank evidence, and flipping stock distinct.
- Treat account isolation and one authoritative ledger as prerequisites for strategy tuning.
  The current companion database and account snapshots still require an account-isolation migration.
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
