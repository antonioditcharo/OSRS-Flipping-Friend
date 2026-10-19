# Quiet-stock valuation candidate

18 October 2026 (America/Anchorage). Branch `codex/qualified-stock-risk-marks`, based on the active
rebuild checkout at `cfdd250`. Candidate only: owner policy decision, exact-head CI, merge,
supported paired installation and player acceptance remain separate outstanding steps. Installed
runtime is still the verified #98 pair.

The owner reports an eleven-minute "Waiting for stock valuation" pause with cash and three open
slots. A bounded read-only inspection retains 1,500 recent plans, including 531 risk refusals.
The current-stock full-cost reserve is driven by trades older than 120 seconds; historical unknown
money remains separate. The #98 retry/explanation work did not change that risk rule. Personal
item/source/amount examples remain private under `local-data/stock-valuation-20261018/`.

## Candidate and limits

The [proposed contract](../architecture/qualified-stock-risk-marks.md) describes CURRENT_ACCOUNT_V2:
fresh global feed, two-sided trades within 15 minutes, bounded spreads, timestamped completed
public low-price/volume support, quantity caps, a 10% price haircut, current tax and no quiet-mark
unrealized gain credit. Every unsupported/unseen remaining unit still reserves its full exact
allocated cost. Original loss anchors, confirmed losses, historical uncertainty, current source,
cash/funding/slot/limit/diversification guards remain. No account row, acquisition or sale is fixed
up; the policy is a risk estimate and cannot promise an executable price or liquidation bound.

The 15-minute cutoff, volume shares and haircut are explicit proposed policy choices, not learned
parameters or calibrated statistical bounds. The installed strict rule must not be silently
changed under the label of a history repair. Owner approval is required before merge/installation.

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

Candidate plugin: 566,416 bytes, SHA-256
`a0a617aa3fb0e15133a2a5b8b4d0445d125e1e85bf2904bf92eb23d9029fbe5b`.
Candidate companion: 18,049,956 bytes, SHA-256
`72d7f5b18fecd8043090642993f1d6be8125623452bb2aa766f6a3b123038727`.
These are build identities, not installed identities.

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

Private receipts include `before-fix.log`, `targeted-final.log`, `clean-build-final.log`,
`python-tests.log`, `build-verification.json`, retained plan/account/financial prefixes,
`financial-preservation.json`, raw public inputs, copied diagnostic databases, retained evaluator
and `public-risk-{baseline,candidate,comparison}.json`. The private probe uses the configured JDK
by absolute path because Java is not on the shell PATH.
