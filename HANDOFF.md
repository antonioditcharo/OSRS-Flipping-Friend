# Agent handoff

Updated 21 October 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Current delivery

Clock/remainder batch: read `local-data/continuation-clock-preservation-20261021/delivery-state.json`
first for actual verification, CI, merge and installation. Future evidence times preserve pending
workflows without authorizing guidance; fresh observations still persist corrected remainders and
closure facts. Late callbacks after saved collection narrow caps without invalidating the document.
Replay 13 / sale policy 5. [Contract](docs/architecture/continuation-clock-preservation.md).

Restoration/acceptance batch: read `local-data/continuation-restoration-20261021/delivery-state.json`
first for source, CI, merge and actual installation. Current-login observation copies preserve durable
remainders across stale offer snapshots; cached EMPTY predecessors cannot invent collection. Exact
accepted cancellations retire rebuy promises only after commit, not during worker proposals or from
a delayed same-item card. [Contract](docs/architecture/continuation-restoration-boundaries.md).

Sale continuation batch: read `local-data/sale-continuation-evidence-20261021/delivery-state.json`
first for source/CI/merge and actual installation. A first verified source added to an older hint
after later fills now has its own acceptance clock; it cannot invalidate/delete the original hint.
Existing parallel same-item sales no longer invalidate relist proof on a heartbeat or valid fill;
new/changed/cleared orders remain refused. Paired sale policy 5 / replay 12; original financial,
source, risk and exact-cost guards persist. [Contract](docs/architecture/sale-continuation-evidence.md).

Runtime health follow-up: `local-data/health-request-isolation-20261021/delivery-state.json`
records actual verification/installation. Live captures reproduced both HTTP workers blocked by
account initialization/reads; the 60s starter retry alone did not resolve that occurrence. Global
health now has a separate dispatch path, account work stays at two workers with a bounded queue,
and routine refresh no longer scans diagnostic history. [Contract](docs/architecture/health-request-isolation.md).
Global health is not account trade readiness. Read the receipt before assuming this runtime is installed.
Subsequent manual login drained all 228 saved observations and supplied fresh authenticated eight-slot
evidence. Genuine accounting effects arrived normally; this does not close controlled full-loop acceptance.

Starter tooling: `local-data/starter-health-retry-20261021/delivery-state.json` records the existing-
listener health retry fix and verification. Start waits at most 60s for transient failures on the
same port-owning PID, rejects credentials/protocol/body failures and never starts a second copy.
This script-only fix is merged as #137; the subsequent runtime blockage and delivery are separate above.

Newest workflow slice: read `local-data/ordinary-sale-queue-20261021/delivery-state.json` for source,
CI, merge and installed state. Ordinary sale reductions now retain every later eligible sale through
final authority; only the presented instruction commits its durable continuation. Pricing, buy/stop
priority, exact costs and risk are unchanged. Controlled acceptance remains open.
[Contract](docs/architecture/ordinary-sale-maintenance-queue.md) /
[receipt](docs/history/ordinary-sale-queue-delivery-2026-10-21.md).

Newest diagnostic batch: read `local-data/sale-maintenance-diagnostics-20261021/delivery-state.json`
for verification, CI, merge and actual installation. Records → Recommendation adds active managed
sale wait explanations from the shared evaluator, with observed clocks and unvalidated original
order estimates. Pricing/authority and accounting are unchanged. Native acceptance remains separate.
[Contract](docs/architecture/sale-maintenance-diagnostics.md) /
[receipt](docs/history/sale-maintenance-diagnostics-delivery-2026-10-21.md).

Previous workflow batch: `local-data/trade-plan-maintenance-20261020/delivery-state.json` records source/tests,
CI, merge, installation and manual acceptance. [Current contract](docs/architecture/trade-plan-maintenance.md) /
[delivery receipt](docs/history/trade-plan-maintenance-delivery-2026-10-20.md).

The new batch preserves same-second accepted-advice ordering independently of prediction scoring,
retains original acquisition plans with possible remaining origin ranges, and removes invented stops.
Selected managed sell quantities use canonical subset costs and final authority on a worker. Earlier
supported non-loss maintenance follows the exact cancellation/collection source into relisting;
original targets remain context. Buy progress/deadline/stabilization and shared default horizons are
corrected. Forecast diagnostics remain observational and unvalidated; learning stays fenced.

The reported three-bludgeon original context is unavailable and stays unknown; those three units
subsequently sold. The newer two-unit acquisition is a different episode. The bolts' original and
replacement contexts survive corrected read-only projection, without financial replay or re-enrollment.

#133 is merged as 6f1634f394befdd68556c1fb892c2249f7f00f1f after both exact-head checks. Its runtime
installation is batched with this delivery. It tries every eligible exact exit through authority,
withdraws refused advice and separates actual inventory from equipment. Bank/collection evidence,
exact quantities/costs and personal stock remain distinct. [Contract](docs/architecture/exit-authority-queue.md) /
[receipt](docs/history/exit-queue-delivery-2026-10-20.md).

Previous #132 is merged as a1e70c1c0e0b8630ce52fb8aa61bb9b4ab3c584b and installed. Fresh manual
login/eight-slot/empty-outbox proof passes. The Dual macuahuitl two-unit rebuy hint is now retired
with its exact recorded sale receipt; other hints and the original source/financial prefix/risk
anchor survive. Subsequent genuine acquisitions changed the stock totals; they are not update
effects. Actual proof: `local-data/continuation-disposition-20261020/delivery-state.json`.
[Contract](docs/architecture/continuation-sale-disposition.md) /
[receipt](docs/history/continuation-sale-disposition-delivery-2026-10-20.md).

Included #131 separates intended-exit unrealized profit from instant-sale P/L, tries all eligible
buy remainders through final authority and supports verified partial exits after refusal.
[Contract](docs/architecture/exit-profit-and-replacement.md). Controlled full-loop acceptance remains open.

Owner is home and actively trading. Earlier login deferrals are superseded. Login and all game
actions remain manual. Batch routine diagnostics/update/launch; do not request pasted logs or
repeated confirmations.

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
