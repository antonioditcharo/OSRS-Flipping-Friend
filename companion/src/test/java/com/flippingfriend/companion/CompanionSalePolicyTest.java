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
    AccountSnapshot setup(SqliteStore store)throws Exception {return setup(store,null);}
    AccountSnapshot setup(SqliteStore store,AcquisitionExitContext context)throws Exception {
        for(int slot=0;slot<8;slot++)accept(store,event(slot,"EMPTY",null,0,0,0,0,at));
        var origin=event(0,"BUYING","purchase",3,0,0,101,at);
        if(context!=null) {
            var raw=json.toJsonTree(origin).getAsJsonObject();raw.add("exitContext",json.toJsonTree(context));
            raw.addProperty("recommendationId","original");raw.addProperty("suggestedPrice",101);raw.addProperty("suggestedQuantity",3);
            raw.addProperty("predictedMinutes",10);raw.addProperty("predictedCompletion",.75);
            raw.addProperty("adviceObservedAt",at-1);
            origin=json.fromJson(raw,OfferEvent.class).withBoundPrediction(json.fromJson(raw,OfferEvent.class),true);
        }
        accept(store,origin);
        var terminal=event(0,"BOUGHT","purchase",3,3,301,101,at+1);
        accept(store,context==null?terminal:terminal.withBoundPrediction(origin,true));
        accept(store,event(0,"EMPTY",null,0,0,0,0,at+2));
        return new AccountSnapshot("current",at+3600,100000000,0,8,8,true,false,0,"MODERATE",Map.of(),0,Map.of(),true,5,Set.of(),Set.of(),Set.of(),false,30)
            .withAccountId(id).withAutomaticTradeScope().withObservedHoldings(Map.of(561,3));
    }
    MarketIngestionService.MarketState market(long now,int high) {
        var latest=new JsonObject();var q=new JsonObject();q.addProperty("low",high);q.addProperty("high",high);
        q.addProperty("lowTime",now);q.addProperty("highTime",now);latest.add("561",q);
        return new MarketIngestionService.MarketState(Map.of(561,new MarketIngestionService.Item(561,"Nature rune",10000)),latest,new JsonObject(),new JsonObject(),now);
    }
    PolicyDecision decide(SqliteStore store,AccountSnapshot account,String source,int quantity,int price,int slot,long now)throws Exception {
        var read=store.saleProjections(id,now);
        return new CompanionSalePolicy().decide(read.planning,read.ledger,account,market(now,120),
            (item,step)->bars(now,120),561,price,quantity,source,false,slot,now);
    }
    List<com.flippingfriend.data.Candle> bars(long now,int high) {
        long end=now/300*300;
        return List.of(new com.flippingfriend.data.Candle(end-300,high,high,100,100),new com.flippingfriend.data.Candle(end-600,high,high,100,100));
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
    @Test public void currentInventoryCapsFinalExitWithoutErasingOtherPhysicalStock()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("inventory.db"))) {
            var account=setup(store).withObservedInventory(Map.of(561,1));long now=at+3600;
            var position=store.ledgerSnapshot(id,now).getTradingPositions().get(0);
            assertEquals(OfferLifecycleAction.PLACE_SELL,decide(store,account,position.getSourceEventId(),1,120,-1,now).getAction());
            assertEquals("SALE_STOCK_NOT_IN_INVENTORY",decide(store,account,position.getSourceEventId(),2,120,-1,now).getReasonCode());
            assertEquals(Integer.valueOf(3),account.getObservedHoldings().get(561));
            assertEquals(3,store.ledgerSnapshot(id,now).getTradingPositions().get(0).getQuantity());
            assertEquals(301,store.ledgerSnapshot(id,now).getTradingPositions().get(0).getTotalCost());
            var unequipped=account.withObservedInventory(Map.of(561,3));
            assertEquals(OfferLifecycleAction.PLACE_SELL,decide(store,unequipped,position.getSourceEventId(),3,120,-1,now).getAction());
        }
    }
    @Test public void acceptedReductionSurvivesLateFillCollectionAndRestartWithoutRestoringTheTarget()throws Exception {
        var path=folder.newFolder().toPath().resolve("reprice-chain.db");long now=at+3600;
        AccountSnapshot account;OfferEvent sale;
        try(var store=new SqliteStore(path)) {
            account=setup(store,new AcquisitionExitContext(140,90,120));
            sale=event(0,"SELLING","reprice-chain",3,0,0,150,now);accept(store,sale);
            assertEquals(OfferLifecycleAction.CANCEL_SELL,decide(store,account,sale.getEventId(),3,119,0,now).getAction());
            accept(store,event(0,"SELLING","reprice-chain",3,1,150,150,now+1));
            accept(store,event(0,"CANCELLED_SELL","reprice-chain",3,1,150,150,now+2));
            var notCollected=store.saleProjections(id,now+2);
            var remaining=notCollected.ledger.getTradingPositions().get(0);
            assertEquals("Uncollected stock is still reserved in the cancelled slot", "SALE_QUANTITY_ALREADY_LISTED",new CompanionSalePolicy().decide(notCollected.planning,notCollected.ledger,
                setupAccountAt(account,now+2),market(now+2,120),(i,s)->bars(now+2,120),561,119,2,remaining.getSourceEventId(),false,-1,now+2,sale.getEventId()).getReasonCode());
            accept(store,event(0,"EMPTY",null,0,0,0,0,now+3));
        }
        try(var store=new SqliteStore(path)) {
            var read=store.saleProjections(id,now+4);var remaining=read.ledger.getTradingPositions().get(0);
            var policy=new CompanionSalePolicy();var fresh=setupAccountAt(account,now+4);
            var accepted=policy.decide(read.planning,read.ledger,fresh,market(now+4,120),(i,s)->bars(now+4,120),
                561,119,2,remaining.getSourceEventId(),false,-1,now+4,sale.getEventId());
            assertEquals(OfferLifecycleAction.PLACE_SELL,accepted.getAction());
            assertEquals(OfferLifecycleAction.WAIT,policy.decide(read.planning,read.ledger,fresh,market(now+4,120),(i,s)->bars(now+4,120),
                561,119,3,remaining.getSourceEventId(),false,-1,now+4,sale.getEventId()).getAction());
            assertEquals("A naked old price cannot bypass the original target",OfferLifecycleAction.WAIT,
                policy.decide(read.planning,read.ledger,fresh,market(now+4,120),(i,s)->bars(now+4,120),
                561,119,2,remaining.getSourceEventId(),false,-1,now+4).getAction());
            assertEquals(201,remaining.getTotalCost());assertEquals(2,remaining.getQuantity());
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
            assertEquals("A recent actual fill keeps the working order intact",OfferLifecycleAction.WAIT,decide(store,account,progress.getEventId(),2,119,0,now+1).getAction());
            var heartbeat=event(0,"SELLING","sale",3,1,150,150,now+601);accept(store,heartbeat);
            var later=setupAccountAt(account,now+601);
            assertEquals(OfferLifecycleAction.CANCEL_SELL,decide(store,later,heartbeat.getEventId(),2,119,0,now+601).getAction());
        }
    }
    @Test public void anExistingParallelSaleCanFillWhileTheOtherRelistsWithTheExactRemainingPoolCostAfterRestart()throws Exception {
        var path=folder.newFolder().toPath().resolve("parallel-relist.db");long now=at+3600;
        AccountSnapshot account;OfferEvent accepted;
        try(var store=new SqliteStore(path)) {
            account=setup(store);
            accept(store,event(0,"SELLING","sale-a",2,0,0,150,at+10));
            var other=event(1,"SELLING","sale-b",1,0,0,145,at+100);
            var raw=json.toJsonTree(other).getAsJsonObject();raw.addProperty("firstSeenAt",at+100);
            accept(store,json.fromJson(raw,OfferEvent.class));
            accepted=event(0,"SELLING","sale-a",2,0,0,150,now);accept(store,accepted);
            assertEquals(OfferLifecycleAction.CANCEL_SELL,decide(store,account,accepted.getEventId(),2,119,0,now).getAction());
            accept(store,event(0,"CANCELLED_SELL","sale-a",2,0,0,150,now+1));
            accept(store,event(0,"EMPTY",null,0,0,0,0,now+2));
            var sold=event(1,"SOLD","sale-b",1,1,145,145,now+3);
            raw=json.toJsonTree(sold).getAsJsonObject();raw.addProperty("firstSeenAt",at+100);
            accept(store,json.fromJson(raw,OfferEvent.class));
        }
        try(var store=new SqliteStore(path)) {
            var read=store.saleProjections(id,now+4);var remaining=read.ledger.getTradingPositions().get(0);
            assertEquals(2,remaining.getQuantity());assertEquals(201,remaining.getTotalCost());
            var fresh=setupAccountAt(account,now+4).withObservedHoldings(Map.of(561,2)).withObservedInventory(Map.of(561,2));
            var policy=new CompanionSalePolicy();int effects=store.positionEventCount();
            var result=policy.decide(read.planning,read.ledger,fresh,market(now+4,120),(i,s)->bars(now+4,120),
                561,119,2,remaining.getSourceEventId(),false,-1,now+4,accepted.getEventId());
            assertEquals("An existing second sale is not a newly started replacement",OfferLifecycleAction.PLACE_SELL,result.getAction());
            assertEquals(OfferLifecycleAction.WAIT,policy.decide(read.planning,read.ledger,fresh,market(now+4,120),(i,s)->bars(now+4,120),
                561,100,2,remaining.getSourceEventId(),false,-1,now+4,accepted.getEventId()).getAction());
            assertEquals(OfferLifecycleAction.WAIT,policy.decide(read.planning,read.ledger,fresh,market(now+4,120),(i,s)->bars(now+4,120),
                561,119,3,remaining.getSourceEventId(),false,-1,now+4,accepted.getEventId()).getAction());
            assertEquals(effects,store.positionEventCount());
        }
    }
    AccountSnapshot setupAccountAt(AccountSnapshot account,long now) {
        var value=json.toJsonTree(account).getAsJsonObject();value.addProperty("observedAt",now);
        return json.fromJson(value,AccountSnapshot.class);
    }
    AccountSnapshot context(AccountSnapshot account,PositionProjection p,int target,int stop,double minutes) {
        var value=json.toJsonTree(account).getAsJsonObject();value.addProperty("targetHoldMinutes",360);
        value.add("positions",json.toJsonTree(List.of(new PositionSnapshot(p.getItemId(),p.getItemName(),p.getQuantity(),
            p.getTotalCost(),true,p.getOpenedAt(),target,stop,minutes))));return json.fromJson(value,AccountSnapshot.class);
    }
    @Test public void clientItemContextCannotInventAnOriginalAcquisitionPlan()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("unproven-context.db"))) {
            var account=setup(store);long now=at+3600;
            var p=store.ledgerSnapshot(id,now).getTradingPositions().get(0);
            account=context(account,p,140,90,120);
            assertEquals("A later item plan cannot become this old purchase's original target",
                OfferLifecycleAction.PLACE_SELL,decide(store,account,p.getSourceEventId(),3,120,-1,now).getAction());
        }
    }
    @Test public void finalAuthorityPreservesInitialTargetAndAllowsEarlierSupportedNonLossMaintenance()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("patient.db"))) {
            var account=setup(store,new AcquisitionExitContext(140,90,120));long now=at+3600;
            var position=store.ledgerSnapshot(id,now).getTradingPositions().get(0);
            account=context(account,position,140,90,120);
            assertEquals(OfferLifecycleAction.PLACE_SELL,decide(store,account,position.getSourceEventId(),3,140,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,decide(store,account,position.getSourceEventId(),3,120,-1,now).getAction());
            var sale=event(0,"SELLING","patient",3,0,0,150,now);accept(store,sale);
            assertEquals("A completed displacement can precede the original forecast",OfferLifecycleAction.CANCEL_SELL,decide(store,account,sale.getEventId(),3,119,0,now).getAction());
            long later=at+3*3600;var heartbeat=event(0,"SELLING","patient",3,0,0,150,later);accept(store,heartbeat);
            account=setupAccountAt(account,later);
            assertEquals(OfferLifecycleAction.CANCEL_SELL,decide(store,account,heartbeat.getEventId(),3,119,0,later).getAction());
            assertEquals("The exact taxed acquisition floor remains protected",OfferLifecycleAction.WAIT,decide(store,account,heartbeat.getEventId(),3,100,0,later).getAction());
            assertEquals(301,store.ledgerSnapshot(id,later).getTradingPositions().get(0).getTotalCost());
        }
    }
    @Test public void durableProgressIsNotAHeartbeatAndMissingProgressCannotBecomePermissionToCancel()throws Exception {
        var path=folder.newFolder().toPath().resolve("progress.db");
        try(var store=new SqliteStore(path)) {
            var account=setup(store);long now=at+3600;
            var sale=event(0,"SELLING","progress",3,0,0,150,now);accept(store,sale);
            var fill=event(0,"SELLING","progress",3,1,150,150,now+1);accept(store,fill);
            var beat=event(0,"SELLING","progress",3,1,150,150,now+61);accept(store,beat);
            var read=store.saleProjections(id,now+61);
            assertEquals(Long.valueOf(now+1),read.planning.saleProgressAt.get(0));
            assertEquals(OfferLifecycleAction.WAIT,decide(store,setupAccountAt(account,now+61),beat.getEventId(),2,119,0,now+61).getAction());
            try(var other=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path);var sql=other.prepareStatement("DELETE FROM event_log WHERE event_id=?")) {
                sql.setString(1,fill.getEventId());sql.executeUpdate();
            }
            assertEquals(Long.valueOf(Long.MAX_VALUE),store.saleProjections(id,now+61).planning.saleProgressAt.get(0));
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
