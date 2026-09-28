# Phase 2 Closure Audit

## Status

Phase 2, Canonical event and state model, is closed at `CoPilot's-Rebuild-from-Main` commit `207a312cca5b91f86fcd048c63145a9f2d652c9a`.

This audit records implemented boundaries and deterministic verification. Recommendation-authority consolidation, maintenance policies, paper trading, challenger models, and productization remain later-phase work.

## Verification baseline

- Focused recovery verification: 20 selected tests, 11 actionable tasks executed.
- Supported clean build: 18 actionable tasks executed.
- Post-merge focused verification: 20 selected tests, 11 actionable tasks executed.
- Post-merge complete root suite: 13 actionable tasks executed.

## Deliverable evidence

### Durable plugin outbox

`OfferEventOutbox` persists account-scoped canonical events before delivery, preserving event, session, and sequence identity across restart. It writes atomically, refuses account-less or corrupt state, orders pending events by sequence, and removes only an exact acknowledgement.

Primary tests: `OfferEventOutboxTest`, `CompanionClientTest`, and `Phase2LostAcknowledgementRecoveryTest`.

### Companion deduplication and acknowledgements

Canonical event identity is unique in `event_log`. New and duplicate acceptance are distinguished. Typed acknowledgements carry event, session, and sequence identity; a valid duplicate acknowledgement safely completes pruning after a lost response.

Primary tests: `IdempotentOfferAcceptanceTest`, `OfferEventAcknowledgementTest`, `OfferEventAcknowledgementResponseTest`, and `Phase2LostAcknowledgementRecoveryTest`.

### Formal offer lifecycle

Shared core owns lifecycle states, actions, classification, immutable projection, pure reduction, transition metadata, monotonic high-water checks, replay handling, terminal clearing, and fail-closed `ERROR_RECONCILIATION` behavior.

Primary tests: `OfferLifecycleClassifierTest`, `OfferLifecycleReducerTest`, and `PositionAccountingEffectTest`.

### Durable offer projection

`offer_projection` stores current slot state. Event acceptance and projection advancement share one SQLite transaction. Duplicate delivery cannot advance the projection twice, and state survives restart.

Primary tests: `OfferProjectionPersistenceTest`, `Phase2TransactionRollbackTest`, and `Phase2SessionReconstructionTest`.

### Durable positions and cost basis

`position_event` is append-only accounting evidence and `position_projection` is the current holding view. Acquisitions preserve incremental quantity and actual cost. Repeated acquisitions blend cost, disposals remove proportional cost basis, full disposal removes the current row, and unsupported accounting fails closed.

Primary tests: `PositionAccountingEffectTest`, `PositionProjectionTest`, `PositionProjectionPersistenceTest`, and `Phase2SessionReconstructionTest`.

### Snapshot reconciliation

Account-event persistence, position reconciliation, audit records, and buy-limit floors share one transaction. Positive evidence may adopt or repair a holding. Unknown cost fails closed. Lower quantity or absence cannot erase durable quantity or cost.

Primary tests: `SnapshotReconciliationTest`, `SnapshotReconciliationPersistenceTest`, `BuyLimitSnapshotReconciliationTest`, and `Phase2TransactionRollbackTest`.

### Durable buy limits

`buy_limit_event` records changes and `buy_limit_projection` stores the active anchored four-hour window. Canonical fills add accepted quantity. Snapshots are upward-only floors. Windows survive restart, duplicate delivery does not count twice, and an older database can use bounded replay when no durable projection exists.

Primary tests: `BuyLimitProjectionPersistenceTest`, `BuyLimitSnapshotReconciliationTest`, `RehydrationTest`, and `Phase2TransactionRollbackTest`.

### Replay and high-water protections

Pending events replay in sequence and stop at the first failure. Retry uses the existing worker with bounded backoff. Lifecycle-generation fences prevent stale work from pruning or mutating a newer lifecycle. Quantity, spend, and accounting effects remain monotonic and idempotent.

Primary tests: `OfferRetryLifecycleBoundaryTest`, `CompanionClientTest`, `OfferLifecycleReducerTest`, and `Phase2LostAcknowledgementRecoveryTest`.

## Exit criteria

### No duplicated or partial accounting after interruption

`Phase2TransactionRollbackTest` injects failures after four canonical and three snapshot mutation boundaries. Every failure rolls back the logical transaction, remains clean after database reopen, and permits one successful retry. Lost acknowledgement preserves exact pending identity and duplicate redelivery remains safe.

### Full-session reconstruction

`Phase2SessionReconstructionTest` processes partial buy fills, restarts, completion, duplicate delivery, collection, partial sale, another restart, final sale, duplicate delivery, and clearing. The final offer is empty, the fully disposed position is absent, acquired and disposed quantities match, acquisition cost equals applied disposal cost basis, and buy-limit usage remains durable.

### Correct partial-fill reconciliation

The reducer preserves fill and spend high-water marks. Accounting effects carry exact incremental acquisition or disposal quantities. Snapshot reconciliation changes only positively supported facts and never treats absence as disposal.

### Buy limits survive reconnect and restart boundaries

Durable projections restore active windows. Duplicate identities cannot add another effect. Snapshot values only raise the floor. The outbox retains unacknowledged events over restart and prunes after a valid new or duplicate acknowledgement.

## Preserved compatibility

Phase 2 closure does not change wire shapes for `OfferEvent`, `AccountSnapshot`, or `PositionSnapshot`; the manual-only interaction boundary; HTTP endpoints; acknowledgement identity; supported artifacts; recommendation ranking; UI behavior; or scheduler ownership.

## Deferred to Phase 3 and later

Deferred work includes removing plugin-side ranking authority, deleting or demoting `SuggestionEngine`, introducing `TradePolicy`, `EntryPolicy`, `BuyMaintenancePolicy`, `SellMaintenancePolicy`, and `ExitPolicy`, persisting recommendation lineage and input timestamps, explicit abstention reasons, complete cancel-collect-replace workflows, paper trading, challenger ML, and productization.

The next phase begins with recommendation-authority inventory and policy-contract foundations. The companion `CandidateFactory` and `PortfolioPlanner` are active while the plugin still references `SuggestionEngine`; Phase 3 must consolidate those authorities without weakening Phase 2 delivery or accounting guarantees.
