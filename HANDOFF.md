# Agent handoff

Updated 20 October 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Current delivery

Read `local-data/continuation-disposition-20261020/delivery-state.json` FIRST for actual tested,
CI, merged, installed and remaining acceptance state. Source alone does not establish installation.
[Current contract](docs/architecture/continuation-sale-disposition.md) /
[delivery receipt](docs/history/continuation-sale-disposition-delivery-2026-10-20.md).

The current batch retires stale collected rebuy promises only from exact recorded later sale
sources, retaining the original continuation and source receipt durably. It also checks collected
and cancelled sale receipts after tracker reload and after collection in the same pass. Older
personal listings, missing/foreign/stale/rejected evidence cannot retire hints. Failed writes
withhold replacements and retry across account switches. No accounting or risk history is replayed.

The reported Dual macuahuitl two-unit continuation was still saved during fresh investigation
after a recorded sale of its three acquired units. Current source qualifies that disposition;
read the private delivery receipt for whether the installed client has actually retired it.
Do not manually delete the hint, infer disposal from absence or replay its financial rows.

Before this batch, PR #131 was merged as ae4ea353a29f6afedb1a80f71894e399f50a573c and installed.
Its receipt is `local-data/exit-profit-replacement-20261020/delivery-state.json`; its
[contract](docs/architecture/exit-profit-and-replacement.md) separates intended-exit unrealized
profit from instant-sale P/L, tries all eligible remainders through final authority, exits verified
partials after refusal and durably supersedes rebuy promises on accepted healthy sales.
Historical fresh eight-slot/empty-outbox proof passed; all slots were occupied. Recheck freshness.

Owner is home and has reported active trading. Earlier 17:30 login deferrals are superseded.
Login and all game actions remain manual. Batch routine diagnostics/update/launch; do not request
pasted logs or repeated confirmations.

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
