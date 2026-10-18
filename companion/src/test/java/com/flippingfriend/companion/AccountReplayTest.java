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
        return (m,a,limits,offers,at)-> {
            for(var item:m.mapping.values())if(!a.getItemsOnOffer().contains(item.id) && limits.get(item.id)>=quantity) {
                var c=new PortfolioCandidate(item.id,item.name,"",100,100,100,120,120,120,quantity,limits.get(item.id),
                    18L*quantity,1,1,1,1,.01,.01,1,at.getEpochSecond()+90);
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
    @Test public void restartDuringPartialPurchasePreservesCanonicalCostLimitsAndRiskWindow()throws Exception {
        var input=input(1,21);for(var f:input.frames)for(var flow:f.flows)flow.lowVolume=600;
        var before=run(input,5,10);input.restartAt=List.of(start+601,start+1100);
        assertEquals(new Gson().toJson(before),new Gson().toJson(run(input,5,10)));
    }
}
