package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Prospective risk from actual fixture acceptance/accounting, not injected profit totals. */
public class DurableRiskWindowTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    final String account=AccountIdentity.fromProfileKey("risk-window");
    final long at=Instant.now().getEpochSecond()-5;
    long sequence;
    String session="session";
    OfferEvent event(int slot,String type,String offer,int quantity,int filled,long spent,int price) {
        var b=OfferEvent.builder("fixture",at,type).accountId(account).authenticatedObservation(true)
            .eventIdentity("event-"+(++sequence),session,offer).slot(slot).sequence(sequence);
        if(!"EMPTY".equals(type))b.item(561,"Nature rune").buying(type.equals("BUYING")||type.equals("BOUGHT"))
            .price(price).quantities(quantity,filled).spent(spent).firstSeenAt(at);
        return b.build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception{s.recordAndProjectOffer(e,gson.toJson(e),true);}
    void clear(SqliteStore s)throws Exception{accept(s,event(0,"EMPTY",null,0,0,0,0));}
    void buy(SqliteStore s,String id,int quantity,int price)throws Exception {
        accept(s,event(0,"BUYING",id,quantity,0,0,price));
        accept(s,event(0,"BOUGHT",id,quantity,quantity,(long)quantity*price,price));clear(s);
    }
    void sell(SqliteStore s,String id,int quantity,int ask,long gross)throws Exception {
        accept(s,event(0,"SELLING",id,quantity,0,0,ask));
        accept(s,event(0,"SOLD",id,quantity,quantity,gross,ask));clear(s);
    }
    AccountSnapshot capture(SqliteStore s,long cash)throws Exception {
        Map<Integer,String> receipt=new HashMap<>();
        for(int slot=0;slot<8;slot++){var e=event(slot,"EMPTY",null,0,0,0,0);accept(s,e);receipt.put(slot,e.getEventId());}
        var a=new AccountSnapshot("snapshot-"+sequence,at,cash,0,8,8,true,false,0).withAccountId(account)
            .withAutomaticTradeScope().withOfferReadSet(receipt).withObservedHoldings(Map.of(561,1000000));
        s.recordAndReconcileAccount(a,PositionStateView.from(a),gson.toJson(a));return a;
    }
    MarketIngestionService.MarketState market(Integer bid) {
        JsonObject latest=new JsonObject();
        if(bid!=null){JsonObject quote=new JsonObject();quote.addProperty("low",bid);quote.addProperty("lowTime",at);latest.add("561",quote);}
        return new MarketIngestionService.MarketState(Map.of(561,new MarketIngestionService.Item(561,"Nature rune",10000)),
            latest,new JsonObject(),new JsonObject(),at);
    }
    AccountRiskSnapshot evaluate(SqliteStore s,AccountSnapshot a,Integer bid)throws Exception {
        var read=s.planningProjections(account);var exposure=DurablePlanningExposure.forAccount(read,a,at);
        return DurableRisk.evaluate(read,a,exposure,market(bid),at);
    }
    @Test public void lossesAndWindowSurviveStoreRestartAndReceiptRotation()throws Exception {
        Path db=folder.newFolder().toPath().resolve("restart.db");String window;
        try(var s=new SqliteStore(db)) {
            var first=capture(s,100000000);window=evaluate(s,first,700).getWindowId();
            buy(s,"buy",10,1000);sell(s,"sell",10,900,9000);
            var a=capture(s,99998820);var risk=evaluate(s,a,700);
            assertTrue(risk.getReason(),risk.isAvailable());assertEquals(Long.valueOf(-1180),risk.getBoundedRealizedProfitLower());
            assertEquals(Long.valueOf(1180),risk.getDrawdownUpper());assertEquals(Long.valueOf(14998820),risk.getRemainingLossBudget());
            assertEquals(1,s.eventCount(RiskWindow.EVENT_TYPE));
        }
        try(var s=new SqliteStore(db)) {
            var a=capture(s,99998820);var risk=evaluate(s,a,700);
            assertTrue(risk.getReason(),risk.isAvailable());assertEquals(window,risk.getWindowId());
            assertEquals(Long.valueOf(1180),risk.getDrawdownUpper());assertEquals(1,s.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
    @Test public void openStockUsesKnownCostAndFreshTaxedMarkRatherThanJournalProfit()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("mark.db"))) {
            capture(s,100000000);buy(s,"buy",10,1000);var a=capture(s,99990000);
            var risk=evaluate(s,a,700);assertTrue(risk.getReason(),risk.isAvailable());
            assertEquals(Long.valueOf(100000000),risk.getCurrentCapital());
            assertEquals(Long.valueOf(-3140),risk.getMarkedUnrealizedProfit());assertEquals(Long.valueOf(3140),risk.getDrawdownUpper());
            var missing=evaluate(s,a,null);assertTrue(missing.isAvailable());
            assertEquals(Long.valueOf(10000),missing.getUncertainLossReserve());assertEquals(Long.valueOf(10000),missing.getDrawdownUpper());
        }
    }
    @Test public void mixedTaxBoundsStayRangesAndTheLowerProfitControlsRisk()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("range.db"))) {
            capture(s,100000000);buy(s,"buy-loss",2,100);sell(s,"sell-loss",2,50,100);
            buy(s,"buy-range",2,100);sell(s,"sell-range",2,100,299);var a=capture(s,99999992);
            var risk=evaluate(s,a,100);assertTrue(risk.getReason(),risk.isAvailable());
            assertEquals(Long.valueOf(-8),risk.getBoundedRealizedProfitLower());assertEquals(Long.valueOf(-7),risk.getBoundedRealizedProfitUpper());
            assertEquals(Long.valueOf(8),risk.getDrawdownUpper());assertEquals(2,risk.getBoundedSaleUpdates());
        }
    }
    @Test public void oldUnknownSalesAndNewUntrackedSalesDoNotBecomeTradingProfit()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("unknown.db"))) {
            sell(s,"old-personal",2,100,200);var a=capture(s,100000000);
            var first=evaluate(s,a,100);assertTrue(first.getReason(),first.isAvailable());assertEquals(Long.valueOf(0),first.getDrawdownUpper());
            assertEquals(0,first.getUntrackedUnpricedSaleUpdates());assertEquals(1,s.ledgerSnapshot(account).getUnresolvedSales());
            sell(s,"new-personal",2,100,200);a=capture(s,100000196);var next=evaluate(s,a,100);
            assertTrue(next.getReason(),next.isAvailable());assertEquals(1,next.getUntrackedUnpricedSaleUpdates());
            assertEquals(Long.valueOf(0),next.getBoundedRealizedProfitLower());assertEquals(Long.valueOf(0),next.getDrawdownUpper());
            assertEquals(2,s.ledgerSnapshot(account).getUnresolvedSales());
        }
    }
    @Test public void unresolvedSaleOfTrackedStockReservesCostWithoutInventingProfit()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("mixed-stock.db"))) {
            capture(s,100000000);buy(s,"buy",2,100);sell(s,"mixed-sale",3,100,300);
            var a=capture(s,100000094);var risk=evaluate(s,a,100);assertTrue(risk.getReason(),risk.isAvailable());
            assertEquals(1,risk.getUnresolvedManagedSaleUpdates());assertEquals(Long.valueOf(200),risk.getHistoricalUncertaintyReserve());
            assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());
            assertEquals(Long.valueOf(0),risk.getBoundedRealizedProfitLower());assertEquals(Long.valueOf(0),risk.getDrawdownUpper());
            assertEquals(1,s.ledgerSnapshot(account).getUnresolvedSales());
        }
    }
    @Test public void firstWindowAndAccountAcceptanceRollBackTogetherWithoutACachedPhantom()throws Exception {
        Path db=folder.newFolder().toPath().resolve("rollback.db");
        try(var s=new SqliteStore(db,stage->{if("RISK_WINDOW_RECORDED".equals(stage))throw new IllegalStateException("fixture marker failure");})) {
            try{capture(s,100000000);fail("marker failure missing");}catch(IllegalStateException expected){assertEquals("fixture marker failure",expected.getMessage());}
            assertEquals(0,s.eventCount(RiskWindow.EVENT_TYPE));assertEquals(0,s.eventCount("ACCOUNT_STATE"));
            assertNull(s.planningProjections(account).riskEvidence.window);
        }
        try(var s=new SqliteStore(db)) {
            var a=capture(s,100000000);assertTrue(evaluate(s,a,100).isAvailable());assertEquals(1,s.eventCount(RiskWindow.EVENT_TYPE));
            assertEquals(16,s.recentOfferEvents(at).size()); // two eight-slot captures, no risk metadata payload
        }
    }
    @Test public void retentionCannotExpireTheWindowOrFinancialReplayIdentities()throws Exception {
        Path db=folder.newFolder().toPath().resolve("retention.db");long old=at-40*86400L;String windowId;
        OfferEvent terminal;
        try(var s=new SqliteStore(db)) {
            Map<Integer,String> receipt=new HashMap<>();
            for(int slot=0;slot<8;slot++) {
                var e=OfferEvent.builder("fixture",old,"EMPTY").accountId(account).authenticatedObservation(true)
                    .eventIdentity("old-empty-"+slot,session,null).slot(slot).sequence(slot+1).build();
                accept(s,e);receipt.put(slot,e.getEventId());
            }
            var original=new AccountSnapshot("old-snapshot",old,100000000,0,8,8,true,false,0)
                .withAccountId(account).withAutomaticTradeScope().withOfferReadSet(receipt);
            var window=new RiskWindow(original,session,0,100000000);windowId=window.windowId;
            // Seed a valid window from forty days ago; no real clock or live file is changed.
            s.recordEvent(old,RiskWindow.correlation(session),RiskWindow.EVENT_TYPE,gson.toJson(window));
            s.recordEvent(old,"old-snapshot","ACCOUNT_STATE",gson.toJson(original));
            accept(s,OfferEvent.builder("fixture",old+1,"BUYING").accountId(account).eventIdentity("old-buy-open",session,"old-buy")
                .slot(0).item(561,"Nature rune").buying(true).price(100).quantities(1,0).sequence(9).build());
            accept(s,OfferEvent.builder("fixture",old+2,"BOUGHT").accountId(account).eventIdentity("old-buy-done",session,"old-buy")
                .slot(0).item(561,"Nature rune").buying(true).price(100).quantities(1,1).spent(100).sequence(10).build());
            accept(s,OfferEvent.builder("fixture",old+3,"EMPTY").accountId(account).eventIdentity("old-buy-clear",session,null).slot(0).sequence(11).build());
            accept(s,OfferEvent.builder("fixture",old+4,"SELLING").accountId(account).eventIdentity("old-sell-open",session,"old-sell")
                .slot(0).item(561,"Nature rune").price(50).quantities(1,0).sequence(12).build());
            terminal=OfferEvent.builder("fixture",old+5,"SOLD").accountId(account).eventIdentity("old-sell-done",session,"old-sell")
                .slot(0).item(561,"Nature rune").price(50).quantities(1,1).spent(50).sequence(13).build();accept(s,terminal);
            accept(s,OfferEvent.builder("fixture",old+6,"EMPTY").accountId(account).eventIdentity("old-sell-clear",session,null).slot(0).sequence(14).build());
            s.pruneEvents(30);
            assertEquals("A counted financial event must remain a duplicate after retention",SqliteStore.EventAcceptance.DUPLICATE,
                s.recordAndProjectOffer(terminal,gson.toJson(terminal),true).acceptance);
            assertEquals(1,s.eventCount(RiskWindow.EVENT_TYPE));assertEquals(0,s.eventCount("ACCOUNT_STATE"));
        }
        sequence=14;
        try(var s=new SqliteStore(db)) {
            var a=capture(s,99999949);var risk=evaluate(s,a,50);
            assertTrue(risk.getReason(),risk.isAvailable());assertEquals(windowId,risk.getWindowId());
            assertEquals(Long.valueOf(51),risk.getDrawdownUpper());assertEquals(1,s.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
    @Test public void plannerAndFinalApprovalIgnoreForgedClientRiskAndKeepLossesAfterRestart()throws Exception {
        Path db=folder.newFolder().toPath().resolve("authority.db");
        try(var s=new SqliteStore(db)) {
            capture(s,100000000);buy(s,"large-buy",1,20000000);sell(s,"loss",1,1,1);
        }
        try(var s=new SqliteStore(db);var service=new CompanionService(gson,s)) {
            var a=capture(s,80000001);
            var forged=AccountRiskSnapshot.builder(account,"invented",session,at,0,at,at)
                .capital(100000000,100000000).marked(50000000).build();
            a=a.withCanonicalRisk(forged);
            set(service,"account",a);set(get(service,"market"),"state",market(100));
            var candidate=new PortfolioCandidate(562,"Other item","",100,100,100,120,120,120,10,100000,200L,
                1,1,.95,.95,.4,.4,1.5,at+100);
            set(service,"plan",new PortfolioPlan("cached",at,at+90,"READY","fixture",100,
                List.of(new PortfolioAllocation(1,candidate,"BUY"))));
            assertEquals(PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED,
                service.entryDecision("cached",1,at).getAbstentionReason());
            var refresh=CompanionService.class.getDeclaredMethod("refreshPlan");refresh.setAccessible(true);refresh.invoke(service);
            var plan=service.plan();assertEquals(PortfolioPlanOutcome.DRAWDOWN_LIMIT_REACHED,plan.getOutcome());
            assertEquals(Long.valueOf(19999999),plan.getCanonicalRisk().getDrawdownUpper());
            assertNotEquals("invented",plan.getCanonicalRisk().getWindowId());
            assertTrue("A risk refusal remains a readable plan, not missing health",service.health().canReadPlan(Instant.now().getEpochSecond()));
        }
    }
    @Test public void marksRefuseFractionalStringFutureAndStalePricesAndExpireWithTheOldestQuote()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("quotes.db"))) {
            capture(s,100000000);buy(s,"buy",1,1000);var a=capture(s,99999000);
            var read=s.planningProjections(account);var exposure=DurablePlanningExposure.forAccount(read,a,at);
            for(JsonElement invalid:List.of(new JsonPrimitive(700.9),new JsonPrimitive("700"),new JsonPrimitive(Long.MAX_VALUE))) {
                var m=market(700);m.latest.getAsJsonObject("561").add("low",invalid);
                assertFalse(DurableRisk.evaluate(read,a,exposure,m,at).isAvailable());
            }
            for(long quoteAt:List.of(at+1)) {
                var m=market(700);m.latest.getAsJsonObject("561").addProperty("lowTime",quoteAt);
                assertFalse(DurableRisk.evaluate(read,a,exposure,m,at).isAvailable());
            }
            var m=market(700);m.latest.getAsJsonObject("561").addProperty("lowTime",at-119);
            assertEquals(Long.valueOf(at+1),DurableRisk.evaluate(read,a,exposure,m,at).getValidUntil());
            read.riskEvidence.unbounded=true;
            assertFalse(DurableRisk.evaluate(read,a,exposure,m,at).isAvailable());
        }
    }
    @Test public void newTradeGapReservesItsBoundWithoutReportingItAsRealizedLoss()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("gap.db"))) {
            capture(s,100000000);
            accept(s,event(0,"BUYING","ambiguous",10,0,0,100));
            accept(s,OfferEvent.builder("fixture",at,"EMPTY").accountId(account)
                .eventIdentity("gap-"+(++sequence),session,null).slot(0).sequence(sequence).build());
            var a=capture(s,99999000);var risk=evaluate(s,a,100);
            assertTrue(risk.getReason(),risk.isAvailable());assertEquals(Long.valueOf(0),risk.getUncertainLossReserve());
            assertEquals(Long.valueOf(1000),risk.getHistoricalUncertaintyReserve());
            assertEquals(Long.valueOf(0),risk.getBoundedRealizedProfitLower());assertEquals(Long.valueOf(0),risk.getDrawdownUpper());
            assertFalse(s.ledgerSnapshot(account).getMissedFlips().isEmpty());
        }
    }
    @Test public void noWindowIsInventedBeforePositiveCapitalAndDuplicateMetadataCannotResetIt()throws Exception {
        Path db=folder.newFolder().toPath().resolve("metadata.db");
        try(var s=new SqliteStore(db)) {
            var zero=capture(s,0);assertFalse(evaluate(s,zero,100).isAvailable());assertEquals(0,s.eventCount(RiskWindow.EVENT_TYPE));
            capture(s,100000000);var window=s.planningProjections(account).riskEvidence.window;
            s.recordEvent(at,RiskWindow.correlation(session),RiskWindow.EVENT_TYPE,gson.toJson(window));
        }
        try(var s=new SqliteStore(db)) {
            try{s.planningProjections(account);fail("Duplicate metadata must not choose/reset a risk window");}
            catch(IllegalStateException expected){assertEquals("Risk window provenance is inconsistent.",expected.getMessage());}
        }
    }
    private Object get(Object target,String field)throws Exception {
        var f=target.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(target);
    }
    @Test public void persistedWindowRequiresItsActualAuthenticatedSourceReceipt()throws Exception {
        Path db=folder.newFolder().toPath().resolve("source-proof.db");
        try(var s=new SqliteStore(db)) {
            Map<Integer,String> bogus=new HashMap<>();for(int slot=0;slot<8;slot++)bogus.put(slot,"missing-"+slot);
            var snapshot=new AccountSnapshot("bogus",at,100000000,0,8,8,true,false,0)
                .withAccountId(account).withAutomaticTradeScope().withOfferReadSet(bogus);
            var window=new RiskWindow(snapshot,session,0,100000000);
            s.recordEvent(at,RiskWindow.correlation(session),RiskWindow.EVENT_TYPE,gson.toJson(window));
            // Actual current events cannot substitute for the claimed historical receipt.
            try{capture(s,100000000);fail("Missing historical proof must not reset the window");}
            catch(IllegalStateException expected){assertEquals("Risk window source receipt is missing.",expected.getMessage());}
            assertEquals(0,s.eventCount("ACCOUNT_STATE"));assertEquals(1,s.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
    private void set(Object target,String field,Object value)throws Exception {
        var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);
    }
    @Test public void unknownWindowSchemaCannotBeLoadedAsAValidRiskAnchor()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("schema.db"))) {
            Map<Integer,String> receipt=new HashMap<>();
            for(int slot=0;slot<8;slot++){var e=event(slot,"EMPTY",null,0,0,0,0);accept(s,e);receipt.put(slot,e.getEventId());}
            var a=new AccountSnapshot("window",at,100000000,0,8,8,true,false,0)
                .withAccountId(account).withAutomaticTradeScope().withOfferReadSet(receipt);
            var wire=gson.toJsonTree(new RiskWindow(a,session,0,100000000)).getAsJsonObject();wire.addProperty("schemaVersion","2");
            s.recordEvent(at,RiskWindow.correlation(session),RiskWindow.EVENT_TYPE,gson.toJson(wire));
            try{capture(s,100000000);fail("Unknown window schema must be rejected");}
            catch(IllegalStateException expected){assertEquals("Risk window provenance is inconsistent.",expected.getMessage());}
        }
    }
}
