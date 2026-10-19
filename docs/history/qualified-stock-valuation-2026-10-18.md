# Quiet-stock valuation candidate

18 October 2026 (America/Anchorage). The owner explicitly approved the prepared policy and normal
client close. [#101](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/101) is merged as
`5260586`, source `5ec358ea420d0d7c0b54076b9eb4f66fcd9c3bb9`, after both exact-head Windows/Linux
checks passed in [run 39214094448](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39214094448).
The clean primary checkout fast-forwarded. The supported paired update is INSTALLED. Fresh source/
valuation checks and retained READY plans pass; sustained, final eligible entry and player acceptance
remain pending, distinct from source/build/delivery and the bounded observations below.

The owner reports an eleven-minute "Waiting for stock valuation" pause with cash and three open
slots. A bounded read-only inspection retains 1,500 recent plans, including 531 risk refusals.
The current-stock full-cost reserve is driven by trades older than 120 seconds; historical unknown
money remains separate. The #98 retry/explanation work did not change that risk rule. Personal
item/source/amount examples remain private under `local-data/stock-valuation-20261018/`.

## Implemented policy and limits

The [approved contract](../architecture/qualified-stock-risk-marks.md) describes CURRENT_ACCOUNT_V2:
fresh global feed, two-sided trades within 15 minutes, bounded spreads, timestamped completed
public low-price/volume support, quantity caps, a 10% price haircut, current tax and no quiet-mark
unrealized gain credit. Every unsupported/unseen remaining unit still reserves its full exact
allocated cost. Original loss anchors, confirmed losses, historical uncertainty, current source,
cash/funding/slot/limit/diversification guards remain. No account row, acquisition or sale is fixed
up; the policy is a risk estimate and cannot promise an executable price or liquidation bound.

The 15-minute cutoff, volume shares and haircut are explicit owner-approved policy choices, not learned
parameters or calibrated statistical bounds. The owner selected this change before merge or
installation; it was not silently introduced under the label of a history repair.

Aggregate ingestion now retains the exact payload's original published clock. Cache/refresh clocks
commit together after successful fetches; failed later requests cannot suppress the retry or renew
old evidence. `/latest` capture time does not move forward while a later aggregate request blocks.
Replay v4 passes explicit completed aggregate clocks through the same risk path; legacy missing
clocks stay missing. Production-policy replacement/relist/foreground coverage and qualified account
benchmarks remain open. No performance, parity or learned-model activation claim is made.

## Verification

Regression first: the old implementation fails `QualifiedStockRiskTest` with a synthetic 76M
full-cost reserve on a 100M account despite corroborating public data. The candidate produces a
taxed, discounted negative mark and a positive allowance, and the final companion policy approves
an unrelated eligible buy. Ledger contents, exact cost and the original risk anchor stay unchanged.
An initial final-entry fixture lacked metadata for its unrelated candidate item; adding that
fixture's mapping fixed the test without changing the authority rule.

Final Windows standard clean test/jar/shadowJar passes in **121s / 18 tasks**: **1,680 Java passed,
six existing skips** (plugin 725, core 193, companion 762 passed/six skipped). Project bytecode is
Java 11. **108 Python tooling tests pass in 143.199s**. An earlier complete clean build also passed;
the final source binds feed freshness to the `/latest` response rather than a later download.

Installed plugin: 566,416 bytes, SHA-256
`a0a617aa3fb0e15133a2a5b8b4d0445d125e1e85bf2904bf92eb23d9029fbe5b`.
Installed companion: 18,049,956 bytes, SHA-256
`72d7f5b18fecd8043090642993f1d6be8125623452bb2aa766f6a3b123038727`.
Both installed identities match the verified candidate build and installation manifest.

Behavioral coverage includes partial public volume, full remaining cost, missing physical stock,
genuine falling prices and tax, confirmed losses without quiet gain offsets, malformed money,
old/missing/future quotes, wide/crossed prices, exact completed publication clocks, stale global/
aggregate evidence, legacy feeds, cache identity, failed-fetch retry and replay input clocks.
Existing source/account/financial/recovery/input/learning-fence suites also pass.

Read-only preservation retains the exact **47,672 canonical rows, 1,404 financial effects and sole
original risk anchor**; later activity may append without changing that prefix. An independently
captured public-feed/account copy was evaluated by the retained #98 JAR and the candidate JAR.
Both correctly withhold risk because one slot has a newer durable observation than the account
frame, despite its six-second age. Their unavailable results and ledger remain identical. This
proves that guard remains active; it is not a recovered entry or live quiet-mark acceptance result.

An earlier attempt to associate installed cached aggregates with newly fetched published clocks
found unequal payloads. No timestamps were assigned to those cached payloads. The independent
sample therefore uses its own captured official public data, with explicit clocks/provenance,
rather than claiming to reconstruct the screenshot's earlier as-of prices. No live account writes,
synthetic POSTs, financial replay, adoption, task changes or game input are used.

The packaged v4 runner and its retained evaluator independently reproduce all twelve synthetic
cadence/share cases at 5m, 30m, 2h and 8h. Legacy inputs keep missing aggregate clocks missing.
These fixtures establish reproducibility and conservation, not qualified market performance.

## Installed delivery and remaining acceptance

`tools/apply-update.ps1 -Unattended -SkipBuild` exits 0. Official RuneLite 1.13.2/source API preflight
passes. Backup **20261018-231405-0f47deda** is VERIFIED (three companion/23 plugin-account files,
artifact hashes and original task export). Installation time is **2026-10-19 07:14:08 UTC
(18 October 23:14 local)**. Task XML and SDDL are byte-identical; lifecycle stays 1. No elevation,
task/permission change or force-kill is used.

Installed hashes/sizes match the build above. The exact frozen 47,672-row/1,404-effect prefix and
sole original risk anchor remain unchanged; one legitimate later effect is retained. Status,
launcher CheckOnly and explicitly scoped GET-only pipeline all exit 0. The pre-login plan correctly
awaits fresh cash/slot evidence. Supported Start reopens RuneLite 1.13.2 at 23:15 local and logs
the verified plugin side-load.

After the login/GE request, fresh current-account evidence arrives. A copied account frame at age
10 seconds and market at 33 seconds passes the production authenticated eight-slot read-set check
and CURRENT_ACCOUNT_V2 risk evaluation. Later evidence at ages two/eighteen seconds passes again
with actual qualified quiet-stock marks and no financial changes. The identical frozen inputs
through the retained #98 and new #101 evaluators change a negative old-policy allowance to a
positive one. Original account/window/financial cursor/capital/profit bounds/historical uncertainty
and ledger contents remain identical. This is an actual captured valuation comparison, not an
invented quote, account repair, execution or profit benchmark.

One real over-15m quote still produces a full reserve/refusal; a newer public quote later arrives.
The policy limit is preserved. Subsequent retained plans include READY with quiet trades older
than two minutes and volume-limited residual reserves, followed by NO_FREE_SLOT with all eight
slots occupied. Current absence of a buy allocation is therefore no free slot, not the earlier
valuation block. No cancellation/collection was requested to force an eligible entry.

Sustained quiet-stock buy availability, fresh final eligible ENTRY GET and owner UI/native-window/
controlled trading acceptance remain pending. Source approval, bounded backend observations and
transport health cannot establish those broader outcomes. The original 47,672-row/1,404-effect
prefix/anchor remains identical after eleven later legitimate financial effects.

Private receipts include `before-fix.log`, `targeted-final.log`, `clean-build-final.log`,
`python-tests.log`, `build-verification.json`, retained plan/account/financial prefixes,
`financial-preservation.json`, raw public inputs, copied diagnostic databases, retained evaluator
and `public-risk-{baseline,candidate,comparison}.json`. The private probe uses the configured JDK
by absolute path because Java is not on the shell PATH.
Installation receipts include `owner-policy-decision.json`, `ci-exact-head.json`, `update.log`,
`installation-{before,after,verification}.json`, retained task snapshots, status/compatibility/
pipeline logs, `financial-preservation.json` and `start-after.log` in the same private directory.
Post-login receipts: `postlogin-source-proof.json`, `quiet-live-{baseline,candidate,comparison}.json`,
the frozen `quiet-live-comparison/` inputs/evaluator, `postlogin-plan-timing.json`, initial mixed/
updating GET outcomes and `installed-market.json`. Personal item/price/source rows remain private.
