# Agent handoff

Updated 13 October 2026 (America/Anchorage). Read this with [AGENTS.md](AGENTS.md) and
[TASKS.md](TASKS.md) before continuing implementation.

## Current source and branch

Active development and the GitHub default branch are **`CoPilot's-Rebuild-from-Main`**.
`main` at `0cb8ef8` is an older cleanup/revert baseline; explicitly checking out `main` does not
include the work below. Start a new `codex/` branch from the current remote rebuild branch.
Do not reset or force-push the historical branches to make them look current.

The latest runtime implementation is merge commit `bfef926d334a3374a7c390d595ec6eab0eba8cdc`.
This handoff changes documentation and repository discoverability; it adds no runtime features.

| Merged work | Result | Merge commit |
| --- | --- | --- |
| [PR #27](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/27) | Physical blocked holdings remain visible; personal stock adoption requires the existing opt-in. Added Windows/Linux CI, checkout portability fixes, and an evidence-based project direction. Removed the orphan reference gitlink that broke checkout. | `ab7aeb23488e17e59773bcf805c144fd85c12434` |
| [PR #28](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/28) | Account-specific databases/services/API scopes, cache and outbox fencing, recovery of offers older than four hours, read-only diagnostics, and Copilot CSV ingestion. | `6ec1a8f238a2232f31bd4906c48e4dafea7244bd` |
| [PR #29](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/29) | Atomic durable sale proceeds, proportional cost, tax/profit bounds, rule evidence, and live Performance reading that account ledger. | `bfef926d334a3374a7c390d595ec6eab0eba8cdc` |

## Implemented boundaries to preserve

- Physical ownership observations are independent of trading exclusions. Personal inventory,
  equipment, bank evidence, offers, collection boxes, and flipping stock are distinct concepts.
- Account identity is an opaque hash of the RuneLite profile. Account path/header/payload/response
  identities agree; requests use `accounts-v1` and `/v1/accounts/<accountId>/...`. A profile switch
  clears advice/ledger caches, and an earlier account's response cannot become the current ledger.
- Live SQLite files are `companion/accounts/<accountId>/flipping-friend.db`; shared market state
  uses `companion/market-state.db`. The previous `companion/flipping-friend.db` is preserved and
  unassigned. Do not infer its owner, prune it, or import it into a live account automatically.
- Durable offer projections restore active/uncollected offers independently of four-hour buy-limit
  expiry. Event IDs, exact acknowledgements, duplicate handling, and transactional rollback matter.
- Sale updates record gross counter deltas, removed blended cost, and tax/profit evidence in the
  same transaction as ownership. Cost remainders survive partial sales and the final unit.
- Mixed-price fills do not justify tax on a rounded average. First-seen time does not prove an
  offer's tax regime. Old unpriced sales and unknown cost/proceeds remain unresolved; current
  quotes or local journal estimates cannot manufacture exact historical profit.
- Performance uses the scoped durable ledger, including uncertainty and coverage. Missing/stale/
  malformed/foreign data says Unavailable. Local history and bank valuation remain labeled estimates.
- Game interaction is manual. The assistant does not place, cancel, or collect offers automatically.

See [account isolation](docs/architecture/account-isolation.md) and
[durable sale accounting](docs/architecture/durable-profit-ledger.md) for design and regression cases.

## Next work and claims that remain unsupported

The single-authority migration is **not complete**. Planner exposure inputs, exits, inventory UI,
journal calibration, and local session-risk inputs still use local records. Migrate these consumers
to the durable account state with positive inventory/cost reconciliation and behavior tests for
partial fills, missed observations, crashes, duplicate delivery, profile changes, and restart.
Then finish the manual cancel -> collect -> revalidate -> replace workflow and companion sell policy.

The benchmark contract is 100M GP, members, eight slots, and separate 5m/30m/2h/8h checking scenarios.
Explicit 2h/8h settings and full-account chronological replay remain pending; do not substitute 3h.
Account GP/hour requires measured attendance/session time and conservative treatment of remaining
inventory. Candidate GP per slot-hour is diagnostic, not the success metric.

The [Copilot baseline](docs/benchmarks/copilot-2026-10-11.md) and importer document the supplied
exports' evidence limits. Raw CSVs and row-level outputs are intentionally gitignored under
`local-data/`; they are not available from a fresh GitHub checkout. Tests include synthetic fixtures.
Session/cadence labels are unavailable. Transaction-export Tax is derived by Copilot's exporter,
not an observation of historical tax. Do not infer a valid GP/hour comparison from these files.

No live RuneLite installation or live database migration was performed. Live verification against
actual holdings/collected coins remains outstanding. Profit superiority, feature parity, online
logistic learning, shadow trading, and automatic challenger promotion have not been established.
Historical package counts, roadmaps, and non-activation scaffolding are not runtime feature evidence.

## Verification and continuation

The last supported local build passed 1,147 Java tests with six capture-dependent skips, plus eight
Python importer tests, and built both JARs. It used Windows/JDK 23, Gradle 8.14, and RuneLite 1.12.39.
[Post-merge CI for the runtime revision](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38197169733)
passed on Windows and Linux with JDK 21. Compilation targets Java 11.

```powershell
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
python -m unittest discover -s tools/tests -v
```

On Linux use `bash ./gradlew` for the same Gradle tasks. Redirect clean-build logs outside `build/`
to avoid Windows file locks. Supported artifacts are `build/libs/osrs-flipping-friend.jar` and
`companion/build/libs/flipping-friend-companion.jar`; update both together. `tools/pipeline-check.ps1`
checks the live protocol/scoped ledger using read-only HTTP requests, without synthetic trades.

Merged implementation working branches and superseded logs were removed. A reusable managed
worktree, original personal data, final verification evidence, the separate `copilot-rebuild`
history, and the pre-takeover stash were preserved. Compare divergent recovery/stash work deliberately.
Update this handoff and TASKS with each implemented boundary, evidence, and remaining limitation.
