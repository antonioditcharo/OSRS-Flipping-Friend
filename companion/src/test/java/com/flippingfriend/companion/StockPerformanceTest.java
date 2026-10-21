package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StockPerformanceTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final DurableRiskWindowTest f=new DurableRiskWindowTest();
    @Before public void setup(){f.folder=folder;}
    PerformanceSnapshot report(SqliteStore store,AccountSnapshot account,MarketIngestionService.MarketState market)throws Exception {
        var report=store.performance(f.account,"ALL",f.at);
        StockPerformance.apply(report,store.planningProjections(f.account),account,market,f.at);assertTrue(report.isValid());return report;
    }
    @Test public void aQuietLastQuoteReportsItsActualAfterTaxEstimateWithoutPretendingToBeCurrentRisk()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("quiet.db"))) {
            f.capture(store,100_000_000);f.buy(store,"purchase",3,41);var account=f.capture(store,99_999_877);
            var market=f.market(44);market.latest.getAsJsonObject("561").addProperty("lowTime",f.at-3600);
            int effects=store.positionEventCount();var result=report(store,account,market);
            assertEquals(Long.valueOf(99_999_877),result.spendableCoins);assertEquals(Long.valueOf(123),result.managedAcquisitionCost);
            assertEquals(Long.valueOf(132),result.portfolioValue);assertEquals(Long.valueOf(9),result.instantSaleProfit);assertEquals(Long.valueOf(0),result.unpricedCost);
            assertEquals(Long.valueOf(0),result.unrealizedProfit);assertEquals(Long.valueOf(123),result.unplannedCost);
            assertEquals(f.at-3600,result.stockValuations.get(0).quotedAt);assertEquals(3,result.stockValuations.get(0).pricedQuantity);
            assertEquals(Long.valueOf(123),f.evaluate(store,account,null).getUnpricedManagedCostReserve());
            assertEquals(effects,store.positionEventCount());assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
    @Test public void partialPhysicalEvidenceKeepsKnownFiguresAndAnExactUnpricedRemainderWithoutPersonalExtras()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("partial.db"))) {
            f.capture(store,100_000_000);f.buy(store,"purchase",3,41);
            var account=f.capture(store,99_999_877).withObservedHoldings(Map.of(561,2));var result=report(store,account,f.market(44));
            assertEquals(Long.valueOf(88),result.portfolioValue);assertEquals(Long.valueOf(6),result.instantSaleProfit);assertEquals(Long.valueOf(41),result.unpricedCost);
            assertEquals(3,result.stockValuations.get(0).quantity);assertEquals(2,result.stockValuations.get(0).observedQuantity);
            result=report(store,account.withObservedHoldings(Map.of(561,6003)),f.market(44));
            assertEquals(3,result.stockValuations.get(0).quantity);assertEquals(3,result.stockValuations.get(0).pricedQuantity);assertEquals(Long.valueOf(0),result.unpricedCost);
        }
    }
    void plannedBuy(SqliteStore store)throws Exception {
        var raw=OfferEvent.builder("fixture",f.at,"BUYING").accountId(f.account).authenticatedObservation(true)
            .eventIdentity("planned-"+(++f.sequence),f.session,"planned-buy").sequence(f.sequence).slot(0)
            .item(561,"Nature rune").buying(true).price(41).quantities(3,0).firstSeenAt(f.at)
            .recommendation("original",41,3,0,10,.75).predictionEvidence(null,f.at-1,false)
            .exitContext(new AcquisitionExitContext(70,30,120)).build();
        var original=raw.withBoundPrediction(raw,true);f.accept(store,original);
        f.accept(store,f.event(0,"BOUGHT","planned-buy",3,3,122,41).withBoundPrediction(original,true));f.clear(store);
    }
    AccountSnapshot listedCapture(SqliteStore store,int listed,int price,int physical)throws Exception {
        Map<Integer,String> receipt=new HashMap<>();
        for(int slot=0;slot<8;slot++) {
            var e=slot==0?f.event(0,"SELLING","current-sale-"+f.sequence,listed,0,0,price):f.event(slot,"EMPTY",null,0,0,0,0);
            f.accept(store,e);receipt.put(slot,e.getEventId());
        }
        var account=new AccountSnapshot("current-"+f.sequence,f.at,99999878,0,7,8,true,false,0).withAccountId(f.account)
            .withAutomaticTradeScope().withOfferReadSet(receipt).withObservedHoldings(Map.of(561,physical));
        store.recordAndReconcileAccount(account,PositionStateView.from(account),f.gson.toJson(account));return account;
    }
    @Test public void expectedExitProfitCanBePositiveWhileInstantSaleIsNegativeAndNeitherChangesRisk()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("targets.db"))) {
            f.capture(store,100000000);plannedBuy(store);var account=f.capture(store,99999878);
            var before=f.evaluate(store,account,40);int effects=store.positionEventCount();
            var result=report(store,account,f.market(40));
            assertEquals(Long.valueOf(-2),result.instantSaleProfit);assertEquals(Long.valueOf(85),result.unrealizedProfit);
            assertEquals(Long.valueOf(207),result.intendedExitValue);assertEquals(Long.valueOf(0),result.unplannedCost);
            assertEquals("Original exit target",result.stockValuations.get(0).exitQuotes.get(0).source);
            assertEquals(before.getDrawdownUpper(),f.evaluate(store,account,40).getDrawdownUpper());assertEquals(effects,store.positionEventCount());
        }
    }
    @Test public void mixedOriginalPlansRemainVisibleWithRangesWithoutChangingAccounting()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("mixed-plans.db"))) {
            f.capture(store,100000000);plannedBuy(store);
            var raw=OfferEvent.builder("fixture",f.at,"BUYING").accountId(f.account).authenticatedObservation(true)
                .eventIdentity("second-"+(++f.sequence),f.session,"second-buy").sequence(f.sequence).slot(0)
                .item(561,"Nature rune").buying(true).price(50).quantities(2,0).firstSeenAt(f.at)
                .recommendation("second-plan",50,2,0,10,.75).predictionEvidence(null,f.at-1,false)
                .exitContext(new AcquisitionExitContext(80,35,90)).build();
            var origin=raw.withBoundPrediction(raw,true);f.accept(store,origin);
            f.accept(store,f.event(0,"BOUGHT","second-buy",2,2,100,50).withBoundPrediction(origin,true));f.clear(store);
            var account=f.capture(store,99999778).withObservedHoldings(Map.of(561,5));int effects=store.positionEventCount();
            var row=report(store,account,f.market(40)).stockValuations.get(0);
            assertEquals(222,row.totalCost);assertEquals(2,row.exitContexts.size());
            assertEquals(Set.of(70,80),new HashSet<>(Arrays.asList(row.exitContexts.get(0).getContext().getTargetSellPrice(),row.exitContexts.get(1).getContext().getTargetSellPrice())));
            assertEquals(80,row.exitQuotes.get(0).price);assertTrue(row.exitQuotes.get(0).source.contains("allocation unknown"));
            assertEquals(effects,store.positionEventCount());
            f.accept(store,f.event(0,"SOLD","partial-disposal",2,2,160,80));f.clear(store);
            account=f.capture(store,99999938).withObservedHoldings(Map.of(561,3));row=report(store,account,f.market(40)).stockValuations.get(0);
            assertEquals(134,row.totalCost);assertEquals(3,row.quantity);assertEquals(2,row.exitContexts.size());
            assertEquals(1,row.exitContexts.get(0).getMinimumRemaining());assertEquals(3,row.exitContexts.get(0).getMaximumRemaining());
            assertEquals(0,row.exitContexts.get(1).getMinimumRemaining());assertEquals(2,row.exitContexts.get(1).getMaximumRemaining());
        }
    }
    @Test public void currentListingOverridesOnlyItsUnsoldQuantityAndSubsetCostConservesEveryCoin()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("listing.db"))) {
            f.capture(store,100000000);plannedBuy(store);var account=listedCapture(store,1,80,1);
            var result=report(store,account,f.market(40));
            assertEquals(2,result.stockValuations.get(0).observedQuantity);
            assertEquals(Long.valueOf(148),result.intendedExitValue);assertEquals(Long.valueOf(67),result.unrealizedProfit);
            assertEquals(Long.valueOf(41),result.unplannedCost);assertEquals(2,result.stockValuations.get(0).exitQuotes.size());
            assertEquals("Current sell offer",result.stockValuations.get(0).exitQuotes.get(0).source);
            // Personal extras never enlarge the three acquired units or the expected profit.
            result=report(store,account.withObservedHoldings(Map.of(561,1000)),f.market(40));
            assertEquals(Long.valueOf(95),result.unrealizedProfit);assertEquals(3,result.stockValuations.get(0).observedQuantity);
            assertTrue(result.isValid());result.unrealizedProfit++;assertFalse(result.isValid());
        }
    }
    @Test public void unknownOriginalTargetUsesOnlyActualListingsEvenWithoutAnInstantQuote()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("unknown-target.db"))) {
            f.capture(store,100000000);f.buy(store,"legacy",3,41);var account=listedCapture(store,1,80,2);
            var result=report(store,account,null);
            assertEquals(Long.valueOf(38),result.unrealizedProfit);assertEquals(Long.valueOf(82),result.unplannedCost);
            assertEquals(Long.valueOf(123),result.unpricedCost);assertEquals(Long.valueOf(0),result.instantSaleProfit);
            assertEquals(1,result.stockValuations.get(0).exitQuotes.size());assertEquals(0,result.stockValuations.get(0).pricedQuantity);
        }
    }
    @Test public void changedSellListingRefreshesExpectedProfitAndNeverClampsARealPlannedLoss()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("relisted.db"))) {
            f.capture(store,100000000);plannedBuy(store);var account=listedCapture(store,3,80,0);
            assertEquals(Long.valueOf(115),report(store,account,f.market(40)).unrealizedProfit);
            var previous=store.planningProjections(f.account).currentEvents.get(0);
            f.accept(store,f.event(0,"CANCELLED_SELL",previous.getOfferIdentity(),3,0,0,80));
            f.clear(store);account=listedCapture(store,3,40,0);
            var result=report(store,account,f.market(40));assertEquals(Long.valueOf(-2),result.unrealizedProfit);
            assertEquals(Long.valueOf(0),result.unplannedCost);assertEquals(40,result.stockValuations.get(0).exitQuotes.get(0).price);
            assertEquals(1,store.positionEventCount());
        }
    }
    @Test public void missingFutureMalformedAndStaleFeedPricesNeverEraseKnownCashOrAcquisitionCost()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("missing.db"))) {
            f.capture(store,100_000_000);f.buy(store,"purchase",3,41);var account=f.capture(store,99_999_877);
            var future=f.market(44);future.latest.getAsJsonObject("561").addProperty("lowTime",f.at+1);
            var fractional=f.market(44);fractional.latest.getAsJsonObject("561").addProperty("low",44.5);
            var fresh=f.market(44);var stale=new MarketIngestionService.MarketState(fresh.mapping,fresh.latest,fresh.fiveMinute,fresh.hourly,f.at-121);
            for(var market:Arrays.asList(f.market(null),future,fractional,stale,null)) {
                var result=report(store,account,market);assertEquals(Long.valueOf(99_999_877),result.spendableCoins);
                assertEquals(Long.valueOf(123),result.managedAcquisitionCost);assertEquals(Long.valueOf(123),result.unpricedCost);
                assertEquals(0,result.stockValuations.get(0).pricedQuantity);assertEquals(3,result.stockValuations.get(0).quantity);
            }
        }
    }
}
