# Quiet-market sale guidance — 18 October 2026

## Problem and source

After installing #89–#92, the owner still waited several minutes for a sell after collection.
Fresh authenticated account GETs reproduce `SALE_QUOTE_STALE`: a current market snapshot contains
an older last trade. The acquisition, exact cost and physical stock agree. A regression first
fails because the existing initial-sale freshness rule returns WAIT instead of PLACE_SELL.

The client, companion and replay now use `SellTimingEngine.evaluateManagedExit`. With fresh trades
it preserves existing pricing. With quiet trades it quotes the greater of the last recorded sell
price and the full-pool exact taxed cost floor. No quiet evidence may trigger a stop, loss cut or
cancel/reprice an existing sell, including sell-only mode. Missing/future quotes, unknown cost,
impossible floors, paused acquisitions and unavailable physical stock do not get this fallback.
Known managed collected relists use the same rule; personal unknown-cost relists remain separate.
The card shows trade ages and the limit-price explanation; refusals identify the blocked action.
The final companion check additionally verifies current observed quantity. Source, account,
pending delivery, listing, exact subset cost, decision TTL and foreground fences remain.

This changes neither entry/risk policy nor valuation of quiet/unpriced inventory. It books no
sale, profit, disposal, historical repair or risk-anchor reset. [Full contract](../architecture/sale-guidance-delivery.md).

## Verification

- Regression-first expected PLACE_SELL / actual WAIT retained privately.
- Standard Windows clean/test/JAR/shadowJAR: **108s, 18 tasks, 1,636 Java passed, six existing skips**.
  Plugin 714, core 186, companion 736 passed. Every project class targets Java 11.
- Tooling: **108 Python tests passed in 141.478s**.
- Tests cover quiet high/low, sell-only, fresh stops, missing/future/unknown/impossible evidence,
  exact partial costs/visible quantities, absent stock, managed/personal relists, unchanged stale
  maintenance refusal, reason display/recovery and chronological replay.
- A read-only frozen account/market comparison uses identical inputs: account age 0s, feed age 17s,
  quiet trade age 1,086s. Installed policy: WAIT/SALE_QUOTE_STALE. Candidate: PLACE_SELL/SALE_AUTHORIZED,
  same supported non-loss order. This is source verification, not installed/player acceptance.
- Replay version **3** shares the quiet-stock policy; retained older bundles need their own evaluator.
  The quiet-trade regression completes its hand-calculated round trip. Four separate benchmark
  cadences remain 5m/30m/2h/8h; simulated volume shares do not establish actual account returns.
  All twelve v3 CLI scenarios reproduce; the retained v2 evaluator independently verifies its
  original bundle. No old bundle is silently assigned the new policy semantics.

Private receipts are under `local-data/quiet-market-exit-20261018/`, including the read-only frozen
copy, mapping/feed, old/candidate policy results, financial prefix and artifact verification.
Build logs are `local-data/quiet-sale-clean-build.log` and the regression/targeted logs beside it.
The first filtered command also selected core:test with an unmatched filter; selected behavioral
tests passed and the subsequent complete standard build above passed.

## Delivery state

PR [#94](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/94), source
`1cbea438afcb5d78f033b0dddfee431ce0670c8c`, passed both exact-head supported platform checks in
run [39164110382](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39164110382).
Merge `c8d3a182b8c974e56a2d4f618a5ddc977085c0ac` fast-forwarded the clean active rebuild checkout.
Historical main stayed untouched.

The supported normal-user paired updater completed at **20:41:14 UTC**, lifecycle version 1.
Backup **20261018-124110-c0f97a8c** is VERIFIED: three companion and 22 plugin account files;
original JAR/manifest/task export checks pass. Exact task XML/SDDL remains unchanged. Installed:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Plugin | 522,113 | `f503cf6e845739c6e35c074f8dbe517e0e79cd183c5c4944c83a57de9a4b3a7a` |
| Companion | 17,993,053 | `963ad38d1a9c1230099b784288cc42be8dbf444ff7d742c0973587d93964d67c` |

Native status, compatibility (1.13.2) and scoped GET-only pipeline exit 0. All **33,986 frozen
canonical rows, 1,352 financial effects and the sole original risk anchor** remain identical.
No financial effects were appended by verification. Account files were not restored/repaired,
and no task/ACL/privilege/elevation change or game input was used.

Supported Start reopened RuneLite; fresh login/GE/Sell-card acceptance is requested and pending.
Old-capture transport readiness is not fresh trading acceptance. No parity, learning promotion
or superior account-return claim follows from this fix.

Player acceptance: log in/open GE, collect the completed suggested buy manually, and confirm the
Sell card appears without waiting for another public trade. Inspect its quiet-price explanation.
Place the sell manually; the agent reads the resulting canonical source/quantity checks directly.
Partial cancellation/relist/restart and other open release gates remain in TASKS.
