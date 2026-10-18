# Current-account inventory policy delivery

18 October 2026. The owner explicitly selected **Use current-account policy**: preserve incomplete
historical trades and pause affected stock, while verified current funds can support unrelated buys.
This supersedes the old account-wide historical uncertainty charge. It does not resolve unknown
money, infer Copilot's private policy or authorize an accounting repair.

## Implemented and tested

[The policy contract](../architecture/current-account-inventory-policy.md) documents schema 2 risk,
separate historical/current unpriced-stock reserves, preserved original loss anchor and confirmed
losses, current uncertain-offer refusal, copied physical observations excluding cached bank units,
exact acquisition-pool allocation, unseen-stock retention, observed unknown-cost sale maintenance
and stable/exact Holdings investigation. Personal belongings are not adopted from item history.

Regression-first receipts under ignored local-data reproduce historical blocking, missing-price
refusal, unsafe live-offer acceptance, phantom sale guidance, rounded holdings profit and paused
manual-sale maintenance. Two active partial-buy regressions found during the first full build were
fixed before the final candidate; existing partial/cancel/replacement tests pass.

The final standard Windows clean build exits 0 in 87s/18 tasks: root 698, core 186 and companion
732 passed, six existing skips: **1,616 Java passed**. Python: **103 passed in 129.904s**.
Private logs: local-data/current-account-final-clean-build.log and current-account-tooling-tests.log.

## Merged and installed

[PR #88](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/88), source
2a08a88d8be312c1beaaa244503b391b27225505, merged as 3eb37d4 into the active rebuild branch after
[both exact-head CI checks](https://github.com/antonioditcharo/OSRS-Flipping-Friend/actions/runs/39111904443)
passed. The clean primary checkout was fast-forwarded before supported installation.

The owner closed RuneLite normally. Native apply-update.ps1 -Unattended -SkipBuild exits 0.
Backup 20261018-003759-5ee9b61e is VERIFIED: three companion/22 plugin account files, verified
original JAR/manifest hashes and task export. Exact native task XML/SDDL is unchanged; lifecycle=1.
An initial comparison differed only because Set-Content appended a newline to the saved after
snapshots; rewriting the snapshots without a newline confirms exact equality. No task was changed.

Installed artifacts match the verified build, with project bytecode major 55 (Java 11):

| Artifact | Bytes | Classes | SHA-256 |
| --- | ---: | ---: | --- |
| Plugin | 518814 | 229 | 8a8fc8fabb251d392e9a83c7861c8f3226deb25ba5aaad79dc53d77d7adb12ee |
| Companion | 17988363 | 488 | 2ebc58f048f22beb8899bf714c16f099efa8e7022d3f45611bce3a1bec71f2aa |

Independent native status, launcher CheckOnly (RuneLite API 1.13.2), and explicitly scoped GET-only
pipeline exit 0. Supported Start reopened RuneLite; login and every game action remained manual.

## Runtime evidence and acceptance

The first installed logged-in plan had positive current-account risk but no history yet for its
90 shortlisted items. After ordinary warming, the 08:40:13 UTC scoped GET returned READY with one
allocation and its exact final entry decision returned PLACE_BUY / ENTRY_CANDIDATE_SELECTED.
Historical uncertainty remains separately reported; old affected items remain excluded.
The owner subsequently confirmed **"I am getting buy suggestions again"** and reported bought
Dragon knives, a Masori helm and another item. This accepts restored buy-guidance availability,
not a controlled end-to-end trade or measured performance.

Read-only/query_only transactions compare all 25,967 pre-update event rows and all 1,337 original
financial effects exactly. The sole original risk anchor is identical, without replacement/reset.
Initial post-update comparison has zero new effects; later normal player activity has 13 new
effects. The ledger advances from 32 to 36 sale updates while 18 unresolved sales and 1,302 missed
evidence rows remain. No historical source, financial row, uncertainty or ownership was repaired.

The owner reported transient "Companion entry decision unavailable" and persistent sale verification
for some new purchases. A GET-only Java probe using the installed client's 750ms budget times out
twice (782/765ms); the same sale read succeeds with a 5s transport budget in 1,955ms. These later
reads correctly refuse stale account evidence because RuneLite is closed; they are latency
reproductions, not fresh sale authority. Client source also discards canonical SELLING identities,
preventing maintenance. The next source increment addresses these faults and their partial-fill
races; [the sale delivery contract](../architecture/sale-guidance-delivery.md) records its gates.

Private artifacts, task snapshots, financial-prefix verifier, API captures and probe code are in
local-data/current-account-delivery-20261018. Broad controlled trading, replay, four-cadence
measurement and qualified learning/promotion remain open. No full-parity claim is made.
