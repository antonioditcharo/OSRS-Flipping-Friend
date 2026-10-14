# Tasks

## Active

- [ ] Make canonical durable positions, offers, costs, proceeds, and realized profit the shared
      source for the planner, exits, inventory UI, calibration, and session risk.
      Durable sale accounting, live Performance, and planner per-item exposure are implemented;
      other consumers still use
      local records and must migrate with positive reconciliation evidence.

## Next
- [ ] Finish the RuneLite login compatibility fix on `codex/runelite-login-classpath` from
      `6411f800`. User-run installation passed read-only pair/companion status, then login froze
      with a long GE getter linkage error from a mixed 1.12.39/1.13.1 classpath. Eight launcher and
      one Java ABI regressions reproduced the problems before fixes; 48 Python tests now pass.
      Final local Java verification stopped at clean because RuneLite holds the full API extra in
      build/runtime-extras. Wait for the user to close RuneLite; do not kill it or delete the lock.
      Run the standard clean verification, review both CI results, then update the pair through the
      numbered wrapper after showing/reviewing the fresh backup. No troubleshooting source changes
      have been installed, and no databases/settings/credentials were altered. See latest HANDOFF.
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
