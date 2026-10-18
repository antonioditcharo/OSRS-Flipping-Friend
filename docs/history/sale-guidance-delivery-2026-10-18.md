# Sell verification delivery

18 October 2026. After #88 restored buys, the owner reported waiting on sell verification for
Dragon knives, a Masori helm and another purchase. The retained new financial effects contain
Dragon knife acquisitions and completed ornate-maul-handle, Robin Hood hat and Masori mask sales;
they are observations, not controlled acceptance of every guided step.

## Implemented and tested

[The sale contract](../architecture/sale-guidance-delivery.md) documents three reproduced faults:

- The client's 750ms read budget times out twice on actual ledger-backed sale reads. A 5s read
  returns in 1,955ms. Bounded GET reads now tolerate that latency without extending advice,
  source, account or quote validity. The new consistent single-prefix read takes 1,922ms cold
  and 435/433ms warm in a read-only standalone probe; these are not HTTP performance guarantees.
- Canonical SELLING identities were discarded, so observed sale maintenance could not authorize.
  Retain authenticated sell identities and reject a replaced source/quantity even if its new
  acknowledgement drains during verification. Delayed exits/entries, wrong sources, cleared
  offers, unknown-cost personal maintenance and both partial-fill races have behavioral HTTP tests.
- The client retained a sell price across a 1% market move under its 2% display tolerance, but
  final authority refused it outside its existing 0.5% drift budget. Shared price stability now
  stays inside that existing sell budget, preserving small-noise/buy/expiry behavior and exact
  taxed floors. Account/logout/backward-clock retention is withdrawn.

The planner/ledger sale read shares one fresh SQLite transaction and scope calculation. A concurrent
progress test proves both reads use the same prefix and the next request sees the new prefix.
Failed reads roll back; no verification request books an execution. The cold-history card now
explains loading rather than claiming the market has no eligible item.

Final standard Windows clean: 80s/18 tasks, root 708 + core 186 + companion 734 passed:
**1,628 Java passed, six existing skips**, zero failed/error. Both artifacts target Java 11.
The supported updater's final **108 Python tests pass in 94.106s**, including isolated lifecycle
guards and a native child test rejecting mismatched creation/arguments before stopping its own child.
Logs remain in ignored local-data; no pre-existing process is controlled by a test.

## Merged source and delivery

Each increment passed both exact-head Windows/Linux CI before merge into the active rebuild branch:

| PR | Source head | Merge | Exact-head CI |
| --- | --- | --- | --- |
| [#89](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/89) | bf8c2e3 | fb5f850 | [39158809371](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39158809371) |
| [#90](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/90) | 29143df | ae25d63 | [39159970701](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39159970701) |
| [#91](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/91) | 5eae502 | 90be5b1 | [39160354931](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39160354931) |
| [#92](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/92) | ee3d265 | da9bdd8 | [39161131411](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39161131411) |

The first supported attempt refused masked CIM arguments before changes. A limited native read
proved the actual installed JAR and current owner. The second identified the companion but its
PowerShell stop was denied; runtime/account/task state stayed unchanged. Original-artifact staging
backup 20261018-115215-18a68d00 is retained, unverified, and is not an account backup/resume point.
Initial #90 Windows CI exposed a test comparing a canonical expanded path with its literal 8.3
argument; corrected exact-argument verification passes. These were not approval rejections or
reasons to weaken process/ownership guards.

The final supported apply-update.ps1 -Unattended -SkipBuild exits 0 from the clean primary checkout
at da9bdd8. Its [native process fallback](../architecture/user-process-updates.md) repeats ownership,
JAR and creation proof, then verifies exact creation/arguments on the held limited native handle.
No elevation, privilege, task/ACL change, manual listener kill or game input is used.

Backup **20261018-120146-7759f4bd** is VERIFIED: three companion/22 plugin account files, original
JAR/manifest hashes and task export. Exact native task XML/SDDL is unchanged; lifecycle=1.
Installed hashes match the verified #91 build (the #92 change is tooling only):

| Artifact | Bytes | Project classes | SHA-256 |
| --- | ---: | ---: | --- |
| Plugin | 520608 | 230 | eabd7f2e6aac071e248b7dfbae9f6978e74a63437be1ee89a3bd0ac899c7b5d1 |
| Companion | 17991421 | 490 | d4a9ee8d4327ae653be34fd754bc9a67bb404de9e675522875ddcad1f59b39ca |

Independent native status, launcher CheckOnly (RuneLite 1.13.2) and scoped GET-only pipeline all
exit 0. The old capture correctly awaits a fresh client observation; transport success is not
current trading authority. Supported Start reopened RuneLite; every login and game action is manual.

Read-only/query_only before/after comparisons preserve all **32,694** pre-update account event
rows, **1,350** financial effects and the sole original risk anchor exactly, with zero new effects
at this check. Historical gaps/unknown amounts remain; the ledger has 36 sale updates/18 unresolved.
Private artifact/task/backup/API/financial receipts and reproduction code remain under
local-data/sale-stability-delivery-20261018 and sale-guidance-delivery-20261018.

## Acceptance and continuing parity work

Owner-confirmed buy availability from #88 and accepted Missed flips stability remain recorded.
Fresh installed sell/maintenance acceptance is pending: log in/open GE, follow Collect manually,
then verify the next collected managed buy receives a Sell card without a persistent verification
wait. Cancel, collect, relist, player-key use, placement and restart acceptance remain manual gates.
The agent collects fresh source/read-set, account, final-policy and timing evidence directly.

Replay v2 now shares live price stability, reproduces twelve synthetic 100M/members/eight-slot
5m/30m/2h/8h cadence/share scenarios, and the retained v1 evaluator still reproduces its old bundle.
Complete production replacement/relist/foreground timing coverage and qualified market benchmarks
remain open. [New prospective evidence/fitting](prospective-evaluation-2026-10-18.md) retains all
unknown/censored outcomes; no later calibration gain or learning promotion is established.
Full functional parity, whole-account superiority and validated learning remain unaccepted.
