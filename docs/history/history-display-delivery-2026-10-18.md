# History display stability delivery

18 October 2026 UTC. The owner opened the installed #84 Missed flips window and observed repeated
populated/empty flickering. This fails history UI acceptance. Unknown financial fields and source
identities were visible; that does not establish stable investigation or trading acceptance.

## Implemented and tested

[The display retention contract](../architecture/history-display-retention.md) describes the fix:
account-scoped retained records, login/account response fencing, stable table publication, preserved
filtered selection, explicit updating/as-of text, and visible Records/filter navigation. Performance
and all trading reads still use current evidence. No financial writer, schema, reserve or policy
was changed. #84 remains the installed runtime until the supported paired update is verified.

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
This fixture is not installed-client acceptance. CI includes all Python tooling tests as usual.

## Delivery and acceptance gates

Both exact-head Windows/Linux CI checks, clean-primary merge, supported backup/pair installation,
status/compatibility/scoped GET checks and post-update owner stability observation remain pending.
The only required UI observation is whether **Records -> Missed flips** stays populated while normal
updates run, including with a filter. Source IDs and diagnostic logs are collected by the agent.
No trade or financial-data action is needed for this UI check.

The actual prospective reserve still exhausted its allowance at the last installed #84 check.
This display fix does not change that result. Broader controlled trading, replay, four-cadence
measurement and learning/promotion gates remain open in TASKS; no parity claim is made.
