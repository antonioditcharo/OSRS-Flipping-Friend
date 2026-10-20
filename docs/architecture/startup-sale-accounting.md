# Startup sale accounting and sale evidence

20 October 2026. Actual delivery state is linked from HANDOFF; tests are not live acceptance.

The companion accepted offer events before the first post-restart account snapshot with its
legacy accounting path. Automatic-scope acquisitions existed in the immutable event ledger but
not the legacy position table. A filled sale during that drain therefore recorded proceeds/tax
with `COST_BASIS_UNRESOLVED; disposal has no durable position`. The local journal independently
retained an estimated flip, producing the reported disagreement. Purchase events were not deleted.

Account service rehydration now reads the latest durable ACCOUNT_STATE for that explicitly
requested account and restores only its automatic-accounting mode. Missing history retains legacy
compatibility; a foreign account fails initialization. It does not restore the account snapshot,
cash, slots, holdings or entry authority. Offer drain still commits source acceptance, exact
acquisition-cost subset allocation, disposal and execution evidence atomically. Duplicate events
remain idempotent. Current authenticated cash/eight-slot proof is still required for entry.

Full SELLING counters may book the last incremental disposal before SOLD. Performance follows
the recorded episode through its later accepted full terminal state, after a disposal source;
it no longer requires SOLD itself to create a financial effect. An episode with reconciliation
cannot qualify. Partial cancellations and full progress without terminal evidence do not count.
This read-derived completion status neither appends nor changes money, tax, cost or learning.

Grouped sale displays retain gross proceeds, matched cost, tax bounds and recorded explanations.
Unmatched cost is explicit and excludes profit from known totals. Matched cost is not claimed to
be the full cost of an unresolved sale. Clicking a sidebar sale selects its Recorded sales evidence,
clearing any filter; outside the latest 100 detail rows, the display explains the boundary. The
detail window retains source identity, tax rule, raw explanation and graph access. Local history
shows approximate journal estimates and is never a fallback for durable Performance.

No schema change, financial history replay, historical profit backfill, legacy ownership inference,
source identity change, deleted-plan reconstruction or risk-anchor reset. The seven already
recorded unmatched-cost sales remain unresolved; this fix protects subsequent acceptance and
explains the earlier failure. Quiet valuation, risk and all current source/funding guards remain.

Regression coverage: pre-account restart drain with exact partial-cost remainder and duplicate
delivery; no stale entry authority; full progress/terminal/collection/read-only restart completion;
rejected terminal and partial cancellation; foreign account; grouped unresolved sale amounts;
sidebar-to-selected-evidence navigation and filtered details. Standard Windows/Linux release
checks and paired delivery remain required. Controlled player trade-loop and sustained timing,
complete production replay, qualified return and learning-promotion gates remain open.

A native pre-install check also reproduced a stale buy card after logout: only Performance
refreshed on the visible heartbeat, while the engine stops on logout. The heartbeat now refreshes
the whole visible panel and fences active/pending advice on account login state. Expired guide
receipts are reread too. This is presentation withdrawal, never deletion of saved plans or trades.
