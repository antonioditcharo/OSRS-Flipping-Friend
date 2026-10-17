# Home-PC test checklist

The default assisted workflow is now batched: the agent handles GitHub, builds, backup verification,
paired updates, status, compatibility, pipeline and logs. You do not need to paste console output,
reply at INSTALL, or respond after every check. See [development workflow](docs/development-workflow.md).

Runtime through PR #80 is installed after verified backup 20261017-131556-67c07d16. Both runtime PRs
#79/#80 passed final Windows/Linux CI. Installed hashes/Java 11, native status, RuneLite 1.13.2
compatibility and scoped GET pipeline pass. Supported relaunch logs side-loading and initialization;
current login/UI/trading acceptance remains unobserved. Latest reads: 11 sale updates/eight unresolved,
WAIT/UNAVAILABLE awaiting fresh client coins/slots. Historical READY/entry approvals do not establish
current buy readiness. The public-price baseline remains active with learning promotion held.
See [the delivery receipt](docs/history/recommendation-delivery-2026-10-17.md).
Open the Flipping Friend panel and work through the acceptance batch below when convenient.
The agent reads diagnostics directly; no additional install or pasted logs are needed.
Historical gaps and controlled UI/trade/restart acceptance remain open.

Owner screenshots on 15 October verify the quantity/price highlights and Confirm lock for mismatched
inputs, and normal Confirm for matching inputs. The compact panel and recorded-profit coverage are
visible. Search, player key and controlled trade/recovery outcomes still need explicit acceptance.

## 1. Prepare one candidate

The agent batches related fixes, runs regression and standard checks, waits for exact-head CI,
merges and updates the normal rebuild checkout. Historical main is preserved. Tooling/documentation
changes alone do not require another JAR installation when runtime code is unchanged.

## 2. Update and start once

When runtime code changes, the agent uses the supported paired updater with -Unattended. It creates
and verifies a fresh backup, records a receipt and installs both JARs. The installer still requires
RuneLite closed for replacement, but development clean builds no longer require a logout. Group
changes into one final restart. The agent starts the client; you log in yourself.

The numbered wrappers remain available if you choose self-service. First setup/re-setup retains
its interactive flow; ordinary agent-managed updates require no separate INSTALL confirmation.
Saved login and account data are retained. No database repair, reset or one-JAR copy is part of this.

## 3. Establish the baseline automatically

The agent reads status, launcher CheckOnly, GET-only pipeline and logs directly. After login it
checks fresh account observations before controlled trading. A successful pipeline confirms reads,
not buy readiness. WAIT, UNAVAILABLE and reconciliation reasons remain meaningful evidence.
No synthetic trades are posted. Report only unexpected UI behavior or another result the agent
cannot observe directly; routine output copying is unnecessary.

## 4. Check the UI without placing an offer

Work through these as one acceptance batch. Report unexpected results together; no reply is needed after each row.

| Check | Expected result |
| --- | --- |
| Sidebar | One compact Suggested Action, action icons, cadence/risk, Recorded profit and spendable budget/free slots; no old accordion or classification controls. |
| Preferences and records | Gear opens Settings. More opens Recommendation, graph, Plan, Holdings, Performance, Local history, Bank estimate and Missed flips. Close/reopen works; long text is readable at your normal UI scale. |
| Account risk in Plan | Fresh evidence shows a loss bound, remaining allowance, uncertainty reserve and tracking start. Missing data says unavailable. Restart preserves the same window and recorded losses; do not create losses just to test the breaker. |
| Presets | 5m, 30m, 2h and 8h persist as their real intervals. A retained custom setting shows its actual minutes. Return to 5m for the initial manual test. |
| Search | With actionable BUY advice, an empty GE BUY search shows Suggested: item. You click to select it. Typing another query hides it; native previous-search/results still work. If no buy is available, record this check as blocked. |
| Input key | In RuneLite > Flipping Friend > Guidance, assign Fill recommended price/quantity to a spare key, for example E. Enter is not a valid fill key. Open quantity, press/release your key, inspect, then manually Enter. Repeat for price. |
| Highlights and Confirm | Wrong committed quantity/price stay highlighted and normal Confirm is locked. Correct both and close the prompt: Confirm becomes available. Filling visible text alone must not unlock it. |
| Personal override | Right-click Confirm and inspect its native Confirm option, then dismiss the menu. Selecting that option places a real offer; use it only for an intended personal trade. |
| Pause and boundaries | Pause withdraws guidance/search/key assistance. Resume obtains fresh advice. A held key cannot advance through fields. Unassigning the key, closing the editor and unrelated chat/bank input leave normal typing intact. |
| Entry withdrawal | If entry advice becomes WAIT while a buy editor is open, the old buy guidance disappears immediately. A key fill queued before refusal must not run after it, even if the same buy returns. Do not manufacture a loss or accounting error to trigger this. |
| Personal items | Hammer, equipment, runes and other routine supplies are not unsolicited sell recommendations. Manually selecting an owned item in SELL setup can show a fresh quote; opening the setup does not create a sale or known profit. |

Keep Protect trade confirmation and Suggested item in GE search enabled for these tests. Do not
submit a mismatched offer just to probe the lock. If no safe recommendation exists, do not override
an accounting block to manufacture one.

## 5. Controlled trade and recovery checks

Only after baseline/UI checks are satisfactory, manually perform an affordable trade you intend
to make. Record item, quantities, unit prices, times and actual spent/received GP. The 100M benchmark
is a planning contract, not an instruction to risk the entire bank during acceptance.

Check observed partial fills, cancel/collect guidance, remaining managed quantity, sell completion
and restart without duplicate profit or resurrected stock. An actual personal SELL should enter
maintenance while its unknown acquisition cost/profit remains unknown. Remaining personal units
of the same item must not become managed stock. Use More > Missed flips to inspect incomplete
evidence; do not use it as proof of current inventory. Compare Recorded profit and Performance to
the durable evidence, preserving ranges and unavailable states.

For an intended cancel/collect/relist, confirm that suggested quantity does not include extra personal
units and managed profit retains the exact allocated cost/taxed floor. A replacement buy must have
fresh companion approval for the same item and no more than the cancelled remainder. If no current
allocation fits, WAIT is expected; do not enlarge or override a trade just to complete this check.

For restart, close RuneLite normally and use the supported Start wrapper again. Never kill it to
simulate a crash during initial acceptance. Repeat the read-only pipeline after relevant changes.

If a check fails, pause that game test and report the visible problem and last manual action.
The agent collects logs and pipeline output directly; screenshots help only for otherwise unobservable UI issues.
All game actions are yours. The agent records
observed outcomes and reproduces a real bug in a regression before changing source. No live
database repair, migration or deletion is part of this test.
