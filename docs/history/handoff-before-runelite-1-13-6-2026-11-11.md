# Agent handoff

Updated 2 November 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Current installation

RuneLite **client 1.13.5** is cached through the official launcher. Compatible companion pair
**#206 is installed** at `2026-11-02T09:20:35.1229909Z`, merge
`6f8215660a10ceaafea84f8fe780afb0b889baef`, verified head
`b2ef20604810f898a2f38f18f1f991294619a1ba`. Installed SHA-256:

- Plugin: `7950fce0432eb83ebdc393c03a98d746800525a4124c31312f012062ba259abf`.
- Companion: `2cab9c4296829f01f8ff1d095a368518c506e664889cba49b6772e7715fceccc`.

Clean Java: 2,575 pass/six existing skips; 179 tooling and 100 actual paired-JAR Java 11
checks pass. Both exact-head Windows/Linux CI `41418435815` pass. The first three tooling
failures were obsolete 1.13.4 release fixtures; production guards were retained.
All plugin/companion production class bytes equal qualified #205, so its verified uninterrupted
3,602.282s hour is reused: 120 groups, 10,920 checks, 9,540 instrumented backend requests,
zero failures, 119 resource samples, both markers/all 1,303 hashes/own-class equivalence.
This is not a new 1.13.5 native soak or trading acceptance.

#205 had merged at `baf2eeb` but was never installed before interruption. #206 includes its
ACK-only validated outbox reuse and transient final-sale timing provenance. Atomic writes,
complete-byte invalidation, financial delivery, exact costs and second corrected-price authority
remain. [Contract](docs/architecture/acknowledged-outbox-preparation.md).

Pre-install restoration verifies 538 files and six configurations. Previous #195/#203/new runtime
financial/stock/scope/exposure/continuation/original-risk reads agree on the restored account;
database bytes unchanged. Supported updater backup `20261102-002029-7b98b948` restores all 541
files. Fresh unassigned legacy snapshot/restoration is separate; its owner remains unassigned.
Post-install comparison verifies all 12 original tables identical; live/manual checks stay pending
in currentDelivery. **#195 remains code rollback, #186 economic
reference; #203 and original runtime artifacts are retained. Never restore old account data for
code rollback.** Primary retains three owner deletions; do not restore them.

Private current receipts: primary `local-data/runelite-1-13-5-20261102`; prior qualified source/hour:
`local-data/prepared-guidance-20261030`. currentDelivery in
`local-data/hosted-pilot/delivery-state.json` is authoritative over historical root aliases.

## Required user step and native verification

Launcher itself remains **2.7.2**. Latest official launcher **2.8.0** installer is downloaded,
SHA-256 verified against the official release, and old launcher files retained. Automatic approval
review rejected its execution with “blocked by policy.” Do not retry through another execution
mechanism. A manual installer request is pending: run the verified local installer, leave RuneLite
closed and report completion. Then launch through the supported Flipping Friend start path;
login/GE/trading remain manual. Do not confuse normal-launcher startup with developer-plugin loading.

Status, API/cache compatibility CheckOnly and authenticated GET-only pipeline pass. Service is ready;
pre-login plan correctly awaits fresh cash/slots. The 41 unresolved historical sales are retained,
not repaired or cleared. Fresh visible #206 guidance and complete workflow/native latency remain
unverified until manual login/GE. No new pricing, loss authority or learning is activated.

Retain #203 native evidence: nine MAIN paints p95 148.129ms, one distinct eligible MAIN 900.258ms;
five editor paints p95 52.163ms with zero eligible editor decisions. Initial first COLLECT took
4.977s; price preparation reached 690ms. No SELL-to-WAIT transition in that bounded audit. The short
JFR did not capture a revision. Keep older #202/#200 delays, #198 outage/root cause, unknown source
identity and unobserved live post-cancellation late fills open.

## Remaining direction

All 81 original requirements remain in [TASKS](TASKS.md) and the
[acceptance inventory](docs/verification/trading-pass-acceptance-inventory.md): 79 remain open,
including one partial. No timing or player requirement closes from package/CI results.

- Qualify distinct MAIN/editor publication p95 <=1s after durable ACK and native cached paint
  p95 <=100ms, separately under idle/mixed load. Keep failures, repeats, missing stages and WAITs.
- Observe manual revision, cancel/collect/relist, partial fills and restart. Deterministic real-handler
  late-fill tests satisfy only the selected construction gate; live rare cases remain unverified.
- #199/#201 common-horizon/budget-diverse pricing stays in shadow. Keep 100M/eight slots and
  separate 5m/30m/2h/8h results; do not promote from synthetic fixtures or open the final fold early.
- Observational learner construction follows delivery/latency/provenance gates. Runtime calibration
  stays neutral; market-informed loss authority is a separate release.
- Hosted #194 remains unchanged, shadow enabled, recommendations/coordination disabled, within
  $50/month. Last integrity audit: 621 seals, 4,040 captures/67.3169h, 4,882 component hashes,
  zero failure rows, 319,862,549 bytes/2GiB. Fixed stop **2026-11-08T01:31:16Z** is unchanged.
  No capacity requalification or server upgrade; previous 1,000/100 pilot experiment remains historical.

Earlier detailed receipts/failures/source locations remain in the
[pre-update handoff](docs/history/handoff-before-runelite-1-13-5-2026-11-02.md).
