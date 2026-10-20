# Supported RuneLite release and paired update preflight

20 October continuation: the official release changed to **1.13.3** during #123 delivery. The
preflight rejected the 1.13.2 candidate before replacing files or stopping the companion. Source
compile/test/manifest now target 1.13.3; launcher fixtures cover its classpath and refusal of the
previous 1.13.2 plugin. The official
[release comparison](https://github.com/runelite/runelite/compare/runelite-parent-1.13.2...runelite-parent-1.13.3)
retains the GE long monetary methods, GE setup/chat constants and observation events used here.
The changed Client interface adds account switching; this assistant does not invoke it or automate
authentication. Existing observation, editor/highlight, player-key and cancellation regressions run
against the new dependency. Actual full build, exact-head CI, paired install and native load state:
local-data/runelite-1133-20261020/delivery-state.json. #123 workflow changes are included; no risk
or financial data is migrated. Live login and controlled trading remain separate acceptance gates.

Earlier compatibility receipt:

14 October 2026. Tested source: `c32e7289b1466b8348d581e7c75447714e497232`, API **1.13.2**.
See the newest [handoff](../../HANDOFF.md) for the installed pair and live acceptance status.

## Why this update is needed

After the approved compact UI pair was installed, the official RuneLite release changed to 1.13.2
while the source and plugin manifest still declared 1.13.1. Companion health passed; the launcher
correctly refused to start an untested client/API combination. Updating official client files alone
cannot change the plugin's compile-time API declaration.

The source pins compile dependencies, tests and the plugin manifest to one tested version. It does
not select the newest cached JAR or mix releases. The launcher reads the official bootstrap,
selects the current platform's artifacts, checks cached hashes and the installed plugin API, and
builds an explicit classpath. CheckOnly validates this without launching or downloading artifacts.
An older cached release remains untouched and is excluded from the classpath.

The official [1.13.1 to 1.13.2 comparison](https://github.com/runelite/runelite/compare/runelite-parent-1.13.1...runelite-parent-1.13.2)
was reviewed for the GE offer APIs, chat/editor constants and account observation boundaries used
here. The long GE offer methods remain present. Version 1.13.2 names the existing price varp
`VarPlayerID.GE_NEWOFFER_PRICE` (5753); GeEditorValues now uses that official constant. This is
source compatibility evidence, separate from live UI acceptance.

## Installation behavior

`tools/apply-update.ps1` checks the official
[bootstrap release](https://static.runelite.net/bootstrap.json) against `build.gradle` before
backup, task stopping or copying either JAR. This applies to setup, re-setup and update, including
SkipBuild. A newer release, failed request or malformed response leaves the running installation
unchanged at this boundary. A successful preflight prints:

```text
RuneLite release preflight passed: 1.13.2 matches the source API.
```

This deliberately requires reachable valid release metadata when installing. Status and the
companion-only StartOnly path bypass installation preflight. The game launcher still performs its
own version/artifact checks when used to start RuneLite. A release can change between install and
launch, so the launch check remains necessary. Passing health does not certify client compatibility.

After release preflight, the existing paired installer still requires a dated backup review and
uppercase INSTALL before building/copying both JARs. It validates installed hashes, task checkout
and scoped companion health. It never replaces a database or clears reconciliation evidence.
Run updates from the normal rebuild checkout; do not install from a temporary development worktree.

## Regression and verification evidence

| Check | Result |
| --- | --- |
| Current-release launcher fixtures with real source pin | Three failures before the pin update; all 11 launcher tests pass afterward |
| Update mismatch / missing / malformed release metadata | Three failures before preflight; all pass afterward with original JARs, manifest and task state preserved |
| Standard clean Java build, all modules | 1,278 passed, six existing skips; both JARs built; Java 11 project bytecode |
| Full Python tooling suite | 54 tests pass |
| Candidate against 31 actual official cached Windows artifacts | Isolated profile, actual run-dev CheckOnly exit zero; no client launch |
| Real installed 1.13.2 pair and login | PASS at rebuild be77e67 after fresh reviewed backup and paired update; actual status/CheckOnly exit zero and manual login observed |
| Live pipeline and manual trade/UI/restart tests | Pipeline FAIL: ledger HTTP 500 at be77e67; startup search exception also observed. Follow-up on codex/live-ledger-startup; controlled trading acceptance pending |

Disposable validation copies contain only public JARs and candidate source. Login files, live
account records and databases are not copied. Logs stay in ignored `.gradle/` outside build.
Future releases require regression/compatibility review, the standard build and current-head
Windows/Linux CI; changing a version pin solely to bypass an error is not validation.

