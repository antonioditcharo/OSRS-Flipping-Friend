package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Full cumulative counters can precede the game's terminal state. Disposable stores only. */
public class FullCounterProgressTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("full-progress");
    final long at=Instant.now().getEpochSecond()-100;
    final Gson gson=new Gson();
    OfferEvent event(String id,String type,int filled,long spent,int sequence) {
        boolean buy=type.equals("BUYING") || type.equals("BOUGHT");
        return OfferEvent.builder("fixture",at+sequence,type).accountId(account)
            .authenticatedObservation(true).eventIdentity(id,"session",buy?"buy":"sell")
            .slot(0).item(561,"Nature rune").buying(buy).price(buy?1000:1200)
            .quantities(9,filled).spent(spent).sequence(sequence).firstSeenAt(at).build();
    }
    SqliteStore.ProjectedEventAcceptance accept(SqliteStore store,OfferEvent event)throws Exception {
        return store.recordAndProjectOffer(event,gson.toJson(event),true);
    }
    OfferEvent clear(String id,int sequence) {
        return OfferEvent.builder("fixture",at+sequence,"EMPTY").accountId(account)
            .authenticatedObservation(true).eventIdentity(id,"session",null).slot(0).sequence(sequence).build();
    }
    void partialBuy(SqliteStore store)throws Exception {
        accept(store,event("open","BUYING",0,0,1));
        accept(store,event("partial","BUYING",2,1999,2));
    }
    void assertOwned(SqliteStore store)throws Exception {
        var ledger=store.ledgerSnapshot(account);
        assertTrue(ledger.getMissedFlips().isEmpty());
        assertEquals(1,ledger.getTradingPositions().size());
        assertEquals(9,ledger.getTradingPositions().get(0).getQuantity());
        assertEquals(8991,ledger.getTradingPositions().get(0).getTotalCost());
    }
    @Test public void fullBuyCountersBookOnlyTheProvenDeltaAndAwaitTerminalState()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("buy.db"))) {
            partialBuy(store);
            var progress=event("full-progress","BUYING",9,8991,3);
            var accepted=accept(store,progress);
            assertTrue(accepted.transition.isAccepted());
            assertEquals(OfferLifecycleAction.WAIT,accepted.transition.getAction());
            assertEquals("BUYING",accepted.transition.getSourceEvent().getEventType());
            assertEquals(OfferLifecycleState.BUY_PARTIAL,store.offerProjections().get(0).getState());
            assertOwned(store);assertEquals(2,store.positionEventCount());
            assertTrue("A progress frame cannot count as terminal learning",store.executionStats().isEmpty());
            accept(store,progress);accept(store,event("full-heartbeat","BUYING",9,8991,4));
            assertEquals(2,store.positionEventCount());
            accept(store,event("terminal","BOUGHT",9,8991,5));
            assertEquals(OfferLifecycleState.BUY_FILLED_UNCOLLECTED,store.offerProjections().get(0).getState());
            assertOwned(store);assertEquals(2,store.positionEventCount());
        }
    }
    @Test public void restartBetweenFullCountersAndTerminalDoesNotLoseOrRecountAcquisition()throws Exception {
        Path path=folder.newFolder().toPath().resolve("restart.db");
        var progress=event("full-progress","BUYING",9,8991,3);
        try(var store=new SqliteStore(path)) {partialBuy(store);accept(store,progress);}
        try(var store=new SqliteStore(path)) {
            accept(store,progress);assertOwned(store);
            accept(store,event("terminal","BOUGHT",9,8991,4));
            accept(store,event("terminal-heartbeat","BOUGHT",9,8991,5));
            assertOwned(store);assertEquals(2,store.positionEventCount());
            assertEquals(9,store.buyLimitProjections().get(0).getUsedQuantity());
        }
    }
    @Test public void fullSellProgressConservesExactCostAndTerminalAddsNoSale()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("sell.db"))) {
            accept(store,event("buy-open","BUYING",0,0,1));
            accept(store,event("buy-done","BOUGHT",9,8991,2));accept(store,clear("buy-clear",3));
            accept(store,event("sell-open","SELLING",0,0,4));
            accept(store,event("sell-partial","SELLING",2,2401,5));
            var progress=event("sell-full-progress","SELLING",9,10802,6);
            var accepted=accept(store,progress);
            assertTrue(accepted.transition.isAccepted());
            assertEquals(OfferLifecycleAction.WAIT,accepted.transition.getAction());
            assertEquals("SELLING",accepted.transition.getSourceEvent().getEventType());
            assertEquals(OfferLifecycleState.SELL_PARTIAL,store.offerProjections().get(0).getState());
            var before=store.ledgerSnapshot(account);
            assertEquals(2,before.getSales());assertEquals(0,before.getUnresolvedSales());
            assertTrue(before.getPositions().isEmpty());assertTrue(before.getMissedFlips().isEmpty());
            accept(store,progress);accept(store,event("sell-done","SOLD",9,10802,7));
            var after=store.ledgerSnapshot(account);
            assertEquals(before.getSales(),after.getSales());
            assertEquals(before.getProfitLower(),after.getProfitLower());
            assertEquals(before.getProfitUpper(),after.getProfitUpper());
            assertEquals(before.getTaxLower(),after.getTaxLower());
            assertEquals(before.getTaxUpper(),after.getTaxUpper());
            assertEquals(OfferLifecycleState.SELL_FILLED_UNCOLLECTED,store.offerProjections().get(0).getState());
        }
    }
    @Test public void clearStillRequiresAnObservedTerminalEvenWithFullCounters()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("clear.db"))) {
            partialBuy(store);accept(store,event("full-progress","BUYING",9,8991,3));
            var cleared=accept(store,clear("unproven-clear",4));
            assertFalse(cleared.transition.isAccepted());
            assertEquals("offer cleared before a terminal observation",cleared.transition.getReason());
            assertFalse(store.ledgerSnapshot(account).getMissedFlips().isEmpty());
        }
    }
    @Test public void firstSeenFullCountersRemainAnUnpricedHistoricalBaseline()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("baseline.db"))) {
            accept(store,event("first-observed","BUYING",9,8991,1));
            accept(store,event("terminal","BOUGHT",9,8991,2));
            var ledger=store.ledgerSnapshot(account);
            assertTrue(ledger.getTradingPositions().isEmpty());
            assertFalse(ledger.getMissedFlips().isEmpty());
            assertEquals(0,ledger.getSales());
        }
    }
    @Test public void overfilledAndRegressingCountersKeepTheirReconciliationGuard()throws Exception {
        for(boolean overfilled:new boolean[]{true,false})
            try(var store=new SqliteStore(folder.newFolder().toPath().resolve("bad.db"))) {
                partialBuy(store);
                if(!overfilled)accept(store,event("full-progress","BUYING",9,8991,3));
                var bad=event("bad","BUYING",overfilled?10:8,overfilled?9990:7992,4);
                assertFalse(accept(store,bad).transition.isAccepted());
                assertFalse(store.ledgerSnapshot(account).getMissedFlips().isEmpty());
            }
    }
    @Test public void existingRejectedProgressHistoryIsNotRepairedOrBackfilled()throws Exception {
        Path path=folder.newFolder().toPath().resolve("old-gap.db");
        var rejected=event("old-full-progress","BUYING",9,8991,3);
        String original=gson.toJson(rejected);
        try(var store=new SqliteStore(path)) {
            partialBuy(store);
            // A disposable fixture of the earlier runtime's immutable rejection and high-water
            // projection. Never seed or rewrite a real account to make this regression pass.
            try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path)) {
                try(var insert=connection.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(?,?,?,?,?)")) {
                    insert.setLong(1,at+3);insert.setString(2,"fixture");insert.setString(3,rejected.getEventId());
                    insert.setString(4,"BUYING");insert.setString(5,original);insert.executeUpdate();
                }
                try(var insert=connection.prepareStatement("INSERT INTO position_event(source_event_id,source_offer_identity,observed_at,effect_type,item_id,item_name,quantity,acquisition_cost,transition_reason,created_at) VALUES(?,?,?,'RECONCILE',561,'Nature rune',0,0,'invalid source observation',?)")) {
                    insert.setString(1,rejected.getEventId());insert.setString(2,"buy");insert.setLong(3,at+3);insert.setLong(4,at+3);insert.executeUpdate();
                }
                try(var update=connection.prepareStatement("UPDATE offer_projection SET lifecycle_state='ERROR_RECONCILIATION',last_sequence=3,last_observed_at=?,source_event_id=? WHERE slot=0")) {
                    update.setLong(1,at+3);update.setString(2,rejected.getEventId());update.executeUpdate();
                }
            }
        }
        try(var store=new SqliteStore(path)) {
            accept(store,event("later-terminal","BOUGHT",9,8991,4));
            var ledger=store.ledgerSnapshot(account);
            assertTrue(ledger.getTradingPositions().isEmpty());
            assertFalse(ledger.getMissedFlips().isEmpty());
            assertEquals(3,store.positionEventCount());
            assertEquals(0,ledger.getSales());
        }
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path);
                var sql=connection.prepareStatement("SELECT payload FROM event_log WHERE event_id=?")) {
            sql.setString(1,rejected.getEventId());try(var rows=sql.executeQuery()) {assertTrue(rows.next());assertEquals(original,rows.getString(1));}
            try(var totals=connection.createStatement();var rows=totals.executeQuery(
                    "SELECT SUM(quantity),SUM(acquisition_cost) FROM position_event WHERE effect_type='ACQUIRE'")) {
                assertTrue(rows.next());assertEquals(2,rows.getInt(1));assertEquals(1999,rows.getLong(2));
            }
            try(var gaps=connection.createStatement();var rows=gaps.executeQuery("SELECT COUNT(*) FROM position_event WHERE effect_type='RECONCILE'")) {
                assertTrue(rows.next());assertEquals(2,rows.getInt(1));
            }
        }
    }
}
