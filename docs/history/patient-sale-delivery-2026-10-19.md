# Sale patience delivery

19 October 2026 (America/Anchorage). Private evidence: local-data/sale-patience-20261019.

The owner reported premature break-even/scrap-profit sells. A read-only account capture retains
59,535 canonical rows and 1,468 financial effects; 74 recent disposal updates include ten known
0–999 GP increments, including exact break-even sales during normal trading. Small increments
alone do not prove a poor complete trade. They are not inferred intent, exact execution times or
a reconstructed history of displayed instructions. The last pre-update capture separately had
sell-only enabled; that does not explain the earlier normal-mode break-even observations.

Four regression-first cases fail on the old source: fresh and quiet initial target preservation,
healthy maintenance jumping to break-even, and custom holding-horizon protection. The new shared
policy lists promptly at a protected target, requires full sell patience/recent-progress and
completed-market support for ordinary maintenance, retains stops/explicit wind-down and checks
the exact floor/target after stabilization. Contract: ../architecture/patient-sale-pricing.md.

Behavioral verification covers the actual client decision chain, scoped final companion policy,
partial fills during a request, durable progress versus heartbeat/missing source, account/episode
fences, unknown personal cost, exact subset accounting, quiet quotes, initial/relist guidance and
simulated restart. Replay v5 preserves the selected entry context; complete production-policy and
four-cadence real benchmark gates remain open. No learning promotion or financial rewrite.
An additional review regression reproduces time-only discounting after expiry (a synthetic current
ask of 1,150 becomes 1,020 under older history). Expiry now releases the old target to the current
ask/cost floor, without an unrequested modeled discount; fresh stops/wind-down retain urgent pricing.

Refused routine maintenance now leaves the existing listing working and continues through the
separate final entry checks. Urgent cuts and unlisted-stock refusals keep priority WAIT. HTTP/source
race fixtures and the actual plugin delivery boundary verify this distinction and prohibit delivery
of the refused modification. Fresh live #103 checks separately show sell-only enabled; new buys
correctly remain disabled by that explicit player setting.

Standard clean Windows build passes in 112s/18 tasks: 1,717 Java passed/six existing skips
(plugin 748/core 193/companion 776), project bytecode Java 11. All 108 tooling tests pass in 153.616s.
The version-5 CLI writes and reproduces all twelve 100M/8-slot cadence/share scenarios from the
retained SCHEMA SMOKE ONLY input; that minute-long structural fixture is not a market benchmark.
The active round-trip/partial/restart behavioral fixtures separately pass. New JAR identities and
module counts are retained in build-verification.json, with the reproducing evaluator in replay-v5-smoke.

## Verified delivery

PR #105 merges as e21a0495462a5fe59620acf45963e96125505caa. Both exact-head checks pass for
24ec8a065a29094fcf73b353246e085974f99a65, run 39232384022: Ubuntu 09:47:57Z and Windows
09:49:10Z on 19 October. The clean primary/default checkout was fast-forwarded; main is untouched.

Installed through the supported unattended paired updater at 2026-10-19T09:50:31.1997882Z after
normal player closure. VERIFIED backup 20261019-015028-11a456ab; lifecycle remains 1 and task
XML/SDDL are byte-identical. Actual installed artifacts match the verified local candidate:

- Plugin: 586,297 bytes; SHA256 6d937810705c52ec5471d5e647d89d219462e0dfedcfadade63d35b948b55c38.
- Companion: 18,081,773 bytes; SHA256 54e85426661d9006df975882732dc9e6b13ca3f79061e4a0683bf2ff80a8b746.

Read-only comparison preserves all 61,527 retained event-log rows, 1,469 financial effects and the
sole original loss anchor, with zero effects created by this update. The earlier #103 prefix is
also preserved, with one legitimate subsequent financial append. No old evidence was repaired.
Status is PAIR VERIFIED / COMPANION RESPONDING; RuneLite 1.13.2 API/cached artifacts match.
Scoped GET pipeline passes (86 sale updates/22 unresolved/one profit range); logged-out entry
correctly waits for current client cash/slots. Supported Start reopens RuneLite and side-loads the
installed JAR. Private delivery-state.json, hashes, frozen prefixes, status/compatibility/pipeline/
start/update logs and exact-head CI receipt retain details. No game input or forced closure.

Fresh login/GE, controlled initial and mature sell quality/partial/cancel/collect/relist/restart
acceptance remain pending. Player steps are in the linked architecture contract. No full sell
workflow, optimal pricing, improved net account return, complete parity or promoted learning is claimed.
