# Stable account history investigation

18 October 2026 UTC. The owner opened Missed flips and reported that its populated table kept
disappearing. This is a failed UI acceptance check, not missing player evidence.

The window read the fresh ledger used by current guidance. Offer capture clears that ledger until
delivery and refresh complete; failed reads and expiry also withdraw it. The investigation window
therefore alternated between records and an empty table. Its model also rebuilt every heartbeat,
discarding the selected row even when no evidence changed. Regression tests reproduced both faults.

`CompanionClient.historyLedger()` retains the last valid account-scoped ledger for read-only Missed
flips and Recorded sales. It survives pending delivery, failed reads and age expiry. Its observation
time remains visible in the status tooltip; the view says it is showing last confirmed history when
the fresh ledger is unavailable. This cache is not a source for holdings, Performance, risk, exits
or entry authority. Fresh ledger expiry and the pending-outbox trading gate remain in force.

Logout, plugin shutdown, account switch and an unsupported account clear retained records. A
separate request generation rejects responses from an earlier account/login lifecycle, including
switching away and back. Delayed failed responses cannot invalidate a newer ledger. Future records
are rejected and older snapshots cannot replace newer displayed history. Nothing is written to a
database, repaired, enrolled as inventory or assigned a missing cost/profit.

Missed flips only publishes table changes when displayed evidence changes. Filters, sorting and
selection survive unchanged heartbeats; new rows retain the selected exact source event within the
same account when it is still visible. Logout also explicitly clears the open view. Unknown money
stays unavailable. Recorded sales uses the same retained history while Performance stays fresh-only.

The sidebar now has a visible **Records** button. Open **Records -> Missed flips** for incomplete
evidence or **Records -> Recorded sales** for sales; both search boxes have visible labels. Details
window titles identify the selected view. Status/filter labels use the existing light text color.

Behavioral coverage includes an actual isolated pending outbox, failed/expired reads, account
switching and late login responses, future/older packets, a filtered open Swing window receiving
new rows, unknown profit, logout, and the separation of retained records from fresh Performance.
Full build/CI/installation and the owner's post-update stability check are separate delivery gates;
see HANDOFF. This fixes investigation stability, not unresolved capital or trading eligibility.
