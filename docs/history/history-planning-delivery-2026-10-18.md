# History-tolerant planning delivery — 18 October UTC

Local date: 17 October 2026, America/Anchorage. PR
[#84](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/84), source
0f1c150609fb72457a1942688dd46e415196e447, is merged at
9d9c3aae6b6659bc73adbec8dcee5ad69ae01f39. Primary checkout was clean and fast-forwarded;
source/build/tooling diff from the tested head is empty. Historical main remains untouched.

## Implemented and verified

The [history/risk contract](../architecture/history-tolerant-planning.md) describes the actual
read-model change and its limits. Lifetime uncertainty remains an exposure guard; new-risk
reserves use the durable opening portfolio and subsequent funding episodes. Previously quarantined
old sales do not become new managed losses, and fresh purchases of paused items retain reserves.
All financial identities/rows, unknown profit, cost/tax rules, affected stock and the original risk
window remain. No schema migration, writer replay, old-data repair, risk reset or override.

Missed flips adds item/reason/time/source filtering and exact source/offer identity columns.
It remains a separate read-only investigation window; incomplete evidence never enrolls belongings.

Regression-first reproduction: three of four initial cases failed before the change. Six final
behavioral cases pass, including actual final companion authorization for an unrelated item with
retained historical gaps, repeated counters/new episodes, missing/foreign opening sources,
fresh paused-stock costs, a genuinely large reserve and restart persistence. Existing account,
tax, atomicity, quote, current-source and SQLite work-budget tests remain enabled.

Standard Windows clean test/JAR pair: exit 0, 89s, 18 tasks. Java 1,596 passed/six existing skips,
zero failures/errors (root 685, core 184, companion 733 including six skips). Python 103 passed in
127.800s. Both final-head CI jobs passed in run 39106989952: Ubuntu 117614886988 (2m36s),
Windows 117614886796 (3m54s).

## Installed

Owner confirmed RuneLite closed normally. Supported unattended paired update with verified existing
artifacts exited 0. Backup 20261017-232123-b54e4672 is VERIFIED: three companion and 22 plugin
account files, original artifacts/manifest and task export. No elevation; task XML/SDDL compare
exactly unchanged, process lifecycle remains 1. Native status, API 1.13.2 compatibility and scoped
GET pipeline each exit 0. Both installed hashes match the manifest and built artifacts.

| Installed artifact | Bytes | Project classes | SHA-256 |
| --- | ---: | ---: | --- |
| Plugin | 512,908 | 229 | `7e098bfb5bd5177041cf06711cdffdb8d61160cf4580f2e729d63427ea6e53da` |
| Companion | 17,980,841 | 488 | `5bd6ddf71c65ce94b4b1018dac53fe3ec9df6b26538240509f905ca0e99cadb1` |

All project classes target Java 11. Supported Start relaunched one client; owner confirmed login
at GE. No game input, force-kill, database repair, financial replay or new history classification.

## Post-login evidence and remaining refusal

Authenticated scoped reads succeed: portfolio/action/ledger/learning samples 143/13/301/569ms.
These are individual samples, not sustained timing acceptance. Ledger remains 32 sale updates,
18 unresolved, zero profit ranges. Unknown cost/profit was not priced or backfilled.

The first isolated read did not pass the current-read guard and is not acceptance evidence.
A later read-only transaction using the actual installed JAR passes at eight seconds of age:
eight unique authenticated sources, all receipt IDs/timestamps/sequences match, eight free slots,
finite exposure, 1,283 retained gaps and zero unbounded scopes. It uses mode=ro and query_only=ON;
no synthetic account/offer observation is posted. The original risk anchor is retained.

Available canonical risk still reports an exhausted prospective uncertainty allowance. The account
therefore refuses buys. The change corrects lifetime/new-capital mixing; it does not turn genuinely
unresolved in-window capital into profit or erase its reserve. No live eligible entry or controlled
buy/collect/sell/partial-cancel/relist/restart is established.

Owner requested Copilot's discrepancy behavior. The inspected client separates missed/ghost/
disappeared records from normal statistics and sends current bag/bank/GE state, with synchronization
exclusions. No client-side account-wide historical reserve breaker was found; private server risk
rules are unknown. No override of Friend's reserve guard was inferred from this question.

Manual history UI acceptance is requested separately. Broader parity, complete production-policy
replay, prospective measurement, runtime learning release controls and measured 100M GP returns at
5m/30m/2h/8h remain open. Detailed private logs, task metadata and source/HTTP/probe responses stay
in local-data/parity-evidence-20261017; artifact/test metadata is local-data/history-tolerant-build-verification.json.
These documentation receipts need no further JAR replacement.
