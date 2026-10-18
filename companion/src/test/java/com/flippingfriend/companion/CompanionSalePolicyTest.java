package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CompanionSalePolicyTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();final String id=AccountIdentity.fromProfileKey("sale-policy-fixture");
    final long at=Instant.now().getEpochSecond()-3600;long seq;
    OfferEvent event(int slot,String type,String episode,int total,int filled,long spent,int price,long at) {
        var b=OfferEvent.builder("fixture",at,type).accountId(id).authenticatedObservation(true)
            .eventIdentity("event-"+(++seq),"session",episode).sequence(seq).slot(slot);
        if(!"EMPTY".equals(type))b.item(561,"Nature rune").buying(type.equals("BUYING")||type.equals("BOUGHT"))
            .quantities(total,filled).spent(spent).price(price).firstSeenAt(this.at);
        return b.build();
    }
    void accept(SqliteStore store,OfferEvent event)throws Exception{store.recordAndProjectOffer(event,json.toJson(event),true);}
    AccountSnapshot setup(SqliteStore store)throws Exception {
        for(int slot=0;slot<8;slot++)accept(store,event(slot,"EMPTY",null,0,0,0,0,at));
        accept(store,event(0,"BUYING","purchase",3,0,0,101,at));
        accept(store,event(0,"BOUGHT","purchase",3,3,301,101,at+1));
        accept(store,event(0,"EMPTY",null,0,0,0,0,at+2));
        return new AccountSnapshot("current",at+3600,100000000,0,8,8,true,false,0,"MODERATE",Map.of(),0,Map.of(),true,5)
            .withAccountId(id).withAutomaticTradeScope().withObservedHoldings(Map.of(561,3));
    }
    MarketIngestionService.MarketState market(long now,int high) {
        var latest=new JsonObject();var q=new JsonObject();q.addProperty("low",high);q.addProperty("high",high);
        q.addProperty("lowTime",now);q.addProperty("highTime",now);latest.add("561",q);
        return new MarketIngestionService.MarketState(Map.of(561,new MarketIngestionService.Item(561,"Nature rune",10000)),latest,new JsonObject(),new JsonObject(),now);
    }
    PolicyDecision decide(SqliteStore store,AccountSnapshot account,String source,int quantity,int price,int slot,long now)throws Exception {
        return new CompanionSalePolicy().decide(store.planningProjections(id),store.ledgerSnapshot(id,now),account,market(now,120),
            (item,step)->List.of(),561,price,quantity,source,false,slot,now);
    }
    @Test public void canonicalAcquisitionAuthorizesExactExitButNotExcessUnknownUnitsOrOldSource()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("fixture.db"))) {
            var account=setup(store);long now=at+3600;
            String source=store.ledgerSnapshot(id,now).getTradingPositions().get(0).getSourceEventId();
            assertEquals(OfferLifecycleAction.PLACE_SELL,decide(store,account,source,2,120,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,source,4,120,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,"wrong",2,120,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,source,2,1,-1,now).getAction());
        }
    }
    @Test public void saleMaintenanceBindsCurrentEpisodeSourceAndRemainingQuantity()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("fixture.db"))) {
            var account=setup(store);long now=at+3600;
            var sell=event(0,"SELLING","sale",3,0,0,150,now);accept(store,sell);
            assertEquals(OfferLifecycleAction.CANCEL_SELL,decide(store,account,sell.getEventId(),3,119,0,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,sell.getEventId(),2,119,0,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,"previous-episode",3,119,0,now).getAction());
            var progress=event(0,"SELLING","sale",3,1,150,150,now+1);accept(store,progress);
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,sell.getEventId(),3,119,0,now+1).getAction());
            assertEquals(OfferLifecycleAction.CANCEL_SELL,decide(store,account,progress.getEventId(),2,119,0,now+1).getAction());
        }
    }
    @Test public void staleSettingsAndQuotesCannotAuthorizeSaleOrChangeTheLedger()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("fixture.db"))) {
            var account=setup(store);long now=at+3800;
            String source=store.ledgerSnapshot(id,now).getTradingPositions().get(0).getSourceEventId();
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,source,2,120,-1,now).getAction());
            var staleQuote=market(at+3400,120);
            var refused=new CompanionSalePolicy().decide(store.planningProjections(id),store.ledgerSnapshot(id,at+3600),account,staleQuote,
                (item,step)->List.of(),561,120,2,source,false,-1,at+3600);
            assertEquals(OfferLifecycleAction.WAIT,refused.getAction());assertEquals("SALE_EVIDENCE_UNAVAILABLE",refused.getReasonCode());
            var ledger=store.ledgerSnapshot(id,now);assertEquals(301,ledger.getTradingPositions().get(0).getTotalCost());assertEquals(0,ledger.getSales());
        }
    }
    @Test public void quietMarketStillAuthorizesAnExactNonLossExitButNeverALossOrReprice()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("quiet.db"))) {
            var account=setup(store);long now=at+3600;
            var ledger=store.ledgerSnapshot(id,now);String source=ledger.getTradingPositions().get(0).getSourceEventId();
            var quiet=market(now,120);quiet.latest.getAsJsonObject("561").addProperty("highTime",now-1200);
            var policy=new CompanionSalePolicy();int effects=store.positionEventCount();
            var exit=policy.decide(store.planningProjections(id),ledger,account,quiet,(i,s)->List.of(),561,120,2,source,false,-1,now);
            assertEquals("A fresh feed with an old last trade must not strand verified stock",OfferLifecycleAction.PLACE_SELL,exit.getAction());
            assertEquals(OfferLifecycleAction.WAIT,policy.decide(store.planningProjections(id),ledger,account,quiet,(i,s)->List.of(),561,1,2,source,true,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,policy.decide(store.planningProjections(id),ledger,account,quiet,(i,s)->List.of(),561,1,2,source,false,-1,now).getAction());
            assertEquals("SALE_STOCK_NOT_OBSERVED",policy.decide(store.planningProjections(id),ledger,
                account.withObservedHoldings(Map.of()),quiet,(i,s)->List.of(),561,120,2,source,false,-1,now).getReasonCode());
            var sale=event(0,"SELLING","sale",3,0,0,150,now);accept(store,sale);
            assertEquals("Old trades cannot justify cancelling an existing sell", "SALE_QUOTE_STALE",
                policy.decide(store.planningProjections(id),ledger,account,quiet,(i,s)->List.of(),561,119,3,sale.getEventId(),false,0,now).getReasonCode());
            assertEquals(effects,store.positionEventCount());assertEquals(0,store.ledgerSnapshot(id,now).getSales());
        }
    }
    @Test public void playerStartedSalesOfPausedStockHaveUnknownCostMaintenanceButNoManagedExit()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("paused-sale.db"))) {
            var account=setup(store);long now=at+3600;
            accept(store,event(0,"BUYING","gap",3,0,0,100,now));
            accept(store,event(0,"EMPTY",null,0,0,0,0,now+1));
            var sale=event(0,"SELLING","manual-sale",3,0,0,150,now+2);accept(store,sale);
            assertTrue(store.ledgerSnapshot(id,now+2).getTradingPositions().isEmpty());
            int effects=store.positionEventCount();
            assertEquals(OfferLifecycleAction.CANCEL_SELL,decide(store,account,sale.getEventId(),3,119,0,now+2).getAction());
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,"recorded-buy",3,119,-1,now+2).getAction());
            assertEquals(effects,store.positionEventCount());assertEquals(0,store.ledgerSnapshot(id,now+2).getSales());
            // A contradictory current price frame is not a maintenance baseline.
            var contradiction=event(0,"SELLING","manual-sale",3,0,0,151,now+3);accept(store,contradiction);
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,contradiction.getEventId(),3,119,0,now+3).getAction());
        }
    }
}
