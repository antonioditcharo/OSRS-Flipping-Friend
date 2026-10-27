# Canonical episode lookup delivery

26 October 2026 (America/Anchorage). Installed PR #184 includes #183 diagnostics and all earlier
protected pricing/chart/workflow work. Tested source `1a0c8f5999d706e37efa8c397c153312537a4a21`
and merge `808623ca60080aeba79b7f5175f447f5a32c7bb1` have identical runtime source. Clean 2,434 Java
tests (six existing skips), 121 tooling, Java 11 and both exact-head CI platforms pass in run
`40438442464`. No trading price, loss authority, accounting rule or financial/source identity changed.

## Live diagnosis and disposable proof

After the owner logged in/opened GE, #182 delivered BUY/SELL/MODIFY_SELL/COLLECT guidance and
retained current offers. A fresh account frame had eight authenticated, ordered durable source
rows preceding it, with all current projections matching; the pending outbox drained. That is
partial live acceptance. Full-slot guidance was visible, but repeated five-second transport
timeouts remained. A frozen initial prefix contains 42 completed guidance logs and 13 separately
logged slow failures; fast request outcomes were unlogged, so there is no total-attempt failure
rate. Subsequent timeouts were also observed. Quiet subsecond samples do not resolve the stall.

Full-depth JFR identifies the retained identity JSON query inside canonical offer delivery:
103 native SQLite samples and a related planner monitor wait of 2.637 seconds. JFR print's default
five-frame rendering hid the application caller; the retained deep rendering exposes it.

A consistent read-only 183,995-event account capture passes quick_check. The old lookup samples
are 10,047/1,519/1,088 ms; the indexed lookup samples are 0.841/0.254/0.166 ms, with identical
selected row/payload results. The two indexes build in 2,146 ms with zero financial DML. These
component observations do not establish live latency. Four new golden/native-work/archive tests,
nine cleared-episode and six existing lookup regressions pass. A native work budget reproduces
the old scan over 20,000 sources and passes repeated indexed lookups.

Old #182 and the candidate read the migrated frozen copy under client Java 11 with matching
ledger totals, managed positions, reconciliation scope, continuations, exposure and original
non-null risk-window hash. See [the contract](../architecture/indexed-offer-episode-lookup.md).

## Installed pair and preservation

- Plugin SHA-256: `33fdb3605e5b221ba344f512870bdecf0c67c12f05fcf5b020d7a26714ea9ca6`.
- Companion SHA-256: `a81d47e26c4dcd9e5f3438b91031a42b8da801b81caa12e564c1151d7e1aeb28`.
- Backup: `.runelite/osrs-flipping-friend/backups/20261026-183607-beb8517d`.

The agent used RuneLite's normal Close control and ordinary exit confirmation. No force kill,
login or in-game input was performed. The supported primary-checkout updater installed the exact
frozen pair after official 1.13.4/API preflight, preserving task XML/permissions and process
lifecycle version 1. Both installed JARs and the manifest verify.

All 12 original financial/source/projection tables match the backup. A disposable 201-file
restoration passes integrity verification. Six configuration files restore on copies; five live
files remain byte-identical, and only the hosted writer generation advances once through the
existing startup fence. Every credential/feature field is preserved. Both old/new readers match
on the fresh restored backup, including the original risk anchor. Runtime rollback preserves
subsequent trades; database restoration is a separate disaster-recovery operation.

Status and launcher CheckOnly pass. The first cold account GET exceeds the pipeline's ten-second
budget; a later complete scoped GET-only pipeline passes. Retain that cold failure as evidence.
No synthetic financial observations were posted. The normal hidden launcher reopens RuneLite;
logs show the installed plugin and the first GLOBAL diagnostic prefix. Account prefixes, durable
ACKs and displayed guidance require their own fresh evidence after the manual login.

The owner's subsequent login/GE confirmation provides that initial evidence: fresh authenticated
ordered eight-slot sources with all projection fields matching, visible full-slot guidance and
logged MODIFY_SELL. The first cumulative account prefix has 215 completed transports, including
94 offer calls with zero transport failures and a 25 ms histogram p95 upper bound. It is a short
observed prefix, not a whole-attempt guarantee or a financial acknowledgement. The first guidance
refresh still took 12,119 ms, including 8,300 ms in the client queue; policy calls have a 2,000 ms
histogram upper bound in that prefix. Keep these independent delays open. Later prefixes/soak
receipts remain private rather than rewriting this initial observation as sustained acceptance.

Fresh post-update guidance, sustained timing including failed/missing outcomes, native charts and
player-operated cancel/late-fill/collect/relist/completion/restart remain acceptance gates. Hosted
advice/coordination and learning remain off; budgeted market-informed loss exits remain unfinished.
Private receipts and reproducible captures: primary `local-data/live-ack-20261026`.
