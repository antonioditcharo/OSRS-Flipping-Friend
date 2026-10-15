package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Wire-level observations in disposable stores; no live account or game interaction. */
public class OfferObservationRecoveryTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final String account = AccountIdentity.fromProfileKey("recovery-fixture");
    private final Gson gson = new Gson();

    private OfferEvent event(String state, int filled, long sequence) {
        OfferEvent.Builder b = OfferEvent.builder("fixture", 1700000000L + sequence, state)
            .accountId(account).eventIdentity("event-" + sequence, "session", "buy")
            .slot(0).sequence(sequence);
        if ("EMPTY".equals(state)) b.offerIdentity(null);
        else b.item(561,"Nature rune").buying(true).price(100)
            .quantities(10,filled).spent(filled * 100L);
        return b.build();
    }
    private OfferEvent live(OfferEvent event) {
        JsonObject wire = gson.toJsonTree(event).getAsJsonObject();
        wire.addProperty("authenticatedObservation", true);
        return gson.fromJson(wire, OfferEvent.class);
    }
    private OfferEvent currentPolicyTime(OfferEvent event) {
        JsonObject wire=gson.toJsonTree(event).getAsJsonObject();
        wire.addProperty("observedAt",SaleAccounting.CURRENT_POLICY_SAFE_FROM+event.getSequence());
        return gson.fromJson(wire,OfferEvent.class);
    }
    private OfferLifecycleProjection error() {
        var open = OfferLifecycleReducer.apply(null,event("BUYING",0,1)).getProjection();
        return OfferLifecycleReducer.apply(open,event("EMPTY",0,2)).getProjection();
    }
    private SqliteStore.ProjectedEventAcceptance accept(SqliteStore store, OfferEvent e) throws Exception {
        return store.recordAndProjectOffer(e,gson.toJson(e),true);
    }

    @Test public void freshAuthenticatedEmptyRestoresCurrentSlotWithoutInventingAnExecution() {
        var next = OfferLifecycleReducer.apply(error(),live(event("EMPTY",0,3)));
        assertTrue(next.getReason(), next.isAccepted());
        assertEquals(OfferLifecycleState.EMPTY,next.getNewState());
        assertEquals(PositionAccountingEffectType.NONE,next.getAccountingEffect().getType());
    }
    @Test public void recoveredPartialIsOnlyABaselineAndLaterDeltasAreAccountedOnce() {
        var recovered = OfferLifecycleReducer.apply(error(),live(event("BUYING",4,3)));
        assertTrue(recovered.getReason(),recovered.isAccepted());
        assertEquals(OfferLifecycleState.BUY_PARTIAL,recovered.getNewState());
        assertEquals(PositionAccountingEffectType.RECONCILE,recovered.getAccountingEffect().getType());
        var next = OfferLifecycleReducer.apply(recovered.getProjection(),live(event("BUYING",6,4)));
        assertTrue(next.isAccepted()); assertEquals(2,next.getAccountingEffect().getQuantity());
        assertEquals(200,next.getAccountingEffect().getAcquisitionCost());
    }
    @Test public void cachedLegacyForeignSessionAndStaleObservationsCannotRecover() {
        var error = error();
        assertFalse(OfferLifecycleReducer.apply(error,event("EMPTY",0,3)).isAccepted());
        assertFalse(OfferLifecycleReducer.apply(error,live(event("EMPTY",0,1))).isAccepted());
        JsonObject foreign = gson.toJsonTree(live(event("EMPTY",0,3))).getAsJsonObject();
        foreign.addProperty("sessionId","another-session");
        assertFalse(OfferLifecycleReducer.apply(error,gson.fromJson(foreign,OfferEvent.class)).isAccepted());
        foreign.addProperty("sessionId","session"); foreign.addProperty("observedAt",1699999999L);
        assertFalse(OfferLifecycleReducer.apply(error,gson.fromJson(foreign,OfferEvent.class)).isAccepted());
        foreign.addProperty("observedAt",1700000003L); foreign.addProperty("slot",1);
        assertFalse(OfferLifecycleReducer.apply(error,gson.fromJson(foreign,OfferEvent.class)).isAccepted());
    }
    @Test public void malformedProofCannotClearTheSlot() {
        for (String field : new String[]{"itemId","spent","totalQuantity","filledQuantity"}) {
            JsonObject wire = gson.toJsonTree(live(event("EMPTY",0,3))).getAsJsonObject();
            wire.addProperty(field,1);
            assertFalse(field,OfferLifecycleReducer.apply(error(),gson.fromJson(wire,OfferEvent.class)).isAccepted());
        }
    }
    @Test public void rejectedObservationAdvancesOnlyTheOrderingWatermark() {
        var error = error();
        assertEquals(2,error.getLastSequence()); assertEquals(1700000002L,error.getLastObservedAt());
        assertEquals(561,error.getItemId()); assertEquals("buy",error.getOfferIdentity());
        assertEquals(0,error.getFilledQuantity());
        assertFalse(OfferLifecycleReducer.apply(error,live(event("BUYING",0,2))).isAccepted());
        var stale = OfferLifecycleReducer.apply(error,live(event("BUYING",0,1)));
        assertEquals(2,stale.getProjection().getLastSequence());
        assertTrue(OfferLifecycleReducer.apply(stale.getProjection(),live(event("BUYING",0,3))).isAccepted());
    }
    @Test public void recoveryCannotRewindCountersOfTheSameOffer() {
        var partial = OfferLifecycleReducer.apply(null,event("BUYING",4,1)).getProjection();
        var error = OfferLifecycleReducer.apply(partial,event("EMPTY",0,2)).getProjection();
        assertFalse(OfferLifecycleReducer.apply(error,live(event("BUYING",2,3))).isAccepted());
        assertTrue(OfferLifecycleReducer.apply(error,live(event("BUYING",4,3))).isAccepted());
    }
    @Test public void authenticatedStaleEventsCannotRollBackAnEmptySlotOrAnotherOffer() {
        var empty = OfferLifecycleReducer.apply(error(),live(event("EMPTY",0,4))).getProjection();
        var old = OfferLifecycleReducer.apply(empty,live(event("BUYING",0,3)));
        assertFalse(old.isAccepted()); assertEquals(4,old.getProjection().getLastSequence());
        var current = OfferLifecycleReducer.apply(empty,live(event("BUYING",0,5))).getProjection();
        var lateEmpty = OfferLifecycleReducer.apply(current,live(event("EMPTY",0,4)));
        assertFalse(lateEmpty.isAccepted()); assertEquals(561,lateEmpty.getProjection().getItemId());
        assertEquals(5,lateEmpty.getProjection().getLastSequence());
    }
    @Test public void restartAndDeliveryReplayPreserveHistoricalGapAndNewFillAccounting() throws Exception {
        Path db = folder.newFolder().toPath().resolve("fixture.db");
        try (SqliteStore store = new SqliteStore(db)) {
            accept(store,event("BUYING",0,1)); accept(store,event("BUYING",2,2));
            accept(store,event("EMPTY",0,3));
            assertTrue(accept(store,live(event("EMPTY",0,4))).transition.isAccepted());
            accept(store,live(event("BUYING",0,5))); accept(store,live(event("BUYING",2,6)));
            assertEquals(4,store.buyLimitProjections().get(0).getUsedQuantity());
        }
        try (SqliteStore store = new SqliteStore(db)) {
            var duplicate = accept(store,live(event("BUYING",2,6)));
            assertEquals(SqliteStore.EventAcceptance.DUPLICATE,duplicate.acceptance);
            accept(store,live(event("BUYING",2,7)));
            assertEquals(4,store.buyLimitProjections().get(0).getUsedQuantity());
            assertEquals(3,store.positionEventCount()); // Two real acquisition deltas and the original gap.
            var ledger = store.ledgerSnapshot(account);
            assertTrue(ledger.isValid()); assertTrue(ledger.hasUnassignedReconciliation());
            assertTrue(ledger.getTradingPositions().isEmpty()); assertEquals(1,ledger.getMissedFlips().size());
            assertEquals(0,ledger.getSales());
            assertNull(DurablePlanningExposure.from(store.planningProjections(),Collections.emptyMap(),true));
        }
    }
    @Test public void recoveredOffersDoNotBecomeCompleteLearningSamplesOnLaterReplay() throws Exception {
        try (SqliteStore store = new SqliteStore(folder.newFolder().toPath().resolve("learning.db"));
             CompanionService service = new CompanionService(gson,store)) {
            service.offer(event("BUYING",0,1)); service.offer(event("EMPTY",0,2));
            service.offer(live(event("BOUGHT",10,3)));
            assertTrue("Recovered cumulative fills are incomplete execution evidence",store.executionStats().isEmpty());
            service.offer(live(event("BOUGHT",10,4)));
            assertTrue("A later terminal replay must not promote the incomplete offer",store.executionStats().isEmpty());
        }
    }
    @Test public void recoveredReplacementSellDoesNotInheritPlacementTaxEvidence() throws Exception {
        Path db=folder.newFolder().toPath().resolve("tax.db");
        try (SqliteStore store=new SqliteStore(db)) {
            accept(store,currentPolicyTime(event("EMPTY",0,1)));
            OfferEvent sell=OfferEvent.builder("fixture",SaleAccounting.CURRENT_POLICY_SAFE_FROM+2,"SELLING").accountId(account)
                .eventIdentity("old-sell","session","old-sell").slot(0).item(561,"Nature rune")
                .price(100).quantities(10,0).sequence(2).build();
            accept(store,sell);
            try (var connection=DriverManager.getConnection("jdbc:sqlite:"+db);
                 var statement=connection.createStatement();
                 var rows=statement.executeQuery("SELECT sale_tax_policy FROM offer_projection WHERE slot=0")) {
                assertTrue(rows.next()); assertEquals(SaleAccounting.CURRENT_POLICY,rows.getString(1));
            }
            accept(store,currentPolicyTime(event("EMPTY",0,3)));
            JsonObject wire=gson.toJsonTree(sell).getAsJsonObject();
            wire.addProperty("eventId","new-sell"); wire.addProperty("offerIdentity","new-sell");
            wire.addProperty("sequence",4); wire.addProperty("observedAt",SaleAccounting.CURRENT_POLICY_SAFE_FROM+4);
            assertTrue(accept(store,live(gson.fromJson(wire,OfferEvent.class))).transition.isAccepted());
            try (var connection=DriverManager.getConnection("jdbc:sqlite:"+db);
                 var statement=connection.createStatement();
                 var rows=statement.executeQuery("SELECT sale_tax_policy FROM offer_projection WHERE slot=0")) {
                assertTrue(rows.next()); assertEquals(SaleAccounting.UNPROVEN_PLACEMENT,rows.getString(1));
            }
        }
    }
    @Test public void recoveryAndItsEvidenceRollBackTogetherOnFailure() throws Exception {
        Path db=folder.newFolder().toPath().resolve("rollback.db");
        try (SqliteStore store=new SqliteStore(db)) {
            accept(store,event("BUYING",0,1)); accept(store,event("EMPTY",0,2));
        }
        try (SqliteStore store=new SqliteStore(db,point->{
            if ("POSITION_ACCOUNTING_APPLIED".equals(point)) throw new IllegalStateException("fixture interruption");
        })) {
            try { accept(store,live(event("BUYING",4,3))); fail("Expected interruption"); }
            catch (IllegalStateException expected) { assertEquals("fixture interruption",expected.getMessage()); }
        }
        try (SqliteStore store=new SqliteStore(db)) {
            assertEquals(OfferLifecycleState.ERROR_RECONCILIATION,store.offerProjections().get(0).getState());
            assertEquals(1,store.positionEventCount()); assertTrue(store.buyLimitProjections().isEmpty());
            assertEquals(SqliteStore.EventAcceptance.NEW,accept(store,live(event("BUYING",4,3))).acceptance);
            assertEquals(OfferLifecycleState.BUY_PARTIAL,store.offerProjections().get(0).getState());
            assertEquals(2,store.positionEventCount());
        }
    }
}
