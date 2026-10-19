# Trade continuation batch — 19 October 2026

PR #116 source 505a912e55a407aa93eeaf29ea7c49f096c952f9 merged as
7e1385b772ed9e67b2e0ec8727a46c9e2e272c92 at 2026-10-19T21:47:59Z; primary clean-fast-forwarded.
The complete batch is INSTALLED, including earlier #114.
The [contract](../architecture/trade-continuations.md) describes the working paths and remaining gates.

Delivered source groups:

- Durable, exact-episode buy continuation and shared live sell/replay state, including late-fill
  bounds, maximum-counter checks, callback collection checkpoints, legacy sell-file compatibility,
  account isolation and restart. Explicit give-up/limit/sell-only clears the buy promise.
- Fresh bounded replacement planning with exact-size economics/forecasts and final cash/eight-slot,
  risk/source/limit/eligibility/resource guards. The client no longer depends on a larger regular
  allocation fitting the remainder, nor silently trims its order or forecast.
- Foreground-accepted maintenance identity; unseen/stale proposals cannot start promises. Shared
  outbid freshness/overflow guards; replay v7 persisted replacement/relist state and rejection of
  overlapping volume windows. No historical accounting changes or strategy/learning promotion.

Windows standard clean build: 92s, 1,761 Java passed/six existing skips, zero errors/failures.
Plugin 767, core 197, companion 803 including six skips; every main class is major 55 (Java 11).
All 108 tooling tests passed in 139.309s. The earlier full pass exposed three old endpoint mocks
and a frozen presentation-source checksum; the mocks now exercise bounded authority and the
reviewed checksum includes the maintenance identity. Final behavior and source-boundary tests pass.
The JRE 11 packaged replay writes and reproduces all twelve 5m/30m/2h/8h x 0.1/0.5/1% schema cases.
Hand fixtures cover a partial buy/remainder delayed to the next visit, another fill during displayed
cancellation, restart between collection and replacement, and partial sell cancellation/relisting
with exact accounting and stop context. These are synthetic conservation tests, not a market benchmark.

Installed plugin: 604,011 bytes, SHA-256 8907da481de72c62f52460e678f746498578f6f82da4badcbd342a9c36d28513.
Installed companion: 18,115,707 bytes, SHA-256 d881ef276aef6558edd435330eecb0742a245b9c3d97b18127142a8476c9e74b.
Private consolidated evidence: `local-data/continuation-batch-20261019/` (build/XML/hash receipts,
focused workflow checks, packaged replay with retained evaluator, CI and delivery state).
Read-only pre-update capture retains 62,073 source rows, 1,470 financial effects and the original anchor.

Both exact-head platform checks passed before merge: run 39329682353, Linux 118324433828 at
21:45:56Z and Windows 118324433654 at 21:47:32Z. Individual API receipts verify full head/state/success.
The agent used normal native Close, verified process exit, and ran the supported unattended updater
once for the entire code batch, with source-identical verified artifacts (-SkipBuild).
Installed at 2026-10-19T21:48:48.9143255Z, VERIFIED backup 20261019-134844-d16fc650. Installed hashes/
lifecycle=1 and protected task XML/SDDL preservation pass. Read-only financial verification preserves
all 62,073 source rows, 1,470 effects and the sole original anchor, with zero new financial effects.
Status is PAIR VERIFIED / COMPANION RESPONDING; 1.13.2 compatibility and scoped GET-only pipeline
pass (87 sale updates/23 unresolved/one range). The new versioned bounded-entry endpoint responds
correctly for the requested account/cap and returns WAIT/COMPANION_STATE_UNAVAILABLE without fresh
cash/eight-slot evidence. No synthetic snapshots/offers/trades were posted by delivery checks.
Supported Start reopened RuneLite at 13:49 AKDT; login and all game actions stay manual.
The new client log confirms the installed plugin side-load and completed client initialization;
post-launch read-only verification again preserves the frozen prefixes/anchor with zero new effects.
Player trade-loop quality, actual timing, remaining full production-replay coverage and qualified
return/learning gates remain open. The fixture gains are not player profit or validated predictions.
