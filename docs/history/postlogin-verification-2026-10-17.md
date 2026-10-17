# Post-login verification — 17 October 2026

Owner confirmed logged in. Observations below were collected at approximately 13:33–13:37
America/Anchorage, from clean rebuild cce61f8275e55b097d238d1cd1ce26c3f8a76d7d. Installed runtime
remains #80/342aa523ca35ff10fbb1bdc42e9778c2762ca994; no JAR change, install, restart, game input,
database repair, financial replay, outbox clearing or risk reset was performed during this check.
Read [the paired delivery receipt](recommendation-delivery-2026-10-17.md) for hashes and backup.

## Scoped pipeline

Native tools/pipeline-check.ps1 -SkipBuild -SkipWalkForward -AccountId with the explicit requested
account returned exit 0. Output below replaces only the private account identifier with
`requested account`; no synthetic observations or trades were posted.

```text
PASS [Artifact] build\libs\osrs-flipping-friend.jar
PASS [Artifact] companion\build\libs\flipping-friend-companion.jar
PASS [Protocol] accounts-v1
PASS [Market] Ready. Choosing which items to study.
PASS [requested account] Ledger: 11 sale updates; 8 unresolved; 0 profit ranges.
PASS [requested account] Action: COLLECT. Plan: UNAVAILABLE. A managed item's instant-sell quote is stale or unsupported.
INFO [Accounting] No synthetic snapshots, offers, or trades were posted by this check.
```

Transport success does not mean a buy is eligible. Subsequent action GETs returned WAIT as offers
changed. No controlled trade or screenshot acceptance is inferred from these responses.

## Fresh durable evidence and sampled latency

The installed companion JAR's actual CurrentOfferReadSet.valid guard was invoked offline against
the explicitly scoped existing database, opened with mode=ro and query_only=ON. Latest account
snapshot and planning projections were read inside one SELECT transaction, then rolled back.
No writer constructor or event-acceptance path was invoked. Probe compilation and execution exit 0.

- First observation at cursor 16,669: age three seconds, eight IDs/eight sources, guard false.
  The abbreviated probe did not record the failed predicate; do not invent a cause or call this
  successful freshness evidence.
- Detailed observation at cursor 16,707: age seven seconds, guard true; eight unique authenticated
  sources, all snapshot IDs/capture times match, source validity passes and reserved coins agree.
  One of eight slots free; 5m/MODERATE.
- Later observation at cursor 16,780: age two seconds, guard true with the same eight-slot and
  reserved-coin checks. Two slots free; 5m/MODERATE.
- The offer outbox had one pending event in the first HTTP sample and zero in the next two reads.
  This drained through normal runtime delivery; no queue file was changed by the agent.

Two scoped authenticated HTTP samples, approximately two minutes apart:

| Read | First ms | Later ms |
| --- | ---: | ---: |
| portfolio/current | 188 | 93 |
| action | 16 | 16 |
| ledger/current | 203 | 406 |
| learning/current | 672 | 453 |

These are individual request samples, not sustained timing acceptance or a measured recommendation
interval. Ledger progressed from 11 sale updates/eight unresolved in the pipeline to 21/nine in
the later sample. Recent effects include a sale without complete acquisition coverage; unknown
cost/profit remains unresolved. These ordinary owner trades were not a controlled test. No new
non-UNCHANGED reconciliation rows were found since this client launch in the inspected transaction;
that query is not proof that every trade/accounting path has passed.

## Current refusal and other findings

Both sampled plans were UNAVAILABLE / COMPANION_STATE_UNAVAILABLE, with unavailable canonical risk:
`A managed item's instant-sell quote is stale or unsupported.` The later fresh market snapshot
contained two canonical ready positions. Uncharged toxic trident's lowTime was 477 seconds old;
Awakener's orb's was 96 seconds old. DurableRisk currently requires lowTime within 120 seconds.
The former is sufficient to explain that refusal. A fresh all-item feed does not make each item's
last observed transaction fresh. No quote-age threshold, loss allowance or historical reserve was
changed. Remaining loss budget was absent; do not reuse an earlier negative-budget value as this
sample's result. The independent earlier read saw a 56-second-old feed with several item quotes
older than 120 seconds. Price freshness policy and usable entry availability need further evaluation.

Learning remains PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION. The first learning response counted
32 episodes: 11 completed, 14 cancelled censored, seven open censored; six comparable/full forecasts,
zero unknown starts/inconsistent sources. Counts alone authorize neither training nor promotion.

RuneLite's current log also contains repeated AWT-event-thread NullPointerExceptions, beginning at
13:33, with the first retained stack in BoxLayout.checkRequests/preferredLayoutSize, ScrollPaneLayout
and FlatTabbedPaneUI. No Flipping Friend class appears in that stack, and other plugins are loaded.
Attribution and reproduction remain open; do not call this a diagnosed Friend bug or a clean UI pass.
Historical test-worker warnings in the same log are not new live runtime failures.

Private raw responses/probe source/logs remain in the primary checkout's ignored
local-data/diagnostics/postlogin-*.json and .gradle/postlogin-20261017-*.log. The helper is not a
new supported product tool. No credential values or row-level account exports are committed here.

## Acceptance and next work

Fresh post-login eight-slot source proof and normal acknowledgement drain pass. New-buy eligibility,
sustained recommendation timing, search/key/pause behavior, UI layout stability and controlled
buy/collect/sell/partial-cancel/relist/restart remain unverified. Historical stock resolution #70
and companion policy consolidation remain open; new activity does not resolve old gaps.

This receipt changes documentation only. Reuse #80's standard verification: 54s/18 tasks/exit 0,
1,562 Java passed/six existing skips, zero failures/errors; prior 103 tooling tests pass. Validate
changed Markdown links and diff, and require both exact-head CI checks before merging this receipt.
No additional runtime build, paired update or client interruption is needed.
