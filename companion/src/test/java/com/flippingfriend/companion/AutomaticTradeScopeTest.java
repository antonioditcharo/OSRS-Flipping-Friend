package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AutomaticTradeScopeTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final String account = AccountIdentity.fromProfileKey("automatic");
    private OfferEvent offer(String id, String identity, String state, boolean buying, int total, int filled, long paid, long sequence) {
        return OfferEvent.builder("fixture", 1700000000L + sequence, state).accountId(account)
            .eventIdentity(id,"session",identity).slot(0).item(561,"Nature rune").buying(buying)
            .price(buying ? 100 : 120).quantities(total,filled).spent(paid).sequence(sequence).build();
    }
    private void accept(SqliteStore store, OfferEvent event) throws Exception {
        store.recordAndProjectOffer(event, new com.google.gson.Gson().toJson(event), true);
    }
    private void clear(SqliteStore store, String id, long sequence) throws Exception {
        accept(store, OfferEvent.builder("fixture",1700000000L+sequence,"EMPTY").accountId(account)
            .eventIdentity(id,"session",null).slot(0).sequence(sequence).build());
    }
    @Test public void observedPurchasesRemainExactAcrossPartialSalesDuplicatesAndRestart() throws Exception {
        Path db = folder.newFolder().toPath().resolve("scope.db");
        OfferEvent partial = offer("partial","sell","SELLING",false,100,40,4800,5);
        try (SqliteStore store = new SqliteStore(db)) {
            accept(store,offer("open","buy","BUYING",true,100,0,0,1));
            accept(store,offer("buy","buy","BOUGHT",true,100,100,10000,2)); clear(store,"clear",3);
            accept(store,offer("sell","sell","SELLING",false,100,0,0,4));
            accept(store,partial); accept(store,partial);
            var ledger = store.ledgerSnapshot(account);
            assertEquals(60,ledger.getTradingPositions().get(0).getQuantity());
            assertEquals(6000,ledger.getTradingPositions().get(0).getTotalCost());
            assertEquals(1,ledger.getSales());
            assertEquals(Long.valueOf(6000),DurablePlanningExposure.from(store.planningProjections(),Collections.emptyMap(),true).committedByItem.get(561));
        }
        try (SqliteStore store = new SqliteStore(db)) {
            accept(store,partial); accept(store,offer("sold","sell","SOLD",false,100,100,12000,6));
            var ledger = store.ledgerSnapshot(account);
            assertTrue(ledger.getTradingPositions().isEmpty());
            assertEquals(2,ledger.getBoundedSales()); assertTrue(ledger.isValid());
        }
    }
    @Test public void snapshotPersonalStockDoesNotEnrollOrRequireClassification() {
        var personal = PositionProjection.reconcile(561,"Nature rune",6026,0,false,1,2,null,null,"snapshot unknown cost");
        var read = new SqliteStore.PlanningProjections(Collections.singletonList(personal),Collections.emptyList());
        read.tradeScope = new AutomaticTradeScope();
        var exposure = DurablePlanningExposure.from(read, Collections.emptyMap(),true);
        assertNotNull(exposure); assertTrue(exposure.committedByItem.isEmpty());
        assertEquals(6026,personal.getQuantity()); assertEquals(PositionConsistencyState.RECONCILE,personal.getConsistencyState());
        read.tradeScope.apply("ACQUIRE",561,"Nature rune",100,10000,3,"buy","buy-offer");
        exposure = DurablePlanningExposure.from(read, Collections.emptyMap(),true);
        assertNotNull(exposure); assertEquals(Long.valueOf(10000),exposure.committedByItem.get(561));
        assertEquals(100,read.tradeScope.readyPositions().get(0).getQuantity());
        assertEquals(6026,personal.getQuantity());
    }
    @Test public void untrackedSaleDoesNotCreateOwnershipOrProfit() throws Exception {
        try (SqliteStore store = new SqliteStore(folder.newFolder().toPath().resolve("sale.db"))) {
            accept(store,offer("open","manual","SELLING",false,100,0,0,1));
            accept(store,offer("sold","manual","SOLD",false,100,100,12000,2));
            var ledger=store.ledgerSnapshot(account);
            assertTrue(ledger.getTradingPositions().isEmpty()); assertEquals(1,ledger.getUnresolvedSales());
            assertEquals(1,ledger.getMissedFlips().size()); assertNull(ledger.getMissedFlips().get(0).getProfitLower());
            assertNotNull(DurablePlanningExposure.from(store.planningProjections(),Collections.emptyMap(),true));
        }
    }
    @Test public void mixedSaleCannotBorrowAcquisitionCostForPersonalUnits() throws Exception {
        try (SqliteStore store = new SqliteStore(folder.newFolder().toPath().resolve("mixed.db"))) {
            accept(store,offer("open","buy","BUYING",true,100,0,0,1));
            accept(store,offer("buy","buy","BOUGHT",true,100,100,10000,2)); clear(store,"clear",3);
            accept(store,offer("sell","sell","SELLING",false,6100,0,0,4));
            accept(store,offer("sold","sell","SOLD",false,6100,6100,732000,5));
            var ledger=store.ledgerSnapshot(account);
            assertTrue(ledger.getTradingPositions().isEmpty()); assertEquals(1,ledger.getUnresolvedSales());
            assertEquals(0,ledger.getBoundedSales()); assertNull(ledger.getMissedFlips().get(0).getProfitLower());
        }
    }
    @Test public void unsafeTradeEvidenceAndLegacyKnownCostConflictsStillBlockEntries() {
        var scope=new AutomaticTradeScope(); scope.apply("RECONCILE",561,"Nature rune",0,0,1,"gap","offer");
        var read=new SqliteStore.PlanningProjections(Collections.emptyList(),Collections.emptyList()); read.tradeScope=scope;
        assertNull(DurablePlanningExposure.from(read,Collections.emptyMap(),true));
        assertTrue(scope.readyPositions().isEmpty());
        var conflict=PositionProjection.reconcile(561,"Nature rune",100,10000,true,1,2,"event","offer","cost conflict");
        read=new SqliteStore.PlanningProjections(Collections.singletonList(conflict),Collections.emptyList()); read.tradeScope=new AutomaticTradeScope();
        assertNull(DurablePlanningExposure.from(read,Collections.emptyMap(),true));
    }
    @Test public void malformedAcquisitionsAndOverflowCannotBecomeSellAdvice() {
        var scope=new AutomaticTradeScope(); scope.apply("ACQUIRE",561,"Nature rune",100,0,1,"missing","offer");
        assertTrue(scope.readyPositions().isEmpty()); assertTrue(scope.unsafeItems.contains(561));
        scope=new AutomaticTradeScope(); scope.apply("ACQUIRE",561,"Nature rune",Integer.MAX_VALUE,10,1,"one","offer");
        try { scope.apply("ACQUIRE",561,"Nature rune",1,10,2,"two","offer2"); fail(); }
        catch (ArithmeticException expected) { }
    }

    @Test public void oldProjectionCannotKeepReservingAnExecutionBackedPositionAfterSale() {
        var scope = new AutomaticTradeScope();
        scope.apply("ACQUIRE",561,"Nature rune",100,10000,1,"buy","buy-offer");
        var old = scope.readyPositions().get(0);
        scope.apply("DISPOSE",561,"Nature rune",40,0,2,"partial","sell-offer");
        var read = new SqliteStore.PlanningProjections(Collections.singletonList(old),Collections.emptyList());
        read.tradeScope=scope;
        assertEquals(Long.valueOf(6000),DurablePlanningExposure.from(read,Collections.emptyMap(),true).committedByItem.get(561));
        scope.apply("DISPOSE",561,"Nature rune",60,0,3,"sold","sell-offer");
        assertTrue(DurablePlanningExposure.from(read,Collections.emptyMap(),true).committedByItem.isEmpty());
    }

    @Test public void unidentifiedReconciliationBlocksAdviceWithoutDiscardingKnownAcquisitions() {
        var scope = new AutomaticTradeScope();
        scope.apply("ACQUIRE",561,"Nature rune",100,10000,1,"buy","buy-offer");
        scope.apply("RECONCILE",0,null,0,0,2,"unidentified-clear",null);
        assertTrue(scope.readyPositions().isEmpty());
        assertEquals(100,scope.positions.get(561).getQuantity());
        assertEquals(10000,scope.positions.get(561).getTotalCost());
        var read = new SqliteStore.PlanningProjections(Collections.emptyList(),Collections.emptyList());
        read.tradeScope=scope;
        assertNull(DurablePlanningExposure.from(read,Collections.emptyMap(),true));
    }

    @Test public void unidentifiedClearRemainsReadableAcrossRestartAndCannotManufactureLaterProfit() throws Exception {
        Path db = folder.newFolder().toPath().resolve("unidentified.db");
        long recordedProfitLower, recordedProfitUpper;
        int recordedEvents;
        try (SqliteStore store = new SqliteStore(db)) {
            accept(store,offer("open","buy","BUYING",true,100,0,0,1));
            accept(store,offer("buy","buy","BOUGHT",true,100,100,10000,2)); clear(store,"clear",3);
            accept(store,offer("sell","sell","SELLING",false,100,0,0,4));
            accept(store,offer("partial","sell","SELLING",false,100,40,4800,5));
            var before = store.ledgerSnapshot(account);
            recordedProfitLower=before.getProfitLower(); recordedProfitUpper=before.getProfitUpper();
            OfferEvent open = OfferEvent.builder("fixture",1700000006L,"BUYING").accountId(account)
                .eventIdentity("other-open","session","other-buy").slot(1).item(562,"Chaos rune")
                .buying(true).price(100).quantities(10,0).spent(0).sequence(6).build();
            // This fixture specifically needs an unidentified legacy predecessor. Other events
            // retain canonical JSON; a fully recorded predecessor would correctly scope this gap.
            store.recordAndProjectOffer(open,"unavailable legacy payload",true);
            OfferEvent unassigned = OfferEvent.builder("fixture",1700000007L,"EMPTY").accountId(account)
                .eventIdentity("unidentified-clear","session",null).slot(1).sequence(7).build();
            accept(store,unassigned); accept(store,unassigned);
            int beforeRead = store.positionEventCount();
            var after = store.ledgerSnapshot(account);
            assertTrue(after.isValid()); assertTrue(after.getTradingPositions().isEmpty());
            assertEquals(before.getSales(),after.getSales());
            assertEquals(recordedProfitLower,after.getProfitLower()); assertEquals(recordedProfitUpper,after.getProfitUpper());
            var evidence = after.getMissedFlips().stream().filter(e->e.getItemId()==0).findFirst().orElseThrow();
            assertEquals("unidentified-clear",evidence.getEventId());
            assertEquals("RECONCILE",evidence.getType()); assertEquals(0,evidence.getQuantity());
            assertEquals("Unidentified offer",evidence.getItemName());
            assertNull(evidence.getGrossProceeds()); assertNull(evidence.getProfitLower());
            assertEquals(beforeRead,store.positionEventCount()); // Reading did not repair or delete evidence.
            assertNull(DurablePlanningExposure.from(store.planningProjections(),Collections.emptyMap(),true));
            accept(store,offer("later-partial","sell","SELLING",false,100,80,9600,8));
            var later = store.ledgerSnapshot(account);
            assertEquals(2,later.getSales()); assertEquals(1,later.getUnresolvedSales());
            assertEquals(recordedProfitLower,later.getProfitLower()); assertEquals(recordedProfitUpper,later.getProfitUpper());
            recordedEvents=store.positionEventCount();
        }
        try (SqliteStore readOnly = SqliteStore.openReadOnly(db)) {
            var ledger=readOnly.ledgerSnapshot(account);
            assertTrue(ledger.isValid()); assertTrue(ledger.getTradingPositions().isEmpty());
            assertEquals(recordedEvents,readOnly.positionEventCount());
            assertEquals(recordedProfitLower,ledger.getProfitLower()); assertEquals(recordedProfitUpper,ledger.getProfitUpper());
            assertEquals(1,ledger.getMissedFlips().stream().filter(e->e.getItemId()==0).count());
        }
    }
}
