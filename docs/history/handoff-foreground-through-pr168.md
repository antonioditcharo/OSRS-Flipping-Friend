# Foreground isolation installed checkpoint through PR #168

25 October 2026. Superseded as current runtime by #170; retained evidence is historical.

PR #168 (`codex/foreground-market-isolation`, tested `a8825be3f0e6a341a744f661083205c319bb4195`,
merged `e650b05e2df9350d31a5babb17d2833e8bd7c420`) was installed in private shadow mode.
Public-feed GETs have bounded workers outside financial FIFO queues; disposable restoration checks
logical size plus runtime headroom before copying. Clean Java/tooling and exact-head Windows/Linux
CI 40224481998 passed. Installation preserved all 12 checked source/financial/projection tables;
95 restored files and five configuration files verified. #167/current reads matched ledger totals,
managed stock, source scope and the present original risk window. Fresh BUY/COLLECT deliveries,
an acknowledged eight-slot frame and zero pending financial events were recorded; original immutable
prefixes matched. These are historical captures, not a current-state authorization.

Receipt: `local-data/hosted-pilot/foreground-market-delivery.json`.
[Contract](../architecture/foreground-market-isolation.md). The owner reported the general workflow
worked but still had brief buy pauses, collected sale reservations and entry flicker. #170 addresses
specific callback/relist causes; sustained latency and controlled player/restart acceptance remain open.
Earlier pilot receipts: [#163–167](handoff-pilot-through-2026-10-25-pr167.md).
