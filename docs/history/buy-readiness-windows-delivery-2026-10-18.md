# Current-stock buy pauses and maximized investigation windows

18 October 2026 (America/Anchorage). Source branch `codex/buy-readiness-and-window-size`.
PR [#98](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/98) is merged as `f662310` after
both exact-head Windows/Linux checks passed on source `82e883b698c044a803cd60da35fe04a65b11822f`,
run [39192695351](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39192695351).
The clean primary checkout fast-forwarded. After the owner closed RuneLite normally, the supported
paired updater installed #98 at **2026-10-19 05:06:38 UTC (18 October 21:06 local)**. Installed/native
player acceptance is pending; this receipt separates delivery from that acceptance.

The owner reports intermittent buy pauses apparently concerning inaccurate records, and requests
full-screen native investigation windows. Read-only retained plan analysis distinguishes legacy
history-policy refusals from 362 newer current-policy valuation refusals in a bounded 3,000-plan
sample. Current-policy historical uncertainty remained separate. Current stock physically in sell
offers could still be unpriced for risk because a recent supported instant-sell quote was absent.
Private source/account examples stay under `local-data/buy-readiness-20261018/`.

## Implemented behavior

The current-stock card says **Waiting for stock valuation**. Details name the item, quantity, exact
cost reserved and either missing physical evidence, a missing bid or the actual quote age/120s
cutoff. Recommendation and Plan expose the reason and explain automatic reevaluation. Current
offer proof failures expose their own current, account-scoped reason, naming the affected item;
foreign/expired diagnostics cannot explain the current account's refusal.

A newer market snapshot retries a durable risk refusal inside normal plan spacing. It still
executes the same complete account/slot/source/exposure/price/risk checks; there is no cached-buy
fallback. Three regressions failed before the fix: the misleading history headline, missing item/
age evidence and a retained risk refusal after newer prices arrived inside the spacing interval.
Focused policy/presentation/heartbeat/history/navigation regressions now pass.

Risk arithmetic is unchanged: recent price marks, missing/unseen stock's full exact cost reserve,
confirmed losses, the 15% allowance and original loss anchor remain. Historical uncertainty never
becomes current loss or capital. Genuine current-stock or live-offer risk can still withhold buys;
the change does not erase those waits or promise an eligible trade before their evidence recovers.

Records views and Missed flips now use native resizable `JFrame` windows, maximized by default on
RuneLite's monitor. Standard OS title bars, maximize/restore and taskbar work area remain available.
Graph expansion toggles that native frame state. The compact sidebar, table filtering/selection
and account/logout clearing remain. Runtime monitor/focus/maximize and guided-trading acceptance
must be checked separately from headless component tests.

No live account/database repair, financial replay, risk reset, task permission change, game input,
private Copilot dependency or learned-model activation is introduced.

## Verification and player check

Final Windows standard clean test/jar/shadowJar passed in **92s / 18 tasks**: **1,658 Java passed,
six existing skips** (plugin 725, core 192, companion 741 passed/six skipped). All project bytecode
targets Java 11. Tooling: **108 Python tests passed in 145.312s**. An earlier clean build also passed;
the final build adds the confirmed-loss explanation regression. No financial or quote guard changes.

Installed plugin: 566,292 bytes, SHA-256 `742b80336e5fb2e8ad30a98c79ae80f8f417e75a4a862b9e2a1921ab4562706e`.
Installed companion: 18,044,098 bytes, SHA-256 `5522a6945fdbe192131cc56524b884c6144128b5e2f5579578ebb1fc210c3acc`.
Both installed files match the verified build identities and installation manifest.

An additional read-only comparison uses a previously retained actual account/market capture and
the retained #96 evaluator versus the new packaged evaluator. Every financial risk field is
identical. This particular available-risk example also retains its original explanation; blocked
quote details/recovery are established by the regression fixtures, not inferred from that example.
`frozen-risk-comparison.json` retains the comparison privately. Current pre-update financial proof
freezes 44,076 canonical rows, 1,393 financial effects and the sole original loss anchor. The
current task XML/SDDL and installed #96 manifest are retained for after-update comparison.

## Installed delivery

`tools/apply-update.ps1 -Unattended -SkipBuild` exited 0 after rechecking the existing verified
pair, closed-client guard and official RuneLite 1.13.2/source API. Fresh backup
**20261018-210634-e9da041d** is VERIFIED: three companion-account and 23 plugin-account files,
JAR/manifest hashes and original task export. Process lifecycle stays 1; task XML and SDDL text
are identical. A first raw-byte assertion noticed the native PowerShell snapshot's UTF-8 BOM;
the retained before/after files decode to exactly identical text. No task change or failed update
occurred, and the original evidence files were not rewritten.

The read-only check preserves the exact 44,076-row canonical prefix, 1,393 financial effects and
sole original risk anchor. Two legitimate later financial effects were already appended and are
not used to rewrite that prefix. Status, launcher CheckOnly and the explicitly scoped GET-only
pipeline all exit 0. The pre-login plan is UNAVAILABLE because fresh client cash/slots are absent;
that is an expected refusal, not evidence of current entry eligibility. No synthetic account,
offer or trade POST was used.

Supported Start launched RuneLite 1.13.2 at 21:07 local; startup logs confirm the installed plugin
was side-loaded. The owner was asked for one combined login/GE/native-window check, replacing the
earlier graph/login request. Fresh authenticated slot/entry proof and native restore/maximize,
filtering and sustained guidance acceptance remain pending. Transport/build checks cannot stand
in for those results.

Private installation receipts: `update.log`, `installation-{before,after,verification}.json`,
`financial-preservation.json`, retained task snapshots, `status-after.log`,
`compatibility-after.log`, `pipeline-after.log` and `start-after.log` in the directory above.

Private receipts: `before-fix.log`, `before-companion-fix.log`, `targeted-final.log`,
`clean-build.log`, `python-tests.log`, retained scoped GETs and `retained-refusals.json` under the
private directory above. The first combined filtered command selected an unrelated core test task;
using exact `:test` and `:companion:test` task paths passed, with unchanged core tests left for the
standard clean build. No test exclusion or guard was weakened.

After a supported paired update, open Records → Price graph, another Records view and Missed flips:
each should open maximized with native restore/maximize controls. Verify graph expansion agrees
with those controls and that Missed flips stays populated while filtering. When a buy pause occurs,
open Recommendation/Plan and inspect the exact current-stock or offer reason. Agent collects fresh
account/slot/final-policy evidence directly. Login and every actual GE action remain manual.
