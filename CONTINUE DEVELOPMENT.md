# Continue development in another environment

Read AGENTS.md, HANDOFF.md and TASKS.md in full before changing code. The active base is
`CoPilot's-Rebuild-from-Main`; main is historical. Current working slice: `codex/safe-sell-relist`.
The installed Windows runtime remains #77; this source slice is not installed. Do not label it
live-tested or claim that it resolves the old stock in issue #70.

PR #79 is open and unmerged. Local verification passes (1,555 Java tests, six skips; 103 tooling
tests). GitHub previously refused both CI jobs before starting because of an account billing lock.
The owner reports billing updated on 17 October; obtain both latest-head checks before merging;
do not bypass the merge gate or treat these refused jobs as executed tests. The final receipt-only
commit does not change runtime source or require another local clean build.

## Resume this slice

Start with a clean checkout. Stop on unexpected changes; never reset, clean, stash or discard them.
Fetch and inspect before selecting the remote branch:

```powershell
git status --short --branch
git fetch origin
git log -5 --oneline origin/codex/safe-sell-relist
```

If the local branch already exists, switch to it and fast-forward only. Otherwise create its
tracking branch with `git switch --track origin/codex/safe-sell-relist`.

```powershell
git switch codex/safe-sell-relist
git merge --ff-only origin/codex/safe-sell-relist
git status --short --branch
Get-Content -Raw AGENTS.md
Get-Content -Raw HANDOFF.md
Get-Content -Raw TASKS.md
```

In a Linux virtual environment the Git commands are the same; use `cat` for those three reads.
The regression commits intentionally fail against the old production code; use the latest branch
head with the fixes. Inspect the linked PR and both exact-head CI checks before any merge.

## Verify changes when needed

Windows (use your actual JDK path if different):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-23'
.\gradlew.bat clean test jar :companion:shadowJar --console=plain
python -m unittest discover -s tools/tests
```

Linux with an installed supported JDK:

```bash
bash ./gradlew clean test jar :companion:shadowJar --console=plain
python3 -m unittest discover -s tools/tests
```

Bytecode remains Java 11. Save redirected logs outside build/. Do not repeatedly run a passing
suite unless code changes or a failure/concern justifies it. Runtime updates happen only on the
home Windows installation using the supported paired wrapper and verified backup; do not copy
JARs individually or transfer private account databases/credentials to a cloud environment.

## Next engineering work

1. Review the safe relist slice and its behavioral tests, then finish any CI findings. Preserve
   every unchecked gate in TASKS. No installed-client claim follows from passing source tests.
2. Consolidate managed exits and sell maintenance into activated companion policies. Audit the
   actual call chain in FlippingFriendPlugin.requestEngineRefresh and SuggestionEngine, not just
   the existing inactive policy interfaces. Keep cancellation, collection and replacement coherent
   through partial fills/restart, with one durable acquisition ledger and fresh quote evidence.
3. Resolve #70 only through auditable positive evidence. The retained audit and independent Copilot
   history do not establish current quantities/costs. Never replay financial events, edit a live
   database, reset risk or relabel affected stock as personal to unblock recommendations.
4. Complete remaining ledger consumers, account-level chronological replay and gated learning.
   Evaluate 100M members/eight slots separately at 5m/30m/2h/8h. Baseline learning stays held until
   chronological return/loss/calibration gates pass. Then complete the feature-parity release gates.

The coding assistant should do implementation, tests, documentation and Git work directly when
its environment permits. When commands must be pasted by the owner, batch them into reviewable
blocks and explain their purpose. Login and every game action remain manual. No additional setup,
database repair, admin helper or update loop is needed merely to continue source development.
