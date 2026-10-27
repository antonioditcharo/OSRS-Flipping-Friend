# Indexed retained offer episodes

26 October 2026. Follow-up to the owner's fresh login/GE confirmation. Runtime #182 produced
BUY/SELL/MODIFY_SELL/COLLECT guidance and a fresh ordered eight-slot read set, but repeated
five-second offer-delivery/report timeouts remain. This is not completed latency acceptance.

A live JFR during active trading includes 103 native SQLite samples inside
`SqliteStore.episodeReconciliationReason`, through canonical offer delivery. A planner monitor
wait reached 2.637 seconds in the same capture. Samples/waits are diagnostic observations, not
exclusive execution times. Quiet-period profiling alone missed this cause. Private evidence:
primary `local-data/live-ack-20261026` (including full-depth JFR renderings).

## Preserve the guard, bound its lookup

The original new-episode query examines retained nonblank canonical event IDs in descending row
order. It returns the newest matching text identity or uncertain source. An unseen identity can
force JSON parsing across the entire history while acknowledgement delivery holds the account
and store monitors. Ordinary unchanged episodes already bypass that history query.

`OfferEpisodeLookup` splits the original CASE predicate into two disjoint indexed branches:
qualified text identities, and uncertain sources. Their UNION ALL selects the same maximum row
ID. Each branch uses its explicit compatible index, preventing a descending full-history scan.
The incoming source ID is excluded from both branches. Malformed/non-object JSON, missing or
non-text identities and EMPTY handling retain the original CASE semantics. Duplicate JSON keys
retain SQLite's first-key lookup followed by the unchanged Gson qualification of the selected
payload. Repeated identical-looking occurrences keep their individual row identities and order.

The two additive indexes are built by writable startup after the legacy event-ID migration.
Their CASE expressions guard JSON functions from malformed old payloads. Read-only archives
create nothing; previous compatible runtimes can read the unchanged tables and ignore the indexes.
Backfilling indexes is one-time startup work, not permission to skip unknown history. No financial
or source row, acquisition context, event ID, projection, tax rule, risk window or learning sample
is rewritten. No cache, acknowledgement exemption, extra timeout, replay or price change is added.

## Verification and release

Golden SQL comparisons independently freeze the old query and compare exact selected row ID and
payload over malformed, scalar, array, missing, numeric and duplicate-key JSON, EMPTY rows,
null/blank legacy IDs, incoming exclusions and repeated occurrences. A native instruction budget
passes repeated existing/missing lookups among 20,000 old offers and reproduces the old scan as
an interruption. Archive/migration checks preserve original rows and create no read-only indexes.
Existing cleared-episode, rollback, duplicate execution and valid distinct-trade regressions pass.

The frozen live account contains 183,995 events; its read-only backup passes quick_check. Final
component comparisons, clean Java/tooling, Java 11, exact-head Windows/Linux CI and the supported
paired backup/rollback checks remain separate release gates. This batch includes #183's bounded
completed-transport prefixes so subsequent live timing can count fast successes and failures.
Source/installed/player-accepted state is tracked in HANDOFF and the private delivery receipt.
Fresh visible guidance and player-operated trade/restart checks remain required after installation.
