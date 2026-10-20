# Cancellation identity and guidance lifetime delivery

20 October 2026, America/Anchorage. Contract:
[guided cancellation and receipt lifetime](../architecture/guidance-lifetime-and-cancellation.md).

Three regressions reproduced on the installed #122 source before implementation: a committed
reprice followed after the card changes gets a rejection cooldown; a same-item card exempts an
uncommitted cancellation; and persistent refresh errors leave a mid-entry sale/personal quote
actionable. The fixed source associates cancellation with committed account/episode evidence and
withdraws persistent failures immediately. Fifteen new behavioral tests also cover dirty writes,
late fills, restored promises before refresh, reused slots, account isolation, monotonic expiry,
pending/editor receipt age, delayed client callbacks and queued player-key renewal/recovery.

Windows clean verification passed in 96 seconds: 1,835 Java tests passed, six existing skips and
Java 11 bytecode. All 108 tooling tests passed in 125.328 seconds. Exact-head Windows/Ubuntu CI, paired installation,
backup/task/financial preservation and scoped runtime results are consolidated in the ignored
local-data/episode-guidance-20261020/delivery-state.json. Read that receipt for actual delivered
state; source in this document does not itself establish installation or player acceptance. Build
logs and personal source evidence remain private alongside it. No financial repair, historical
replay, risk reset or learning promotion is performed. Existing cooldowns remain unchanged.

The 60-second limit bounds displayed advice and pending inputs; it does not expire saved trade
plans. This batch changes no profit accounting or risk policy. Full player-operated buy/collect/
sell, partial cancel/collect/relist, controlled restart, sustained latency and complete parity/
benchmark/learning gates remain unchecked in TASKS.md. All eight occupied slots remain a valid
entry refusal; do not manufacture funds or an empty slot for acceptance.
