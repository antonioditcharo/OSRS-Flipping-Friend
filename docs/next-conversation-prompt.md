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

Then consult the completed research as needed, reconciling it against current source rather than
rebuilding already implemented capabilities:

- `docs/research/flipping-copilot-2026-10-17.md`
- `local-data/copilot-reverse-engineering/20261017/parity-learning-analysis.md`
- `local-data/copilot-reverse-engineering/20261017/client-workflows-analysis.md`
- `local-data/copilot-reverse-engineering/20261017/protocol-analysis.md`
- `local-data/copilot-reverse-engineering/20261017/wire-contract.json`

## Delivery state at this handoff

PR #105 is merged as `e21a0495462a5fe59620acf45963e96125505caa` and INSTALLED. Its exact source
`24ec8a065a29094fcf73b353246e085974f99a65` passed Windows/Linux CI. Standard Windows clean build:
1,717 Java passed/six existing skips, Java 11; 108 tooling tests passed. Delivery documentation
#106 is merged as `95dec731473746271046d8c4a6ab87aa461009b4`; later documentation needs no reinstall.

#105 fixes premature break-even/scrap-profit pricing. Healthy initial/quiet/relist limits retain an
available remembered target within the configured holding horizon; routine reductions require
full sell patience, no recent observed fills and sustained completed public-market evidence.
Expiry alone no longer discounts a current healthy ask. Declined ordinary maintenance leaves the
listing working and continues through separately authorized entry checks. Stops, explicit wind-down,
exact cost/tax floors, unknown costs and source/quantity/account fences remain. Maintenance retains
its non-loss floor unless a fresh stop authorizes a cut; initial wind-down loss permission is unchanged.
The pair uses `companion-sale-2`; replay is version 5. These are implemented rules, not proven optimal prices.

Supported installation/VERIFIED backup `20261019-015028-11a456ab`, installed hashes, unchanged task
XML/SDDL, all 61,527 retained event-log rows/1,469 financial effects/original loss anchor, status,
RuneLite 1.13.2 compatibility and scoped GET checks pass. RuneLite was reopened normally.
**Fresh #105 login/GE and controlled sell-quality acceptance remain pending.** The last closeout
read had only aged pre-update account evidence; saved sell-only state is not a fresh current setting.
Determine current state directly. Do not reinstall #105 or ask to close RuneLite merely to repeat delivery.

Included #103 already implements the expanded native sidebar/session/period/reset/profit/ROI/
elapsed rate/managed-stock marks/paged sales and estimate display, plus validated history/candidate
caches, coalesced refreshes and removal of fixed five-second plan spacing. Actual #103 deliveries:
12 BUY cards, median 1,038.5ms; first publication 5,186ms, including 4,154ms capture/durable publication.
62 WAIT cards: median 606.5ms/max 1,436ms. These are request-to-client delivery measurements,
not exact game-event-to-frame transitions. Panel/reset, sustained timing and controlled outcomes
remain open. Windows already open maximized natively; do not reintroduce old accordions.

Private receipts/scripts are in `local-data/sale-patience-20261019/` and
`local-data/panel-parity-20261019/`. Use `runtime-105-read.py` for scoped GET/read-only source
inspection; its eight-slot identity result must also be fresh before it establishes current proof.
`start.log` in the sale-patience directory is the actual launched client's output. Global
`~/.runelite/logs/client.log` can also contain Test worker output; exclude that from live timing claims.

## Work in descending impact order

1. **Make the complete trade loop dependable and preserve earned margin.** Collect fresh account,
   eight-slot/ACK, inventory, ledger, entry and sale evidence directly after manual login/GE.
   Verify #105 against player-operated buy -> partial/full fill -> collect -> sell -> collect,
   cancellation/relist and restart. Reproduce and fix failures before tuning strategy. Healthy
   sells must not collapse to break-even unnecessarily; verified current cash must support
   unrelated eligible buys despite incomplete historical trades. Distinguish legitimate current
   risk/exposure/limit waits and explicit sell-only mode from delivery bugs.

   Trace the target/stop/sell-duration context through acquisition, partial fills and restart.
   Audit `TradePlans.java`: it is item-keyed and currently has a six-hour TTL, while supported
   holds/checking horizons can be longer. Determine and reproduce any loss/misattribution of
   intended exit context; preserve acquisition provenance and unknown original plans rather than
   inventing a target or rewriting old money. This is a concrete high-impact follow-up, not a
   claim that every current position has incorrect context. Complete the remaining ledger-consumer
   authority audit. While manual acceptance is pending, continue independent implementation/tests.

2. **Make suggestion transitions consistently near-instant.** Measure actual observation, ordered
   outbox publication/ACK, planning, final authority and client delivery, especially fills/collections,
   partial progress, cold start and reconnect. Start with the measured first-publication bottleneck.
   Reuse existing caches/coalescing; do not rebuild them. Optimize demonstrated work and transport
   while preserving source freshness, exact accounting, account/generation fences and every final
   guard. Never drop/coalesce away financial events, show obsolete advice or manufacture funds to
   hit a timing target. Report median/tail/cold behavior and end-to-end scope honestly.

3. **Finish production-policy account replay and return measurement before ranking/strategy claims.**
   Replay v5 already models shared cash/eight slots/limits/partial fills/tax/visits/restart/conservative
   terminal marks and retained entry context. Its twelve schema/synthetic scenarios are not market
   performance. Complete missing production buy-replacement, persisted sale-relist and foreground
   acceptance coverage. Use qualified chronological frozen market evidence and prospective account
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
