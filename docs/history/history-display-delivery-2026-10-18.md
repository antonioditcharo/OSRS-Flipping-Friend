# History display stability delivery

18 October 2026 UTC. The owner opened the installed #84 Missed flips window and observed repeated
populated/empty flickering. This fails history UI acceptance. Unknown financial fields and source
identities were visible; that does not establish stable investigation or trading acceptance.

## Implemented and tested

[The display retention contract](../architecture/history-display-retention.md) describes the fix:
account-scoped retained records, login/account response fencing, stable table publication, preserved
filtered selection, explicit updating/as-of text, and visible Records/filter navigation. Performance
and all trading reads still use current evidence. No financial writer, schema, reserve or policy
was changed. #86 supersedes #84 as the installed runtime; the delivery evidence is below.

Regression-first reproduction (`local-data/history-flicker-reproduction.log`) failed four assertions:
pending captures emptied history, expired/failed reads lost history, logout retained old display
state, and unchanged table polls lost selection. The fixture stubbing error in the earlier first
attempt was corrected before this reproduction. All regressions now pass, plus late login/account
responses, backward/future history, a filtered open Swing window receiving new rows, and separation
between retained sales and fresh Performance. The first targeted command accidentally selected
subproject test tasks with no matching classes; the tests themselves passed, and the final complete
build below is the successful verification command.

`gradlew.bat clean test jar :companion:shadowJar --console=plain --no-daemon` exits 0 in 91 seconds,
18 tasks. Root 693 passed; core 184 passed; companion 727 passed/six existing skips: 1,604 passed,
zero failed/error. Log: `local-data/history-display-clean-build.log`. The generated compact sidebar
fixture visibly fits the Records button, and the existing narrow-sidebar behavior test passes.
This fixture is not installed-client acceptance.

## Merged and installed

[PR #86](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/86), exact source
8fe03fac37592036ebb8152da0a70c704d1aaf56, merged as 478426f into the active rebuild branch.
[Exact-head CI](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39108916385)
passes Ubuntu job 117620622438 (2m56s) and Windows job 117620622686 (3m42s), including all 103 Python
tooling tests on each platform. The normal checkout was clean and fast-forwarded before installation.

RuneLite was already closed. Supported apply-update.ps1 -Unattended -SkipBuild exits 0 through
native Windows PowerShell; no forced game shutdown or game input was used. Backup
20261017-235035-8555dccc is VERIFIED, with three companion/22 plugin account files, original
JAR/manifest hashes and task export. The protected task XML/SDDL is unchanged; lifecycle=1.

Installed artifacts match the verified build, all project class major versions 55 (Java 11):

| Artifact | Bytes | Project classes | SHA-256 |
| --- | ---: | ---: | --- |
| Plugin | 515222 | 229 | 77aff5f389dd01d0f051624d02c1a62f1ce7585e3ec8020f9d4c6a62e4c7efd1 |
| Companion | 17983155 | 488 | cfc364c25634b00e32a8c087fb0f79be8602443abd45cdf2d322ec5e5710f556 |

Independent native status, supported launcher CheckOnly (RuneLite API 1.13.2) and scoped GET-only
pipeline each exit 0. Before/after read-only probes preserve risk evidence and historical gaps.
Ledger comparison differs only in observedAt, its read timestamp: sale/evidence/position content
and totals are identical (32 sale updates, 18 unresolved, zero profit ranges; 1,302 missed-evidence
rows, with legacy rows shown separately). There was no financial repair/replay or risk reset.
The probe's initial whole-object comparison correctly differed on observedAt; a field-by-field
comparison isolated that timestamp before financial preservation was accepted.

Supported Start relaunched the client. Private logs, build/artifact receipts, task snapshots and
read-only comparisons are under local-data/parity-evidence-20261017/history-display-*.

## Accepted history stability and remaining gates

The owner answered **"Stays populated"** after the installed **Records -> Missed flips** minute/filter
check. This accepts the reported flicker fix. A fresh read-only post-login check independently
passes eight authenticated exact source IDs, receipt/capture/sequence matches at age six seconds,
eight free slots and zero unbounded gap scopes. The 1,283 historical reconciliation gaps persist.
The new launch log has no ERROR/uncaught-exception lines or matched layout exceptions at this check;
that is a bounded observation, not sustained UI or trading acceptance. Raw evidence remains private.

The actual prospective reserve still exhausts its allowance in the fresh installed #86 check.
This display fix does not change that result. Broader controlled trading, replay, four-cadence
measurement and learning/promotion gates remain open in TASKS; no parity claim is made.
