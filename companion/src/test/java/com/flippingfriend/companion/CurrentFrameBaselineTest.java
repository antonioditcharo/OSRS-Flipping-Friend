package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CurrentFrameBaselineTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final DurableRiskWindowTest f=new DurableRiskWindowTest();
    @Before public void setup(){f.folder=folder;}
    void interrupted(SqliteStore store)throws Exception {
        f.capture(store,100000000);f.buy(store,"buy",20,1000000);
        f.accept(store,f.event(0,"SELLING","sale",20,0,0,1100000));
        f.clear(store);f.clear(store); // A callback and the old restore pass both published the default EMPTY.
    }
    AccountSnapshot board(SqliteStore store,boolean wrongReceipt)throws Exception {
        Map<Integer,String> ids=new HashMap<>();
        for(int slot=0;slot<8;slot++) {
            var source=f.event(slot,slot==0?"SELLING":"EMPTY",slot==0?"sale":null,slot==0?20:0,0,0,slot==0?1100000:0);
            f.accept(store,source);ids.put(slot,source.getEventId());
        }
        if(wrongReceipt)ids.put(7,"missing");
        return new AccountSnapshot("current-frame-"+f.sequence,f.at,80000000,0,7,8,true,false,0)
            .withAccountId(f.account).withAutomaticTradeScope().withOfferReadSet(ids).withObservedHoldings(Map.of());
    }
    @Test public void completeCurrentFrameRestoresSlotsWithoutBookingMissedCountersOrClearingRiskHistory()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("baseline.db"))) {
            interrupted(store);var snapshot=board(store,false);int effects=store.positionEventCount();
            assertEquals(OfferLifecycleState.ERROR_RECONCILIATION,store.offerProjections().get(0).getState());
            store.recordAndReconcileAccount(snapshot,PositionStateView.from(snapshot),f.gson.toJson(snapshot));
            assertEquals(effects,store.positionEventCount());var read=store.planningProjections(f.account);
            assertTrue(CurrentOfferReadSet.valid(read,snapshot,f.at));assertEquals(20,read.tradeScope.positions.get(561).getQuantity());
            assertTrue(read.tradeScope.unsafeItems.contains(561));assertFalse(read.tradeScope.hasUnboundedReconciliation);
            var risk=f.evaluate(store,snapshot,1000000);assertTrue(risk.getReason(),risk.isAvailable());
            assertTrue(risk.getHistoricalUncertaintyReserve()>=20000000);assertTrue(risk.getRemainingLossBudget()>0);
            try(var service=new CompanionService(f.gson,store)) {
                var helper=new BoundedPlanningStateTest();helper.set(service,"account",snapshot);
                var market=(MarketIngestionService)helper.get(service,"market");
                helper.set(market,"state",new MarketIngestionService.MarketState(Map.of(562,new MarketIngestionService.Item(562,"Item 562",10000)),
                    new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),f.at));
                var candidate=new PortfolioCandidate(562,"Item 562","",100,100,100,120,120,120,10,10000,200,1,1,.95,.95,.4,.4,1.5,f.at+90);
                helper.set(service,"plan",new PortfolioPlan("current-frame",f.at,f.at+90,"READY","fixture",100,List.of(new PortfolioAllocation(1,candidate,"BUY"))));
                assertEquals(OfferLifecycleAction.PLACE_BUY,service.entryDecision("current-frame",1,f.at).getAction());
            }
            // Quarantined episode increments remain unresolved; a baseline is never a recovered fill.
            f.accept(store,f.event(0,"SELLING","sale",20,3,3300000,1100000));
            assertEquals(0,store.ledgerSnapshot(f.account).getSales());assertEquals(20,store.planningProjections(f.account).tradeScope.positions.get(561).getQuantity());
        }
    }
    @Test public void incompleteOrForeignSessionFramesCannotRestoreAnError()throws Exception {
        for(boolean foreign:List.of(false,true))try(var store=new SqliteStore(folder.newFolder().toPath().resolve("bad.db"))) {
            interrupted(store);if(foreign)f.session="foreign";var snapshot=board(store,!foreign);
            store.recordAndReconcileAccount(snapshot,PositionStateView.from(snapshot),f.gson.toJson(snapshot));
            assertEquals(OfferLifecycleState.ERROR_RECONCILIATION,store.offerProjections().get(0).getState());
            assertNull(DurablePlanningExposure.forAccount(store.planningProjections(f.account),snapshot,f.at));
        }
    }
    @Test public void aFailedFrameTransactionRollsBackTheDerivedSlotBaseline()throws Exception {
        var fail=new java.util.concurrent.atomic.AtomicBoolean();
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("rollback.db"),step->{if(fail.get() && step.equals("CURRENT_FRAME_BASELINES_WRITTEN"))throw new Exception("rollback");})) {
            interrupted(store);var snapshot=board(store,false);fail.set(true);
            try{store.recordAndReconcileAccount(snapshot,PositionStateView.from(snapshot),f.gson.toJson(snapshot));fail();}catch(Exception expected){assertEquals("rollback",expected.getMessage());}
            assertEquals(OfferLifecycleState.ERROR_RECONCILIATION,store.offerProjections().get(0).getState());
        }
    }
}
