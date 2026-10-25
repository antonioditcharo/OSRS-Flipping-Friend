package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** The owner's current-account policy retains history without making it a permanent buy switch. */
public class CurrentAccountRiskPolicyTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final DurableRiskWindowTest f=new DurableRiskWindowTest();
    @Before public void setup(){f.folder=folder;}
    @Test public void unchangedFrameGapRetainsItemPauseButUnrelatedSaleAndFinalEntryRemainAvailable()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("overtaken.db"))) {
            var start=f.capture(store,100000000);String window=f.evaluate(store,start,100).getWindowId();
            f.buy(store,"proven-buy",100,100);
            for(int phase=0;phase<5;phase++) {
                int filled=phase==0?0:phase==4?5866:1448;String type=phase==4?"BOUGHT":"BUYING";
                var source=OfferEvent.builder("fixture",phase==2?f.at-1:f.at,type).accountId(f.account).authenticatedObservation(true)
                    .eventIdentity("manta-"+phase,f.session,"manta").slot(7).sequence(++f.sequence)
                    .item(389,"Raw manta ray").buying(true).price(910).quantities(5866,filled).spent(filled*910L).build();
                f.accept(store,source);
            }
            f.accept(store,f.event(7,"EMPTY",null,0,0,0,0));
            f.sell(store,"proven-sale",100,120,12000);var account=captureWithMantaSell(store,0,5866);
            var read=store.planningProjections(f.account);assertFalse(read.tradeScope.hasUnboundedReconciliation);
            assertTrue(read.tradeScope.unsafeItems.contains(389));assertEquals(Set.of(389),read.tradeScope.reconciliationScope.get("manta-2"));
            assertTrue(read.tradeScope.unchangedFrameItems.contains(389));
            var risk=f.evaluate(store,account,100);assertTrue(risk.getReason(),risk.isAvailable());assertEquals(window,risk.getWindowId());
            assertTrue("The active listing exception must retain the full prospective cost reserve",risk.getHistoricalUncertaintyReserve()>=5866L*910);
            assertTrue(risk.getRemainingLossBudget()>0);assertEquals(0,store.ledgerSnapshot(f.account).getUnresolvedSales());
            assertTrue(store.ledgerSnapshot(f.account).getMissedFlips().stream().anyMatch(e->e.getEventId().equals("manta-2")));
            try(var service=new CompanionService(f.gson,store)) {
                var helper=new BoundedPlanningStateTest();helper.set(service,"account",account);
                var market=(MarketIngestionService)helper.get(service,"market");
                helper.set(market,"state",new MarketIngestionService.MarketState(Map.of(562,new MarketIngestionService.Item(562,"Item 562",10000)),
                    new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),f.at));
                var candidate=new PortfolioCandidate(562,"Item 562","",100,100,100,120,120,120,10,10000,200,1,1,.95,.95,.4,.4,1.5,f.at+90);
                helper.set(service,"plan",new PortfolioPlan("after-frame-gap",f.at,f.at+90,"READY","fixture",100,List.of(new PortfolioAllocation(1,candidate,"BUY"))));
                assertEquals(OfferLifecycleAction.PLACE_BUY,service.entryDecision("after-frame-gap",1,f.at).getAction());
            }
            // The no-delta gap cannot turn a later fully observed sale into a zero-cost result.
            account=captureWithMantaSell(store,100,5866);
            assertEquals(0,store.ledgerSnapshot(f.account).getUnresolvedSales());
            var partial=store.ledgerSnapshot(f.account).getRecentSales().get(0);assertEquals(91000,partial.getAppliedCost());
            assertEquals(Long.valueOf(3400),partial.getProfitLower());
            // An active buy or an excessive sell still has unknown live exposure and blocks entries.
            var readAfter=store.planningProjections(f.account);assertTrue(UnchangedFrameRecovery.boundedSell(readAfter,readAfter.offers.stream().filter(o->o.getSlot()==7).findFirst().get()));
            var changed=captureWithMantaSell(store,100,5867);assertFalse(f.evaluate(store,changed,100).isAvailable());
        }
    }
    AccountSnapshot captureWithMantaSell(SqliteStore store,int filled,int total)throws Exception {
        Map<Integer,String> receipt=new HashMap<>();
        for(int slot=0;slot<8;slot++) {
            var e=slot==7?OfferEvent.builder("fixture",f.at,"SELLING").accountId(f.account).authenticatedObservation(true)
                .eventIdentity("manta-sell-"+(++f.sequence),f.session,"manta-sale-"+total).slot(7).sequence(f.sequence)
                .item(389,"Raw manta ray").price(963).quantities(total,filled).spent(filled*963L).build()
                :f.event(slot,"EMPTY",null,0,0,0,0);
            f.accept(store,e);receipt.put(slot,e.getEventId());
        }
        var account=new AccountSnapshot("manta-board-"+f.sequence,f.at,100000000,0,7,8,true,false,0)
            .withAccountId(f.account).withAutomaticTradeScope().withOfferReadSet(receipt).withObservedHoldings(Map.of());
        store.recordAndReconcileAccount(account,PositionStateView.from(account),f.gson.toJson(account));return account;
    }
    void gap(SqliteStore store)throws Exception {
        f.accept(store,f.event(0,"BUYING","incomplete",20,0,0,1000000));f.clear(store);
    }
    @Test public void historicalUncertaintyRemainsVisibleWhileUnrelatedFinalEntryCanPass()throws Exception {
        var path=folder.newFolder().toPath().resolve("history.db");String window;
        try(var store=new SqliteStore(path)) {
            f.capture(store,100000000);gap(store);var account=f.capture(store,80000000);
            int effects=store.positionEventCount();var ledger=f.gson.toJsonTree(store.ledgerSnapshot(f.account));
            var risk=f.evaluate(store,account,1000000);window=risk.getWindowId();
            assertTrue(risk.getReason(),risk.isAvailable());
            assertTrue("Uncertainty in a closed historical episode is not a confirmed account loss",risk.getRemainingLossBudget()>0);
            assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());
            assertEquals(20000000,f.gson.toJsonTree(risk).getAsJsonObject().get("historicalUncertaintyReserve").getAsLong());
            assertEquals(Long.valueOf(0),risk.getBoundedRealizedProfitLower());
            try(var service=new CompanionService(f.gson,store)) {
                var helper=new BoundedPlanningStateTest();helper.set(service,"account",account);
                var market=(MarketIngestionService)helper.get(service,"market");
                helper.set(market,"state",new MarketIngestionService.MarketState(
                    Map.of(562,new MarketIngestionService.Item(562,"Item 562",10000)),
                    new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),new com.google.gson.JsonObject(),f.at));
                var candidate=new PortfolioCandidate(562,"Item 562","",100,100,100,120,120,120,10,10000,200,
                    1,1,.95,.95,.4,.4,1.5,f.at+90);
                helper.set(service,"plan",new PortfolioPlan("current-policy",f.at,f.at+90,"READY","fixture",100,
                    List.of(new PortfolioAllocation(1,candidate,"BUY"))));
                assertEquals(OfferLifecycleAction.PLACE_BUY,service.entryDecision("current-policy",1,f.at).getAction());
            }
            assertEquals(effects,store.positionEventCount());
            var current=f.gson.toJsonTree(store.ledgerSnapshot(f.account));
            ledger.getAsJsonObject().remove("observedAt");current.getAsJsonObject().remove("observedAt");assertEquals(ledger,current);
            assertTrue(store.planningProjections(f.account).tradeScope.unsafeItems.contains(561));
        }
        try(var store=new SqliteStore(path)) {
            var account=f.capture(store,80000000);var risk=f.evaluate(store,account,1000000);
            assertEquals(window,risk.getWindowId());assertTrue(risk.getRemainingLossBudget()>0);
        }
    }
    @Test public void aLiveOfferInPausedStockStillWithholdsEntry()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("live.db"))) {
            f.capture(store,100000000);gap(store);f.capture(store,80000000);
            Map<Integer,String> receipt=new HashMap<>();
            for(int slot=0;slot<8;slot++) {
                var e=slot==0?f.event(0,"BUYING","new-live",10,0,0,1000):f.event(slot,"EMPTY",null,0,0,0,0);
                f.accept(store,e);receipt.put(slot,e.getEventId());
            }
            var account=new AccountSnapshot("active",f.at,79990000,10000,7,8,true,false,0)
                .withAccountId(f.account).withAutomaticTradeScope().withOfferReadSet(receipt);
            store.recordAndReconcileAccount(account,PositionStateView.from(account),f.gson.toJson(account));
            assertFalse("Historical tolerance cannot authorize unresolved live offers",f.evaluate(store,account,1000).isAvailable());
        }
    }
    @Test public void missingOrStaleManagedPricesReserveFullCostWithoutFreezingAvailableCash()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("unpriced.db"))) {
            f.capture(store,100000000);f.buy(store,"stock",5,1000000);var account=f.capture(store,95000000);
            var risk=f.evaluate(store,account,null);
            assertTrue("Zero terminal value safely bounds missing prices: "+risk.getReason(),risk.isAvailable());
            assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());assertEquals(5000000,risk.getValuationUncertaintyExcluded());
            assertEquals(Long.valueOf(0),risk.getDrawdownUpper());assertEquals(Long.valueOf(15000000),risk.getRemainingLossBudget());
            var read=store.planningProjections(f.account);var market=f.market(1000000);
            market.latest.getAsJsonObject("561").addProperty("lowTime",f.at-121);
            risk=DurableRisk.evaluate(read,account,DurablePlanningExposure.forAccount(read,account,f.at),market,f.at);
            assertTrue(risk.isAvailable());assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());assertEquals(5000000,risk.getValuationUncertaintyExcluded());
            assertEquals(1,store.ledgerSnapshot(f.account).getTradingPositions().size());
        }
    }
    @Test public void physicalCountsNeverEnrollPersonalUnitsAndUnseenCostHasZeroTerminalValue()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("physical.db"))) {
            f.capture(store,100000000);f.buy(store,"stock",2,41);var account=f.capture(store,99999918);
            var absent=account.withObservedHoldings(Map.of());
            var risk=f.evaluate(store,absent,1000);
            assertEquals(Long.valueOf(82),risk.getUncertainLossReserve());assertEquals(Long.valueOf(0),risk.getMarkedUnrealizedProfit());
            var partial=account.withObservedHoldings(Map.of(561,1));risk=f.evaluate(store,partial,42);
            assertEquals(Long.valueOf(41),risk.getUncertainLossReserve());assertEquals(Long.valueOf(1),risk.getMarkedUnrealizedProfit());
            var personal=account.withObservedHoldings(Map.of(561,6002));risk=f.evaluate(store,personal,42);
            assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());assertEquals(Long.valueOf(2),risk.getMarkedUnrealizedProfit());
            assertEquals("6,000 personal units never widen the tracked position",2,store.ledgerSnapshot(f.account).getTradingPositions().get(0).getQuantity());
            var forged=f.gson.toJsonTree(account).getAsJsonObject();
            forged.add("observedHoldings",f.gson.toJsonTree(Map.of(561,-1)));
            var invalid=f.gson.fromJson(forged,AccountSnapshot.class);
            assertNull(DurablePlanningExposure.forAccount(store.planningProjections(f.account),invalid,f.at));
        }
    }
    @Test public void staleQuotePauseNamesTheItemAndFreshQuotesRestoreAllowanceWithoutRepair()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("quote-recovery.db"))) {
            f.capture(store,100000000);f.buy(store,"large-stock",20,1000000);var account=f.capture(store,80000000);
            var read=store.planningProjections(f.account);var exposure=DurablePlanningExposure.forAccount(read,account,f.at);
            var market=f.market(1000000);market.latest.getAsJsonObject("561").addProperty("lowTime",f.at-121);
            int effects=store.positionEventCount();var stale=DurableRisk.evaluate(read,account,exposure,market,f.at);
            assertTrue(stale.isAvailable());assertTrue(stale.getRemainingLossBudget()>0);assertEquals(Set.of(561),stale.getValuationPausedItems());
            assertTrue(stale.getReason(),stale.getReason().contains("Nature rune"));
            assertTrue(stale.getReason(),stale.getReason().contains("121s"));
            assertTrue(stale.getReason(),stale.getReason().contains("120s"));
            var fresh=f.evaluate(store,account,1000000);
            assertTrue(fresh.getReason(),fresh.getRemainingLossBudget()>0);
            assertEquals(Long.valueOf(0),fresh.getUnpricedManagedCostReserve());
            assertEquals(stale.getWindowId(),fresh.getWindowId());assertEquals(effects,store.positionEventCount());
        }
    }
    @Test public void aFreshMarketRetriesAStockValuationRefusalInsidePlanSpacing()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("prompt-recovery.db"))) {
            f.capture(store,100000000);f.buy(store,"large-stock",20,1000000);var account=f.capture(store,80000000);
            var read=store.planningProjections(f.account);var old=f.market(1000000);
            old.latest.getAsJsonObject("561").addProperty("lowTime",f.at-121);
            var risk=DurableRisk.evaluate(read,account,DurablePlanningExposure.forAccount(read,account,f.at),old,f.at);
            // A retained V2 refusal is re-evaluated; old policy snapshots are not rewritten.
            risk=AccountRiskSnapshot.builder(f.account,risk.getWindowId(),risk.getSessionId(),risk.getWindowStartedAt(),
                risk.getLedgerCursor(),f.at,f.at).capital(100000000,100000000).uncertainty(20000000,0,0)
                .currentAccountPolicy(0,20000000).build();
            assertTrue(risk.getRemainingLossBudget()<0);
            try(var service=new CompanionService(f.gson,store)) {
                var helper=new BoundedPlanningStateTest();helper.set(service,"account",account);
                var market=(MarketIngestionService)helper.get(service,"market");
                var quotes=f.market(1000000);
                helper.set(market,"state",new MarketIngestionService.MarketState(quotes.mapping,quotes.latest,quotes.fiveMinute,quotes.hourly,f.at+1));
                long now=java.time.Instant.now().getEpochSecond();
                helper.set(service,"plan",PortfolioPlan.unavailable("stock-pause",PortfolioPlanOutcome.DRAWDOWN_LIMIT_REACHED,
                    risk.getReason(),now).withCanonicalRisk(risk));
                helper.set(service,"lastPlanAt",now);helper.set(service,"lastPlanFreeSlots",8);helper.set(service,"lastPlanSellOnly",false);
                service.refreshAccountPlan();
                ((java.util.concurrent.ExecutorService)helper.get(service,"planThread")).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
                assertNotEquals("New price evidence must not retain the older cached refusal",PortfolioPlanOutcome.DRAWDOWN_LIMIT_REACHED,service.plan().getOutcome());
            }
        }
    }
}
