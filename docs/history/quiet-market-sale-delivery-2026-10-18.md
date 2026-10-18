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
- Tests cover quiet high/low, sell-only, fresh stops, missing/future/unknown/impossible evidence,
  exact partial costs/visible quantities, absent stock, managed/personal relists, unchanged stale
  maintenance refusal, reason display/recovery and chronological replay.
- A read-only frozen account/market comparison uses identical inputs: account age 0s, feed age 17s,
  quiet trade age 1,086s. Installed policy: WAIT/SALE_QUOTE_STALE. Candidate: PLACE_SELL/SALE_AUTHORIZED,
  same supported non-loss order. This is source verification, not installed/player acceptance.
- Replay version **3** shares the quiet-stock policy; retained older bundles need their own evaluator.
  The quiet-trade regression completes its hand-calculated round trip. Four separate benchmark
  cadences remain 5m/30m/2h/8h; simulated volume shares do not establish actual account returns.

Private receipts are under `local-data/quiet-market-exit-20261018/`, including the read-only frozen
copy, mapping/feed, old/candidate policy results, financial prefix and artifact verification.
Build logs are `local-data/quiet-sale-clean-build.log` and the regression/targeted logs beside it.
The first filtered command also selected core:test with an unmatched filter; selected behavioral
tests passed and the subsequent complete standard build above passed.

## Delivery state

Source is queued on `codex/quiet-market-exits`; installed runtime remains #91/#92 until delivery.
Both exact-head platform checks, Python verification, merge, paired update, backup/financial-prefix
verification and fresh installed sale acceptance are recorded here when completed. No parity,
learning promotion or superior account-return claim follows from this fix.

Player acceptance: log in/open GE, collect the completed suggested buy manually, and confirm the
Sell card appears without waiting for another public trade. Inspect its quiet-price explanation.
Place the sell manually; the agent reads the resulting canonical source/quantity checks directly.
Partial cancellation/relist/restart and other open release gates remain in TASKS.
