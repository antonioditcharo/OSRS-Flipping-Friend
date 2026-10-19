# Tasks

Updated 18 October 2026. Read [HANDOFF.md](HANDOFF.md) for current source/runtime evidence.
All unchecked release requirements remain visible below; detailed completion receipts are in history.

## Active

- [ ] Finish/verify the qualified quiet-stock valuation candidate, then obtain the owner's policy
      decision before merge/install. #98's strict 120s-only bid rule reproduced long full-cost
      reserve pauses despite cash/slots. Candidate uses timestamped completed public price/volume,
      a 10% haircut/tax and bounded quantities; missing stock remains fully reserved. Keep original
      accounting/anchors, confirmed-loss and final authority guards. See
      docs/architecture/qualified-stock-risk-marks.md. Windows clean 121s: 1,680 Java passed/six
      skips and 108 tooling tests pass; exact-head CI/owner decision/delivery remain pending.
      Installed runtime remains #98. Receipt: docs/history/qualified-stock-valuation-2026-10-18.md.

- [ ] Deliver/accept merged #98/f662310: current-stock valuation explanations,
      prompt recheck on fresh market evidence and native maximized Records/Missed flips windows.
      Preserve historical separation, exact reserves, quote/source guards and original loss anchor.
      Windows clean 92s: 1,658 Java passed/six skips, Java 11; 108 tooling tests pass.
      Both exact-head Windows/Linux checks pass. #98 is INSTALLED with VERIFIED backup
      20261018-210634-e9da041d; hashes/task/financial-prefix/status/compatibility/scoped GET checks
      pass and supported Start reopened RuneLite. Fresh account/native player acceptance remains
      pending. Genuine risk waits remain. See docs/history/buy-readiness-windows-delivery-2026-10-18.md.

- [ ] Deliver/accept the source graph increment in docs/architecture/market-graphs.md: independent
      public 5m/1h/6h history, low/high/volume, selected-item stats, ranges/zoom/pan/reset/expand and
      explicitly experimental historical-return bands with reproducible rolling-origin evaluation.
      Native QA/full build pass: 1,653 Java passed/six skips and 108 tooling tests. Frozen evaluation
      reproduces; all four horizons have worse known-target MAE than persistence. Keep display-only.
      #96/1ebe4f8 is merged/installed after both exact-head CI checks passed. Verified backup,
      hashes/Java 11/task/financial-prefix/status/compatibility/scoped GET checks pass; supported
      Start reopened RuneLite. Fresh login/installed owner graph and guidance acceptance is pending.
      See docs/history/market-graph-delivery-2026-10-18.md.
      Installed #98 runtime includes #94 sells and #96 graphs. Other players' trades never become
      this account's missing ledger rows or execution samples. Validated forecast quality remains open.

- [x] Deliver/accept initial sell guidance in installed #94. Owner reported indefinite verification
      after #91/#92; fresh scoped GETs reproduce SALE_QUOTE_STALE for verified held stock. Shared
      client/companion/replay non-loss limit is installed after both exact-head checks. Windows clean:
      1,636 Java passed/six skips; 108 tooling tests pass. Verified backup/pair/task/financial-prefix/
      status/compatibility/scoped GET checks pass. Owner confirms the fresh Sell-card check "Looks good";
      observed post-update sale and a newly acquired two-unit position listed; fresh eight-slot proof passes.
      Keep fresh evidence for
      loss cuts and live sell cancellation, exact floors, physical/source and historical guards.
      See docs/history/quiet-market-sale-delivery-2026-10-18.md; complete player buy/collect/sell remains open.

- [x] Implement/test the parity increment in docs/architecture/parity-account-evaluation.md:
      final companion managed-sale/maintenance authority, shared exact pricing, durable sale
      investigation, graph lookup, public market-drop watch list, local/webhook warning dispatch,
      executable account replay and frozen conservative completion fitter. Windows standard:
      1,590 Java passed/six existing skips; 103 Python passed. #83 is merged/installed after both CI
      checks; see docs/history/parity-delivery-2026-10-17.md. Manual acceptance remains open.
      These foundations do not close the broader replay, session measurement or learning gates below.

- [x] Deliver #79 sell maintenance/relisting and #80 replacement entry/refusal guards. Installed
      #80/342aa52 after both final Windows/Linux checks passed. Backup 20261017-131556-67c07d16 is
      VERIFIED; hashes, Java 11, unchanged task metadata, native status/compatibility/scoped pipeline
      pass. Client relaunched; owner confirms login. Fresh installed-guard eight-slot source proof
      passes twice; outbox drains normally. UI/controlled trading acceptance remains open. See
      docs/history/postlogin-verification-2026-10-17.md. Final standard 54s:
      1,562 Java passed/six existing skips; prior 103 tooling tests pass. See
      docs/history/recommendation-delivery-2026-10-17.md. No reinstall for this docs receipt.
- [x] Deliver #77 normal-user paired updates without an initial admin approval. Task permissions
      remain read-only and unchanged; optional #76 ACL repair was never applied. Status/start/update
      use processLifecycleVersion=1. See docs/history/normal-user-update-2026-10-16.md.
- [x] Preserve the completed #69/#72 startup/episode prevention, #73 offline audit and #75 accurate
      risk explanations. Prevention/audit do not repair historical stock or release reserves.
      Earlier regression/build/CI/delivery receipts remain in the linked task/handoff history.

- [ ] Restore safe sell guidance for already affected stock through auditable evidence-based
      resolution. The post-login ledger reaches 21 sale updates/nine unresolved; new activity does
      not resolve historical affected stock and unknown acquisition coverage stays unresolved.
      A nine-unit offer has 11 units in affected acquisition rows; old aggregate positions are
      not safe fallback quantities. Preserve original rows, unknown costs/profit and risk window.
      Prevention alone does not resolve this task. No manual database repair, replay or bypass.
      Tracked in https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/70.
      Complete read-only canonical prefix retained/independently verified: 11,598 rows/cursor 13,221,
      six eligible forecasts with ambiguous horizon outcomes. No accounting or learning promotion.
      Existing Copilot history was retrieved read-only without another owner export: 17,829 unique
      transactions, latest 15 October 02:17:25 UTC. Only 16 overlap the retained Friend range;
      3,037 nonempty Friend observations are later. This does not close the missing stock evidence.
- [ ] Complete a controlled player-operated buy -> collect -> sell workflow, partial cancel/relist
      and restart. Fresh #69 capture/source tests do not establish live eligibility or acceptance.
- [ ] Observe new full-counter/terminal buy accounting in a controlled trade. Fresh #69 capture
      passes; it does not establish a completed live trade or new buy budget.
- [ ] Design an auditable evidence-based resolution for already recorded false progress gaps.
      Preserve the raw sources, financial identities, unknown cost/profit and durable risk window.
      No manual database edit, replay, adoption, risk reset or bypass to force advice.
- [ ] Finish sustained latency and final-authority acceptance. #88 fresh READY/final PLACE_BUY and
      owner-confirmed buy guidance pass after normal history warming. Sale/entry reads can exceed the
      old 750ms client budget; transport and quiet-market corrections are installed. Measure sustained post-update reads,
      ACK drain and final eligible advice while retaining freshness, funding, loss and 5m/Medium
      settings. No manufactured funds, cleared reserve or risk reset to force an answer.
- [ ] Finish search/player-key/pause/context acceptance; these are not established by screenshots.
      Controlled intended trade, partial cancel/collect/relist and restart still need real outcomes.
- [ ] Continue batched HOME TEST CHECKLIST.md and PR #31 live acceptance. Installed combined #91 runtime/#92 tooling,
      status/API 1.13.2 compatibility and GET pipeline pass; fresh post-login source proof passes.
      Investigate unattributed repeated Swing layout exceptions before claiming UI stability.
      Confirm/highlight screenshot checks pass. Search/player-key/pause/context and controlled
      trade/cancel/collect/relist/restart still need outcomes. Agent collects logs directly; every
      game input remains manual. Earlier READY responses remain historical evidence only.
- [ ] Finish one authoritative ledger for planner, exits, inventory UI, calibration and session risk.
      Durable accounting, Performance, exposure and managed trade quantities/exits are implemented;
      durable recommendation risk is source-verified above; finish calibration/remaining consumer audit
      and preserve positive acquisition/cost evidence. Reproduce
      real faults before fixes; never edit a database or bypass exposure guards to unblock advice.
      Current consumer audit/final sale authority are source-verified in
      docs/architecture/parity-account-evaluation.md; controlled runtime acceptance stays open.

## Next

- [x] Deliver #84 history-tolerant planning: lifetime and prospective capital are separated;
      paused-item exposure, new-capital reserves, unknown financial rows and the anchor persist.
      Windows clean 89s: 1,596 Java passed/six existing skips; 103 Python passed in 127.800s.
      Both exact-head CI checks pass; verified paired update/status/compatibility/scoped API pass.
      docs/history/history-planning-delivery-2026-10-18.md records source, install and live limits.
- [x] Reproduce/fix the owner's flickering Missed flips window and unstable selection. #86 is merged
      and installed after both exact-head CI checks: 1,604 Java passed/six existing skips; 103 Python
      tests pass. Verified backup/pair/Java 11/task/status/compatibility/scoped GET checks pass.
      Read-only ledger/risk/gap preservation passes. docs/history/history-display-delivery-2026-10-18.md
      records delivery. Owner confirms installed Missed flips stays populated while filtering;
      fresh #86 authenticated eight-slot proof passes at age six seconds.
- [x] Deliver owner-selected current-account discrepancy/risk policy (#88/3eb37d4). Original financial
      rows, unknown amounts, paused items and risk anchor persist. Both exact-head CI checks pass;
      1,616 Java passed/six existing skips; 103 Python passed; supported backup/pair/status/compatibility/
      scoped GET checks pass. Fresh READY/final PLACE_BUY and owner-confirmed buy guidance establish
      entry availability. See docs/history/current-account-delivery-2026-10-18.md. This does not
      resolve affected historical stock, establish complete trading acceptance or validate learning.
- [ ] Accept the installed combined sell-verification fixes (through #94/c8d3a18). Delayed reads,
      missing sell identities, acknowledged partial-fill races and incompatible retained prices are
      reproduced/fixed. Windows clean: 1,628 Java passed/six existing skips (80s), Java 11; 108 tooling
      tests pass. Both exact-head CI checks, verified pair/backup/task/financial-preservation/status/
      compatibility/scoped GET checks pass. Owner still saw waiting; #94 adds the quiet-market fix
      above. Initial sell guidance now has owner confirmation and fresh recorded trade transitions;
      complete maintenance/partial/cancel/relist/restart acceptance remains pending. See
      docs/history/quiet-market-sale-delivery-2026-10-18.md. No task/privilege/data repair or bypass.
- [ ] Audit calibration prediction provenance and cancellation censoring before changing strategy.
      Attribution, observed intervals and the runtime promotion fence are source-verified in history.
      Historical execution aggregates still pool censored/completed times but no longer authorize
      runtime correction in the new source. Forecast decomposition is source-verified above;
      complete frozen source captures and conservative horizon scoring are source-verified above.
      Finish supported censor-aware fitting, frozen challenger/training artifacts and promotion gates.
      Preserve existing data/dedup keys and separate
      real/paper evidence. Reproduce concrete faults first; promotion requires chronological held-out
      evaluation against a frozen baseline with return/loss/calibration guardrails.
      A reproducible bounded-logit/minimax Brier fitter and chronological validation CLI now exist;
      unknown/censored labels remain visible. The earlier validation has zero paired Brier improvement;
      the newer fit's later 25-forecast cohort (13 known/12 uncertain) still permits degradation.
      No model activates. See
      docs/history/prospective-evaluation-2026-10-18.md. Runtime promotion/compatible forecast loader, rollback
      and drift controls remain unimplemented. No fitted artifact activates learning.
- [ ] Finish manual cancel -> collect -> revalidate -> replace and partial-fill protection as a
      complete live workflow; migrate sell maintenance and exits to companion policies.
- [ ] Build account-level chronological replay for 100M members at 5m, 30m, 2h, and 8h checks.
      Include end-of-run inventory liquidation, cash, buy limits, tax, and missed visits.
      AccountReplay now models shared cash/eight slots, partial fills, sequential attendance,
      collection, tax, canonical limits/risk, restart and conservative terminal marks in twelve
      scenarios. Shared live/replay display-price stability and quiet-stock pricing are installed in replay v3. Complete production
      buy-replacement/relist/foreground acceptance coverage and a
      qualified frozen market benchmark remain open; fixtures are not performance evidence.
- [ ] Add prospective session/cadence measurement and full-account replay; historical CSVs do not
      contain the session clocks needed for a fair GP/hour baseline.
- [ ] Measure fill calibration, then add gated challenger learning and clearly separated paper data.
- [ ] Close the feature-parity matrix in `docs/project-direction.md` and update user documentation.
      Deferred: validated forecast bands/actionable dump strategy, bank tags/badges/slot colors,
      broader paginated history/export and optional cross-device sync. Current details/player steps:
      docs/architecture/parity-account-evaluation.md. No parity claim is established.

## Waiting

- Historical session/cadence labels are unavailable. Do not infer them from execution timestamps.

## Historical evidence

Detailed completion receipts are preserved in [the 17 October task archive](docs/history/tasks-through-2026-10-17-recommendation-delivery.md). The earlier full pre-compaction task list remains in
[task history](docs/history/tasks-through-2026-10-16-source-audit.md). Earlier receipts remain in
[older task history](docs/history/tasks-through-2026-10-15.md). All unchecked work remains above;
read HANDOFF.md for current source/runtime evidence.
