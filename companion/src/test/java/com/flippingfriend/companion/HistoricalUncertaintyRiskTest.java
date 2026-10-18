package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Historical incompleteness must not be charged again as newly lost account capital. */
public class HistoricalUncertaintyRiskTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final DurableRiskWindowTest f=new DurableRiskWindowTest();
    @Before public void fixture(){f.folder=folder;}
    private void gap(SqliteStore s,String identity,int size,int price)throws Exception {
        f.accept(s,f.event(0,"BUYING",identity,size,0,0,price));f.clear(s);
    }
    @Test public void anUnpricedSaleOfAlreadyQuarantinedHistoryIsNotANewManagedLoss()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("old-stock.db"))) {
            f.accept(s,f.event(0,"BUYING","old",10,0,0,1000000));
            f.accept(s,f.event(0,"BUYING","old",10,4,4000000,1000000));f.clear(s);
            f.capture(s,100000000); // Old uncertain stock was excluded from the proven starting capital.
            f.sell(s,"old-stock-sale",2,1000000,2000000);
            var a=f.capture(s,101960000);var risk=f.evaluate(s,a,1000000);
            assertTrue(risk.getReason(),risk.isAvailable());
            assertEquals("Unknown old profit stays unknown, rather than becoming a new loss",Long.valueOf(0),risk.getUncertainLossReserve());
            assertEquals(0,risk.getUnresolvedManagedSaleUpdates());assertEquals(1,risk.getUntrackedUnpricedSaleUpdates());
            assertEquals(1,s.ledgerSnapshot(f.account).getUnresolvedSales());
            assertTrue(s.ledgerSnapshot(f.account).getTradingPositions().isEmpty());
            assertFalse(s.ledgerSnapshot(f.account).getMissedFlips().isEmpty());
        }
    }
    @Test public void aSmallNewGapDoesNotReserveTheItemsEntireLifetimeTurnover()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("turnover.db"))) {
            f.buy(s,"old-buy",100,1000000);f.sell(s,"old-sale",100,1000000,100000000);
            gap(s,"old-gap",1,100);f.capture(s,100000000);
            gap(s,"new-gap",10,10);var a=f.capture(s,99999900);
            var read=s.planningProjections(f.account);var exposure=DurablePlanningExposure.forAccount(read,a,f.at);
            assertNotNull(exposure);
            assertTrue("The historical capital envelope remains intact",exposure.committedByItem.get(561)>=100000000);
            assertTrue(exposure.unavailableEntryItems.contains(561));
            var risk=f.evaluate(s,a,10);assertTrue(risk.getReason(),risk.isAvailable());
            assertEquals(Long.valueOf(100),risk.getHistoricalUncertaintyReserve());
            assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());
            assertTrue("Unrelated entries retain a positive loss allowance",risk.getRemainingLossBudget()>0);
            assertEquals(1,s.ledgerSnapshot(f.account).getSales());
            assertEquals(0,s.ledgerSnapshot(f.account).getUnresolvedSales());
            // Exercise the actual final service authority with fresh canonical proof and an
            // independently selected unrelated candidate. No historical row is repaired.
            try(var service=new CompanionService(f.gson,s)) {
                var fixture=new BoundedPlanningStateTest();
                fixture.set(service,"account",a);
                var market=(MarketIngestionService)fixture.get(service,"market");
                fixture.set(market,"state",new MarketIngestionService.MarketState(
                    Map.of(562,new MarketIngestionService.Item(562,"Item 562",10000)),
                    new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),f.at));
                var candidate=new PortfolioCandidate(562,"Item 562","",100,100,100,120,120,120,
                    10,10000,200,1,1,.95,.95,.4,.4,1.5,f.at+90);
                fixture.set(service,"plan",new PortfolioPlan("history-tolerant",f.at,f.at+90,"READY","fixture",100,
                    List.of(new PortfolioAllocation(1,candidate,"BUY"))));
                assertEquals(OfferLifecycleAction.PLACE_BUY,service.entryDecision("history-tolerant",1,f.at).getAction());
            }
        }
    }
    @Test public void repeatedCountersDoNotMultiplyNewFundingButAmbiguousNewEpisodesDo()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("episodes.db"))) {
            f.capture(s,100000000);
            f.accept(s,f.event(0,"BUYING","new",10,0,0,100));
            f.accept(s,f.event(0,"BUYING","new",10,1,100,100));
            f.accept(s,f.event(0,"BUYING","new",10,2,200,100));f.clear(s);
            var a=f.capture(s,99999000);
            assertEquals(Long.valueOf(1000),f.evaluate(s,a,100).getHistoricalUncertaintyReserve());
            gap(s,"later",10,100);a=f.capture(s,99998000);
            assertEquals(Long.valueOf(2000),f.evaluate(s,a,100).getHistoricalUncertaintyReserve());
        }
    }
    @Test public void missingOrForeignAnchorSourcesCannotReleaseAReserve()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("anchor.db"))) {
            f.capture(s,100000000);gap(s,"new",10,100);var a=f.capture(s,99999000);
            var read=s.planningProjections(f.account);
            assertEquals(Long.valueOf(1000),ProspectiveGapExposure.calculate(read).get(561));
            var history=new ArrayList<>(read.history);
            String missingSource=read.riskEvidence.window.sourceForSlot(7);
            history.removeIf(e->e.getEventId().equals(missingSource));
            read.history=history;assertNull(ProspectiveGapExposure.calculate(read));
            var risk=DurableRisk.evaluate(read,a,DurablePlanningExposure.forAccount(s.planningProjections(f.account),a,f.at),f.market(100),f.at);
            assertFalse(risk.isAvailable());assertNull(risk.getRemainingLossBudget());
            read=s.planningProjections(f.account);history=new ArrayList<>(read.history);
            history.set(0,history.get(0).withAccountId(AccountIdentity.fromProfileKey("foreign")));
            read.history=history;assertNull(ProspectiveGapExposure.calculate(read));
        }
    }
    @Test public void newPurchasesOfAnOldPausedItemStillReserveTheirFullKnownCost()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("new-cost.db"))) {
            gap(s,"old-gap",10,100);f.capture(s,100000000);
            f.buy(s,"new-capital",2,1000000);var a=f.capture(s,98000000);
            var risk=f.evaluate(s,a,1000000);assertTrue(risk.getReason(),risk.isAvailable());
            assertEquals(Long.valueOf(2000000),risk.getHistoricalUncertaintyReserve());
            f.sell(s,"new-unpriced",2,1000000,2000000);a=f.capture(s,99960000);
            risk=f.evaluate(s,a,1000000);
            assertEquals("An unpriced sale cannot erase retained uncertainty",Long.valueOf(2000000),risk.getHistoricalUncertaintyReserve());
            assertEquals(Long.valueOf(0),risk.getBoundedRealizedProfitLower());
        }
    }
    @Test public void aLargeHistoricalGapAndItsOriginalAnchorSurviveRestartWithoutBecomingConfirmedLoss()throws Exception {
        Path db=folder.newFolder().toPath().resolve("large-gap.db");String window;
        try(var s=new SqliteStore(db)) {
            var a=f.capture(s,100000000);window=f.evaluate(s,a,100).getWindowId();
            gap(s,"new-large",20,1000000);a=f.capture(s,80000000);
            var risk=f.evaluate(s,a,1000000);assertEquals(Long.valueOf(20000000),risk.getHistoricalUncertaintyReserve());
            assertTrue(risk.getRemainingLossBudget()>0);assertEquals(Long.valueOf(0),risk.getBoundedRealizedProfitLower());
        }
        try(var s=new SqliteStore(db)) {
            var a=f.capture(s,80000000);var risk=f.evaluate(s,a,1000000);
            assertEquals(window,risk.getWindowId());assertEquals(Long.valueOf(20000000),risk.getHistoricalUncertaintyReserve());
            assertTrue(risk.getRemainingLossBudget()>0);
        }
    }
}
