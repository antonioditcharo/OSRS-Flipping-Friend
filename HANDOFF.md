# Agent handoff

Updated 23 October 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Current delivery

Read `local-data/sale-acceptance-renewal-20261023/delivery-state.json` first for current source,
verification, CI and delivery. PR #153 (`e67a517d1ccc642ca65e16328b884a809eb1a73e`) is installed.
Backup, hashes, task settings, all 114,716 frozen source rows, 1,861 accounting effects and the
loss anchor passed preservation. Fresh eight-slot/empty-outbox/final-entry checks passed, but
the panel now withholds authorized maintenance at "Saving this trade workflow".

The current source fixes renewal matching: a fresh exact source/receipt may retain the immutable
original acceptance for the same economic offer episode as observations and partial counters advance.
[Contract](docs/architecture/sale-acceptance-renewal.md). No source/financial history is repaired.
Guidance acceptance remains open; this fix is queued until its receipt records installation.

Installed PR #153 routes capped saved replacements directly to canonical pricing, permits one
strictly reauthorized sale-price correction, and checks independently authorized working maintenance
after urgent exits refuse. [Contract](docs/architecture/trading-flow-authority-recovery.md).
No original stop, exact cost, source,
loss authority or acquisition context may be invented to make a refused action pass.

Installed PR #152 moves trading heartbeat/account-refresh/persistence scheduling and offer-retry backoff
to local monotonic time. UTC corrections cannot extend these scheduling waits. Initial/negative
origins, retry backoff and lifecycle fences remain supported; recorded trade timestamps, original
order deadlines and current-source freshness guards remain unchanged.
[Contract](docs/architecture/monotonic-maintenance-scheduling.md). Exact costs, event identities, original plans, proof formats 1/2, sale policy
9 / replay 18 / evaluator 5 and CURRENT_ACCOUNT_V3 persist. Controlled trading and sustained latency
remain open; already published receipt-free cancellations are not repaired.

[Prior checkpoints](docs/history/handoff-through-2026-10-21-pr141.md).

The prior live blockade has fresh recovery evidence in the PR #149 receipt. Recheck current
account/slot/outbox and guidance evidence instead of assuming it remains fresh. Preserve all
pending event identities and financial/source/risk prefixes. Normal closure, verified backup/update/
launch and scoped reads are authorized; login and game actions remain manual. Disk space now reads
more than 12 GB; the earlier disk-full receipt describes its actual prior attempt, not a quota.

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
