package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Exact current money and source-backed holdings survive a valuation-only entry pause. */
public class VerifiedCashRiskTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final DurableRiskWindowTest f=new DurableRiskWindowTest();
    @Before public void setup(){f.folder=folder;}
    AccountRiskSnapshot risk(SqliteStore store,AccountSnapshot account,MarketIngestionService.MarketState market)throws Exception {
        var read=store.planningProjections(f.account);
        return DurableRisk.evaluate(read,account,DurablePlanningExposure.forAccount(read,account,f.at),market,f.at);
    }
    @Test public void missingPriceKeepsExactStockAndCashButDoesNotChargeVisibleStockAsLostAfterRestart()throws Exception {
        var path=folder.newFolder().toPath().resolve("stock.db");String window;int effects;
        try(var store=new SqliteStore(path)) {
            f.capture(store,100_000_000);f.buy(store,"known-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            effects=store.positionEventCount();var before=store.ledgerSnapshot(f.account).getTradingPositions().get(0);
            var risk=risk(store,account,f.market(null));window=risk.getWindowId();
            assertTrue(risk.getReason(),risk.isAvailable());assertTrue(risk.isVerifiedCashPolicy());
            assertEquals(Long.valueOf(76_000_000),risk.getUnpricedManagedCostReserve());
            assertEquals(76_000_000,risk.getValuationUncertaintyExcluded());assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());
            assertEquals(Long.valueOf(15_000_000),risk.getRemainingLossBudget());assertEquals(Set.of(561),risk.getValuationPausedItems());
            assertEquals(24_000_000,account.getSpendableCoins());
            var after=store.ledgerSnapshot(f.account).getTradingPositions().get(0);
            assertEquals(before.getSourceEventId(),after.getSourceEventId());assertEquals(2,after.getQuantity());assertEquals(76_000_000,after.getTotalCost());
            assertEquals(effects,store.positionEventCount());assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
        }
        try(var store=new SqliteStore(path)) {
            var account=f.capture(store,24_000_000);var risk=risk(store,account,f.market(null));
            assertEquals(window,risk.getWindowId());assertEquals(effects,store.positionEventCount());
            assertEquals(Long.valueOf(15_000_000),risk.getRemainingLossBudget());assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
    @Test public void unseenUnitsStillChargeTheirExactCostAndCannotBeReleasedByAQuote()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("unseen.db"))) {
            f.capture(store,100_000_000);f.buy(store,"known-purchase",2,38_000_000);
            var account=f.capture(store,24_000_000).withObservedHoldings(Map.of(561,1));
            var risk=risk(store,account,f.market(null));
            assertEquals(Long.valueOf(76_000_000),risk.getUnpricedManagedCostReserve());
            assertEquals(Long.valueOf(38_000_000),risk.getUncertainLossReserve());assertEquals(38_000_000,risk.getValuationUncertaintyExcluded());
            assertTrue(risk.getRemainingLossBudget()<0);
            var fresh=risk(store,account,f.market(38_000_000));assertEquals(Long.valueOf(38_000_000),fresh.getUncertainLossReserve());
            assertEquals(0,fresh.getValuationUncertaintyExcluded());assertTrue(fresh.getRemainingLossBudget()<0);
        }
    }
    @Test public void confirmedLossAndMalformedOrStaleCurrentEvidenceStillRefuse()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("loss.db"))) {
            f.capture(store,100_000_000);f.buy(store,"loser",1,20_000_000);f.sell(store,"sold-loss",1,4_000_000,4_000_000);
            f.buy(store,"quiet-stock",1,38_000_000);var account=f.capture(store,45_920_000);
            var risk=risk(store,account,f.market(null));assertTrue(risk.isAvailable());assertTrue(risk.getRemainingLossBudget()<0);
            assertEquals(Long.valueOf(-16_080_000),risk.getBoundedRealizedProfitLower());
            var bad=f.market(38_000_000);bad.latest.getAsJsonObject("561").addProperty("lowTime",f.at+1);
            assertFalse(risk(store,account,bad).isAvailable());
            var stale=new MarketIngestionService.MarketState(bad.mapping,bad.latest,bad.fiveMinute,bad.hourly,f.at-121);
            assertFalse(risk(store,account,stale).isAvailable());
            var broken=account.withOfferReadSet(new java.util.HashMap<Integer,String>(){{for(int i=0;i<8;i++)put(i,"missing-"+i);}});
            assertFalse(risk(store,broken,f.market(null)).isAvailable());
        }
    }
    @Test public void finalAuthorityAllowsOnlyUnrelatedEligibleCashFundedOrders()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("entry.db"))) {
            f.capture(store,100_000_000);f.buy(store,"known-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            var market=f.market(null);var mapping=new HashMap<>(market.mapping);mapping.put(562,new MarketIngestionService.Item(562,"Chaos rune",10000));
            market=new MarketIngestionService.MarketState(mapping,market.latest,market.fiveMinute,market.hourly,f.at);
            try(var service=new CompanionService(f.gson,store)) {
                var helper=new BoundedPlanningStateTest();helper.set(service,"account",account);helper.set(helper.get(service,"market"),"state",market);
                for(int item:new int[]{562,561}) {
                    var candidate=new PortfolioCandidate(item,"Fixture","",100,100,100,120,120,120,10,10000,200,1,1,.95,.95,.4,.4,1.5,f.at+90);
                    helper.set(service,"plan",new PortfolioPlan("verified-cash-"+item,f.at,f.at+90,"READY","fixture",100,List.of(new PortfolioAllocation(1,candidate,"BUY"))));
                    var decision=service.entryDecision("verified-cash-"+item,1,f.at);
                    if(item==562)assertEquals(decision.getReasonCode(),OfferLifecycleAction.PLACE_BUY,decision.getAction());
                    else assertNotEquals("Unpriced held stock is paused even if a cached plan proposes it",OfferLifecycleAction.PLACE_BUY,decision.getAction());
                }
            }
            assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));assertEquals(2,store.ledgerSnapshot(f.account).getTradingPositions().get(0).getQuantity());
        }
    }
}
