package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AccountReplayTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long start=1800000000;
    AccountReplayInput input(int items,int minutes) {
        var input=new AccountReplayInput();input.sourceDescription="Hand-calculated synthetic fixture; not market performance";
        input.startAt=start;input.endAt=start+minutes*60;input.minimumProfit=0;input.terminalHaircutBps=0;
        for(int i=1;i<=items;i++)input.mapping.put(i,new MarketIngestionService.Item(i,"Test item "+i,10,true));
        for(int minute=0;minute<=minutes;minute++) {
            var f=new AccountReplayInput.Frame();f.availableAt=f.observedAt=start+minute*60;
            for(int i=1;i<=items;i++) {
                JsonObject q=new JsonObject();q.addProperty("low",100);q.addProperty("high",120);
                q.addProperty("lowTime",f.availableAt);q.addProperty("highTime",f.availableAt);f.latest.add(""+i,q);
                if(minute>0 && minute%5==0) {
                    var flow=new AccountReplayInput.Flow();flow.itemId=i;flow.bucketStart=f.availableAt-300;flow.bucketEnd=f.availableAt;
                    flow.low=100;flow.high=120;flow.lowVolume=1000;flow.highVolume=1000;f.flows.add(flow);
                }
            }
            input.frames.add(f);
        }
        return input;
    }
    AccountReplay.Entries fixtureEntries(int quantity) {
        return fixtureEntries(quantity,.01);
    }
    AccountReplay.Entries fixtureEntries(int quantity,double sellHours) {
        return (m,a,limits,offers,at)-> {
            for(var item:m.mapping.values())if(!a.getItemsOnOffer().contains(item.id) && limits.get(item.id)>=quantity) {
                var c=new PortfolioCandidate(item.id,item.name,"",100,100,100,120,120,120,quantity,limits.get(item.id),
                    18L*quantity,1,1,1,1,.01,sellHours,1,at.getEpochSecond()+90);
                return new PortfolioPlan(a.getCorrelationId(),at.getEpochSecond(),at.getEpochSecond()+90,"READY","fixture",0,
                    List.of(new PortfolioAllocation(1,c,"BUY")));
            }
            return PortfolioPlan.unavailable(a.getCorrelationId(),PortfolioPlanOutcome.NO_ELIGIBLE_CANDIDATE,"fixture",at.getEpochSecond());
        };
    }
    AccountReplay.Result run(AccountReplayInput input,int cadence,int quantity)throws Exception {
        try(var replay=new AccountReplay(input,cadence,100,folder.newFolder().toPath().resolve("synthetic.db"),fixtureEntries(quantity))){return replay.run();}
    }
    @Test public void handCalculatedSingleRoundTripMatchesCanonicalTaxAndCostCoinForCoin() throws Exception {
        var result=run(input(1,21),5,10);
        // 10 @100, sale 10 @120, tax floor(120/50)=2 per unit: 1,180 - 1,000 =180.
        assertEquals(new Gson().toJson(result),180,result.realizedProfit);assertEquals(20,result.tax);
        assertEquals(100000180,result.equityLower);assertEquals(0,result.inventoryCost);
        assertTrue(result.terminalMarksComplete);assertEquals(1,result.completedSales);
    }
    @Test public void eightSlotsShareCapitalAndCompletedPurchasesWaitForAttendance() throws Exception {
        var result=run(input(9,12),30,10);
        assertEquals(new Gson().toJson(result),8,result.actions.size());assertEquals(8000,result.inventoryCost);
        assertEquals(99992000,result.bagCoins);assertEquals(0,result.realizedProfit);
        assertEquals(8,result.actions.stream().filter(s->s.contains("BUY ")).count());
        assertFalse(result.actions.stream().anyMatch(s->s.contains("COLLECT")));
    }
    @Test public void missesDelayCollectionAndEachRequiredCadenceRunsIndependently() throws Exception {
        for(int cadence:new int[]{5,30,120,480}) {
            var input=input(1,12);input.missedVisitIndices=List.of(0);
            var result=run(input,cadence,10);assertEquals(cadence,result.cadenceMinutes);assertEquals(1,result.missedVisits);
            if(cadence>5)assertEquals(0,result.interactions);
        }
    }
    @Test public void partialVolumeAndUnpricedTerminalStockAreNotInventedLiquidations() throws Exception {
        var input=input(1,11);
        for(var f:input.frames)for(var flow:f.flows)flow.lowVolume=300;
        var result=run(input,30,10);assertEquals(new Gson().toJson(result),300,result.inventoryCost);assertEquals(700,result.lockedBuyCoins);
        assertEquals(294,result.terminalValueLower);assertEquals(-6,result.netEquityChangeLower);
        input.frames.get(input.frames.size()-1).latest=new JsonObject();
        var unknown=run(input,30,10);assertFalse(unknown.terminalMarksComplete);assertEquals(0,unknown.terminalValueLower);
    }
    @Test public void futureHistoryQuotesAndDuplicateFlowAreRejectedBeforeReplay() throws Exception {
        var a=input(1,10);a.frames.get(0).latest.getAsJsonObject("1").addProperty("lowTime",start+1);
        try{a.validate();fail();}catch(IllegalArgumentException expected){}
        var b=input(1,10);var f=b.frames.get(5);f.flows.add(f.flows.get(0));
        try{b.validate();fail();}catch(IllegalArgumentException expected){}
        var c=input(1,10);c.frames.get(0).shortHistory.put(1,List.of(new com.flippingfriend.data.Candle(start,120,100,10,10)));
        try{c.validate();fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void sameFrozenInputsReproduceExactActionsAndNumbers() throws Exception {
        var input=input(1,21);assertEquals(new Gson().toJson(run(input,5,10)),new Gson().toJson(run(input,5,10)));
    }
    @Test public void quietTradeTimesDoNotStrandCollectedStockInReplay()throws Exception {
        var input=input(1,21);
        for(var frame:input.frames)if(frame.availableAt>=start+600)
            frame.latest.getAsJsonObject("1").addProperty("highTime",start);
        var result=run(input,5,10);
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(s->s.contains("SELL ")));
        assertEquals(1,result.completedSales);assertEquals(180,result.realizedProfit);
        assertEquals("account-replay-15",result.policyVersion);
        assertEquals("companion-sale-6",result.salePolicyVersion);assertEquals(1,result.authorizedSales);
    }
    AccountReplayInput interruptedRelist(boolean recovered) {
        var input=input(1,26);var gson=new Gson();
        for(var frame:input.frames) {
            int high=frame.availableAt>=start+600?110:120;
            frame.latest.getAsJsonObject("1").addProperty("high",high);
            for(var flow:frame.flows)flow.highVolume=0;
            long end=frame.availableAt/300*300;int support=frame.availableAt>=start+1200?110:120;
            frame.shortHistory.put(1,List.of(new com.flippingfriend.data.Candle(end-600,support,100,100,100),
                new com.flippingfriend.data.Candle(end-300,support,100,100,100)));
            if(frame.availableAt>=start+1260)frame.shortHistory.clear();
        }
        var missing=gson.fromJson(gson.toJson(input.frames.get(20)),AccountReplayInput.Frame.class);
        missing.availableAt=missing.observedAt=start+1220;missing.flows.clear();missing.shortHistory.clear();
        missing.latest.getAsJsonObject("1").addProperty("highTime",missing.availableAt);
        missing.latest.getAsJsonObject("1").addProperty("lowTime",missing.availableAt);
        input.frames.add(21,missing);
        if(recovered)for(var frame:input.frames)if(frame.availableAt>=start+1500)frame.latest.getAsJsonObject("1").addProperty("high",150);
        return input;
    }
    @Test public void vanishedHistoryAfterCancellationCannotBypassTheRelistThroughAnOrdinaryTargetExit()throws Exception {
        var input=interruptedRelist(false);var result=run(input,5,10);
        assertTrue("The supported working sale should first be cancelled: "+result.actions,
            result.actions.stream().anyMatch(s->s.startsWith((start+1215)+" CANCEL ")));
        assertTrue("Bucket evidence disappears between cancellation and collection: "+result.actions,
            result.actions.stream().anyMatch(s->s.startsWith((start+1230)+" COLLECT ")));
        assertEquals(result.actions.toString(),1,result.actions.stream().filter(s->s.contains(" SELL ")).count());
        assertEquals(0,result.relistedSales);assertEquals(1000,result.inventoryCost);assertEquals(0,result.realizedProfit);
        input.restartAt=List.of(start+1235);
        var restarted=run(input,5,10);
        assertEquals(result.actions,restarted.actions);assertEquals(result.inventoryCost,restarted.inventoryCost);
        assertEquals(0,restarted.relistedSales);assertEquals(0,restarted.realizedProfit);
        var recovered=run(interruptedRelist(true),5,10);
        assertEquals(recovered.actions.toString(),1,recovered.relistedSales);
        assertTrue(recovered.actions.toString(),recovered.actions.stream().anyMatch(s->s.contains(" SELL 1 10 @ 150")));
        assertEquals(1000,recovered.inventoryCost);assertEquals(0,recovered.realizedProfit);
    }
    @Test public void restartDuringPartialPurchasePreservesCanonicalCostLimitsAndRiskWindow()throws Exception {
        var input=input(1,21);for(var f:input.frames)for(var flow:f.flows)flow.lowVolume=600;
        var before=run(input,5,10);input.restartAt=List.of(start+601,start+1100);
        assertEquals(new Gson().toJson(before),new Gson().toJson(run(input,5,10)));
    }
    @Test public void replayCarriesOnlyExplicitCompletedAggregateClocks() {
        var input=input(1,10);var frame=input.frames.get(0);
        frame.fiveMinuteStartedAt=(frame.observedAt/300-1)*300;
        frame.hourlyStartedAt=(frame.observedAt/3600-1)*3600;
        input.validate();var market=frame.market(input.mapping);
        assertEquals(frame.fiveMinuteStartedAt,market.fiveMinuteStartedAt);assertEquals(frame.hourlyStartedAt,market.hourlyStartedAt);
        frame.fiveMinuteStartedAt=(frame.observedAt/300)*300;
        try{input.validate();fail("Unfinished aggregate accepted");}catch(IllegalArgumentException expected){}
        frame.fiveMinuteStartedAt=0;frame.hourlyStartedAt=0;input.validate();
        assertEquals(0,frame.market(input.mapping).fiveMinuteStartedAt);
    }
    @Test public void entryTargetSurvivesALowerQuoteAndPartialSaleAcrossRestart()throws Exception {
        var input=input(1,26);
        for(var frame:input.frames) {
            if(frame.availableAt>=start+600)frame.latest.getAsJsonObject("1").addProperty("high",103);
            for(var flow:frame.flows)flow.highVolume=600;
        }
        var before=run(input,5,10);input.restartAt=List.of(start+905);
        var after=run(input,5,10);
        assertEquals(new Gson().toJson(before),new Gson().toJson(after));
        assertTrue(after.actions.toString(),after.actions.stream().anyMatch(s->s.contains("SELL 1 10 @ 120")));
        assertEquals(180,after.realizedProfit);assertEquals(20,after.tax);
        assertEquals(1,after.completedSales);assertEquals(2,after.saleUpdates);
    }
    @Test public void originalAdviceIsQualifiedByCanonicalSourcesBeforePartialFillsAndRestart()throws Exception {
        var input=input(1,11);input.restartAt=List.of(start+650);
        for(var frame:input.frames)for(var flow:frame.flows)flow.lowVolume=300;
        var db=folder.newFolder().toPath().resolve("synthetic.db");
        try(var replay=new AccountReplay(input,30,100,db,fixtureEntries(10))){replay.run();}
        try(var store=new SqliteStore(db)) {
            var p=store.ledgerSnapshot(AccountIdentity.fromProfileKey("synthetic-account-replay-only"),input.endAt).getTradingPositions().get(0);
            assertEquals(3,p.getQuantity());assertEquals(300,p.getTotalCost());
            assertNotNull("Replay must use the same qualified canonical acquisition as live exits",p.getExitContext());
            assertEquals(120,p.getExitContext().getTargetSellPrice());
            try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+db);
                var sql=connection.prepareStatement("SELECT payload FROM event_log WHERE event_id=?")) {
                sql.setString(1,p.getExitContext().getSourceEventId());
                try(var rows=sql.executeQuery()) {
                    assertTrue(rows.next());var origin=new Gson().fromJson(rows.getString(1),OfferEvent.class);
                    assertTrue(ExecutionObservationLedger.qualifiesOriginal(origin));
                    assertEquals(start,origin.getAdviceObservedAt());
                    assertEquals(start+input.actionSeconds,origin.getFirstSeenAt());
                }
            }
        }
    }
    OfferEvent sameSecondBuyOrigin() {
        var advice=CaptureOrder.next();var capture=CaptureOrder.next();
        var forecast=new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,1,true,100,10,3,1,
            3600,12,12,120,new com.flippingfriend.model.FillEstimate(.75,.2,100,.1),1,.2);
        var raw=OfferEvent.builder("original",start,"BUYING")
            .accountId(AccountIdentity.fromProfileKey("synthetic-account-replay-only")).authenticatedObservation(true)
            .eventIdentity("origin","synthetic-session","episode").sequence(1).slot(0).item(1,"Test item 1").buying(true)
            .price(100).quantities(10,0).firstSeenAt(start).recommendation("accepted",100,10,0,12,.75)
            .predictionEvidence(null,start,false).executionForecast(forecast)
            .acceptedAdviceProof(new AcceptedAdviceProof(advice,capture)).build();
        return raw.withBoundPrediction(raw,false);
    }
    @Test public void acceptedSameSecondForecastUsesItsOriginalDeadlineInsteadOfFallbackThroughReplayRestart() {
        var horizon=com.flippingfriend.TradingHorizon.of(com.flippingfriend.RiskProfile.MODERATE,AccountReplay.interval(5));
        var offer=new AccountReplay.Offer(0,1,100,10,true,start,"episode");offer.origin=sameSecondBuyOrigin();
        assertFalse("Workflow timing does not qualify learning",ExecutionObservationLedger.qualifiesOriginal(offer.origin));
        assertTrue(AccountReplay.qualifiedOriginalBinding(offer.origin));
        long deadline=AccountReplay.freezeBuyMaintenanceDeadline(offer,horizon,start+600);
        assertEquals(start+720,deadline);
        offer.filled=2;offer.spent=200;offer.lastProgressAt=start+60;
        var gson=new Gson();var restarted=gson.fromJson(gson.toJson(offer),AccountReplay.Offer.class);
        assertTrue("The retained exact source remains eligible for replay restoration",AccountReplay.qualifiedOriginalBinding(restarted.origin));
        assertEquals(deadline,AccountReplay.freezeBuyMaintenanceDeadline(restarted,horizon,start+900));
        var quote=new com.flippingfriend.data.LatestPrice(120,start+900,100,start+900);
        assertTrue("A stalled partial order is overdue at its original 12 minute estimate",
            com.flippingfriend.session.BuyRepricePricing.overdue(restarted.placedAt,restarted.lastProgressAt,
                restarted.buyMaintenanceDeadlineAt,quote,horizon,start+900));
        var unknown=new AccountReplay.Offer(0,1,100,10,true,start,"unknown");
        long fallback=AccountReplay.freezeBuyMaintenanceDeadline(unknown,horizon,start+600);
        assertEquals(start+600+(long)Math.ceil(horizon.legHorizonHours()*3600),fallback);
        assertFalse(com.flippingfriend.session.BuyRepricePricing.overdue(unknown.placedAt,unknown.lastProgressAt,
            fallback,quote,horizon,start+900));
        var changed=gson.toJsonTree(offer.origin).getAsJsonObject();changed.addProperty("suggestedPrice",101);
        assertFalse("A changed recommendation cannot supply restart attribution",
            AccountReplay.qualifiedOriginalBinding(gson.fromJson(changed,OfferEvent.class)));
    }
    @Test public void changedAdviceConsumesAnotherManualActionDelayBeforePlacement()throws Exception {
        var input=input(1,2);
        AccountReplay.Entries changing=(m,a,l,o,at)->fixtureEntries(at.getEpochSecond()<start+10?1:2).plan(m,a,l,o,at);
        AccountReplay.Result result;
        try(var replay=new AccountReplay(input,5,100,folder.newFolder().toPath().resolve("synthetic.db"),changing)){result=replay.run();}
        assertTrue(result.actions.toString(),result.actions.get(0).startsWith((start+30)+" BUY 1 2 @ 100"));
        assertEquals(1,result.withdrawnGuidance);
    }
    @Test public void restartWithdrawsPendingForegroundAdviceAndRequiresAnotherActionDelay()throws Exception {
        var input=input(1,21);input.restartAt=List.of(start+605);
        var result=run(input,5,10);
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(s->s.startsWith((start+620)+" COLLECT ")));
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(s->s.startsWith((start+635)+" SELL 1 10 @ 120")));
        assertEquals(1,result.withdrawnGuidance);assertEquals(1,result.authorizedSales);
        assertEquals(180,result.realizedProfit);assertEquals(100000180,result.equityLower);
    }
    @Test public void pendingEntryExpiresWithoutBorrowingTheLaterPlansExpiry()throws Exception {
        for(boolean candidateExpiry:new boolean[]{true,false}) {
            var input=input(1,2);
            AccountReplay.Entries expiring=(m,a,l,o,at)-> {
                var c=new PortfolioCandidate(1,"Test item 1","",100,100,100,120,120,120,1,10,18,1,1,1,1,.01,.01,1,
                    candidateExpiry && at.getEpochSecond()==start?start+10:start+90);
                return new PortfolioPlan(a.getCorrelationId(),at.getEpochSecond(),!candidateExpiry && at.getEpochSecond()==start?start+10:start+90,"READY","fixture",0,
                    List.of(new PortfolioAllocation(1,c,"BUY")));
            };
            AccountReplay.Result result;
            try(var replay=new AccountReplay(input,5,100,folder.newFolder().toPath().resolve("synthetic.db"),expiring)){result=replay.run();}
            assertTrue(result.actions.toString(),result.actions.get(0).startsWith((start+30)+" BUY "));
            assertEquals(1,result.withdrawnGuidance);
        }
    }
    @Test public void eightHourVisitUsesCanonicalOriginalAfterPartialFillsAndColdRestart()throws Exception {
        var input=input(1,481);input.restartAt=List.of(start+650,start+8*3600-5);
        for(var frame:input.frames) {
            if(frame.availableAt>=start+600)frame.latest.getAsJsonObject("1").addProperty("high",103);
            for(var flow:frame.flows)flow.lowVolume=300;
        }
        var result=run(input,480,10);
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(s->s.startsWith((start+8*3600+30)+" SELL 1 10 @ 120")));
        assertEquals(1,result.authorizedSales);assertEquals(0,result.realizedProfit);assertEquals(1000,result.inventoryCost);
    }
    @Test public void incompleteOriginalContextStaysUnknownInsteadOfUsingTheSimulatedEntryTarget()throws Exception {
        var input=input(1,21);input.restartAt=List.of(start+650);
        for(var frame:input.frames)if(frame.availableAt>=start+600)frame.latest.getAsJsonObject("1").addProperty("high",103);
        AccountReplay.Result result;
        try(var replay=new AccountReplay(input,5,100,folder.newFolder().toPath().resolve("synthetic.db"),fixtureEntries(10,0))){result=replay.run();}
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(s->s.contains("SELL 1 10 @ 103")));
        assertFalse(result.actions.toString(),result.actions.stream().anyMatch(s->s.contains("SELL 1 10 @ 120")));
        assertEquals(10,result.realizedProfit);assertEquals(1,result.authorizedSales);
    }
}
