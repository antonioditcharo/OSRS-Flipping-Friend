# Tasks

## Active

- [ ] Make companion accounting and projections account-specific; prove isolation on account switch
      and restart before migrating production position authority.
- [ ] Make canonical durable positions, offers, costs, proceeds, and realized profit the shared
      source for the planner, exits, UI, and performance reports.
- [ ] Restore long-lived active offers from durable state, including an eight-hour absence.

## Next

- [ ] Finish manual cancel -> collect -> revalidate -> replace and partial-fill protection as a
      complete live workflow; migrate sell maintenance and exits to companion policies.
- [ ] Build account-level chronological replay for 100M members at 5m, 30m, 2h, and 8h checks.
      Include end-of-run inventory liquidation, cash, buy limits, tax, and missed visits.
- [ ] Import the user's Flipping Copilot history as an immutable baseline; distinguish measured
      historical results, simulated comparisons, and prospective alternating-session evidence.
- [ ] Measure fill calibration, then add gated challenger learning and clearly separated paper data.
- [ ] Close the feature-parity matrix in `docs/project-direction.md` and update user documentation.

## Waiting

- Copilot flip-history export and any available timestamps for session starts/ends and remaining
  inventory. Intake requirements are in `docs/project-direction.md`.

## Done

- Assessed main, the latest package branch, the separate recovery branch, and the preserved stash.
- Fixed blocked items disappearing from physical inventory, equipment, and bank evidence.
- Required the existing adoption opt-in for personal inventory/equipment and retained block policy.
- Made the existing source-boundary hash independent of Windows checkout line endings.
- Fixed the diagnostic capture verifier to emit the LF format its contract already requires.
- Added Windows/Linux verification of tests and both supported artifacts.
- Replaced unsupported product claims with the verified status and explicit project direction.
