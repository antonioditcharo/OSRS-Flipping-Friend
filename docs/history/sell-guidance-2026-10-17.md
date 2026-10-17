# Sell guidance source receipts — 17 October 2026

The following original checkpoint predates the final CI success and merge of PR #79.
Final source: df3da1e; merge: 7d2032a38bb39e051163570fcdb8297ca988f8f9.
Run 39068966156: Windows 117502409112 / Ubuntu 117502408869 both pass.
Installed runtime remains #77. Billing refusal below is historical and resolved.

## Merged safe sell-relist slice — not installed

17 October continuation: bc05fb7 reproduces five additional sell-maintenance failures before the
fix. Maintenance and relisting now share fresh matching-account ledger/gap checks and current
two-sided quote checks. Mixed-cost offers cannot lose their known subset's floor. Unrelated scoped
gaps preserve clean sale advice; external profit remains unknown. Standard clean verification:
BUILD SUCCESSFUL in 54s / 18 tasks / exit 0; 1,561 Java tests, 1,555 passed/six existing skips,
zero failures/errors. Plugin 489,750 bytes; companion 17,902,507 bytes. Prior 103 tooling tests
remain passing; no tooling changes since that run. Logs: reusable .gradle/sell-maintenance-evidence-*.
Owner reports GitHub billing updated; fresh latest-head CI is required before merge/install.

Branch codex/safe-sell-relist starts at #78/a142f4f. Regression commits 02d1c4b and 8692ed3
reproduce six failures before fixes: lost cost floor, excess personal units, missing ledger/gap
bypasses, offer identity confusion and malformed terminal counters. The fix requires matching fresh
durable evidence and quote, preserves exact subset costs/floors and stop/sell-only rules, caps
quantities and retains episode/cost-required guidance through restart. Personal sale profit stays
unknown. Read [the contract](docs/architecture/safe-sell-relist.md) and
[portable continuation instructions](CONTINUE%20DEVELOPMENT.md).

This slice is queued source, not installed or controlled-trade acceptance. Installed runtime remains
#77 below. No client restart/update, real database changes or in-game actions were performed for
this slice. #70 remains open; this does not repair historical stock or complete companion sell-policy
migration. Windows standard verification: BUILD SUCCESSFUL in 53s, all 18 tasks executed, exit 0;
1,552 Java tests / 1,546 passed / six existing skips / zero failures/errors. Plugin 489,471 bytes,
companion 17,902,228 bytes; 218/461 project classes, all Java 11 (major 55). Logs are in reusable
.gradle/safe-relist-*. A focused invocation passed the root tests but selected no companion tests;
the subsequent standard command above completed all modules. All 103 tooling tests pass in 100.027s,
exit 0. Implementation 187a79a is pushed in [PR #79](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/79).
Both exact-head CI jobs were refused before starting: "The job was not started because your account
is locked due to a billing issue." Run 39068304395, Windows 117500451578 / Ubuntu 117500451725.
This original run predates the owner's reported billing update. Rerun both latest-head checks. Logs
could not be fetched (provider HTTP 502); check annotations establish the refusal, not a code failure.
PR remains unmerged; require both exact-head checks before merging. No automatic installation.

