package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Durable episode evidence must survive collection, process restart and new delivery UUIDs. */
public class ClearedOfferIdentityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    final String account=AccountIdentity.fromProfileKey("cleared-identity");
    final long at=Instant.now().getEpochSecond()-100;
    OfferEvent event(String id,String identity,String type,int filled,long spent,int sequence) {
        boolean buy=type.equals("BUYING") || type.equals("BOUGHT") || type.equals("CANCELLED_BUY");
        return OfferEvent.builder("fixture",at+sequence,type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",identity).sequence(sequence).slot(0).item(561,"Nature rune")
            .buying(buy).price(buy?1000:1200).quantities(9,filled).spent(spent).firstSeenAt(at).build();
    }
    OfferEvent clear(String id,int sequence) {
        return OfferEvent.builder("fixture",at+sequence,"EMPTY").accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",null).sequence(sequence).slot(0).build();
    }
    SqliteStore.ProjectedEventAcceptance accept(SqliteStore store,OfferEvent event)throws Exception {
        return store.recordAndProjectOffer(event,gson.toJson(event),true);
    }
    void buy(SqliteStore store)throws Exception {
        accept(store,event("buy-open","buy-a","BUYING",0,0,1));
        accept(store,event("buy-partial","buy-a","BUYING",2,1999,2));
        accept(store,event("buy-full","buy-a","BUYING",9,8991,3));
        accept(store,event("buy-terminal","buy-a","BOUGHT",9,8991,4));
        accept(store,clear("buy-clear",5));
    }
    long sum(Path path,String column,String type)throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+path);
            var sql=connection.prepareStatement("SELECT COALESCE(SUM("+column+"),0) FROM position_event WHERE effect_type=?")) {
            sql.setString(1,type);try(var rows=sql.executeQuery()){assertTrue(rows.next());return rows.getLong(1);}
        }
    }
    @Test public void oldBoughtAfterClearAndRestartCannotAcquireOrConsumeLimitAgain()throws Exception {
        Path path=folder.newFolder().toPath().resolve("buy.db");
        try(var store=new SqliteStore(path)){buy(store);}
        try(var store=new SqliteStore(path)) {
            var old=event("different-delivery","buy-a","BOUGHT",9,8991,6);
            assertFalse(accept(store,old).transition.isAccepted());
            assertEquals(9,sum(path,"quantity","ACQUIRE"));assertEquals(8991,sum(path,"acquisition_cost","ACQUIRE"));
            assertEquals(9,store.buyLimitProjections().get(0).getUsedQuantity());
            assertEquals(OfferLifecycleState.ERROR_RECONCILIATION,store.offerProjections().get(0).getState());
            assertTrue(store.ledgerSnapshot(account).getTradingPositions().isEmpty());
            int count=store.positionEventCount();assertEquals(SqliteStore.EventAcceptance.DUPLICATE,accept(store,old).acceptance);
            assertEquals(count,store.positionEventCount());
        }
    }
    @Test public void repeatedOldSoldCannotCreateAnotherSaleOrChangeTaxProfit()throws Exception {
        Path path=folder.newFolder().toPath().resolve("sell.db");
        AccountLedgerSnapshot before;
        try(var store=new SqliteStore(path)) {
            buy(store);accept(store,event("sell-open","sell-a","SELLING",0,0,6));
            accept(store,event("sell-terminal","sell-a","SOLD",9,10802,7));
            accept(store,clear("sell-clear",8));before=store.ledgerSnapshot(account);
        }
        try(var store=new SqliteStore(path)) {
            assertFalse(accept(store,event("old-sell","sell-a","SOLD",9,10802,9)).transition.isAccepted());
            var after=store.ledgerSnapshot(account);
            assertEquals(before.getSales(),after.getSales());assertEquals(before.getUnresolvedSales(),after.getUnresolvedSales());
            assertEquals(before.getTaxLower(),after.getTaxLower());assertEquals(before.getTaxUpper(),after.getTaxUpper());
            assertEquals(before.getProfitLower(),after.getProfitLower());assertEquals(before.getProfitUpper(),after.getProfitUpper());
            assertEquals(9,sum(path,"quantity","DISPOSE"));
        }
    }
    @Test public void zeroFillClosedEpisodeCannotReopenAndLaterBookItsOldCounters()throws Exception {
        Path path=folder.newFolder().toPath().resolve("zero.db");
        try(var store=new SqliteStore(path)) {
            accept(store,event("zero-open","buy-a","BUYING",0,0,1));
            accept(store,event("zero-cancel","buy-a","CANCELLED_BUY",0,0,2));
            accept(store,clear("zero-clear",3));store.pruneEvents(0);
        }
        try(var store=new SqliteStore(path)) {
            assertFalse(accept(store,event("old-open","buy-a","BUYING",0,0,4)).transition.isAccepted());
            assertFalse(accept(store,event("old-partial","buy-a","BUYING",2,1999,5)).transition.isAccepted());
            assertFalse(accept(store,event("old-full","buy-a","BOUGHT",9,8991,6)).transition.isAccepted());
            assertEquals(0,sum(path,"quantity","ACQUIRE"));assertTrue(store.buyLimitProjections().isEmpty());
        }
    }
    @Test public void distinctEpisodeWithIdenticalOrderAndStartSecondRemainsTradable()throws Exception {
        Path path=folder.newFolder().toPath().resolve("new.db");
        try(var store=new SqliteStore(path)) {
            buy(store);assertTrue(accept(store,event("new-open","buy-b","BUYING",0,0,6)).transition.isAccepted());
            assertTrue(accept(store,event("new-full","buy-b","BOUGHT",9,8991,7)).transition.isAccepted());
            var held=store.ledgerSnapshot(account).getTradingPositions();assertEquals(1,held.size());
            assertEquals(18,held.get(0).getQuantity());assertEquals(17982,held.get(0).getTotalCost());
            assertEquals(18,store.buyLimitProjections().get(0).getUsedQuantity());
        }
    }
    @Test public void failedGapTransactionRollsBackSourceProjectionAndGuardTogether()throws Exception {
        Path path=folder.newFolder().toPath().resolve("rollback.db");
        try(var store=new SqliteStore(path)){buy(store);}
        var old=event("old-terminal","buy-a","BOUGHT",9,8991,6);
        try(var store=new SqliteStore(path,point->{if(point.equals("POSITION_ACCOUNTING_APPLIED"))throw new Exception("fixture rollback");})) {
            try{accept(store,old);fail("expected rollback");}catch(Exception expected){assertEquals("fixture rollback",expected.getMessage());}
        }
        try(var store=new SqliteStore(path)) {
            assertEquals(OfferLifecycleState.EMPTY,store.offerProjections().get(0).getState());assertEquals(2,store.positionEventCount());
            assertEquals(SqliteStore.EventAcceptance.NEW,accept(store,old).acceptance);
            assertEquals(9,sum(path,"quantity","ACQUIRE"));assertEquals(3,store.positionEventCount());
        }
    }
    @Test public void unreadableHistoricalIdentityCannotAuthorizeANewAcquisition()throws Exception {
        Path path=folder.newFolder().toPath().resolve("unreadable.db");
        try(var store=new SqliteStore(path)){accept(store,clear("empty",1));}
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+path);
            var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(1,'fixture','unreadable','BUYING','{broken')");
        }
        try(var store=new SqliteStore(path)) {
            assertFalse(accept(store,event("new","buy-b","BOUGHT",9,8991,2)).transition.isAccepted());
            assertEquals(0,sum(path,"quantity","ACQUIRE"));
        }
    }
    @Test public void contradictoryReuseKeepsBothRecordedItemsPaused()throws Exception {
        Path path=folder.newFolder().toPath().resolve("collision.db");
        try(var store=new SqliteStore(path)) {
            buy(store);
            var payload=gson.toJsonTree(event("collision","buy-a","BUYING",0,0,6)).getAsJsonObject();
            payload.addProperty("itemId",562);payload.addProperty("itemName","Chaos rune");
            var collision=gson.fromJson(payload,OfferEvent.class);
            assertFalse(accept(store,collision).transition.isAccepted());
            assertEquals(java.util.Set.of(561,562),store.ledgerSnapshot(account).getReconciliationScope().get("collision"));
            assertEquals(9,sum(path,"quantity","ACQUIRE"));
        }
    }
    @Test public void rejectedTerminalCannotProduceCollectAdviceBeforeOrAfterRestart()throws Exception {
        Path path=folder.newFolder().toPath().resolve("advice.db");
        try(var store=new SqliteStore(path)){buy(store);}
        try(var store=new SqliteStore(path);var service=new CompanionService(gson,store)) {
            service.offer(event("repeated-terminal","buy-a","BOUGHT",9,8991,6));
            assertEquals(CompanionActionType.WAIT,service.action().getType());
        }
        try(var store=new SqliteStore(path);var service=new CompanionService(gson,store)) {
            assertEquals(CompanionActionType.WAIT,service.action().getType());
        }
    }
}
