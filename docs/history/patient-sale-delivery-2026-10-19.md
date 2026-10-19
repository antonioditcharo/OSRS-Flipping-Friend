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

Exact-head Windows/Linux CI, paired installation and player acceptance remain pending at this
checkpoint. Installed #103 includes the earlier #94 sell availability fix, not this queued pricing
change. No full sell workflow, optimal pricing or improved net account return is claimed.
