# Durable cancelled-sale guidance

19 October 2026. Read HANDOFF for tested, merged, installed and player-accepted states.

Two regression-first cases exposed lost relist guidance. The old intent treated every other sale
of the item as its replacement, including a listing already working when maintenance was advised.
It also relied on the tracker's temporary settled-slot record on every read. Reusing that slot or
the twelve-hour settled retention window could erase a previously observed collection.

New maintenance intents retain the exact identities of same-item listings already present. Those
offers do not end the pending workflow; a subsequently observed different sale episode does,
including a same-slot/same-second episode with a different persistent identity. Repeated advice
for the original offer cannot enroll later listings in that initial set. The live advice writer
checks that the original episode is still SELLING in its slot before creating an intent.

The offer callback checkpoints a valid cancelled-and-collected remainder synchronously, before
settled retention pruning or deferred planning. The existing account intent file retains that
bounded quantity and its managed-cost requirement alongside the original offer identity/counters.
Restart and unrelated slot reuse preserve it. Cancellation without collection, regressed/out-of-
range terminal counters and another episode cannot supply collection evidence. A later matching
counter regression withdraws a retained receipt rather than falling back to its cached quantity.
A sold original
or a new replacement ends the intent. Legacy files are not backfilled with assumed collection
or an invented set of prior listings; their existing exact-offer checks remain conservative.

This is local workflow continuity, not recovered financial evidence, acquisition ownership or
permission to sell personal stock. Every relist still requires current visible inventory, the
fresh scoped canonical ledger, unaffected item scope, exact managed quantity/cost and original
acquisition-bound target/stop/duration. Managed context cannot become unknown-cost personal advice
when the ledger is unavailable. The quantity and managed-cost requirement travel together in an
immutable request, so concurrent intent withdrawal cannot downgrade an in-flight managed relist.
Quiet/current quote rules, exact taxed non-loss floors and final
companion sale authority remain unchanged. Old financial rows, gaps, reserves, risk anchors,
event identities and learning samples are neither replayed nor edited.

Verification exercises both old failures, real offer callbacks before any engine refresh, restart
after slot reuse, same-second replacement/stale-worker rejection, missing collection/bad counters,
and real managed relist guidance with exact costs and subsequent ledger withdrawal. The existing
personal, target, scope, progress and sale-authority regressions remain required. Player-operated
partial cancel -> collect -> relist -> restart acceptance is pending. Replay v6 still lacks this
persisted client intent; this runtime fix does not close production-replay or return/learning gates.
