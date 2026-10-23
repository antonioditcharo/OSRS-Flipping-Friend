# Monotonic trading maintenance scheduling

23 October 2026. Actual verification, merge and installation are recorded in
`local-data/monotonic-maintenance-20261023/delivery-state.json`. PR #149 is installed; #150/#151
and this pass await one owner-deferred paired update.

The trading heartbeat and offer-delivery retry pump previously scheduled waits against the PC's
wall clock. A backward UTC correction could leave account refresh, guidance, persistence or queued
event retries waiting until the old timestamp was reached again.

These process-local intervals now use monotonic milliseconds. Heartbeat timers have an explicit
uninitialized sentinel, so the first pass remains immediately due even with a negative timer origin.
Replay resume sets its retry deadline to the current local timer value, preserving immediate resume
with negative origins. Exponential transport backoff, coalescing, ordered ACKs and generation fences
retain their existing behavior. An old queued lifecycle cannot release a newer queued request.

Recorded event IDs/sequences/UTC timestamps, acquisition clocks, immutable expected durations,
maintenance holding/no-progress ages, financial accounting and current-source freshness guards
remain unchanged. This scheduling fix does not grant trading authority to future-dated evidence.
No new worker/timer service, financial replay, data repair or learning promotion is introduced.

Behavioral regressions exercise the real heartbeat, independent account/guidance/persistence
intervals, negative-origin immediate replay, transport backoff, exact retained sources and stale
queued lifecycle work. Full controlled trading/restart and sustained latency remain open.
