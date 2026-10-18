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
            .withAccountId(id).withAutomaticTradeScope();
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
            var staleQuote=market(at+3600,120);staleQuote.latest.getAsJsonObject("561").addProperty("lowTime",at+3200);
            var refused=new CompanionSalePolicy().decide(store.planningProjections(id),store.ledgerSnapshot(id,at+3600),account,staleQuote,
                (item,step)->List.of(),561,120,2,source,false,-1,at+3600);
            assertEquals(OfferLifecycleAction.WAIT,refused.getAction());assertEquals("SALE_QUOTE_STALE",refused.getReasonCode());
            var ledger=store.ledgerSnapshot(id,now);assertEquals(301,ledger.getTradingPositions().get(0).getTotalCost());assertEquals(0,ledger.getSales());
        }
    }
}
