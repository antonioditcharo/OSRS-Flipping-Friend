# Home-PC test checklist

The [combined PR #43](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/43) is approved,
merged and installed. It contains the compact UI, automatic trade scope, walkthrough/Confirm
protection and search/key assistance from #40/#41/#42. RuneLite then updated to 1.13.2, so the
compatibility continuation requires a fresh paired update. Source tests and isolated cached-client
compatibility pass; real 1.13.2 installation, login and live acceptance are pending.

## 1. Prepare the right checkout

Require both CI checks and merge of `codex/runelite-1-13-2` before installing. Do not install older
individual slices separately. `main` is historical. Use the regular project folder, not a Codex
temporary worktree or an old desktop shortcut. Close RuneLite yourself.

If the regular checkout has not already been updated, open PowerShell and use this guarded block.
It stops for local changes, a missing candidate on the rebuild branch, or any Git failure.
It does not reset, stash, force, delete branches or discard files.

```powershell
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath 'C:\Users\Antonio\OSRS Flipping Friend Rebuild'
git status --short --branch
if ($LASTEXITCODE -ne 0) { throw 'Status failed. Stop and report the output.' }
$localChanges = git status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'Status failed. Stop and report the output.' }
if ($localChanges) { throw 'Local changes exist. Stop without discarding anything.' }
git fetch origin
if ($LASTEXITCODE -ne 0) { throw 'Fetch failed. Stop and report the output.' }
git merge-base --is-ancestor c32e7289b1466b8348d581e7c75447714e497232 "origin/CoPilot's-Rebuild-from-Main"
if ($LASTEXITCODE -ne 0) { throw 'The complete candidate is not on the rebuild branch. Do not install yet.' }
git switch "CoPilot's-Rebuild-from-Main"
if ($LASTEXITCODE -ne 0) { throw 'Switch failed. Stop and report the output.' }
git merge --ff-only "origin/CoPilot's-Rebuild-from-Main"
if ($LASTEXITCODE -ne 0) { throw 'Fast-forward failed. Stop; do not reset or force.' }
git status --short --branch
git rev-parse HEAD
```

The ancestry guard assumes the normal merge-commit strategy used for this combined PR. If someone
squash-merges it, report the guard result for verification rather than bypassing it. The source
anchor includes the 1.13.2 source pin, update preflight, final budget fix and all integrated slices.

## 2. Install the paired update

1. From that updated folder, double-click **4 - Update.bat**. It checks the official RuneLite release,
   then builds/installs both supported JARs. Expect `RuneLite release preflight passed: 1.13.2 matches the source API.`
   Existing data and saved login are retained; this is not a fresh installation.
2. At **Type INSTALL**, stop. Send the fresh backup listing here, including paths and sizes, for
   the requested backup review. It is normal for the companion to be stopped at this point.
3. After that review/go-ahead, type uppercase **INSTALL** and press Enter. Capture the final output
   before closing the window. Expect successful build, companion readiness and both JARs verified.
4. Update does not start the game. Before launching, run **3 - Status.bat** and the read-only
   `tools/run-dev.ps1 -CheckOnly`. Both must pass on the real installed pair. Then double-click
   **2 - Start Flipping Friend.bat** and log in yourself.

If Update reports a disabled or foreign task, or any error, stop and send its output. Re-setup is
the supported returning-user path when needed, but do not guess a repair or repeatedly install.
Never copy just one JAR, replace a database, clear account state or use a first-time data reset.

## 3. Establish the baseline before trading

Run **3 - Status.bat** and save its output. After manual login, run this in PowerShell from the
project folder:

```powershell
.\tools\pipeline-check.ps1 -SkipBuild
```

Send both outputs here before the trading tests. Pipeline makes GET-only requests and writes no
synthetic trades. Exit zero verifies transport/ledger checks; it does not guarantee a buy plan is
available. Preserve any WAIT, UNAVAILABLE or reconciliation reason. Existing account faults may
still block entries and must not be cleared by editing records.

## 4. Check the UI without placing an offer

Perform one check at a time and report any unexpected result before continuing.

| Check | Expected result |
| --- | --- |
| Sidebar | One compact Suggested Action, action icons, cadence/risk, Recorded profit and spendable budget/free slots; no old accordion or classification controls. |
| Preferences and records | Gear opens Settings. More opens Recommendation, graph, Plan, Holdings, Performance, Local history, Bank estimate and Missed flips. Close/reopen works; long text is readable at your normal UI scale. |
| Presets | 5m, 30m, 2h and 8h persist as their real intervals. A retained custom setting shows its actual minutes. Return to 5m for the initial manual test. |
| Search | With actionable BUY advice, an empty GE BUY search shows Suggested: item. You click to select it. Typing another query hides it; native previous-search/results still work. If no buy is available, record this check as blocked. |
| Input key | In RuneLite > Flipping Friend > Guidance, assign Fill recommended price/quantity to a spare key, for example E. Enter is not a valid fill key. Open quantity, press/release your key, inspect, then manually Enter. Repeat for price. |
| Highlights and Confirm | Wrong committed quantity/price stay highlighted and normal Confirm is locked. Correct both and close the prompt: Confirm becomes available. Filling visible text alone must not unlock it. |
| Personal override | Right-click Confirm and inspect its native Confirm option, then dismiss the menu. Selecting that option places a real offer; use it only for an intended personal trade. |
| Pause and boundaries | Pause withdraws guidance/search/key assistance. Resume obtains fresh advice. A held key cannot advance through fields. Unassigning the key, closing the editor and unrelated chat/bank input leave normal typing intact. |
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

For restart, close RuneLite normally and use the supported Start wrapper again. Never kill it to
simulate a crash during initial acceptance. Repeat the read-only pipeline after relevant changes.

If a check fails, pause that game test and send the exact message, screenshot, pipeline output and
the last manual step performed; avoid including login credentials. All game actions are yours. The agent records
observed outcomes and reproduces a real bug in a regression before changing source. No live
database repair, migration or deletion is part of this test.
