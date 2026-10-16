# Tasks

Updated 16 October 2026. The current checkpoint below supersedes historical runtime/review
statements later in this file. Read [HANDOFF.md](HANDOFF.md) for exact evidence.

## Active

- [x] Source one-time, task-specific maintenance access on codex/user-owned-companion-updates
      from #75/1f98391. Regression-first 2c2a5ab reproduces late permission failure/missing setup
      access; native tests 26fe7c6 cover exact scope, least privilege, preservation and rollback.
      Standard 49s: 1,528 Java passed/six skips; 94 tooling tests pass in 76.495s; PowerShell 7
      native gate 13 passed. 462b39f reproduces CI's fake-task SID alias mismatch; fixture corrected,
      production rollback checks unchanged. Real scope is valid; maintenance/repair access is false.
      See the branch PR for exact-head CI and delivery. No real permissions/JARs changed.
- [ ] Apply the one-time task repair with Windows administrator approval, then verify a normal-user
      unattended paired update and launch. Use 6 - Enable User Updates as administrator once under
      the same account; never disable UAC or create an elevated unattended helper. RuneLite was
      closed normally on the owner's explicit request; recheck rather than ask for another close.
      Installed pair remains #69; batch the already merged #72/#75 runtime changes together.
- [x] Reproduce misleading reserve/session-loss explanations in 945bf47, then correct the durable
      risk reason, waiting card and notification together. Fresh matching risk is required for its
      explanation; no accounting, risk arithmetic or refusal changes. Standard: 1,528 Java passed/
      six skips, 70s; 79 Python passed in 94.933s; both JARs Java 11. Merged #75 as 1f98391 after
      both checks at c038cab passed (run 38946004083; Windows 117125172627/Ubuntu 117125172354);
      installed pair remains #69. This does not resolve stock, release a reserve or authorize buys.
- [x] Add/run an offline trade-source audit on codex/trade-evidence-audit from #72/eaa63b1.
      Verified parent capture, private report, retained actual auditor and full recomputation remain
      separate from financial authority. 35 observed identities/23 with flags over 11,598 rows;
      audit/wrapper verify/retained-artifact verify exit 0. Original rows and unknown money unchanged.
      Standard 47s: 1,520 Java passed/six skips; 79 Python passed in 85.248s; Java 11. Merged #73
      as cb25c1f after both exact-head checks at a63a745 pass (run 38880832949).
      Source references identify missing evidence; this does not resolve stock or authorize buys.
- [x] Reproduce/prevent cumulative accounting replay after a cleared episode, source-only on
      codex/cleared-offer-accounting from #71/a4935b4. Regressions a63a534/cf94779/86137e3/1357b42
      precede fixes. Persist new episode UUIDs, preserve legacy wire/learning keys, reject reused
      identity via retained canonical sources, keep contradictory items paused and withdraw stale
      Collect cache on rejection. Standard 49s: 1,503 Java passed/six skips; 79 Python passed in
      85.639s; both JARs Java 11. Merged #72 as eaa63b1 after both checks at da449a7 passed
      (run 38876972197; Windows 116894240240/Ubuntu 116894240365). Read-only timing in HANDOFF;
      installed runtime remains #69. Batch update with remaining work; no repeated client cycle.
- [x] Observe fresh #69 login/current capture with no additional reconciliation gaps. Installed
      packaged read-only proof: valid=true, eight EMPTY/eight matched sources, age three seconds,
      5m/Medium. Outbox drains four -> zero normally. 30 post-risk-cursor gap rows remain unchanged;
      ledger now 11 sales/eight unresolved. Risk still refuses entries on existing reserves.
- [ ] Restore safe sell guidance for already affected stock through auditable evidence-based
      resolution. The latest recorded ledger has 11 sales/eight unresolved; eligibility is withheld.
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
- [ ] Finish sustained latency/final eligible entry acceptance. Latest 10:07 portfolio/action/ledger/
      learning reads are 1,062/79/1,765/703ms, outbox zero, plan COMPANION_STATE_UNAVAILABLE and no
      canonical risk. Earlier fresh eight-slot/risk evidence remains historical. Retain freshness/
      funding/loss guards and 5m/Medium. No manufactured funds,
      cleared reserve or additional restart to obtain a final eligible-entry response.
- [ ] Finish search/player-key/pause/context acceptance; these are not established by screenshots.
      Controlled intended trade, partial cancel/collect/relist and restart still need real outcomes.
- [ ] Continue batched HOME TEST CHECKLIST.md and PR #31 live acceptance. Installed #69 pair,
      status, API 1.13.2 compatibility and fresh current-source proof pass; #72 remains queued.
      Confirm/highlight screenshot checks pass. Search/player-key/pause/context and controlled
      trade/cancel/collect/relist/restart still need outcomes. Agent collects logs directly; every
      game input remains manual. Earlier READY responses remain historical evidence only.
- [ ] Finish one authoritative ledger for planner, exits, inventory UI, calibration and session risk.
      Durable accounting, Performance, exposure and managed trade quantities/exits are implemented;
      durable recommendation risk is source-verified above; finish calibration/remaining consumer audit
      and preserve positive acquisition/cost evidence. Reproduce
      real faults before fixes; never edit a database or bypass exposure guards to unblock advice.

## Next

- [ ] Audit calibration prediction provenance and cancellation censoring before changing strategy.
      Attribution, observed intervals and the runtime promotion fence are source-verified in history.
      Historical execution aggregates still pool censored/completed times but no longer authorize
      runtime correction in the new source. Forecast decomposition is source-verified above;
      complete frozen source captures and conservative horizon scoring are source-verified above.
      Finish supported censor-aware fitting, frozen challenger/training artifacts and promotion gates.
      Preserve existing data/dedup keys and separate
      real/paper evidence. Reproduce concrete faults first; promotion requires chronological held-out
      evaluation against a frozen baseline with return/loss/calibration guardrails.
- [ ] Finish manual cancel -> collect -> revalidate -> replace and partial-fill protection as a
      complete live workflow; migrate sell maintenance and exits to companion policies.
- [ ] Build account-level chronological replay for 100M members at 5m, 30m, 2h, and 8h checks.
      Include end-of-run inventory liquidation, cash, buy limits, tax, and missed visits.
- [ ] Add prospective session/cadence measurement and full-account replay; historical CSVs do not
      contain the session clocks needed for a fair GP/hour baseline.
- [ ] Measure fill calibration, then add gated challenger learning and clearly separated paper data.
- [ ] Close the feature-parity matrix in `docs/project-direction.md` and update user documentation.

## Waiting

- Historical session/cadence labels are unavailable. Do not infer them from execution timestamps.

## Historical evidence

Detailed prior completion receipts and the full pre-compaction task list are preserved in
[task history](docs/history/tasks-through-2026-10-16-source-audit.md). Earlier receipts remain in
[older task history](docs/history/tasks-through-2026-10-15.md). All unchecked work remains above;
read HANDOFF.md for current source/runtime evidence.
