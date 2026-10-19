# Trade continuation batch — 19 October 2026

Source batch on `codex/trade-continuation-batch`; #114 remains installed at this checkpoint.
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

Candidate plugin: 604,011 bytes, SHA-256 8907da481de72c62f52460e678f746498578f6f82da4badcbd342a9c36d28513.
Candidate companion: 18,115,707 bytes, SHA-256 d881ef276aef6558edd435330eecb0742a245b9c3d97b18127142a8476c9e74b.
Private consolidated evidence: `local-data/continuation-batch-20261019/` (build/XML/hash receipts,
focused workflow checks, packaged replay with retained evaluator, CI and delivery state).
Read-only pre-update capture retains 62,073 source rows, 1,470 financial effects and the original anchor.

Both exact-head platform CI, merge and supported paired installation are pending at this checkpoint.
The agent handles normal native closure/update/relaunch; login and all game actions stay manual.
Player trade-loop quality, actual timing, remaining full production-replay coverage and qualified
return/learning gates remain open. The fixture gains are not player profit or validated predictions.
