# Continuation prompt

Copy the text below into the next conversation, or ask it to read and execute this file.

---

Continue implementing OSRS Flipping Friend toward functional Flipping Copilot parity, starting
with the largest practical impact on reliable trading and net account returns. Implement, verify
and deliver working increments; do not stop at an assessment or another roadmap.

Workspace: `C:\Users\Antonio\OSRS Flipping Friend Rebuild`
Active/default branch: `CoPilot's-Rebuild-from-Main`. `main` is historical. Read the actual latest
checkout and preserve unrelated changes. Do not pin work to the historical commit IDs below.

Read first:

1. `AGENTS.md`, `HANDOFF.md`, `TASKS.md`.
2. `docs/architecture/patient-sale-pricing.md` and
   `docs/history/patient-sale-delivery-2026-10-19.md`.
3. `docs/architecture/panel-performance-and-latency.md` and
   `docs/history/panel-speed-delivery-2026-10-19.md`.
4. `docs/project-direction.md` and `docs/architecture/parity-account-evaluation.md`.
5. `docs/architecture/acquisition-exit-context.md` and its current delivery receipt in HANDOFF.

Then consult the completed research as needed, reconciling it against current source rather than
rebuilding already implemented capabilities:

- `docs/research/flipping-copilot-2026-10-17.md`
- `local-data/copilot-reverse-engineering/20261017/parity-learning-analysis.md`
- `local-data/copilot-reverse-engineering/20261017/client-workflows-analysis.md`
- `local-data/copilot-reverse-engineering/20261017/protocol-analysis.md`
- `local-data/copilot-reverse-engineering/20261017/wire-contract.json`

## Delivery state at this handoff

Completed-offer latency #111/b74f93b is merged with both exact-head checks and Windows clean
1,736 Java passed/six skips, Java 11; 108 tooling passed. Synthetic median acceptance is 173ms -> 18ms,
with identical financial/prediction results. INSTALLED at 18:55:13Z on 19 October after the agent
closed RuneLite normally with explicit owner authorization. VERIFIED backup 20261019-105509-9ebbbcba;
pair/task/62,073 source rows/1,470 effects/original anchor/status/compatibility/scoped GETs pass,
zero update-created financial effects. Supported Start reopened RuneLite and loads the installed
plugin. Fresh login/trading/sustained acceptance is pending; availability is recorded privately in
local-data/completion-latency-20261019/delivery-state.json. Agent handles normal maintenance closure;
login/GE stay manual.
Source reuses the canonical prefix during terminal prediction
qualification, preserving original-source/transaction checks and external/tail/rollback invalidation.
Read docs/architecture/completed-offer-latency.md and docs/history/completed-offer-latency-2026-10-19.md
for measured source, exact-head CI and actual installed state; do not infer installation from source.

Offline replay v6 adds canonical original-context qualification, transactional final companion sale
authority and delayed/revalidated displayed actions, including restart withdrawal. See
docs/architecture/replay-canonical-sale.md and docs/history/replay-canonical-sale-2026-10-19.md.
This evaluator-only increment needs no separate installation or game restart; installed #111 includes it.
Production replacement/persisted relist/real client acceptance and qualified benchmarks remain open.

Included PR #108 merged as `fe8a33b9548559253968073e58d7aea69bd8078f` and was previously installed at
2026-10-19T16:13:02Z. Exact source `2e30f76c03653b7127a8a6daf5c2fc536497fea8` passed both
Windows/Linux checks. Windows clean 66s: 1,726 Java passed/six existing skips, Java 11;
108 tooling passed. VERIFIED backup `20261019-081258-a59c264b`; pair/task XML/SDDL, all 61,527
retained event-log rows/1,469 financial effects/original risk anchor, status, 1.13.2 compatibility
and scoped GETs pass. Supported Start reopened RuneLite. Subsequent manual login yields fresh
authenticated eight-slot source proof, zero pending outbox, scoped Performance, READY and final
PLACE_BUY (156ms/250ms). Client delivered BUY/COLLECT/WAIT. One legitimate financial append
preserves the old prefix/anchor. Controlled full-loop/price-quality and sustained timing remain open.
Do not reinstall #111 or older #108 merely to repeat delivery. Read newest HANDOFF and
`docs/history/acquisition-exit-delivery-2026-10-19.md` for actual delivery/acceptance state.

Live original target/stop/sell-duration context now comes from the exact prospective canonical
buy, through partial fills, ACK/restart and partial sales without the item-keyed six-hour TTL.
Client snapshots cannot create original plans. Mixed/unproven originals remain unknown; legacy
item files and old money are not backfilled. The final sale protocol is `companion-sale-3`.
Replay v6 qualifies this boundary; remaining production replay and all promotion gates remain open.

Included #105/e21a049 fixes premature break-even/scrap-profit pricing: available original healthy
targets survive within the configured horizon; routine reductions require full sell patience,
no recent fills and sustained completed market support. Declined routine maintenance leaves the
listing working and continues through separate entry checks. Stops/wind-down, exact floors,
unknown costs and source/quantity/account fences remain. Read `docs/architecture/patient-sale-pricing.md`
and `docs/history/patient-sale-delivery-2026-10-19.md`. Controlled sell-quality acceptance remains
open. Version 6 replaces v5's independently simulated context with canonical qualification and
final sale authority; complete replay and measured return/optimal-price gates remain open.

Included #103 already implements the expanded native sidebar/session/period/reset/profit/ROI/
elapsed rate/managed-stock marks/paged sales and estimate display, plus validated history/candidate
caches, coalesced refreshes and removal of fixed five-second plan spacing. Actual #103 deliveries:
12 BUY cards, median 1,038.5ms; first publication 5,186ms, including 4,154ms capture/durable publication.
62 WAIT cards: median 606.5ms/max 1,436ms. These are request-to-client delivery measurements,
not exact game-event-to-frame transitions. Panel/reset, sustained timing and controlled outcomes
remain open. Windows already open maximized natively; do not reintroduce old accordions.

Private #108 receipts/scripts are in `local-data/acquisition-exit-20261019/`; prior evidence is in
`local-data/sale-patience-20261019/` and
`local-data/panel-parity-20261019/`. Use `local-data/acquisition-exit-20261019/runtime-108-read.py` for scoped GET/read-only source
inspection; its eight-slot identity result must also be fresh before it establishes current proof.
`start.log` in the acquisition-exit directory is the actual launched client's output. Global
`~/.runelite/logs/client.log` can also contain Test worker output; exclude that from live timing claims.

## Work in descending impact order

1. **Make the complete trade loop dependable and preserve earned margin.** Collect fresh account,
   eight-slot/ACK, inventory, ledger, entry and sale evidence directly after manual login/GE.
   Verify installed #108 (including #105 patient pricing) against player-operated
   buy -> partial/full fill -> collect -> sell -> collect,
   cancellation/relist and restart. Reproduce and fix failures before tuning strategy. Healthy
   sells must not collapse to break-even unnecessarily; verified current cash must support
   unrelated eligible buys despite incomplete historical trades. Distinguish legitimate current
   risk/exposure/limit waits and explicit sell-only mode from delivery bugs.

   Accept the acquisition-bound target/stop/sell-duration context through partial fills, relisting
   and restart. Live policy now avoids the legacy item-keyed TradePlans TTL/client snapshot path;
   preserve proven original acquisition context and unknown legacy/mixed plans. Complete remaining
   ledger-consumer authority checks. While manual acceptance is pending, continue independent work.

2. **Make suggestion transitions consistently near-instant.** Measure actual observation, ordered
   outbox publication/ACK, planning, final authority and client delivery, especially fills/collections,
   partial progress, cold start and reconnect. Start with the measured first-publication bottleneck.
   Reuse existing caches/coalescing; do not rebuild them. Optimize demonstrated work and transport
   while preserving source freshness, exact accounting, account/generation fences and every final
   guard. Never drop/coalesce away financial events, show obsolete advice or manufacture funds to
   hit a timing target. Report median/tail/cold behavior and end-to-end scope honestly.

3. **Finish production-policy account replay and return measurement before ranking/strategy claims.**
   Replay v6 models shared cash/eight slots/limits/partial fills/tax/visits/restart/conservative
   terminal marks, canonical original context, final sale authority and delayed revalidated advice.
   Its twelve schema/synthetic scenarios are not market performance. Complete missing production
   buy-replacement, persisted sale-relist and real client-thread/transport acceptance coverage.
   Use qualified chronological frozen market evidence and prospective account
   clocks/cadence/attendance. Benchmark 100M GP, members, eight GE slots separately at 5m, 30m, 2h
   and 8h; compare net account returns over elapsed time, losses and conservative terminal inventory.
   Never substitute 3h, item-by-item tests, headline slot rates or simulated touches for this evidence.

4. **Finish validated learning with explicit controls.** Runtime remains PUBLIC_PRICE_BASELINE /
   HOLD_FOR_VALIDATION. The existing fitter/evaluator already retain unknown/censored outcomes;
   a later 25-forecast cohort (13 known/12 uncertain) still permits deterioration. Do not refit or
   promote merely because counts grow. Use qualified prospective immutable advice/source context,
   deduplication and frozen chronological training/held-out prefixes. Complete the compatible
   runtime forecast loader, explicit promotion/rollback/drift controls and account return/loss/
   calibration gates before any activation. Failed or insufficient evidence keeps learning held.

5. **Close remaining product gaps by measured value.** Explicit account catalog/selection and real
   cross-account performance; usable portfolio/history investigation/export; independently sourced
   actionable dump opportunities and verified opt-in local/sound/webhook delivery; remaining guidance
   conveniences. Reconcile each with installed source first. The current public-drop watch list is
   not a validated dump feed. Graphs already have public history/volume/experimental scenarios;
   tested bands were worse than persistence and stay display-only. Bank badges/tags/slot colors and
   optional cross-device sync rank after trading reliability, latency and evaluation prerequisites.

## Non-negotiable constraints and delivery

- Copilot's private backend/model/training data were not recovered. Do not use its private API as
  a production dependency or treat other users' transactions as this account's missing financial rows.
- Preserve account isolation, event IDs/deduplication, exact subset costs/tax bounds, unknown money,
  historical gaps, risk reserves and the original loss anchor. No DB repair, replay, auto-adoption,
  outbox clearing, invented disposal or risk reset to force advice. Old unassigned data stays unassigned.
  Routine personal inventory needs no player classification. CURRENT_ACCOUNT_V2 and its approved
  quiet-stock limits remain in force; target prices never become executable risk marks.
- Issue #70 remains open. Old aggregate/counter maxima are unsafe remaining-stock evidence. The
  retained own-account Copilot export lacks sufficient overlap; do not repeat it without new evidence.
- Login and every game action remain manual. Never send game input or force-kill RuneLite. The
  configured player-key exception only fills one already open matching quantity/price prompt;
  Enter, confirmation, placement, cancellation and collection remain the player's actions.
- Routine diagnostics, backups, paired updates, launcher and GitHub delivery are authorized. Batch
  fixes/checks; do not repeatedly ask for confirmation or pasted logs. Build/test with RuneLite open;
  normal closure is required only for a new verified paired runtime replacement.
- Windows: `gradlew.bat clean test jar :companion:shadowJar`; tooling:
  `python -m unittest discover -s tools/tests`. Target Java 11 and redirect clean logs outside build/.
  Require both exact-head Windows/Linux CI checks before merging and fast-forward the clean active
  default checkout. Supported updater: `tools/apply-update.ps1 -Unattended`; use `-SkipBuild` only
  for verified matching artifacts. Lifecycle 1 is already active; task XML/ACLs stay unchanged and
  no elevation is needed. Documentation-only changes need no runtime reinstall.
- Keep HANDOFF/TASKS concise/current and link detailed receipts. Distinguish implemented, tested,
  merged, installed and player-accepted states. Explicitly retain every deferred gate. Do not claim
  complete parity, validated forecasts, superior profit or finished learning without corresponding evidence.

Start with the current runtime/source checks and the first reproducible high-impact trade-loop
failure; deliver the fix while continuing independent work if player acceptance is pending.
