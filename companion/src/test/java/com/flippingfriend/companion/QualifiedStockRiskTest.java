package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Quiet public ticks must not be confused with missing stock or a confirmed full-cost loss. */
public class QualifiedStockRiskTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final DurableRiskWindowTest f=new DurableRiskWindowTest();
    @Before public void setup(){f.folder=folder;}

    MarketIngestionService.MarketState supported() {
        var tree=f.gson.toJsonTree(f.market(37_000_000)).getAsJsonObject();
        tree.getAsJsonObject("mapping").add("562",f.gson.toJsonTree(new MarketIngestionService.Item(562,"Item 562",10000)));
        var quote=tree.getAsJsonObject("latest").getAsJsonObject("561");
        quote.addProperty("lowTime",f.at-300);quote.addProperty("high",38_000_000);quote.addProperty("highTime",f.at-200);
        long shortStart=(f.at/300-1)*300, hourStart=(f.at/3600-1)*3600;
        tree.addProperty("fiveMinuteStartedAt",shortStart);tree.addProperty("hourlyStartedAt",hourStart);
        tree.getAsJsonObject("fiveMinute").add("561",JsonParser.parseString("{\"avgLowPrice\":37000000,\"lowPriceVolume\":3}"));
        tree.getAsJsonObject("hourly").add("561",JsonParser.parseString("{\"avgLowPrice\":37500000,\"lowPriceVolume\":40}"));
        return f.gson.fromJson(tree,MarketIngestionService.MarketState.class);
    }
    AccountRiskSnapshot evaluate(SqliteStore store,AccountSnapshot account,MarketIngestionService.MarketState market)throws Exception {
        var read=store.planningProjections(f.account);
        return DurableRisk.evaluate(read,account,DurablePlanningExposure.forAccount(read,account,f.at),market,f.at);
    }
    @Test public void quietStockUsesQualifiedDiscountedEvidenceAndPreservesItsAcquisition()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("quiet.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            int effects=store.positionEventCount();var before=f.gson.toJsonTree(store.ledgerSnapshot(f.account));
            var risk=evaluate(store,account,supported());
            assertTrue(risk.getReason(),risk.isAvailable());
            assertTrue("Corroborated public trades with a 10% haircut must not become a 76M full-cost loss reserve: "+risk.getReason(),risk.getRemainingLossBudget()>0);
            assertEquals(Long.valueOf(0),risk.getUnpricedManagedCostReserve());
            assertEquals(Long.valueOf(-10_732_000),risk.getMarkedUnrealizedProfit());
            assertEquals(effects,store.positionEventCount());
            var after=f.gson.toJsonTree(store.ledgerSnapshot(f.account));
            before.getAsJsonObject().remove("observedAt");after.getAsJsonObject().remove("observedAt");assertEquals(before,after);
            assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
    @Test public void availableCashCanPassFinalEntryWithoutChangingTheRiskAnchor()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("entry.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            int effects=store.positionEventCount();String window=evaluate(store,account,supported()).getWindowId();
            try(var service=new CompanionService(f.gson,store)) {
                var helper=new BoundedPlanningStateTest();helper.set(service,"account",account);
                helper.set(helper.get(service,"market"),"state",supported());
                var candidate=new PortfolioCandidate(562,"Item 562","",100,100,100,120,120,120,10,10000,200,
                    1,1,.95,.95,.4,.4,1.5,f.at+90);
                helper.set(service,"plan",new PortfolioPlan("quiet-policy",f.at,f.at+90,"READY","fixture",100,
                    List.of(new PortfolioAllocation(1,candidate,"BUY"))));
                var decision=service.entryDecision("quiet-policy",1,f.at);
                assertEquals(decision.getReasonCode(),OfferLifecycleAction.PLACE_BUY,decision.getAction());
            }
            assertEquals(effects,store.positionEventCount());assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
            assertEquals(window,evaluate(store,account,supported()).getWindowId());
        }
    }
    @Test public void missingPhysicalStockStillReservesItsEntireExactCost()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("absent.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);
            var account=f.capture(store,24_000_000).withObservedHoldings(Map.of());
            var risk=evaluate(store,account,supported());
            assertEquals(Long.valueOf(76_000_000),risk.getUnpricedManagedCostReserve());
            assertEquals(Long.valueOf(0),risk.getMarkedUnrealizedProfit());assertTrue(risk.getRemainingLossBudget()<0);
        }
    }
    @Test public void insufficientVolumeLeavesTheUnsupportedRemainderFullyReserved()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("volume.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            var market=supported();market.hourly.getAsJsonObject("561").addProperty("lowPriceVolume",19);
            var risk=evaluate(store,account,market);
            assertEquals(Long.valueOf(38_000_000),risk.getUnpricedManagedCostReserve());
            assertEquals(Long.valueOf(-5_366_000),risk.getMarkedUnrealizedProfit());assertTrue(risk.getRemainingLossBudget()>0);
            assertEquals(38_000_000,risk.getValuationUncertaintyExcluded());assertEquals(Set.of(561),risk.getValuationPausedItems());
        }
    }
    @Test public void aFallingCurrentBidAndTaxStillExhaustTheLossAllowance()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("drop.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            var market=supported();market.latest.getAsJsonObject("561").addProperty("low",20_000_000);
            market.latest.getAsJsonObject("561").addProperty("high",21_000_000);
            var risk=evaluate(store,account,market);
            assertEquals(Long.valueOf(0),risk.getUnpricedManagedCostReserve());
            assertEquals(Long.valueOf(-40_720_000),risk.getMarkedUnrealizedProfit());assertTrue(risk.getRemainingLossBudget()<0);
        }
    }
    @Test public void quietAppreciationCannotOffsetConfirmedLosses()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("loss.db"))) {
            f.capture(store,100_000_000);f.buy(store,"losing-purchase",1,20_000_000);f.sell(store,"loss-sale",1,4_000_000,4_000_000);
            f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,7_920_000);
            var market=supported();market.latest.getAsJsonObject("561").addProperty("low",80_000_000);
            market.latest.getAsJsonObject("561").addProperty("high",81_000_000);
            market.fiveMinute.getAsJsonObject("561").addProperty("avgLowPrice",80_000_000);
            market.hourly.getAsJsonObject("561").addProperty("avgLowPrice",80_000_000);
            var risk=evaluate(store,account,market);
            assertEquals(Long.valueOf(0),risk.getMarkedUnrealizedProfit());
            assertEquals(Long.valueOf(-16_080_000),risk.getBoundedRealizedProfitLower());assertTrue(risk.getRemainingLossBudget()<0);
        }
    }
    @Test public void aRecentCompletedBarCannotMakeAnOldOrMissingBidCurrent()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("old.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            var market=supported();market.latest.getAsJsonObject("561").addProperty("lowTime",f.at-901);
            assertEquals(Long.valueOf(76_000_000),evaluate(store,account,market).getUnpricedManagedCostReserve());
            market.latest.getAsJsonObject("561").remove("low");
            assertEquals(Long.valueOf(76_000_000),evaluate(store,account,market).getUnpricedManagedCostReserve());
        }
    }
    @Test public void fractionalStringAndFutureBidEvidenceStillRefusesRisk()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("money.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            for(JsonElement bad:List.of(new JsonPrimitive("37000000"),new JsonPrimitive(37_000_000.5))) {
                var market=supported();market.latest.getAsJsonObject("561").add("low",bad);
                assertFalse(evaluate(store,account,market).isAvailable());
            }
            var market=supported();market.latest.getAsJsonObject("561").addProperty("lowTime",f.at+1);
            assertFalse(evaluate(store,account,market).isAvailable());
        }
    }
    @Test public void legacyFeedsWithNoPublicationClocksKeepTheFullReserve()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("legacy.db"))) {
            f.capture(store,100_000_000);f.buy(store,"large-purchase",2,38_000_000);var account=f.capture(store,24_000_000);
            var with=supported();var legacy=new MarketIngestionService.MarketState(with.mapping,with.latest,with.fiveMinute,with.hourly,with.observedAt);
            assertEquals(Long.valueOf(76_000_000),evaluate(store,account,legacy).getUnpricedManagedCostReserve());
        }
    }
}
