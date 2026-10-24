package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real canonical acquisition and current eight-slot proof, with no live account or HTTP writes. */
public class EntryStopPauseTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final CompanionSalePolicyTest f=new CompanionSalePolicyTest();
    final long now=f.at+3600;
    MarketIngestionService.MarketState market(int high) {
        var prices=new JsonObject();
        for(int item:List.of(561,562)) {
            var q=new JsonObject();q.addProperty("high",item==561?high:120);q.addProperty("low",item==561?high-10:100);
            q.addProperty("highTime",now);q.addProperty("lowTime",now);prices.add(""+item,q);
        }
        return new MarketIngestionService.MarketState(Map.of(561,new MarketIngestionService.Item(561,"Nature rune",10000),
            562,new MarketIngestionService.Item(562,"Other",10000)),prices,new JsonObject(),new JsonObject(),now);
    }
    AccountSnapshot seed(SqliteStore store)throws Exception {
        var state=f.setup(store,new AcquisitionExitContext(140,90,120));
        Map<Integer,String> sources=new HashMap<>();
        for(int slot=0;slot<8;slot++) {
            var e=f.event(slot,"EMPTY",null,0,0,0,0,now);f.accept(store,e);sources.put(slot,e.getEventId());
        }
        state=state.withOfferReadSet(sources).withObservedInventory(Map.of(561,3));
        store.recordAndReconcileAccount(state,PositionStateView.from(state),f.json.toJson(state));return state;
    }
    void plan(CompanionService service,int item,String id)throws Exception {
        var c=new PortfolioCandidate(item,"Item","",71,71,71,120,120,120,3,10000,100L,1,1,.95,.95,.4,.4,1.5,now+100);
        ExitSlotReservationTest.set(service,"plan",new PortfolioPlan(id,now,now+90,"READY","fixture",100,List.of(new PortfolioAllocation(1,c,"BUY"))));
    }
    @Test public void staleReadyPlansAndCappedReplacementsCannotAccumulateThroughAnUnconfirmedOriginalStop()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("stop.db"));var root=new CompanionService(f.json,store);
            var service=root.forAccountStore(store,f.id)) {
            var state=seed(store);ExitSlotReservationTest.set(service,"account",state);
            var ingestion=(MarketIngestionService)ExitSlotReservationTest.get(service,"market");
            ExitSlotReservationTest.set(ingestion,"state",market(80));int effects=store.positionEventCount();
            plan(service,561,"old-plan");
            assertEquals("ENTRY_STOP_RECHECK",service.entryDecision("old-plan",1,now).getReasonCode());
            var replacement=service.replacementEntry(561,2,now);
            assertEquals("ENTRY_STOP_RECHECK",replacement.getDecision().getReasonCode());
            plan(service,562,"unrelated");
            assertEquals("An affected acquisition cannot freeze unrelated verified-cash entries",OfferLifecycleAction.PLACE_BUY,
                service.entryDecision("unrelated",1,now).getAction());
            ExitSlotReservationTest.set(ingestion,"state",market(120));plan(service,561,"recovered");
            assertEquals(OfferLifecycleAction.PLACE_BUY,service.entryDecision("recovered",1,now).getAction());
            assertEquals(effects,store.positionEventCount());
            assertEquals(3,store.ledgerSnapshot(f.id,now).getTradingPositions().get(0).getQuantity());
            assertEquals(301,store.ledgerSnapshot(f.id,now).getTradingPositions().get(0).getTotalCost());
        }
    }
    @Test public void planningRestrictionsMergeExistingGuardsAndNeverBorrowClientOrMissingOriginalPlans()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("scope.db"))) {
            var state=seed(store);var read=store.planningProjections(f.id);
            var exposure=DurablePlanningExposure.forAccount(read,state,now);assertNotNull(exposure);
            var guarded=new DurablePlanningExposure(exposure.committedByItem,exposure.reservedBuyCoins,Set.of(999));
            assertEquals(Set.of(561,999),EntryStopPause.restrictions(guarded,read,market(80),now));
            assertEquals(Set.of(999),EntryStopPause.restrictions(guarded,read,market(120),now));
            assertEquals(Set.of(561),EntryStopPause.items(read,market(80),now));
            assertTrue(EntryStopPause.items(read,market(80),now+121).isEmpty());
            assertTrue(EntryStopPause.items(read,market(80),now-1).isEmpty());
            read.tradeScope.unsafeItems.add(561);
            assertTrue(EntryStopPause.items(read,market(80),now).isEmpty());
            assertEquals(Set.of(999),EntryStopPause.restrictions(guarded,read,market(80),now));
        }
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("unknown-plan.db"))) {
            f.setup(store);var read=store.planningProjections(f.id);
            assertFalse(read.tradeScope.readyPositions().isEmpty());
            assertTrue("An item-level client plan cannot supply an absent canonical stop",EntryStopPause.items(read,market(80),now).isEmpty());
        }
    }
}
