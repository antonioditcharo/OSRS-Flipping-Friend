package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Full current read-set plus historical gap, all in a disposable account store. */
public class BoundedPlanningStateTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("bounded");
    final Gson gson=new Gson();
    final long now=1700000100L;
    OfferEvent offer(String id,int slot,int item,String type,int filled,long seq,long at,boolean auth) {
        var b=OfferEvent.builder("fixture",at,type).accountId(account).eventIdentity(id,"s",item==0?null:"offer-"+slot)
            .authenticatedObservation(auth).slot(slot).sequence(seq);
        if(item>0) b.item(item,"Item "+item).buying(true).price(100).quantities(10,filled).spent(filled*100L);
        return b.build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception {s.recordAndProjectOffer(e,gson.toJson(e),true);}
    Map<Integer,String> seed(SqliteStore s)throws Exception {
        accept(s,offer("open",0,561,"BUYING",0,1,now-3,false));
        accept(s,offer("partial",0,561,"BUYING",2,2,now-2,false));
        accept(s,offer("gap",0,0,"EMPTY",0,3,now-1,false));
        Map<Integer,String> read=new HashMap<>();
        for(int slot=0;slot<8;slot++) {
            String id="current-"+slot;
            accept(s,offer(id,slot,0,"EMPTY",0,4+slot,now,true));read.put(slot,id);
        }
        return read;
    }
    AccountSnapshot snapshot(Map<Integer,String> read) {
        return new AccountSnapshot("snapshot",now,100_000_000,0,8,8,true,false,0)
            .withAccountId(account).withAutomaticTradeScope().withOfferReadSet(read);
    }
    @Test public void boundedPastStockPermitsUnrelatedPlanningWithoutInventingDisposal()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("account.db"))) {
            var read=seed(s); int count=s.positionEventCount();
            var exposure=DurablePlanningExposure.forAccount(s.planningProjections(account),snapshot(read),now);
            assertNotNull(exposure);assertEquals(Long.valueOf(1000),exposure.committedByItem.get(561));
            assertEquals(Set.of(561),exposure.unavailableEntryItems);
            assertEquals(0,exposure.reservedBuyCoins);assertEquals(count,s.positionEventCount());
            var ledger=s.ledgerSnapshot(account);
            assertEquals(1,ledger.getMissedFlips().size());assertTrue(ledger.hasUnassignedReconciliation());
            assertEquals(2,s.planningProjections(account).tradeScope.positions.get(561).getQuantity());
            var planned=snapshot(read).withPlanningRestrictions(exposure.committedByItem,exposure.unavailableEntryItems);
            assertTrue(PortfolioPlanner.rejected(candidate(561),planned));
            assertFalse(PortfolioPlanner.rejected(candidate(562),planned));
        }
    }
    @Test public void staleIncompleteOrSupersededCashReadCannotReleaseTheGuard()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("account.db"))) {
            var read=seed(s);
            var missing=new HashMap<>(read);missing.remove(7);
            assertNull(DurablePlanningExposure.forAccount(s.planningProjections(account),
                new AccountSnapshot("legacy",now,100000000,0,8,8,true,false,0).withAccountId(account).withAutomaticTradeScope(),now));
            assertNull(DurablePlanningExposure.forAccount(s.planningProjections(account),snapshot(read),now+151));
            accept(s,offer("new-current",0,562,"BUYING",0,20,now+1,true));
            assertNull(DurablePlanningExposure.forAccount(s.planningProjections(account),snapshot(read),now+1));
        }
    }
    @Test public void aLaterObservedBuyAfterAnAmbiguousEmptyAddsAnotherUpperBound()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("account.db"))) {
            var read=seed(s);
            accept(s,offer("again",0,561,"BUYING",0,20,now+1,true));
            accept(s,offer("again-gap",0,0,"EMPTY",0,21,now+2,false));
            Map<Integer,String> second=new HashMap<>();
            for(int slot=0;slot<8;slot++) {
                String id="second-"+slot;
                accept(s,offer(id,slot,0,"EMPTY",0,30+slot,now+3,true));second.put(slot,id);
            }
            var value=gson.toJsonTree(snapshot(second)).getAsJsonObject();value.addProperty("observedAt",now+3);
            var exposure=DurablePlanningExposure.forAccount(s.planningProjections(account),gson.fromJson(value,AccountSnapshot.class),now+3);
            assertNotNull(exposure);assertEquals(Long.valueOf(2000),exposure.committedByItem.get(561));
        }
    }
    PortfolioCandidate candidate(int item) {
        return new PortfolioCandidate(item,"Item "+item,"",100,100,100,120,120,120,10,100000,200L,
            1,1,.95,.95,.4,.4,1.5,now+100);
    }
    @Test public void entryPolicyRechecksPausedItemsInsteadOfTrustingACachedAllocation()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("policy.db"));var service=new CompanionService(gson,s)) {
            var receipt=seed(s);set(service,"account",snapshot(receipt));
            var market=(MarketIngestionService)get(service,"market");
            var f=MarketIngestionService.class.getDeclaredField("state");f.setAccessible(true);
            f.set(market,new MarketIngestionService.MarketState(Map.of(561,new MarketIngestionService.Item(561,"Item 561",10000),
                562,new MarketIngestionService.Item(562,"Item 562",10000)),new com.google.gson.JsonObject(),
                new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),now));
            set(service,"plan",new PortfolioPlan("policy",now,now+90,"READY","fixture",100,
                List.of(new PortfolioAllocation(1,candidate(562),"BUY"))));
            assertEquals(OfferLifecycleAction.PLACE_BUY,service.entryDecision("policy",1,now).getAction());
            set(service,"plan",new PortfolioPlan("policy",now,now+90,"READY","fixture",100,
                List.of(new PortfolioAllocation(1,candidate(561),"BUY"))));
            assertNotEquals(OfferLifecycleAction.PLACE_BUY,service.entryDecision("policy",1,now).getAction());
        }
    }
    Object get(Object target,String field)throws Exception {
        var f=target.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(target);
    }
    void set(Object target,String field,Object value)throws Exception {
        var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);
    }
}
