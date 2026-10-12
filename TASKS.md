# Tasks

## Active

- [ ] Make canonical durable positions, offers, costs, proceeds, and realized profit the shared
      source for the planner, exits, inventory UI, calibration, and session risk.
      Durable sale accounting and live Performance are implemented; other consumers still use
      local records and must migrate with positive reconciliation evidence.

## Next

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

- Persisted sale proceeds, proportional cost, tax/profit bounds, and rule evidence atomically.
- Replaced live Performance journal totals with the account ledger, including unavailable/stale
  response handling, unresolved coverage, and wrapped GP ranges; retained labeled local history.
- Preserved old unpriced disposals and unproven offer tax rules without manufacturing profit.
- Removed merged implementation branches and superseded build logs; retained final verification,
  source data, and the reusable managed worktree.
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
