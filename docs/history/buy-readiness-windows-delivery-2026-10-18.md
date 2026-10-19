# Current-stock buy pauses and maximized investigation windows

18 October 2026 (America/Anchorage). Source branch `codex/buy-readiness-and-window-size`.
This increment is not yet installed; runtime remains the verified #96 pair. Standard verification
passes; both exact-head CI checks, paired delivery and player acceptance remain pending.

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

Candidate plugin: 566,292 bytes, SHA-256 `742b80336e5fb2e8ad30a98c79ae80f8f417e75a4a862b9e2a1921ab4562706e`.
Candidate companion: 18,044,098 bytes, SHA-256 `5522a6945fdbe192131cc56524b884c6144128b5e2f5579578ebb1fc210c3acc`.
These are verified build identities, not a claim of installation.

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
