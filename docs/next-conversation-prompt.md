# Continuation prompt

Continue in `C:\Users\Antonio\OSRS Flipping Friend Rebuild`.
Implement, verify and deliver reliable trade/inventory workflow batches; do not stop at a roadmap.
Active/default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

Read `local-data/continuation-disposition-20261020/delivery-state.json` FIRST, then current
AGENTS.md, HANDOFF.md and TASKS.md. The receipt distinguishes source/tests/CI/merge/install/player
acceptance. Older PR checkpoints and availability notes are historical, not installation proof.

The current batch qualifies exact recorded sale sources and durably retires older collected rebuy
promises with their complete original hint/source receipts. It also handles collected/cancelled-sale
receipts and same-pass collection after tracker reload. See
[contract](architecture/continuation-sale-disposition.md) and
[delivery](history/continuation-sale-disposition-delivery-2026-10-20.md).

The important case was Dual macuahuitl: three acquired units had a recorded sale while a two-unit
saved buy remainder remained. Recheck whether the installed client has committed its proven
disposition. Never blindly delete it, infer disposal from absence or replay financial history.
Before this batch, #131 was merged/installed; its receipt is
`local-data/exit-profit-replacement-20261020/delivery-state.json`. It separates expected target/listing
profit from instant-sale P/L, tries all eligible remainders through final authority and supports
verified partial exits after refusal. Preserve those paths and all final guards.

Owner is home and reported active trading; earlier login deferrals are superseded. Login and every
GE/game action remain manual. Routine normal closure, backup verification, paired update and launch
are authorized. Read logs/scoped authenticated GETs directly; do not repeatedly request confirmations.

## Work in impact order

1. Complete placement → partial fills → instructed cancel → late fills → collect → exact remaining
   replacement OR supported partial exit → sale completion. Verify refresh/advice changes/logout/
   shutdown/restart/retries/slot reuse/account switches. Keep canonical ledger, physical inventory/
   offers/collection evidence, continuations, exits, sidebar and reports consistent. Accept original
   acquisition targets/stops/durations and margin-preserving sale maintenance through these paths.
2. Measure actual observation/local persistence/ordered ACK/planning/final authority/client delivery,
   including warm/cold/reconnect/collection. Preserve every ordered source and freshness guard;
   report median/tail/scope honestly. Reuse existing caches and coalescing; reproduce concrete faults.
3. Complete production-policy account replay, real foreground/editor/transport/action-order parity
   and qualified frozen market benchmarks. Use 100M GP, members, eight slots at 5m/30m/2h/8h separately,
   with realistic visits/cash/limits/tax/partial fills/losses/terminal inventory. No 3h substitution,
   slot-rate performance claim or learning based on simulated price touches.
4. Finish learning only after prospective immutable provenance/deduplication, conservative interval/
   censor scoring, chronological frozen training/held-out return/loss/calibration gates, compatible
   runtime loader and explicit promotion/rollback/drift controls. Counts do not authorize activation;
   keep PUBLIC_PRICE_BASELINE / HOLD_FOR_VALIDATION until qualified evidence passes.
5. Complete outstanding account catalog/cross-account results, investigation/export, actionable dump
   feed/opt-in alerts and remaining convenience/parity requirements visible in TASKS.md.

## Constraints and delivery

- Manage only remaining proven acquisitions with exact pooled/subset costs and provenance. Personal
  extras never enroll automatically. Unknown cost/profit stays unknown; absence does not prove sale.
- Preserve original event IDs/deduplication, transactional accounting, recovery tests, account data,
  backups, source/financial rows, unknown coverage, historical gaps and original risk anchors. Keep
  issue #70 open. The retained own-account Copilot export lacks sufficient overlap; no repeat without
  new evidence. Unassigned legacy data stays unassigned. Public graphs remain display-only.
- CURRENT_ACCOUNT_V3 is owner approved: only fresh verified cash funds unrelated buys; valuation-only
  uncertainty in physically proven stock pauses that item without consuming the whole loss allowance.
  Unseen stock retains exact-cost reserves; confirmed loss/source/exposure/funding/slot/limit guards stay.
  Target display prices never become risk marks or funds. No database repair, financial replay,
  auto-adoption, outbox clearing, invented disposal or risk reset to force advice.
- Batch related fixes/behavioral regressions before standard Windows clean test/JAR verification,
  Java 11, 108-tooling-suite verification and one paired restart. Logs stay outside build/. Require both
  exact-head Windows/Linux CI checks before merge; fast-forward the clean active default checkout.
- Use `tools/apply-update.ps1 -Unattended`, -SkipBuild only for verified matching artifacts. Existing
  lifecycle 1 preserves task XML/ACLs without elevation. Normal close is authorized; never force-kill
  RuneLite or send game input. The narrow configured player key fills one already open matching
  quantity/price prompt only; confirmation/placement/cancel/collect remain player actions.
- Keep root docs concise/current and detailed receipts in linked history. Preserve every unchecked
  release requirement. Full parity, controlled full-loop/restart acceptance, sustained latency,
  complete production replay, superior account returns and validated learning remain unfinished.

The [previous full prompt](history/continuation-prompt-through-2026-10-20-pr131.md) is retained for
history; current HANDOFF/TASKS and delivery receipt govern. Start from fresh runtime/source evidence
and the first reproducible high-impact failure; keep progressing independently of manual acceptance.
