# Agent handoff

Updated 21 October 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Current delivery

Read `local-data/relist-market-evidence-20261021/delivery-state.json` first for actual source,
verification, CI, merge and installation. Collected sale reductions share market-evidence evaluation
across engine/editor/companion/replay. Missing bucket support cannot silently restore the old target;
a fresh supported current-market exit, proven stop or explicit wind-down follows its separate rules.
Pending relists cannot fall through to generic replay exits. Exact subset floors survive stabilization
and final authority. Paired sale policy 6 / replay 15 / maintenance evaluator 4.
[Contract](docs/architecture/relist-market-evidence.md).

Installed before this batch: #142 at `33ae39f8c0b22f5f5b4a6b8935fccb8501fd3b8d`;
`local-data/continuation-acceptance-20261021/delivery-state.json` records verification/installation.
It stages new hints/source upgrades until durable acceptance, retaining accepted progress/removal
retries, and shares original BUY duration between live/replay without learning promotion. Earlier
clock, restoration, source, plan/cost and maintenance guards remain in effect.
[Previous delivery checkpoints](docs/history/handoff-through-2026-10-21-pr141.md).

Next reproduced high-impact boundary: two accepted same-item continuations can survive collection,
but placing one successor closes both and loses the other's saved cap/source. A safe fix needs an
explicit successor-to-origin association in client persistence, companion evidence and replay. Never
weaken unknown-order supersession or guess an association. Private synthetic reproduction is in
`local-data/parallel-continuation-audit-20261021/`; this batch does not resolve that separate path.

At this batch's start the saved eight-slot capture was stale and the outbox empty; current account
guidance awaited fresh client observations. Earlier fresh captures and source tests do not establish
controlled full-loop or sustained latency acceptance. Recheck actual evidence after delivery.
Owner login and every game action remain manual. Normal closure, verified backup/update/launch and
direct scoped diagnostics are authorized; do not repeatedly request logs or confirmations.

The old three-bludgeon original plan stays unknown; those units subsequently sold, and the newer
two-unit purchase is separate. Bolts retain separately proven contexts. Dual macuahuitl's older rebuy
hint was retired with its exact sale receipt in #132. Never replay financial history, borrow later
plans, infer disposal or delete unknown historical hints.

## Runtime contracts and source locations

- Canonical accounting and ledger reads: companion SqliteStore/AutomaticTradeScope; exact costs,
  original event IDs, duplicate handling, transactional accounting and unknown money persist.
  [Automatic scope](docs/architecture/automatic-trade-scope.md),
  [durable profit](docs/architecture/durable-profit-ledger.md).
- Original acquisition target/stop/duration: canonical prospective acquisition context, never later
  item-cache advice. [Exit context](docs/architecture/acquisition-exit-context.md),
  [patient pricing](docs/architecture/patient-sale-pricing.md).
- Continuations: core OfferContinuation; client OfferContinuationStore, BuyRepriceIntent,
  SellRelistIntent, OfferTracker; SuggestionEngine and FlippingFriendPlugin own delivery/final checks.
  [Continuations](docs/architecture/trade-continuations.md),
  [durable maintenance](docs/architecture/durable-maintenance-delivery.md).
- Captured sources commit to the forced atomic outbox before worker dispatch. Ordered retries,
  account/generation fences and original bindings survive logout/restart/rejection.
  [Capture](docs/architecture/capture-before-dispatch.md),
  [persistence](docs/architecture/offer-persistence-recovery.md).
- CURRENT_ACCOUNT_V3 is owner approved: only fresh verified cash funds unrelated entries.
  Valuation-only uncertainty in physically proven stock pauses that item without charging the whole
  account; unseen stock retains exact-cost reserves. All source/exposure/loss/slot/limit guards stay.
  [Verified cash](docs/architecture/verified-cash-workflow-recovery.md).
- Sidebar Performance reads durable account results. Expected exits, instant bids, portfolio marks,
  historical totals and session results stay distinct. Reset never resets risk or financial history.
  [Panel](docs/architecture/panel-performance-and-latency.md). Records/Missed flips use native maximized frames.

## Remaining release gates

TASKS.md retains every unchecked requirement. Priorities: complete reliable margin-preserving trade
workflows, measured event/ACK/cold latency, complete production replay and qualified account returns,
then validated learning and remaining product gaps.

Controlled player placement/partial fills/cancel/late fills/collect/exact replacement or supported
partial exit/sale completion/restart remain open. Offline tests and historical eight-slot proof
cannot close them. Sustained latency, UI/search/player-key/context acceptance and complete production
foreground/editor/transport replay remain open.

Keep issue #70 open. Old false-progress/startup gaps are not repaired by prevention. Counter maxima,
aggregate holdings and the insufficient-overlap Copilot export cannot prove remaining stock or
release exposure. Preserve every source/financial row, unknown cost/profit, account isolation and
original loss anchor. Old unassigned data stays unassigned. Exact-source review archival only
changes review visibility; retained purchases and all risk evidence persist.

Benchmark 100M GP, members, eight slots at 5m/30m/2h/8h separately. Slot-hour rates, simulated price
touches and twelve synthetic replay scenarios do not establish account returns. Runtime learning
stays PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION. Compatible loader, promotion/rollback/drift and
chronological return/loss/calibration validation remain unfinished. Public graphs remain display-only.

## Delivery and history

Require standard Windows clean test/JAR verification, Java 11, tooling tests and both exact-head
Windows/Linux CI checks before merge; fast-forward the clean active default checkout. Use
`tools/apply-update.ps1 -Unattended`, with -SkipBuild only for verified matching artifacts. Lifecycle
1 preserves task XML/SDDL. Normal RuneLite window closure is authorized; never force-kill it or send
game input. Verify backup, installed hashes, source/financial/risk preservation and scoped GETs.
[Workflow](docs/development-workflow.md), [user-process updates](docs/architecture/user-process-updates.md).

Detailed earlier checkpoints are in [the PR #131 handoff archive](docs/history/handoff-through-2026-10-20-pr131.md).
They are history, not current installation or owner availability. The concise
[next-conversation prompt](docs/next-conversation-prompt.md) carries current priorities.
