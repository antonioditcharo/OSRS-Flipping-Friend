# Reversible trade review archive

20 October 2026 owner direction: prune historical unresolved trades that lack sufficient retained
evidence, preserving what can still be investigated. This supersedes the earlier read-only-only
review restriction for annotations, not the prohibition on financial repairs or ownership inference.
Read HANDOFF and the linked actual delivery receipt before claiming installed behavior.

Review classifies unresolved DISPOSE rows by exact canonical source and original financial cursor:

- Purchase records retained: an earlier recorded acquisition exists for that item. This is a reason
  to investigate, not proof of a complete remaining cost match or recovered profit. Do not archive.
- Live or closure unproven: no accepted same-account/session/slot/item/offer terminal-to-EMPTY
  evidence, missing/invalid source history, or current offer/remaining stock/legacy positive-cost
  exposure exists. Do not archive. RECONCILE rows never qualify for this sale archive.
- Archive eligible: the original sale source is present, its sell episode has recorded terminal and
  clear evidence, no current item exposure remains, and no earlier acquisition is recorded.
- Archived: the exact eligible source has a committed archive annotation. Restorable at any time.

No condition establishes an unobserved disposal, collection, tax rule, profit or physical holding.
All original financial rows, canonical identities, unknown coverage, gaps, quantities/costs, risk
anchors and learning data remain. Archived sale updates still count as unresolved in measurements;
their wholly unknown groups leave the normal sidebar list. All recorded sales remain inspectable.
Missed flips offers Needs attention / Archived / All, archive-eligible and restore-selected controls.
New failures have new source identities and remain visible. If the item gains live exposure, its
old review reappears even while its original annotation is retained. Legacy clients keep all rows.

Scoped GET trade-review/current returns display classification. Authenticated POST trade-review
requires matching account, unique request ID, exact source IDs, an observed financial cursor and
archive/restore action. Archive revalidates eligibility inside one SQLite transaction; unknown,
future, newer-than-review, foreign or changed-identity requests fail atomically. Repeating the exact
request is idempotent. Append-only TRADE_REVIEW_V1 events store annotations; no source, position,
financial row, performance session or risk window is edited. Offline review needs no fresh spending
authority and supplies none. UI requests/responses remain account/generation fenced and clear on
logout/switch; failed writes leave original evidence visible. Archive failure/malformed metadata
cannot become a buy pause or make Performance claim resolved money.

Review validates the canonical tail; unchanged economic state/live-item sets reuse classification.
Archive changes, rollback and external data changes invalidate it. This avoids repeating full
history classification for the adjacent Performance/review HTTP reads. Current offers and live
exposure are checked independently on every review read.

## Preventing another accounting split

Automatic mode is enforced inside source acceptance, independently of a caller's in-memory flag.
The first future automatic capture/source acceptance records AUTOMATIC_ACCOUNTING_V1 in the same
transaction. The account-bound nonfinancial marker survives snapshot retention. Failed first
captures roll it back; restart restores only mode, never stale cash or slot authority. A legacy
snapshot cannot downgrade an established automatic account or seed a competing legacy holding.
For installations predating the marker, an earlier same-account positive activation survives a
later legacy false flag and snapshot retention. Unassigned/malformed legacy payloads supply no
mode proof; explicit foreign-account evidence is rejected. Startup offer recovery uses an explicit
offer-type allowlist, so session resets, review annotations and future metadata never become offers.
Original historical acquisitions/sales are not replayed or backfilled. Unscoped legacy ownership
is never inferred; scoped API validation and all current entry proofs remain required.

Verification covers original wrong-mode failures, mode rollback/retention, archive/restore/restart,
duplicate and changed requests, future/foreign/stale cursors, partial/unproven closure, retained
purchase evidence, new failures/live stock, failed annotation commits, API isolation and production
UI routing/coverage/profile/logout behavior. Full controlled trading, sustained timing, complete
production-policy replay and qualified cadence returns/learning remain open.
