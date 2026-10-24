# Collection observations through workflow read failures

23 October 2026. HANDOFF records source, installation and manual acceptance separately.

A genuinely unreadable continuation document at cancellation/collection prevented the callback
checkpoint from saving the exact remainder. The first EMPTY retained a copied current-login
predecessor, but a repeated EMPTY erased it; unrelated slot reuse also replaced the slot-keyed
receipt. Restoring the original document could therefore strand the accepted replacement/relist.

The tracker now retains copied collection observations by original episode for the current login.
Only an EMPTY with matching processed nonempty predecessor identity, counters, proceeds and state
can create one. Repeated EMPTY and slot reuse cannot erase that already observed receipt. The
continuation reducer selects only its exact original identity from these retained receipts; it
does not reintroduce old episodes as new successor/sale candidates. Current callbacks still take precedence for counters;
new same-item orders retain their conservative supersession and source-classification barriers.

These are temporary observations, not a second durable outbox or financial ledger. Login/account
reset clears them. A cached cancellation followed by EMPTY cannot recreate them. Once the original
unchanged document becomes readable, normal forced-atomic continuation persistence saves the exact
late-fill remainder before guidance can resume. Changed documents remain conflicts. A process exit
before any successful commit can still lose unsaved collection evidence; no history is replayed to
repair that absence. Original plans, costs, tax, event identities, risk and final authority persist.

Behavioral tests use actual tracker callbacks, an unreadable directory at the continuation path,
late fills, duplicate EMPTY, optional unrelated slot reuse, real outbox capture/ACK, document recovery
and store/tracker restart for both buy and sell. Separate refusals cover login reset and same-item
reuse. Existing restoration tests retain cached-predecessor, regression and account isolation guards.
Controlled player cancellation/partial collection/replacement/relist remains open.
