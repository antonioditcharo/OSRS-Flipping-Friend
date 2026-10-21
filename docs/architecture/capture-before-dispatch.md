# Commit client observations before background delivery

Authenticated offer callbacks previously copied the observation into a worker task. The durable
outbox append happened only when that task ran. Logout changed the generation and discarded the
task; shutdown could reject/remove it. The original fill/terminal/EMPTY sequence and prospective
advice could disappear before receiving any durable source identity. This is distinct from an
outbox write or HTTP failure after acceptance.

The client now commits each known-account copied callback to the existing forced atomic outbox
before dispatch. Capture performs local persistence only, never HTTP. It freezes original advice,
exit context, observation clock, episode, counters, event ID, session and sequence before lifecycle
or account changes can withdraw advice. Network requests remain on the existing bounded worker.
Stopped, missing or rejecting workers leave the committed source for restart/retry. Logout still
pauses HTTP delivery and withdraws old guidance; it cannot delete an already committed observation.
Unknown-profile callbacks cannot establish an owner or cross login/account boundaries.

A new capture wakes a drained retry timer, preserving transport-failure backoff. Concurrent replay
ACKs remove only their matched prefix; a later capture stays pending and scheduled. Account changes
cannot redirect the committed source to another account. Cash/eight-slot publication and financial
authority still require the full acknowledged source prefix. Source, financial and learning keys,
original risk anchors and historical gaps are unchanged; there is no financial replay or repair.

The existing unsaved-tail path retains failed local appends under their original IDs/order/bindings
for worker retry and withholds authority. Storage that never successfully commits before process
death remains a limitation. Ordinary UI reads and retries do not force writes; the new forced write
is the accepted observation boundary. Local disk latency and controlled game workflows still need
player-operated acceptance; a disposable callback test is not a sustained latency measurement.

Nine added behavioral tests cover stalled/rejected/stopped/missing workers, logout before dispatch,
original prediction/exit binding, no capture HTTP, account changes, storage failure, retry spacing,
and a concurrent capture during replay. Four failures were reproduced against the preceding source.
Existing callback/source, batch/ACK, financial, input and lifecycle regressions remain required.
See [delivery](../history/capture-dispatch-delivery-2026-10-20.md) and the newest HANDOFF state.
