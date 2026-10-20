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
            assertEquals(Long.valueOf(132),result.portfolioValue);assertEquals(Long.valueOf(9),result.unrealizedProfit);assertEquals(Long.valueOf(0),result.unpricedCost);
            assertEquals(f.at-3600,result.stockValuations.get(0).quotedAt);assertEquals(3,result.stockValuations.get(0).pricedQuantity);
            assertEquals(Long.valueOf(123),f.evaluate(store,account,null).getUnpricedManagedCostReserve());
            assertEquals(effects,store.positionEventCount());assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
    @Test public void partialPhysicalEvidenceKeepsKnownFiguresAndAnExactUnpricedRemainderWithoutPersonalExtras()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("partial.db"))) {
            f.capture(store,100_000_000);f.buy(store,"purchase",3,41);
            var account=f.capture(store,99_999_877).withObservedHoldings(Map.of(561,2));var result=report(store,account,f.market(44));
            assertEquals(Long.valueOf(88),result.portfolioValue);assertEquals(Long.valueOf(6),result.unrealizedProfit);assertEquals(Long.valueOf(41),result.unpricedCost);
            assertEquals(3,result.stockValuations.get(0).quantity);assertEquals(2,result.stockValuations.get(0).observedQuantity);
            result=report(store,account.withObservedHoldings(Map.of(561,6003)),f.market(44));
            assertEquals(3,result.stockValuations.get(0).quantity);assertEquals(3,result.stockValuations.get(0).pricedQuantity);assertEquals(Long.valueOf(0),result.unpricedCost);
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
