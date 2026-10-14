# Tasks

## Active

- [ ] Make canonical durable positions, offers, costs, proceeds, and realized profit the shared
      source for the planner, exits, inventory UI, calibration, and session risk.
      Durable sale accounting, live Performance, and planner per-item exposure are implemented;
      other consumers still use
      local records and must migrate with positive reconciliation evidence.

## Next
- [ ] Live-retest explicit unpriced-stock review installed at rebuild `49934e44` (PR #37).
      Regressions preceded implementation (`932ab1f` / `1f728eb6`); local standard verification passed
      1,186 Java tests with six skips and all 48 Python tests. Final Windows/Linux CI at `0e38e070`
      passed before merge. Owner completed the supported paired update after review of backup
      `20261013-233014-f45bc48b`: three companion account files / 6,225,568 bytes and 22 plugin records /
      1,502,156 bytes matched source filenames/sizes; prior JAR/manifest hashes and task action/owner
      matched. Completion output supplied 14 October reports BUILD SUCCESSFUL in 4s and both JARs
      verified. Agent status and launcher CheckOnly exited zero; installed/build hashes matched
      (plugin 407,086 bytes; companion 17,737,530 bytes), with RuneLite API 1.13.1 compatibility passed.
      Source/runtime now `49934e44`; documentation branch `codex/stock-review-live-verification`.
      Owner had confirmed four personal
      holdings and three fully sold/no-longer-held old entries. Player must make those declarations
      in Review holdings after installation; do not edit live records or seed preferences from chat.
      Preferences exclude matching unpriced entries from trading decisions without changing ledger
      quantities, costs, sale history or unknown profit. Known-cost conflicts, changed evidence and
      open/uncollected offers remain protected. Scoped degraded history stays visible while a separate
      safe planning gate handles reviewed exposure. Next: numbered Start, manual login, confirm Review
      holdings is present, then inspect the fresh scoped ledger before the player makes declarations.
      14 October 01:02 AKDT: player reports Review holdings appears. Read-only pipeline exited zero,
      but Action WAIT / Plan UNAVAILABLE persists. Fresh scoped ledger GET returned HTTP 200 and
      shows the seven prior unpriced entries plus three new invalid lifecycle transition entries,
      including two known-cost positions. Review choices were left unchanged; cause is not established.
      Diagnose read-only and reproduce any real bug on a fresh codex branch before source changes;
      never clear or repair live records to unblock advice. Private evidence is in local-data diagnostics.
      Review saving, post-review buy readiness and controlled manual acceptance remain pending. Full single-authority
      exits/UI/risk migration and positive cost reconciliation are still prerequisites.

- [ ] Complete live acceptance with the compatible pair installed at `cb203a83` (PR #36 merged after
      final Windows/Linux CI passed at `10d1a4bc`). The earlier mixed 1.12.39/1.13.1 classpath and
      long GE getter linkage error were reproduced by regressions before the source fix. Standard
      local verification passed 1,168 Java tests with six skips and all 48 Python tests.
      User confirmed the paired update after review of backup `20261013-220953-f1ad1d84`: companion
      account filenames/sizes matched (3 files / 321,176 bytes), plugin accounts matched (22 files /
      1,500,384 bytes), both prior JARs and manifest matched by SHA-256, and task action/owner matched.
      User reported BUILD SUCCESSFUL in 5s, Companion ready (accounts-v1), and both JARs verified.
      Read-only status and launcher CheckOnly both exited zero; installed plugin/companion matched
      the verified builds (393,350 / 17,722,571 bytes), with RuneLite API 1.13.1 compatibility passed.
      User now reports login and sell recommendations working, but no new buys. Pipeline check at
      22:27 AKDT exited zero: artifacts/protocol/market/ledger reads passed; plan UNAVAILABLE with
      "Durable holdings or offers require reconciliation before new buys." Scoped health and ledger
      GETs identified seven RECONCILE entries: five unknown-cost holdings and two disposal entries
      without a durable position. Raw account details remain private in local-data diagnostics.
      New-buy readiness is blocked; controlled manual acceptance cases remain untested. The user has
      supplied classification; the new source workflow above now awaits paired installation/live review.
      Do not edit live records or bypass the guard.
      No agent database, credential or game-setting changes occurred. See latest HANDOFF; documentation branch
      is `codex/pr31-live-verification`.
      An unrelated popup was traced to obsolete hourly `FlippingFriendLearning`, pointing to the
      absent daemon JAR in this checkout. Its definition was backed up (1,907 bytes), action/principal
      checked, then disabled/stopped. Current companion remained running. The historical forecaster
      task was not changed; do not revive unsupported components or infer database ownership.
      Full 64-bit unit-price support remains pending; unsupported-price evidence pauses advice and
      accounting across restart. Preserve that evidence rather than manually clearing it.

- [ ] Run the live verification checklist in HANDOFF.md for PR #31 (durable planner exposure)
      on the real account and RuneLite; record results before relying on it for trading.
      13 October checkpoint at `ee0d3aff`: build passed; installation and manual checks remain
      unperformed. The scheduled task is disabled and configured for another checkout; the expected
      scoped accounts directory is absent. Locate backup sources read-only before installation.
      The updater now rejects the bad task before changes; it does not repair live configuration.
      The returning-user workflow is now `1b - Re-setup Existing Installation.bat`: close RuneLite,
      review the generated backup before typing INSTALL, then launch/login and perform read-only
      pipeline/manual checks. No live install was executed during development.
      Later backup review at base `cb2c1d2d`: both prior JAR backups matched source hashes; task XML
      resolves to the current Windows user. Plugin account records were found separately from the
      absent companion scope directory and copied into the dated backup (21 files / 1,501,532 bytes;
      relative filenames/sizes matched). Installer remains at INSTALL; no installed pair replacement,
      pipeline-check, login or manual trading checks have been observed. See the latest handoff.
      These are historical checkpoints. Latest checkpoint at `cb203a83`: backup and compatible
      paired update completed; status and launcher compatibility passed. User reported login/sell
      display; read-only pipeline passed but buy plan remains reconciliation-blocked. Controlled manual
      trading acceptance remains pending, recorded above and in the latest HANDOFF continuation.

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

## Done
- Reproduced the missing plugin account backup before fixing it on `codex/plugin-account-backup`.
  The wrapper now preserves the separate profile records as `plugin-accounts/` before confirmation,
  retaining companion hashed scopes separately and rejecting links without importing data.
  Standard build passed 1,163 Java tests with six skips; all 40 Python tests passed (32 lifecycle).
  Completed the observed live backup with a supplemental copy while the user's installer was paused;
  source data and all databases stayed unchanged. Installation and live checks remain pending.

- Unified first setup, explicit returning-user re-setup, paired updates, start and status behind
  the guarded installer. Backup review precedes confirmation/build/copies/task reconfiguration.
  Installed companion is outside source build outputs; hash manifest blocks incomplete or altered
  pairs. Everyday launch does not rebuild. Re-setup preserves saved login and refreshes the shortcut.
- Tested failure, preservation, status and uninstall paths with 29 actual-script lifecycle tests;
  all 37 Python tests passed. Standard build at `b5ea2936` passed 1,163 Java tests with six skips and
  built both JARs. These are automated results; real setup, login and trading remain unverified.
  Review added a reproducing task-registration guard regression (38 final Python tests) so setup
  cannot report completion through direct launch when the configured task is unobservable.

- Added actual-script updater regressions before fixes, using disposable files and mocked
  process/task/network boundaries. Reject disabled or foreign-checkout/JAR tasks before stopping
  or copying; require both artifacts before replacing the plugin; preserve the valid direct launch
  and Java-on-PATH fallback without JAVA_HOME. Seven updater tests plus eight importer tests pass.
- Verified implementation `152db838` with the standard Windows build: 1,163 Java tests passed,
  six skipped, both JARs built, 15 Python tests passed. Recorded the incomplete live checklist
  without inventing a pipeline-check result or changing databases, installed JARs or scheduled tasks.
- Preserved historical dashboard leftovers in a sibling backup using moves, with matching file
  counts/sizes; historical `main` remains unchanged.

- Prevented older or same-timestamp snapshots from restoring sold or partly sold positions.
- Planner per-item exposure now reads durable positions and open-buy reservations in one
  transaction; unresolved or overflowing state withdraws buy advice. Cash, committedCoins,
  preferences, and session risk still come from the client snapshot.

- Persisted sale proceeds, proportional cost, tax/profit bounds, and rule evidence atomically.
- Replaced live Performance journal totals with the account ledger, including unavailable/stale
  response handling, unresolved coverage, and wrapped GP ranges; retained labeled local history.
- Preserved old unpriced disposals and unproven offer tax rules without manufacturing profit.
- Removed merged implementation branches and superseded build logs; retained final verification,
  source data, and the reusable managed worktree.
- Added a current agent handoff linked from README/AGENTS; designated the tested rebuild branch
  as the GitHub default so fresh checkouts discover the implemented work.
- Isolated live companion accounting, policy state, and calibration into explicit account scopes.
- Fenced account-specific API paths, payloads, responses, recommendation caches, and outbox upgrades.
- Restored active/uncollected offers independently of four-hour buy-limit expiry.
- Imported both Copilot exports read-only; committed deidentified aggregate evidence and importer
  tests while preserving original CSVs and keeping raw/row-level outputs local.
- Replaced synthetic-account diagnostics with read-only protocol and account checks.

- Assessed main, the latest package branch, the separate recovery branch, and the preserved stash.
- Fixed blocked items disappearing from physical inventory, equipment, and bank evidence.
- Required the existing adoption opt-in for personal inventory/equipment and retained block policy.
- Made the existing source-boundary hash independent of Windows checkout line endings.
- Fixed the diagnostic capture verifier to emit the LF format its contract already requires.
- Added Windows/Linux verification of tests and both supported artifacts.
- Removed an unused, unconfigured reference gitlink that blocked GitHub Actions checkout.
- Replaced unsupported product claims with the verified status and explicit project direction.
