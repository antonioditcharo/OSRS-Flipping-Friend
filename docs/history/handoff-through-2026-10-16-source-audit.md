<!-- Historical snapshot before current-note compaction, 16 October 2026. -->

# Agent handoff

Updated 16 October 2026 (America/Anchorage). Read this with [AGENTS.md](../../AGENTS.md) and
[TASKS.md](../../TASKS.md) before continuing implementation.

## Verified-capture trade evidence audit — 16 October 2026

Primary rebuild is clean/current at #72 merge `eaa63b1cfc11351fbe96bf75603fbcb29799cca4`.
#72 passed both checks at `da449a7a6d7d801ef76a6dca4921027884dcf3e8` in
[run 38876972197](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38876972197)
(Windows 116894240240 / Ubuntu 116894240365). [Delivery receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/72#issuecomment-6211948560).
Runtime remains installed #69/86f69da; #72 is queued for a batched update. This new offline tool
does not require another installation or player restart. Historical main remains untouched.

The previous goal turn was progress: it delivered the tested episode guard, fixed stale cached
Collect advice, and established fresh #69 eight-slot/acknowledgement proof. New branch
`codex/trade-evidence-audit` starts from eaa63b1. The next safe action is to establish explicit
source ambiguities for #70, preserving original financial rows rather than replaying old money.

New tools/audit-trades.ps1 runs a complete verified-capture -> private audit -> recomputation
workflow. No database, token, service or game client is opened. Source references and anomaly flags
identify repeated identities, initial filled baselines, missing terminals, regressed counters and
metadata/order/session/clock conflicts. Raw cumulative maxima are never summed into positions or
profit. Reports always authorize neither accounting nor trading; unflagged rows are not eligible
holdings. Parent capture, report bytes and retained actual auditor artifact are hash-bound.
Foreign/changed prefixes, rehashed edited claims, unsupported versions, missing/incomplete outputs
and overwrites fail. Read [the contract/commands](../../docs/architecture/trade-evidence-audit.md).

Feature-first tests c3f4936/476b0b8 initially fail compilation because the audit capability does not
yet exist; they are not claimed as regressions of shipped runtime. Regression 744b75b separately
reproduces unsupported manifest versions being accepted in the new unshipped command before its
constructor-field fix. Final standard: BUILD SUCCESSFUL in 55s / 18 tasks executed / exit 0;
Java 1,518 passed/six existing skips, zero failures/errors (641 plugin/179 core/704 companion).
Python: 79 tests in 85.248s / OK / exit 0. Plugin 486,838 bytes/218 project classes; companion
17,895,369 bytes/460 classes; all project bytecode Java 11. Exact-head CI/delivery pending here.

The packaged PowerShell 5.1 command audits the retained complete source prefix from 00:03 local,
then wrapper verification and independent verification using its retained auditor.jar.bin pass:

```text
status=OBSERVATION_AUDIT_ONLY / sourceRows=11598
identities=35 / identitiesWithFlags=23 / unassignedProblems=0
EMPTY_WITHOUT_TERMINAL=13 / IDENTITY_REAPPEARED_AFTER_EMPTY=19
INITIAL_FILLED_BASELINE=6 / INITIAL_TERMINAL_BASELINE=4
RECORDED_RECONCILIATION=19 / TERMINAL_TO_ACTIVE=1
authorizesAccounting=false / authorizesTrading=false
OWNER_CAPTURE_AUDIT_EXIT=0 / OWNER_AUDIT_VERIFY_EXIT=0 / RETAINED_AUDITOR_VERIFY_EXIT=0
sourceManifestSha256=003c8d207f1462215fe7455413c8223ee630032d419ee939ff945bae12bad3b6
auditManifestSha256=42c07d429a5bc19f8a0552d5d65f5c8c1febbb3f40ae3ddd73a236fe0ef84f2d
```

Flag counts overlap and describe observed identities, not erroneous trades or losses. No remaining
quantity/cost/profit or clearance of the old reserve is inferred. Inputs lack complete financial
effects and independent disposal/funding evidence, so the missing-sell problem remains open in #70.
An additional private exploratory terminal-envelope calculation (JSON only, not production policy)
reduces the raw envelope for 15 terminal-witness segments but retains 14 full envelopes; the
candidate raw upper bound remains above the account allowance. It does not authorize a risk change
or establish a safe recovery algorithm. Do not implement it merely to reduce a refusal.

Private primary bundle: local-data/trade-audits/sell-recovery-20261016, alongside the original
local-data/execution-evaluation/sell-gap-evidence-20261016. Raw rows/items remain private. Reusable
worktree .gradle/trade-audit-*.log records feature tests, verification and actual commands. Private
primary .gradle/terminal-envelope-investigation.py and local-data/diagnostics/terminal-envelope-
investigation-20261016.json retain the exploratory calculation. No game input, database repair,
financial replay/backfill, risk reset, adoption, outbox clearing or player interaction occurred.

## Cleared-offer accounting guard source and fresh #69 proof — 16 October 2026

Primary rebuild is clean/current at `a4935b4118a46124ab2957e6cb8a1b876a6ea8e0` (#71 docs merge).
Installed pair remains #69/86f69da. `codex/cleared-offer-accounting` starts from that rebuild;
its changes below are source-only until a supported paired update. Historical main is untouched.

The previous goal turn made progress: #69 was merged/installed, #71 records verification, and an
independent complete source freeze exposed terminal -> clear -> repeated original terminal identity.
Pure offline replay compares 46 recorded effects, with six changed by current full-counter rules;
replaying raw inputs still duplicates a cumulative terminal after EMPTY. It is not a safe historical
recovery algorithm. [Evidence](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70#issuecomment-6211380958).

Regression-first a63a534 reproduces five durable accounting/restart/unknown-source failures;
cf94779 reproduces two producer identity failures, 86137e3 adds contradictory item scope and
1357b42 reproduces cached COLLECT after a rejected cancelled-to-active transition. Positive service
coverage 7d09a08 verifies an already rejected old terminal is withheld before/after restart.
New local episodes now persist a random ge-UUID through copies/account reload/late terminal replay.
An active zero-fill replacement gets a new ID after zero-fill cancellation. Existing saved offers
and outbox events retain their shipped identities; ExecutionRecorder's learning key is unchanged.

The store checks new/switched episodes against retained canonical JSON in its existing accounting
transaction. Reused or unknown identity evidence appends RECONCILE with no new economic or limit
effect. A durable reason prevents later recovery frames bypassing the guard. Scope retains every
item recorded against the reused ID; unknown source scope stays global. Rejection withdraws that
slot's cached action, without asserting a physical collection. See
[the contract](../../docs/architecture/offer-episode-identity.md).

The first broader run exposes ten older fixtures using placeholder source payloads or one ID for
an intentionally new episode. Their setup now serializes canonical events/distinct new identities;
the specifically opaque legacy-source fixture remains explicit and preserves global uncertainty.
Financial/recovery assertions are retained. A second run catches that explicit fixture still
needing opaque predecessor evidence; its corrected setup passes. No live source was normalized.
Final standard: BUILD SUCCESSFUL in 49s / 18 tasks executed / exit 0; Java 1,503 passed/six existing
skips, zero failures/errors (641 plugin/179 core/689 companion). Python: 79 tests in 85.639s / OK /
exit 0; tooling unchanged afterward. Candidate plugin 486,838 bytes/218 project classes and companion
17,879,995 bytes/453 classes; all major 55/Java 11. Exact-head CI and delivery pending here.

Read-only packaged candidate guard probe opens the explicit scoped database with mode=ro, performs
only SELECTs and rolls back. Original native lookup: novel identity 759ms cold, 114/138ms warm.
Descending row order avoids random UUID-index reads: final novel identity 586ms cold, 48/42ms warm;
known old identity 135ms cold, 17/13ms warm. This measures only the guard, not full HTTP ingestion or
a latency guarantee. No cache, index/table/schema migration or real event POST is introduced.

The running #69 client has independently supplied fresh observations. At 00:48 local,
portfolio/action/ledger/learning GETs take 141/16/218/719ms. Plan UNAVAILABLE/DRAWDOWN_LIMIT_REACHED,
risk AVAILABLE with negative remaining allowance, Action WAIT. Ledger: 11 sales/eight unresolved;
22 timing episodes/six full forecasts/eight completed intervals/14 canceled-censored/zero open.
Original learning promotion stays held. New position effects beyond risk cursor: 11 ACQUIRE /
30 RECONCILE / seven DISPOSE. The 30-gap count and reason breakdown are unchanged from before
the startup fix; two new disposals are retained without assigning unknown costs. No controlled
buy/sell success or realized-loss amount is inferred. Outbox four pending at the GET later drains
normally to zero; predictions zero. Installed #69's packaged read-only proof then reports:

```text
PACKAGED_CURRENT_READ_SET_VALID=true
CURRENT_SOURCE_COUNT=8 CAPTURE_SOURCE_COUNT=8
CAPTURE_AGE_SECONDS=3 FREE_SLOTS=8 TOTAL_SLOTS=8
CHECK_INTERVAL_MINUTES=5 RISK=MODERATE
CURRENT_EVENT_TYPES={EMPTY=8}
MODE_RO_TRANSACTION_COMPLETE=true / STARTUP_CURRENT_PROOF_EXIT=0
```

Fresh #69 capture/ACK proof and unchanged new-gap count pass. Existing affected stock/risk reserves
remain unresolved in #70; this guard is prevention, not historical reconciliation. Batch installation
with the remaining work while the client stays open; no additional close/login request is needed
for source verification. No game input, database repair/replay, financial backfill or risk reset.
Private evidence: reusable worktree .gradle/{cleared-offer*,episode-identity*,episode-scope*,
stale-collection*,rejected-terminal*}; primary local-data/diagnostics/{episode-guard-*,
startup-current-proof-20261016.txt,recommendation-stall-20261016-004854.json,
live-trade-gap-trace-20261016-004855.json}. Probe sources remain in primary .gradle.

## Startup prevention pair installed; stock recovery remains open — 16 October 2026

PR #69 merged as `86f69dabf9f32652872da0635c451f4b2bbefb5d`; primary rebuild is clean/current
at that revision. Historical main remains 0cb8ef8 and required ee0d3af ancestry passes. Both
exact-head checks passed at `311b02d945db91d1292fec15a0bc4d6ac84aa739` in
[run 38872839010](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38872839010):
Windows 116880770298 / Ubuntu 116880770698. The final profile-fence regression and 41s clean build
below are included. Java 1,491 passed/six skips; Python 79 passed; source/tests are unchanged by
this receipt. No further test suite or JAR reinstall is needed for these documentation changes.

With zero RuneLite clients, one elevated helper invoked the supported updater -Unattended from
the exact clean primary revision. Backup verified before paired replacement:

```text
C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261016-002238-6687103d
backup-verification.json            335 bytes / VERIFIED
companion-task.xml                 1,895 bytes
old companion JAR            17,878,059 bytes
old installation.json                458 bytes
old plugin JAR                  486,098 bytes
companion accounts: 3 files / 32,890,552 bytes
plugin accounts:   22 files / 1,507,868 bytes
```

Helper success=true/exitCode=0, sourceRevision=86f69da, finished 08:22:56 UTC / 00:22 local.
Supported build: BUILD SUCCESSFUL in 6s, three tasks executed/five up-to-date. Installed plugin
486,494 bytes/218 project classes and companion 17,878,455 bytes/453 classes match manifest and
primary build hashes. Every project class matches the final standard-tested candidate and targets
Java 11. Each JAR has a harmless 20-byte packaging difference from that candidate.

```text
PAIRED_INSTALL_PROOF=PASS / PAIRED_PROOF_EXIT=0
Overall status: PAIR VERIFIED / COMPANION RESPONDING / STATUS_EXIT=0
RuneLite 1.13.2 and installed plugin API match; cached artifacts verified.
COMPATIBILITY_EXIT=0
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 9 sale updates; 7 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_EXIT=0
```

Agent launch through tools/start.ps1 observes one client and the plugin side-load line. Fresh
post-login first-tick evidence, restored sell guidance and controlled trading are not established.
Existing stock remains paused by historical gaps; prevention is not recovery. See
[issue #70](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70) for the next work.
No game input, database repair/migration, financial backfill, risk reset, preference change or
outbox clearing occurred. Original data and unknown sale profit remain intact.

A complete canonical prefix was frozen read-only and independently verified with its retained
evaluator artifact (no database reopened): 11,598 source rows/cursor 13,221, 22 episodes/six eligible
forecasts, all six horizon outcomes ambiguous. Both commands exit 0; promotion remains held.
Manifest SHA-256 003c8d207f1462215fe7455413c8223ee630032d419ee939ff945bae12bad3b6.
This is reproducible evidence, not an accounting resolution or learning/performance claim.
[Recovery evidence receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70#issuecomment-6211234889).

Private primary receipts: .gradle/startup-offer-{update*,install-proof,status,compatibility,launch.*};
local-data/diagnostics/startup-offer-postinstall-pipeline-20261016.txt; complete frozen bundle
local-data/execution-evaluation/sell-gap-evidence-20261016 and .gradle/sell-gap-*-20261016.log.
Continue the source-proof recovery investigation before asking the owner to repeat trades/restarts.

## Missing sell guidance: startup evidence prevention — 16 October 2026

Resume starts at clean/current rebuild `4e822ec99a1585fa2c5c17d4638ab6b2508065b1` (#68).
Installed pair remains #67. RuneLite is closed; companion is responding. New branch
`codex/startup-offer-evidence` starts from that rebuild. Historical main remains untouched.

Read-only 00:03 local authenticated GETs take portfolio/action/ledger/learning 7,594/93/2,047/453 ms.
Plan UNAVAILABLE/COMPANION_STATE_UNAVAILABLE and Action WAIT; no fresh logged-in account is claimed.
Ledger has nine sale updates/seven unresolved, 23 aggregate positions, zero eligible trading
positions and 1,290 Missed flips entries. Outbox pending=0; two prediction bindings. Learning has
22 episodes/six full forecasts/six completed intervals/14 canceled-censored/two open-censored;
public-price baseline promotion remains held. Current slots: six EMPTY/two SELL_OPEN.

The earlier 15 October 20:42 read had zero eligible trading positions and a 365,478,028 GP
uncertainty reserve with zero bounded realized updates. This is not a recorded realized loss.
Beyond risk cursor 1260, the latest read retains 11 ACQUIRE/30 RECONCILE/five DISPOSE effects.
Gap reasons: three invalid sources, 15 authenticated baselines, 11 clears before terminal and one
invalid lifecycle transition. The 20:41 login sequence contains eight EMPTY events followed by
old nonempty identities. RuneLite's API documentation confirms initial EMPTY callbacks on login;
the old payloads cannot identify each event's callback-versus-snapshot origin. A cancelled-then-
active observation is also retained. Do not relax reducer guards to accept that old sequence.

Regression-first eedb4c2 reproduces five snapshot/restoration failures; 7cced9d separately reproduces
logged-in default EMPTY callbacks. Producer changes wait for the first logged-in server tick before
array restoration/account publication, ignore initial EMPTY callbacks without a same-login nonempty
predecessor, and require snapshot identity/phase/fill/spend to match processed callbacks. Real
terminal-then-clear delivery remains ordered, including pre-tick and buffered observations. Read
[the contract](../../docs/architecture/startup-offer-evidence.md). This prevents new false gaps; it does
not restore eligibility for the already affected stock or backfill accounting.

Final review regression d5a2e30 also reproduces deferred restoration switching to a different
profile. Its fix retains the original generation/profile across the first-tick retry; a changed
profile cannot relabel the pending account read.

Final standard: BUILD SUCCESSFUL in 41s / 18 tasks executed / exit 0; Java 1,491 passed/six existing
skips, zero failures/errors (638 plugin/179 core/680 companion). Python: 79 tests in 71.713s / OK /
exit 0; tooling unchanged afterward.
Candidate plugin 486,474 bytes and companion 17,878,435 bytes, all project classes Java 11.
Exact-head CI and installation are
pending at this checkpoint; source verification is not live acceptance.

Read-only evidence also finds an affected nine-unit buy whose accepted acquisition rows sum to
11 units. Unknown/duplicated/missing history cannot safely be turned into sell quantities using
the old aggregate positions. Already affected items stay paused pending auditable source-based
resolution; do not clear rows, risk reserves, outboxes, or adopt physical/personal stock. Original
financial records, incomplete sale costs and durable risk anchor remain unchanged. No database
write/repair/migration, synthetic live POST, account assignment or game input occurred.

Private primary-checkout evidence: local-data/diagnostics/recommendation-stall-20261016-000357.json,
live-trade-gap-trace-20261016-000358.json and sell-guidance-evidence-20261016-000854.json. Helpers
.gradle/trace-live-trade-gaps.py and sell-guidance-evidence.py use explicit scoped mode=ro,
query_only and rolled-back read transactions. Reusable worktree .gradle/startup-*.log retains
regressions/final verification. A focused command initially also selected a subproject with no
matching tests; the final unfiltered standard above is authoritative. No latency acceptance or
restored live sell recommendation is claimed. Batch any supported installation; no repeated player
INSTALL/log-paste steps are required.
Historical stock recovery is tracked in [issue #70](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70).

## Full-counter pair installed and verified — 15 October 2026

Normal rebuild is `e5bc8da6a41e38896fdec4c5a028b4bf3d757d9e` (#67 merge), clean/current.
Both exact-head checks passed at `98cb168dc7e5337580087ed3982d6a64097d68e7` in
[run 38850014046](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38850014046):
Windows 116808932982 and Ubuntu 116808932841.
[Source delivery receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/67#issuecomment-6208036733).
Historical main remains 0cb8ef8 and required rebuild ancestry passes.

The owner closed RuneLite normally; process inspection confirmed zero clients and no pending
updater/elevation request. The elevated helper invoked supported tools/apply-update.ps1 -Unattended
from the exact clean revision. A fresh backup verified before both JARs were replaced:

```text
C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-203043-87e9237b
backup-verification.json            335 bytes / status VERIFIED
companion-task.xml                 1,895 bytes
flipping-friend-companion.jar 17,878,060 bytes
installation.json                   458 bytes
osrs-flipping-friend.jar          486,099 bytes
companion accounts: 3 files / 31,411,896 bytes
plugin accounts:   22 files / 1,511,962 bytes
```

Backup verification checks the old JAR/manifest hashes, saved task export and account relative
names/sizes without opening or hashing account contents. Helper success=true/exitCode=0 at
04:30:59 UTC on 16 October (15 October local); sourceRevision=e5bc8da. Build: BUILD SUCCESSFUL
in 5s, four tasks executed/four up-to-date. Installed plugin 486,098 bytes/218 project classes and
companion 17,878,059 bytes/453 classes match manifest/normal build and every standard-tested
candidate class; all major 55/Java 11. Packaging differs by 20 bytes per candidate JAR only.

```text
PAIRED_INSTALL_PROOF=PASS / PAIRED_PROOF_EXIT=0
Overall status: PAIR VERIFIED / COMPANION RESPONDING / STATUS_HOST_EXIT=0
RuneLite 1.13.2 and installed plugin API match; cached artifacts verified.
COMPATIBILITY_HOST_EXIT=0
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: COLLECT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_HOST_EXIT=0
```

The agent launched tools/start.ps1 after these checks. One client initialized in 8,223 ms;
normal reflective-access warnings are retained. Outbox pending=0 after launch. Fresh post-login
capture and a new full-progress/terminal execution have not yet been observed for this pair.
Existing historical gaps, incomplete cost/profit, risk windows and uncertainty reserves remain
intact. No new buy availability or repaired historical accounting is claimed. Login and all game
input remain manual. No synthetic live POST, database edit, migration, repair, queue clearing,
backfill, account assignment, preference change or force-kill occurred.

Private normal-checkout receipts: .gradle/full-counter-{update*,install-proof,status,compatibility,
launch.*} and local-data/diagnostics/full-counter-postinstall-pipeline-20261015.txt. The source
verification (1,484 Java passed/six skips, 79 Python passed) remains valid; this receipt changes
documentation only and requires no further JAR installation or client restart.

## Full-counter progress regression and preserved live gaps — 15 October 2026

Normal rebuild is `2ebf9cbacd2b619d6619c41d7da80de3c7c7addc` (#66 documentation merge).
Installed runtime remains #64. After the earlier acknowledgement proof below, six file-only
samples at ten-second intervals from 20:07:31 to 20:08:21 local all retain pending=0 and eight
prediction bindings. A later authenticated probe takes portfolio/action/ledger/learning
484/0/406/391 ms and returns Action COLLECT, with entries refused by DRAWDOWN_LIMIT_REACHED.
The risk body contains zero bounded realized updates, marked unrealized profit 4,380 GP and
uncertainLossReserve=47,147,786 GP. This is an uncertainty reserve, not a recorded realized loss.

Read-only source tracing finds two full-counter BUYING observations immediately before matching
BOUGHT confirmations. They produced "invalid source observation" gaps, followed by the expected
"authenticated slot baseline; earlier executions remain unresolved" recovery gaps. Current
accounting contains six new ACQUIRE effects and four RECONCILE records beyond the risk cursor.
The classifier's filled>=total rejection caused the first gaps, and existing guards correctly
preserved them afterward. Raw item/row-level traces stay private in local-data.

`codex/full-counter-progress` starts at that current rebuild. Test-first commit `8d1eb4e` reproduces
two classifier and five companion failures. The fix accepts bounded full counters as nonterminal
BUY_PARTIAL/SELL_PARTIAL progress, leaving the raw BUYING/SELLING type untouched. Existing reducer
delta accounting, source identity/order, exact costs, tax ranges, terminal collection and all
contradictory-data checks remain in force. Seven companion cases cover buy/sell conservation,
duplicates, restart, missing terminal, first-seen baseline, bad counters and preserved legacy gaps.
See [the contract](../../docs/architecture/full-counter-progress.md).

The first full build found a bad assertion in the added legacy fixture: uncertain positions are
withheld from the public ledger. The corrected fixture instead verifies unchanged original source
payload, original acquisition quantity/cost rows and both retained reconciliation records. Runtime
guards were not changed to satisfy that test. Final standard verification passes: BUILD SUCCESSFUL
in 51s, all 18 tasks executed, exit 0. Java: 1,484 passed/six existing skips, zero failures/errors
(631 plugin/179 core/680 companion tests). Python: 79 tests in 83.729s / OK / exit 0. Candidate
plugin 486,078 bytes/218 project classes, companion 17,878,039 bytes/453 classes; all class major 55
(Java 11). Exact-head CI and delivery are to be recorded on the PR.
The fix is not installed yet and does not clear the existing live uncertainty reserve or invent
the missing historical acquisition effects. No manual database change or event replay occurred.

Private evidence: normal-checkout local-data/diagnostics/recommendation-ack-window-20261015-200821.json,
recommendation-stall-20261015-200846.json and live-trade-gap-trace-20261015-200949.json; ignored
.gradle/trace-live-trade-gaps.py uses mode=ro/query_only/one read transaction. Reusable worktree
.gradle/full-counter-*.log records failures and verification. Player can keep the client running
during source verification. Any later supported paired installation requires a normal manual close.

## Post-login acknowledgement and current-slot proof — 15 October 2026

At clean normal rebuild `7bf45284db856f307f81fc43274d698f4f3a6ddc` (#65 documentation merge),
the owner reports being logged in and trading. Installed runtime remains the verified #64 pair.
Read-only 20:03 local authenticated GETs observed portfolio 94 ms, action 16 ms, ledger 218 ms
and learning 532 ms. The old 32-event outbox drained normally to zero; eight original prediction
bindings remain. No queue, database or accounting state was cleared. Risk was AVAILABLE and plan
refused entries with NO_FREE_SLOT / "Every Grand Exchange slot is occupied."

```text
HTTP portfolio/current elapsedMs=94
HTTP action elapsedMs=16
HTTP ledger/current elapsedMs=218
HTTP learning/current elapsedMs=532
PLAN_STATUS=UNAVAILABLE / PLAN_OUTCOME=NO_FREE_SLOT / ALLOCATION_COUNT=0
RISK_STATUS=AVAILABLE / RISK_REASON=Durable account risk available.
ACTION=WAIT / OUTBOX_PENDING=0 / OUTBOX_PREDICTIONS=8
```

The subsequent GET-only pipeline exits 0, retains four sale updates/two unresolved/zero profit
ranges, and reports Action WAIT / Plan UNAVAILABLE with
"A managed item's instant-sell quote is stale or unsupported." This distinct financial refusal
is retained, not bypassed or presented as a stalled HTTP request. DurableRisk requires each managed
holding's instant-sell quote to be supported and no older than 120 seconds. No item or fallback price
is inferred from the generic reason. The follow-up at 20:04 reports portfolio 110 ms, action 31 ms,
ledger 1,453 ms and learning 453 ms; eight newly queued observations appear and then drain to zero
on the next file read. One above-timeout ledger read means sustained latency acceptance remains open.
No final entry GET is available without a current allocation; do not manufacture a free slot.

The installed companion JAR itself validates the next durable read, in one explicitly scoped
mode=ro transaction, using its packaged CurrentOfferReadSet.valid implementation:

```text
PACKAGED_CURRENT_READ_SET_VALID=true
CURRENT_SOURCE_COUNT=8 CAPTURE_SOURCE_COUNT=8
CAPTURE_AGE_SECONDS=4 FREE_SLOTS=0 TOTAL_SLOTS=8
CHECK_INTERVAL_MINUTES=5 RISK=MODERATE
CURRENT_EVENT_TYPES={BUYING=8}
MODE_RO_TRANSACTION_COMPLETE=true / CURRENT_READ_PROOF_EXIT=0
```

MODERATE is the persisted Medium choice. The learning read now contains 15 episodes/13 full
forecasts, seven canceled/censored and eight open/censored, with zero completed intervals.
This proves current intake/association, not intent, successful fills, trained learning or profit.
Promotion remains HOLD_FOR_VALIDATION with PUBLIC_PRICE_BASELINE. Existing recorded sales and
unknown profit coverage remain intact. These are actual live-account checks, not the 100M benchmark.

Private normal-checkout evidence: local-data/diagnostics/recommendation-{trading-probe,
trading-pipeline,trading-followup,current-read-proof}-20261015.txt and raw
recommendation-stall-20261015-200327.json / recommendation-stall-20261015-200415.json.
Ignored .gradle/ReadOnlyTradingProof.java opens only the explicitly scoped existing account via the
packaged openReadOnly method; it rolls back its read transaction and prints aggregate proof only.
No game input, synthetic live POST, database change, account assignment, preference change or
additional JAR installation occurred. Current acknowledgement/fresh eight-slot proof pass;
sustained response timing, final entry when naturally eligible, full trade/partial cancel/collect/
relist/restart and remaining UI acceptance stay open. Documentation needs no runtime restart.

## Recommendation latency pair installed; fresh login proof pending — 15 October 2026

PR #64 merged as `a3ef954d6cb919a411bd8d982e30a6555a678f00`. Both checks passed at exact
head `82645c0503b5127c959acce04633778e8902de2a` in
[run 38845928840](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38845928840):
Windows job 116797969029 and Ubuntu job 116797970834. Original Windows job 116796334644
hit one two-second socket read timeout on the first POST in AccountApiIsolationTest's
unidentified-clear case. All three cases passed in a focused local recheck (23s/exit 0), and one
unchanged CI retry passed. No test or production timeout was changed. The initial failure remains
in [the delivery receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/64#issuecomment-6207561445).

The owner closed RuneLite normally. The first elevation request was canceled before the helper
started; the owner requested another prompt. The second request launched supported
tools/apply-update.ps1 -Unattended at 03:44:32 UTC on 16 October (15 October local).
A fresh backup verified before paired replacement:

```text
C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-194438-56468ed6
backup-verification.json            335 bytes / status VERIFIED
companion-task.xml                 1,895 bytes
flipping-friend-companion.jar 17,877,081 bytes
installation.json                   458 bytes
osrs-flipping-friend.jar          486,099 bytes
companion accounts: 3 files / 26,365,624 bytes
plugin accounts:   22 files / 1,523,447 bytes
```

Helper success=true/exitCode=0 at 03:44:57 UTC. Supported build: BUILD SUCCESSFUL in 6s,
three tasks executed/five up-to-date. Installed plugin 486,099 bytes/218 project classes and
companion 17,878,060 bytes/453 classes match manifest/normal build and every standard-tested
candidate class, all Java 11. Packaging is 20 bytes larger per candidate JAR without a class change.

```text
PAIRED_INSTALL_PROOF=PASS / PAIRED_PROOF_EXIT=0
Overall status: PAIR VERIFIED / COMPANION RESPONDING / STATUS_HOST_EXIT=0
RuneLite 1.13.2 and installed plugin API match; cached artifacts verified.
COMPATIBILITY_HOST_EXIT=0
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_HOST_EXIT=0
```

Agent launch through tools/start.ps1 initialized one RuneLite client in 8,308 ms. Normal reflective
access warnings are retained. At 19:46 local, before fresh login evidence, portfolio/current took
1,672 ms, action 0 ms, ledger/current 2,547 ms and learning/current 359 ms. Plan remained UNAVAILABLE
waiting for client cash/slots. Outbox was 32 pending before installation and remained 32; the earlier
diagnosis observed 40. Nothing was cleared or repaired. Learning retains seven censored
episodes/six full forecasts/zero completed intervals and held public-price baseline promotion.
These initial cold HTTP reads are not latency acceptance. Require fresh login, normal durable
acknowledgement drain and current plan/risk/final-policy proof before claiming restored advice.

Private normal-checkout receipts: .gradle/recommendation-latency-{update*,install-proof,status,
compatibility,launch.*}; local-data/diagnostics/recommendation-postinstall-pipeline-20261015.txt,
recommendation-postlogin-probe-20261015.txt and recommendation-stall-20261015-194602.json. No game
input, synthetic live POST, manual database edit, schema/index migration, outbox reset, risk reset
or preference change occurred. Controlled trading and remaining player UI checks stay open.

## Recommendation latency diagnosis and regression source — 15 October 2026

Owner reports multi-minute gaps, then no buys after changing to 5m/Medium and following cancellation
advice. Client remains open. Normal rebuild is clean at #63 merge
`e14121b3d5c6fbf157fa4f255ee7b4c58bf72d66`; installed runtime is still #61 via tooling #62 below.
Read-only probe: portfolio/current timed out at 15,141 ms; action took 3,516 ms. Forty pending
outbox observations were preserved. Learning read showed seven canceled/censored episodes, six
full forecasts, zero completed intervals; cancellations are not failed/completed fills.

Companion-only JVM thread dump showed account ingestion holding service/store locks in
SqliteStore.currentOfferEvidence while ledger reads waited. No game process was attached or stopped.
Read-only query inspection found the existing identity index is partial. The JDBC runtime rebuilt
an automatic covering index for the current-slot join because the nonblank predicate was missing.
Same scoped database/query: original 387 ms; existing-index predicate 1 ms. Python's SQLite chose
a different plan (full scan, 63 ms), so JDBC results are the relevant runtime evidence.

`codex/recommendation-latency` starts at that rebuild. Regression `17bf941` reproduces heartbeat and
restart reads exceeding deterministic SQLite VM budgets over 30,000 fixture rows; four source-guard
cases already pass. `016018e` fixes current-slot and original risk-anchor lookups using the existing
partial index, retaining LEFT JOIN missing-source failure. Regression `79555e1` separately
reproduces reading full canonical history twice for gap scope and capital bounds. Operation-local
history sharing fixes that duplication without a cross-request cache. A later-foreign-evidence
test ensures a successful earlier read cannot hide new uncertainty. See
[the evidence-read contract](../../docs/architecture/durable-risk-window.md#evidence-read-work-budget).

Before-query-fix read-only full planning probes took 2,052/866 ms (cold/warm); the index-only
candidate took 1,269/327 ms; the final shared-history candidate took 555/196 ms, retaining all eight
current sources. These are local active-database measurements, not a latency guarantee. Final standard:
BUILD SUCCESSFUL in 57s / all 18 tasks executed / exit 0; 1,477 Java passed, six existing skips,
zero failures/errors. Python: Ran 79 tests in 100.299s / OK / exit 0 (tooling unchanged thereafter).
Candidate plugin: 486,079 bytes / 218 project classes; companion: 17,878,040 bytes / 453 project
classes; every project class targets Java 11. All probes use existing explicitly scoped data in mode=ro; raw source
and row-level results stay in ignored local-data. No DDL/index migration, database repair, event
drop, accounting/risk reset, timeout increase or game input is part of the fix. Current risk/cadence
preferences stay as the owner selected them. Faster reads are not new buy authorization; require
post-update fresh acknowledgement, current cash/slot/risk and final entry-policy proof.

Private evidence: normal-checkout local-data/diagnostics/recommendation-*, companion-stall-threads-*
and ignored .gradle read-only probes; reusable worktree .gradle/recommendation-*.log. Runtime update,
live timing and resumed advice remain pending. Batch one supported paired update after both CI
checks; player closes/logs in manually, agent handles backup/install/launch/diagnostics.

## Installed through #61; supported recovery and fresh entry read — 15 October 2026

Normal rebuild is `fc62526a793e08a8a3b68320a14d2bf57edaa186` (#62 merge). Runtime Java is the
tested #61 source; #62 changes tooling only. #61 passed both checks at
`13e3ded155c6d485b8fa44f5f799086406434019` in
[run 38828090222](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38828090222).
#62 passed Windows job 116781965493 and Ubuntu job 116781965138 at
`e89323498d531b2edc022e71b86327a8022a88eb` in
[run 38841359657](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38841359657).
[Tooling delivery receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/62#issuecomment-6206825082).
Historical main remains `0cb8ef8`; required rebuild ancestry is preserved.

After Windows elevation, the supported updater used -Unattended -ResumeBackup with the exact
verified interruption below. Its read-only proof passed before the retry and again under the
installation lock. A fresh backup verified before both JARs were replaced:

```text
C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-184127-76f3a3f8
backup-verification.json            335 bytes / status VERIFIED
companion-task.xml                 1,925 bytes
flipping-friend-companion.jar 17,832,054 bytes
installation.json                   458 bytes
osrs-flipping-friend.jar          469,023 bytes
companion accounts: 3 files / 13,000,352 bytes
plugin accounts:   22 files / 1,498,001 bytes
```

The receipt checks old JAR/manifest hashes, task export and account relative names/sizes without
opening or hashing account contents. The original failed-attempt backup and logs remain intact.
The successful helper receipt names fc62526, success=true/exitCode=0, finishing at
02:41:41 UTC on 16 October (15 October local). Supported build: BUILD SUCCESSFUL in 2s, eight tasks
up-to-date. Installed plugin 486,099 bytes / 218 project classes and companion 17,877,081 bytes /
452 classes match normal build and manifest hashes. Every project class matches the standard-tested
candidate and targets Java 11; ZIP packaging is 20 bytes larger per JAR without a class change.

```text
PAIRED_INSTALL_PROOF=PASS / PAIRED_PROOF_EXIT=0
Overall status: PAIR VERIFIED / COMPANION RESPONDING / STATUS_HOST_EXIT=0
RuneLite 1.13.2 and the installed plugin API match; cached artifacts verified.
COMPATIBILITY_HOST_EXIT=0
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
PIPELINE_HOST_EXIT=0
```

The agent started tools/start.ps1 with hidden console/log capture. One RuneLite client was observed;
initialization completed in 9,302 ms. The owner logged in; fresh authenticated account observations
arrived. Post-login pipeline exit 0 retains four sale updates/two unresolved and shows Action WAIT /
Plan READY. Read-only plan/risk/final-policy checks observed:

```text
First capture: PLAN_STATUS=READY / ALLOCATION_COUNT=3
RISK_STATUS=AVAILABLE / RISK_FRESH=True / RISK_BUDGET_POSITIVE=True
ENTRY_ACTION=WAIT / ENTRY_REASON=COMPANION_STATE_UNAVAILABLE
Later current-plan read: PLAN_STATUS=READY / ALLOCATION_COUNT=4 / RISK_STATUS=AVAILABLE
ENTRY_ACTION=PLACE_BUY / ENTRY_REASON=ENTRY_CANDIDATE_SELECTED
LEARNING_STATUS=OBSERVING / LEARNING_MODEL=PUBLIC_PRICE_BASELINE
LEARNING_PROMOTION=HOLD_FOR_VALIDATION / LEARNING_OBSERVATIONS=0 / LEARNING_FULL_FORECASTS=0
```

Both entry responses are retained, including the initial refusal; no offer was placed by the
agent. Fresh GET approval does not certify UI behavior, fills, profit or controlled restart. The
first login logs contain a RuneLite LootTrackerPlugin.onChatMessage NullPointerException at
18:43:20 local; the recorded stack contains no Flipping Friend frame. It is not a clean-exception
claim or a reproduced Flipping Friend bug. Normal reflective-access warnings are also retained.

The owner reports "everything looks good so far" and supplied two live screenshots. BUY guidance
matches the committed editor quantity/price and a normal highlighted Confirm. In the negative case,
both committed values differ: both input controls remain highlighted and Confirm says Locked-rightclick.
Both show eight free slots, so no placement
is established by those images. One image shows waiting for the account ledger; the other shows
known recorded profit 6,480 GP with four sale updates/two unresolved. Screenshot order does not
establish a timed availability sequence. Search and player key behavior are not visible in these
images and remain specifically unverified.

Subsequent player offers reached canonical intake. The first read observed one open BUY with a
full original forecast, predictionComparable=true and timingCoherent=true; no fill bound was present.
A read-only complete-prefix freeze at 18:53 local then observed three episodes, two eligible
forecasts and one unqualified episode. Both eligible horizon outcomes remain ambiguous, with zero
known completions/incompletions; they are not labeled failed fills or successful flips. Packaged
verification, independent retained-artifact verification and comparison against the pre-install
frozen baseline all exit 0. The comparison confirms an unchanged training prefix and a later
prospective cohort; promotion remains HOLD_FOR_VALIDATION, without fitting or return claims.

```text
status=VERIFIED / sourceCursor=6695 / sourceRows=5656
totalObservations=3 / eligible=2 / knownCompleted=0 / knownIncomplete=0 / ambiguous=2
manifestSha256=976e5d51f4b52db1a5eae7051587e2bb0bdb19fb39166c8a5d92daf775c5d785
READ_ONLY_FREEZE_EXIT=0 / CAPTURE_VERIFY_EXIT=0 / RETAINED_ARTIFACT_VERIFY_EXIT=0
FROZEN_PREFIX_COMPARE_EXIT=0 / status=RECORDED_PREFIX_COMPARISON
```

Private capture: normal checkout local-data/execution-evaluation/live-first-prospective-20261015-185318.
Its evaluator.jar.bin is 17,877,081 bytes; baseline is the reusable worktree's
local-data/execution-evaluation/baseline-20261015-retained. Raw sources, forecasts, scored rows and
CLI logs remain in local-data. These reads do not edit the live database or require closing the game.
Controlled fill/collection/sell completion, partial cancel/relist and restart remain pending.

Private normal-checkout evidence: .gradle/batched-forecast-{update,retry}*, batched-install-*,
batched-launch.* and local-data/diagnostics/batched-{postinstall,postlogin}-pipeline-20261015.txt,
batched-live-{plan,entry,learning}-20261015-*.json. No agent game input, synthetic live POST, manual
database edit, migration, repair, assignment or historical backfill occurred. Normal application
ingestion continues from real client observations. This documentation receipt changes no runtime
source and requires no reinstall. Player UI/search/key/Confirm checks were requested as one batch;
the screenshot Confirm/highlight checks above pass, with the remaining UI checks still open.
controlled trade/partial cancel/collect/relist/restart acceptance remains pending. Next implementation
work remains companion sell/replacement authority and #56 evaluation/learning gates.

## Interrupted paired update and tooling regression — 15 October 2026

At clean rebuild `75190a28a6b6f680ca616517aac61d2b01180574` (#61 merge), the owner approved
Windows elevation. The supported -Unattended updater created and verified
`C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-181822-c05e3e5f`:
three companion account files and 22 plugin account files; JAR/manifest hashes and task export match.
It then stopped in tools/build.ps1 when PowerShell 5.1 redirected a normal javac deprecation note
as a NativeCommandError under Stop. Receipt success=false/exitCode=1 at 02:18:33 UTC on 16 October
(15 October local). Installed JARs/manifest were not replaced; the #53 task is disabled.

`codex/update-build-recovery` starts at that rebuild. Regression `83abfda` reproduces the native
warning abort and missing interrupted-update recovery before source changes. The builder now
retains diagnostics and checks the native exit code; real build failures stay failures. Explicit
-Unattended -ResumeBackup proves the unchanged pair/manifest, original task definition except
Enabled, verified local backup and account names/sizes, then repeats proof under the lock. It never
enables the task before both copies, changes a database or rewrites the old backup. Ordinary disabled
tasks remain rejected. See [recovery workflow](../../docs/development-workflow.md).

Standard verification: BUILD SUCCESSFUL in 49s, all 18 tasks executed; 1,469 Java passed, six existing
skips, zero failures/errors. Python: Ran 79 tests in 71.147s / OK / exit 0. Exact-head CI is to be
recorded at delivery.
Runtime Java is unchanged from tested #61; installed runtime remains #53 until the supported retry.
No live readiness, launch or trading acceptance is claimed by this checkpoint.

## Exact exit cost source; paired update not started — 15 October 2026

`codex/exact-exit-costs` starts from #60 merge `36ee500`. Regression `f446c27` reproduces rounded
cost overstating actual initial sale guidance and allowing a fractional-average loss to look like
break-even. `fa31f49` reproduces the same errors in sell maintenance. Source
`4198a8cf84e53afd13314101940fb9ed96341e87` uses exact total acquisition costs in price ranking,
the durable SaleAccounting.allocateCost convention for suggested/repriced subsets, and the lowest
legal taxed quote covering those costs. Stabilization cannot defeat the exact floor; unreachable
non-loss prices are reported rather than clamped to a false break-even. Read
[the contract](../../docs/architecture/exact-exit-costs.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 48s / 18 actionable tasks: 18 executed / exit 0
Plugin: 631; Core: 179; Companion: 665 including six existing skips
Total: 1,469 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 56.930s / OK / exit 0
Candidate plugin: 486,079 bytes / 218 project classes
Candidate companion: 17,877,061 bytes / 452 project classes
Both project JARs: class major 55 (Java 11)
```

All changes are source-only; no game input, manual financial-row write, historical profit rewrite
or tax-rule migration occurred. Private evidence: reusable worktree .gradle/exact-exit-*.log.
Companion exit-policy migration and full-account performance evaluation remain open.

After #60 merge, status read verified the installed #53 pair/companion responding and no RuneLite
process was observed. One hidden elevated helper was requested for the authorized -Unattended
paired update. Windows returned "The operation was canceled by the user" before Start-Process
returned a PID. No updater receipt/log or helper process appeared; installation never started.
No backup or task/data change was performed by that attempt. Do not start a second simultaneous
updater or claim #57 onward is installed. The next successful administrator launch must target the
then-current clean rebuild revision. Login and every game action remain manual.

## Frozen evaluation and read-only account capture — 15 October 2026

PR #60 merged as `36ee500dd0b682a786adc073cae2ffb6a8502e87` after both checks at
`5993fc56eddbab1498250afef6259a5760867323` passed in
[run 38826360749](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38826360749).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/60#issuecomment-6204815483).

`codex/frozen-execution-evaluation` starts at #59 merge `0fba976`. Runtime/tool source through
`39569c2` implements a complete offline freeze -> verify -> recorded-prefix comparison workflow.
Regressions `3b71dec` (literal-hash read-only path), `25f5bec` (unassigned named gap ignored) and
`7115219` (proven clear context globally refused) precede fixes. Existing TradeGapEvidence source
proof now scopes evaluation uncertainty by affected items; unknown source scope remains held.
The read-only HTTP learning report and offline evaluator use the same complete source snapshot.

Captures retain all source rows, raw reconciliation evidence, original forecasts, hashes and the
actual executing evaluator artifact. Verification reproduces scores without opening a database.
Horizon Brier/completion-rate bounds retain early cancellations and uncertain readout intervals as
unknown. Comparison rejects changed/reversed/foreign prefixes and excludes older offers settling
later. This is not fitted learning or measured account returns. See
[the contract and commands](../../docs/architecture/frozen-execution-evaluation.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 39s / 18 actionable tasks: 18 executed / exit 0
Plugin: 623; Core: 176; Companion: 665 including six existing skips
Total: 1,458 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 57.643s / OK / exit 0
Candidate plugin: 485,476 bytes / 218 project classes
Candidate companion: 17,876,458 bytes / 452 project classes
Both project JARs: class major 55 (Java 11)
```

Packaged PowerShell 5.1 read-only capture of the explicitly scoped existing account passed, then
the retained evaluator.jar.bin independently verified its capture without reopening that database:

```text
status=VERIFIED / sourceCursor=5573 / sourceRows=4651
totalObservations=0 / eligible=0 / promotionStatus=HOLD_FOR_VALIDATION
manifestSha256=a4f47e35bc4a296c9854a1204384faded28edb48d3fb9f4feeac3deb475d1da6
READ_ONLY_CAPTURE_EXIT=0 / RETAINED_ARTIFACT_VERIFY_EXIT=0
```

Zero prospective observations reflects the installed #53 source; older rows were not backfilled.
The first capture attempt safely refused unnamed historical gaps; a reproduced source-scope fix,
not a database change, allowed capture. No rows were repaired, assigned or removed. Private data:
reusable worktree local-data/execution-evaluation/baseline-20261015-retained; logs are ignored
.gradle/frozen-evaluation-*.log. The earlier first capture remains private historical evidence.
No new runtime installation, live pipeline result or game action is claimed. Runtime remains #53;
batch #57/#58/#59 and this source for the next paired update. Issue #56 remains open for supported
censor-aware fitting, frozen challenger/training artifacts, chronological calibration/return/loss
gates and full-account replay at all four cadences. Hashes alone do not authenticate historical time.

## Accepted guidance and immutable leg forecasts — 15 October 2026

PR #59 merged as `0fba976167b310f5fb800dfe635764ba7290c1b2` after both checks at
`2b31e06d8bf3431d3319f9869dd9a314d1588114` passed in
[run 38810497176](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38810497176).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/59#issuecomment-6202774257).

`codex/immutable-execution-forecasts` starts at rebuild `f161a39` (#58 merge). Regression `9471568`
reproduces premature proposal attribution, whole-flip probability used for a buy, and missing sell
timing replaced by whole-flip timing. `8ffb3b2` reproduces the generic sell-editor quote overwriting
managed guidance. The active entry-policy converter's missing leg metadata was also observed as
a failing behavioral test before correction; it now shares conversion with the legacy adapter.
Implementation is `a18210f1fac3e064cff82fdb3025ec91a118fb80`.

Only client-thread accepted foreground guidance records evidence; paused, stale, foreign and
deferred proposals do not. Buy/sell forecasts keep actual price/quantity, configured model/version,
horizon, curve inputs, waiting, working, minimum overhead and probability. Original outbox binding
survives ACK/restart/expiry. The canonical readout validates and exposes the original snapshot;
missing older context stays missing. Managed sells recompute for actual displayed inputs; generic
personal quotes stay unknown and cannot erase an exact managed instruction. See
[the contract](../../docs/architecture/immutable-execution-forecasts.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 39s / 18 actionable tasks: 17 executed, 1 up-to-date / exit 0
Plugin: 623; Core: 176; Companion: 644 including six existing skips
Total: 1,437 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 49.509s / OK / exit 0
Candidate plugin: 485,476 bytes / 218 project classes
Candidate companion: 17,856,011 bytes / 441 project classes
Both project JARs: class major 55 (Java 11)
```

Private regression/build evidence: reusable worktree .gradle/immutable-forecast-*.log. Full-suite
source-hash/text guards were updated for reviewed metadata and converter changes; behavioral gates
remain. Runtime is still the #53 pair below; #57/#58 and this source are not installed. Batch the
next supported paired update and player acceptance. No new live pipeline or trade result is
claimed; no manual database change or agent game input occurred. Promotion stays held. #56 remains
open for censor-aware fitting, frozen baseline/manifests, chronological return/loss/calibration
gates and full-account four-cadence evaluation.

## Observation intervals and calibration fence source — 15 October 2026

PR #58 merged as `f161a3964da8e00d0f592d033eddc55e39e7231d` after both checks at
`910a5c0e5161d3be5d54a1032d186227651c7bc3` passed in
[run 38777494280](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38777494280).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/58#issuecomment-6198271091).

`codex/execution-observation-intervals` starts from #57 merge `58480d6`. Regression `d209012`
reproduces the actual account planner halving waiting estimates from forty canceled offers, or
from forty completed offers available only as aggregate totals. Prospective source reads now keep
placement, first-fill and full-completion intervals, censor canceled/ended observations, preserve
the first authenticated terminal bound across reconnects and fail closed on inconsistent clocks
or source scope. Account API GET learning/current exposes bounded diagnostic rows/full counts;
account/header/token/method isolation and SELECT-only reopening pass. The service reads this
canonical evidence and keeps the public-price baseline active pending challenger validation.

This is a promotion fence and real observation pipeline, not completed learning. Even forty
qualified completions cannot promote without forecast decomposition and chronological account
return/loss/calibration gates. Historical aggregates/claims, financial source rows, uncertain sales
and all exposure guards remain unchanged. Read [the contract](../../docs/architecture/execution-observation-intervals.md).

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 48s / 18 actionable tasks: 18 executed / exit 0
Plugin: 606; Core: 176; Companion: 641 including six existing skips
Total: 1,417 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 49.946s / OK / exit 0
Candidate plugin: 477,859 bytes; companion: 17,848,203 bytes
Both project JARs: class major 55 (Java 11)
```

New-path identity collision, contradictory-clock, source-cursor and terminal-quantity edge failures
were also observed before correction; private logs are .gradle/execution-observation-*.log in the
reusable worktree. Source is not installed; runtime remains the #53 pair below. Batch #57 and this
change with the next supported paired update. No manual live database write or agent game input
occurred. Issue #56 remains open: immutable model/horizon/wait-work forecast context, censor-aware
fitting, frozen manifests/baseline, held-out evaluation, full-account four-cadence replay and promotion.

## Execution prediction provenance source — 15 October 2026

PR #57 merged as `58480d679981e7156f979c2ff89134401aa40579` after both checks at
`b37a19d9bc0da34ab811c443a240825d7dcd84c1` passed in
[run 38772952899](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38772952899).
[Exact receipt](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/57#issuecomment-6197609988).
Branch `codex/execution-advice-provenance` started at rebuild `b516b7e` (#55). The installed
runtime remains through #53, with the live READY/entry GET receipt below; this source work is not
installed or live-accepted. Batch a later paired update, without another immediate login cycle.

Regressions `d1d18dd` (six publisher failures), `43f5c34` (five companion failures), `942fcbe`
(refreshed forecast borrowing an older clock), `878db45` (missing provenance) and `32a567d`
(saved binding schema) and `2980fa5` (same-second ambiguity) preceded their fixes. Original advice and negative bindings now persist
atomically with the existing account outbox and survive ACK/restart. The companion validates the
earliest original source and uses its prediction without rewriting raw events. Unknown/mismatched
evidence remains unpaired; existing accounting, dedup keys, gaps and historical totals remain.
See [the provenance contract](../../docs/architecture/execution-prediction-provenance.md).
Runtime implementation is `c595c42`, `08d4832` and the subsequent strict same-second fix. Current
forecast time must strictly precede first observation to train timing; same-second association can
remain diagnostic. Positive maintenance fixtures now begin with a real authenticated,
zero-fill source before a partial observation; cached or already partial starts cannot invent lineage.

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 46s / 18 actionable tasks: 18 executed / exit 0
Plugin: 606; Core: 176; Companion: 623 including six existing skips
Total: 1,399 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 59.273s / OK / exit 0
Candidate plugin: 473,199 bytes; companion: 17,837,507 bytes
Both project JARs: class major 55 (Java 11)
```

Private evidence is in the reusable worktree's ignored .gradle/execution-*.log. No live database
was changed by the agent, no new installation occurred and no game input was sent. This repairs
prediction attribution, not cancellation censoring, offline completion intervals or calibration
promotion. Issue #56 remains open for those gates and chronological full-account evaluation.

## Installed #51/#52/#53 pair; live entry read passes — 15 October 2026

At this installation checkpoint, normal rebuild was `33f4dc0f18ab6505edb16b54d7c77da3b60b6f6f`
(documentation #54). Installed runtime source is the tested #53 merge `3362b62`; no runtime source changed
in #54 or this receipt. #53 CI and full verification are recorded below. #54 passed both checks
at `37a7bd184a4b7e66595477f25e0d9ad5ffa9860e` in
[run 38765010660](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38765010660).
Runtime PR #53 passed both checks at `65d6f1fcc96703ce4f4c1cab6053df317c57445c` in
[run 38763296900](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/38763296900).
Historical main remains `0cb8ef8` and required ancestry remains intact.

The earlier elevated helper was canceled before it started. The owner then opened the supported
administrator Update shortcut. The agent observed its active process/installation lock and verified
its fresh backup read-only before the owner supplied INSTALL in that existing console. No second
updater was launched. Backup:

```text
C:\Users\Antonio\.runelite\osrs-flipping-friend\backups\20261015-080957-c3a069ab
companion-task.xml                 1,895 bytes
flipping-friend-companion.jar 17,812,619 bytes
installation.json                   458 bytes
osrs-flipping-friend.jar          461,863 bytes
companion accounts: 3 files / 10,407,584 bytes
plugin accounts:   22 files / 1,499,405 bytes
```

Prior JAR/manifest hashes matched; account relative names/sizes matched without opening/hashing
their contents. Saved/live task action and working directory matched, and both principals resolved
to the current-user SID. An initial text-only principal comparison differed because SID and display
name are different representations; resolved identity verification passed. This was an interactive
update with recorded pre-install checks, not an unattended backup-verification.json receipt.

The new manifest names that backup. Installed plugin 469,023 bytes and companion 17,832,054 bytes
match their normal-checkout builds and manifest hashes. All 214 plugin / 435 companion project
classes exactly match the standard-tested candidate; all target Java 11. The 20-byte package-size
difference from candidate JARs does not change any project class. Read-only receipt:

```text
PAIRED_INSTALL_PROOF=PASS / PAIRED_PROOF_EXIT=0
Overall status: PAIR VERIFIED / COMPANION RESPONDING
STATUS_HOST_EXIT=0
RuneLite 1.13.2 and the installed plugin API match; cached artifacts verified.
COMPATIBILITY_HOST_EXIT=0
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [<account-id>] Ledger: 4 sale updates; 2 unresolved; 0 profit ranges.
PASS [<account-id>] Action: WAIT. Plan: UNAVAILABLE. Waiting for the game client to report your coins and free slots.
PIPELINE_HOST_EXIT=0
```

The agent launched tools/start.ps1 with a hidden console and captured logs. One RuneLite client was
observed; initialization completed. Fresh authenticated account observations subsequently arrived.
The post-launch GET-only pipeline exited 0, preserving four sale updates/two unresolved, with
Action WAIT / Plan READY. A separate account-scoped GET observed an unavailable current-proof
response; a later read returned the following actual result:

```text
PLAN_STATUS=READY
ALLOCATION_COUNT=4
RISK_STATUS=AVAILABLE
RISK_FRESH=True
RISK_BUDGET_POSITIVE=True
ENTRY_DECISION=ENTRY
ENTRY_ACTION=PLACE_BUY
ENTRY_REASON=ENTRY_CANDIDATE_SELECTED
GET_ONLY_PROOF_COMPLETE=True
LAUNCH_ERROR_EXCEPTION_LINES=0
```

Final approval was a read-only policy GET, not a placed offer. Do not infer UI selection, successful
fills, realized returns or controlled trade/restart acceptance from it. Login UI itself was not
inspected. Launch stderr contains RuneLite's reflective-access warnings; no ERROR/Exception line
was found in either captured launch log at the check. No agent game input, synthetic live POST,
manual database edit, repair, migration, historical backfill or owner assignment occurred. Normal
application accounting/risk ingestion continues from real client observations.

Private normal-checkout evidence: .gradle/durable-risk-install-proof.py, .gradle/durable-risk-launch.*,
and local-data/diagnostics/durable-risk-{postinstall,postlaunch}-pipeline-20261015.txt plus
durable-risk-live-{plan,entry}-20261015.json. The old canceled helper receipt remains absent.
Keep this current checkpoint compact; previous full states are archived below. Next: player home
checklist acceptance, calibration provenance/censoring audit and companion sell/replacement work,
followed by chronological whole-account cadence evaluation. No additional install for this
documentation-only receipt is required.

## Source contract and verification

Final runtime source is `ef4d863bff77dfbf0355fa2f399688833b0b818e`; the merged tree includes its
regressions and documentation. The original foreign-journal-session regression is `774a521`, local
cache isolation is `bb4339d`, durable migration is `a97e692`, and schema regressions are `02744c7`
before the final fix. The retention failure was also reproduced before its correction.

```text
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
BUILD SUCCESSFUL in 44s / 18 actionable tasks: 18 executed
RISK_FINAL_STANDARD_EXIT=0
Plugin: 591; Core: 175; Companion: 613 including six existing skips
Total: 1,373 passed; 6 skipped; 0 failures/errors
Python: Ran 63 tests in 48.631s / OK / RISK_PYTHON_EXIT=0
Candidate plugin: 469,003 bytes; companion: 17,832,034 bytes
Both project JARs: class major 55 (Java 11)
```

The earlier complete 39s build passed with RuneLite open; the final schema-corrected build ran
after its normal close. Native RuneLiteLAF Plan rendering passed clipping checks and visual review.
Private build/regression evidence remains under the reusable worktree's ignored .gradle/durable-risk-*.
No extra local runtime rebuild is needed for this documentation-only checkpoint: Java sources match
3362b62 and the exact-head CI-tested tree.

The live planner and final entry approval use acknowledged fresh eight-slot/cash proof, conservative
historical capital bounds, paused affected items and one durable prospective risk result. The risk
window records an account/session/cursor, uses realized profit ranges, fresh taxed marks and possible
loss reserves, and survives restart. Client journal totals and wire-supplied risk never authorize
runtime entries. Unknown profit stays unknown. No old unscoped database is assigned, no history is
backfilled, and no live financial rows were repaired or pruned.

## Active source map

| Boundary | Contract/source |
| --- | --- |
| Current cash, offers and historical bounds | [bounded-current-planning.md](../../docs/architecture/bounded-current-planning.md); CurrentOfferReadSet, BoundedHistoricalExposure, BankCashEvidence |
| Durable risk and restart | [durable-risk-window.md](../../docs/architecture/durable-risk-window.md); AccountRiskSnapshot, DurableRisk, RiskWindow, RiskLedgerEvidence |
| Accounting and trade ownership | [durable-profit-ledger.md](../../docs/architecture/durable-profit-ledger.md), [automatic-trade-scope.md](../../docs/architecture/automatic-trade-scope.md), [trade-gap-scoping.md](../../docs/architecture/trade-gap-scoping.md) |
| Observation recovery and learning writes | [offer-observation-recovery.md](../../docs/architecture/offer-observation-recovery.md), [execution-evidence-atomicity.md](../../docs/architecture/execution-evidence-atomicity.md) |
| Compact UI and manual assistance | [compact-panel.md](../../docs/architecture/compact-panel.md), [trade-input-assistance.md](../../docs/architecture/trade-input-assistance.md) |
| Delivery and home acceptance | [development-workflow.md](../../docs/development-workflow.md), [HOME TEST CHECKLIST.md](../../HOME%20TEST%20CHECKLIST.md) |

## Remaining work and historical evidence

Complete controlled acceptance from the current installed READY/entry proof. Player login
and all game input remain manual. Then finish companion sell/replacement workflows, remaining
calibration authority, controlled UI/partial-fill/cancel/collect/restart acceptance and chronological
whole-account replay. Benchmark 100M GP, members, eight slots, with 5m/30m/2h/8h visits reported
separately. Historical Copilot exports lack session/cadence labels; do not invent GP/hour or superiority.

Older full checkpoints are preserved in [handoff history](../../docs/history/handoff-through-2026-10-15.md)
and [task history](../../docs/history/tasks-through-2026-10-15.md). They include detailed PR #31 live
verification steps, PR #49 installation receipt, earlier PRs and all unfinished acceptance evidence.
Read them only for a specific investigation; their older states and instructions are superseded by
this checkpoint and AGENTS.md. Personal CSVs/raw diagnostics stay ignored in local-data/.
