package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ExitSlotReservationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();final long now=java.time.Instant.now().getEpochSecond();
    final String account=AccountIdentity.fromProfileKey("exit-slot");
    OfferEvent event(String id,int slot,int item,String type,int total,int filled,long sequence,long at) {
        var b=OfferEvent.builder("fixture",at,type).accountId(account).eventIdentity(id,"session",item==0?null:"episode-"+slot+"-"+item)
            .authenticatedObservation(true).slot(slot).sequence(sequence).firstSeenAt(item==561?now-5:at);
        if(item>0)b.item(item,"Item "+item).buying(type.equals("BUYING")||type.equals("BOUGHT"))
            .price(100).quantities(total,filled).spent(100L*filled);
        return b.build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception{s.recordAndProjectOffer(e,gson.toJson(e),true);}
    AccountSnapshot seed(SqliteStore s)throws Exception {
        accept(s,event("buy",0,561,"BUYING",10,0,1,now-5));
        accept(s,event("fill",0,561,"BOUGHT",10,10,2,now-4));
        accept(s,event("collect",0,0,"EMPTY",0,0,3,now-3));
        Map<Integer,String> receipts=new HashMap<>();
        for(int slot=0;slot<8;slot++) {
            String id="current-"+slot;
            accept(s,event(id,slot,slot<7?1000+slot:0,slot<7?"SELLING":"EMPTY",slot<7?1:0,0,4+slot,now));receipts.put(slot,id);
        }
        var state=new AccountSnapshot("capture",now,100000000,0,1,8,true,false,0).withAccountId(account).withAutomaticTradeScope()
            .withOfferReadSet(receipts).withObservedHoldings(Map.of(561,10,8000,100)).withObservedInventory(Map.of(561,10,8000,100));
        s.recordAndReconcileAccount(state,PositionStateView.from(state),gson.toJson(state));return state;
    }
    @Test public void finalEntryAndReplacementAndPlanningHonorTheSameDurableLastSlotReservation()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("ledger.db"));var owner=new CompanionService(gson,store);
            var service=owner.forAccountStore(store,account)) {
            var state=seed(store);int effects=store.positionEventCount();set(service,"account",state);
            var ingestion=(MarketIngestionService)get(service,"market");
            set(ingestion,"state",new MarketIngestionService.MarketState(Map.of(562,new MarketIngestionService.Item(562,"Other",10000)),
                new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),now));
            var candidate=new PortfolioCandidate(562,"Other","",100,100,100,120,120,120,10,100000,200L,1,1,.95,.95,.4,.4,1.5,now+100);
            set(service,"plan",new PortfolioPlan("old-plan",now,now+90,"READY","fixture",100,List.of(new PortfolioAllocation(1,candidate,"BUY"))));
            var refused=service.entryDecision("old-plan",1,now);
            assertEquals("EXIT_SLOT_RESERVED",refused.getReasonCode());assertNotEquals(OfferLifecycleAction.PLACE_BUY,refused.getAction());
            assertEquals(PortfolioPlanOutcome.NO_FREE_SLOT,service.replacementEntry(562,10,now).getPlan().getOutcome());
            var refresh=CompanionService.class.getDeclaredMethod("refreshPlan");refresh.setAccessible(true);refresh.invoke(service);
            assertEquals(PortfolioPlanOutcome.NO_FREE_SLOT,((PortfolioPlan)get(service,"plan")).getOutcome());
            assertEquals(effects,store.positionEventCount());assertEquals(10,store.planningProjections(account).tradeScope.positions.get(561).getQuantity());
            var moved=state.withObservedInventory(Map.of(8000,100));
            store.recordAndReconcileAccount(moved,PositionStateView.from(moved),gson.toJson(moved));set(service,"account",moved);
            set(service,"plan",new PortfolioPlan("released",now,now+90,"READY","fixture",100,List.of(new PortfolioAllocation(1,candidate,"BUY"))));
            assertEquals("The entry workflow resumes when the inventory no longer needs the slot",
                OfferLifecycleAction.PLACE_BUY,service.entryDecision("released",1,now).getAction());
        }
    }
    @Test public void personalExtrasBankOnlyAlreadyListedAndHealthyAccumulationDoNotReserveAnExitSlot()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("scope.db"))) {
            var state=seed(store);var read=store.planningProjections(account);
            assertEquals(561,ExitSlotReservation.awaiting(read,state,0).getItemId());
            assertNull("Exact own-item replacement must remain possible",ExitSlotReservation.awaiting(read,state,561));
            assertNull(ExitSlotReservation.awaiting(read,state.withObservedInventory(Map.of(8000,100)),0));
            var stock=read.tradeScope.positions.get(561);read.tradeScope.unsafeItems.add(561);
            assertNull("Paused historical stock and personal extras cannot reserve a sell slot",ExitSlotReservation.awaiting(read,state,0));
            read.tradeScope.unsafeItems.clear();assertEquals(stock,read.tradeScope.positions.get(561));
            var current=new HashMap<>(read.currentEvents);current.put(7,event("listed",7,561,"SELLING",10,0,20,now));read.currentEvents=current;
            assertNull(ExitSlotReservation.awaiting(read,state,0));
            current.put(7,event("accumulating",7,561,"BUYING",20,0,21,now));
            assertNull(ExitSlotReservation.awaiting(read,state,0));
            current.remove(7);
            var body=gson.toJsonTree(state).getAsJsonObject();body.addProperty("freeSlots",2);
            assertNull(ExitSlotReservation.awaiting(read,gson.fromJson(body,AccountSnapshot.class),0));
        }
    }
    static Object get(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
}
