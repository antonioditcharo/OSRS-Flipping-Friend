# Item-scoped sale verification

30 October 2026. Installed #202 remains the operational baseline while this change is qualified
with the queued #203 public-cache streaming work. Pricing policy and financial identities stay
unchanged; no learning or new loss authority is activated.

## Measured work

The retained #202 native session contains selected-sale authority work up to 982.253ms and two
distinct eligible editor quotes at p95 1,469.888ms. The frozen restored account has 194,329 offer
observations and 90 historical policy receipts. Sixteen warm full sale reads take 124–300ms in
the diagnostic process. Its CPU profile includes repeated receipt, account-read-set and source
validation. This identifies avoidable work, not the complete cause of every live delay.

## Current decision reads

Initial exits and working-sale maintenance do not consume historical cancellation receipts in
the shared sale policy. Their new read path therefore omits those receipt lookups. Relists read
the item-specific receipt inputs prepared from the existing validated immutable source prefix.
They still retain the complete canonical history: conflicting claims, parallel sales, late fills,
successor ordering, duplicate advice and cancellation/collection evidence cannot disappear when
the receipt lookup is narrowed.

Preparation stores receipt input identities only. Every relevant receipt is revalidated in the
current coherent SQLite snapshot, including its exact source, original account payload/hash,
all eight pinned offer sources, issuance order and first binding. External edits, account changes,
rollback and incompatible prefixes invalidate preparation through the existing source path.
Malformed or contradictory proofs cannot borrow another item's authority or become legacy proof.
The full historical reader remains available for replay, diagnostics and equivalence checks.

Cash, current offers, stock, exact acquisition allocation, physical inventory, original plans,
scope and market freshness remain fresh checks. A corrected price still requires the second
complete final authorization. Working-sale receipt issuance remains a separate durable POST.
No displayed authority, financial result, receipt or final policy decision is cached by this change.

## Verification and release

Cover full versus narrowed decisions across prices/quantities/cost floors, accepted cancellation
and relisting, malformed/wrong-item claims, external receipt edits and concurrent WAL progress.
Prove item scoping retains all eight source checks and does no receipt lookups for an item with
no claims. Preserve full-history cold reads and all source/financial rows on frozen-account probes.

Batch this with public-cache streaming. Require clean Java/tooling checks, actual-JAR Java 11,
bytecode 55, exact-head Windows/Linux CI, a fresh uninterrupted hour including these regressions,
backups/restoration and previous-runtime financial reads before paired installation. Keep native
MAIN and SELECTED_SALE p95 targets and player-operated acceptance open until measured afterward.
The older failed runs, slow samples and unobserved live post-cancellation late fill remain visible.
