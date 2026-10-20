package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AccountReplayContinuationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final AccountReplayTest f=new AccountReplayTest();
    AccountReplay.Entries boundedEntries() {
        return (market,account,limits,offers,at)-> {
            for(var item:market.mapping.values()) {
                int quantity=Math.min(10,limits.getOrDefault(item.id,0));
                if(quantity<=0 || account.getItemsOnOffer().contains(item.id))continue;
                var quote=market.latest.getAsJsonObject(""+item.id);int buy=quote.get("low").getAsInt()+1,sell=quote.get("high").getAsInt();
                var c=new PortfolioCandidate(item.id,item.name,"",buy,buy,buy,sell,sell,sell,quantity,limits.get(item.id),
                    (long)(sell-sell/50-buy)*quantity,1,1,1,1,.01,.01,1,at.getEpochSecond()+90);
                return new PortfolioPlan(account.getCorrelationId(),at.getEpochSecond(),at.getEpochSecond()+90,"READY","bounded fixture",0,
                    List.of(new PortfolioAllocation(1,c,"BUY")));
            }
            return PortfolioPlan.unavailable(account.getCorrelationId(),PortfolioPlanOutcome.NO_ELIGIBLE_CANDIDATE,"fixture",at.getEpochSecond());
        };
    }
    AccountReplayInput repricing() {
        var input=f.input(1,41);
        for(var frame:input.frames) {
            if(frame.availableAt>=f.start+600) {var q=frame.latest.getAsJsonObject("1");q.addProperty("low",110);q.addProperty("high",140);}
            for(var flow:frame.flows) {
                flow.lowVolume=frame.availableAt==f.start+600?300:1000;
                if(frame.availableAt>f.start+600)flow.low=110;
                flow.high=140;
            }
        }
        return input;
    }
    AccountReplay.Result run(AccountReplayInput input,AccountReplay.Entries entries)throws Exception {
        try(var replay=new AccountReplay(input,5,100,folder.newFolder().toPath().resolve("synthetic.db"),entries)){return replay.run();}
    }
    @Test public void aPartialBuyReplacesOnlyItsCollectedRemainderBeforeSellingTheFilledUnits()throws Exception {
        var input=repricing();input.visitSeconds=30;input.restartAt=List.of(f.start+700);
        var result=run(input,boundedEntries());
        assertEquals(result.actions.toString(),1,result.replacementBuys);
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("BUY 1 7 @ 111")));
        long replacement=result.actions.stream().filter(a->a.contains("BUY 1 7 @ 111")).mapToLong(a->Long.parseLong(a.split(" ")[0])).findFirst().getAsLong();
        assertFalse(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("SELL ") && Long.parseLong(a.split(" ")[0])<replacement));
        assertEquals(300,result.realizedProfit);assertEquals(100000300,result.equityLower);assertEquals(0,result.inventoryCost);
    }
    @Test public void aFreshStopDuringAnAcceptedRepriceMustCancelTheBuyInsteadOfLeavingItsPartialStockStranded()throws Exception {
        for(boolean restart:new boolean[]{false,true}) {
            var input=repricing();
            var stop=new AccountReplayInput.Frame();stop.availableAt=stop.observedAt=f.start+610;stop.latest=input.frames.get(10).latest.deepCopy();
            var q=stop.latest.getAsJsonObject("1");q.addProperty("low",70);q.addProperty("high",80);q.addProperty("lowTime",stop.availableAt);q.addProperty("highTime",stop.availableAt);
            input.frames.add(stop);input.frames.sort(Comparator.comparingLong(a->a.availableAt));
            for(var frame:input.frames)if(frame.availableAt>stop.availableAt) {
                var quote=frame.latest.getAsJsonObject("1");quote.addProperty("low",70);quote.addProperty("high",80);
                for(var flow:frame.flows){flow.lowVolume=0;flow.low=70;flow.high=80;}
            }
            if(restart)input.restartAt=List.of(f.start+620,f.start+650);
            var result=run(input,boundedEntries());
            assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("CANCEL ") && a.endsWith("remaining 7") && Long.parseLong(a.split(" ")[0])<f.start+710));
            assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("SELL 1 3 @ ")));
            assertEquals(result.actions.toString(),0,result.replacementBuys);
        }
    }
    @Test public void aFreshStopAfterCollectionExitsTheFilledUnitsWithoutResumingTheSavedRebuy()throws Exception {
        var input=repricing();input.visitSeconds=30;input.restartAt=List.of(f.start+800);
        for(var frame:input.frames)if(frame.availableAt>=f.start+700) {
            var q=frame.latest.getAsJsonObject("1");q.addProperty("low",70);q.addProperty("high",80);
            for(var flow:frame.flows){flow.lowVolume=0;flow.low=70;flow.high=80;}
        }
        var result=run(input,boundedEntries());
        assertEquals(result.actions.toString(),0,result.replacementBuys);
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("SELL 1 3 @ ")));
        assertEquals(result.actions.toString(),0,result.inventoryCost);assertTrue(result.realizedProfit<0);
    }
    @Test public void anOrdinaryRelistAfterRecoveryCannotHideAFreshStopOnAnotherListedSale()throws Exception {
        var input=f.input(2,31);
        for(var frame:input.frames) {
            for(var flow:frame.flows)flow.highVolume=0;
            if(frame.availableAt>=f.start+1200 && frame.availableAt<f.start+1240) {
                var q=frame.latest.getAsJsonObject("1");q.addProperty("low",70);q.addProperty("high",80);
            }
            if(frame.availableAt>=f.start+1240) {
                var q=frame.latest.getAsJsonObject("2");q.addProperty("low",70);q.addProperty("high",80);
            }
        }
        var switchFrame=new AccountReplayInput.Frame();switchFrame.availableAt=switchFrame.observedAt=f.start+1240;
        switchFrame.latest=input.frames.get(21).latest.deepCopy();
        for(String item:new String[]{"1","2"}) {var q=switchFrame.latest.getAsJsonObject(item);q.addProperty("lowTime",switchFrame.availableAt);q.addProperty("highTime",switchFrame.availableAt);}
        input.frames.add(switchFrame);input.frames.sort(Comparator.comparingLong(a->a.availableAt));
        var result=run(input,f.fixtureEntries(10));
        var actions=result.actions;
        long firstRelist=actions.stream().filter(a->a.contains("SELL 1 ") && Long.parseLong(a.split(" ")[0])>f.start+1240).mapToLong(a->Long.parseLong(a.split(" ")[0])).findFirst().orElseThrow();
        // Both original sale offers are still working when the first cancellation is shown.
        assertTrue(actions.toString(),actions.stream().anyMatch(a->a.contains("CANCEL ") && Long.parseLong(a.split(" ")[0])>f.start+1240 && Long.parseLong(a.split(" ")[0])<firstRelist));
        assertTrue(actions.toString(),actions.stream().anyMatch(a->a.contains("SELL 2 ") && Long.parseLong(a.split(" ")[0])>f.start+1240 && Long.parseLong(a.split(" ")[0])<firstRelist));
    }
    @Test public void aFillDuringTheDisplayedCancellationReducesTheBoundAndRequiresAnotherActionDelay()throws Exception {
        var input=repricing();var extra=new AccountReplayInput.Frame();extra.availableAt=extra.observedAt=f.start+610;
        input.frames.get(10).flows.clear();input.frames.get(15).flows.clear(); // No overlapping rolling-volume evidence.
        extra.latest=input.frames.get(10).latest.deepCopy();
        var flow=new AccountReplayInput.Flow();flow.itemId=1;flow.bucketStart=f.start+310;flow.bucketEnd=f.start+610;
        flow.low=100;flow.high=140;flow.lowVolume=400;flow.highVolume=1000;extra.flows.add(flow);
        input.frames.add(extra);input.frames.sort(Comparator.comparingLong(a->a.availableAt));
        input.restartAt=List.of(f.start+620,f.start+652);
        var result=run(input,boundedEntries());
        assertEquals(result.actions.toString(),1,result.replacementBuys);
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("CANCEL ") && a.endsWith("remaining 6")));
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("BUY 1 6 @ 111")));
        assertFalse(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("BUY 1 7 @ 111")));
        assertTrue(result.withdrawnGuidance>=2);assertEquals(310,result.realizedProfit);assertEquals(100000310,result.equityLower);
    }
    @Test public void overlappingRollingFlowCannotSpendTheSamePublicVolumeTwice() {
        var input=f.input(1,11);var flow=input.frames.get(10).flows.get(0);
        flow.bucketStart-=10;flow.bucketEnd-=10;
        try{input.validate();fail("Overlapping volume windows must not create extra fills");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void aPartialSellRetainsItsRelistReceiptExactCostAndStopAcrossRestart()throws Exception {
        var input=f.input(1,31);
        for(var frame:input.frames) {
            if(frame.availableAt>=f.start+1200){var q=frame.latest.getAsJsonObject("1");q.addProperty("low",80);q.addProperty("high",90);}
            for(var flow:frame.flows)if(frame.availableAt==f.start+1200){flow.high=120;flow.highVolume=400;}
            else if(frame.availableAt>f.start+1200){flow.high=90;flow.low=80;}
        }
        var before=run(input,f.fixtureEntries(10));input.restartAt=List.of(f.start+1235);
        var after=run(input,f.fixtureEntries(10));
        assertEquals(after.actions.toString(),1,after.relistedSales);
        assertTrue(after.actions.toString(),after.actions.stream().anyMatch(a->a.contains("CANCEL ") && a.endsWith("remaining 6")));
        assertTrue(after.actions.toString(),after.actions.stream().anyMatch(a->a.contains("SELL 1 6 @ ")));
        assertEquals(before.realizedProfit,after.realizedProfit);assertEquals(before.tax,after.tax);
        assertEquals(0,after.inventoryCost);assertEquals(3,after.authorizedSales);
    }
    AccountReplayInput twoPending(boolean secondCompletes) {
        var input=f.input(2,51);
        for(var frame:input.frames) {
            if(frame.availableAt>=f.start+600)for(int item:new int[]{1,2}) {
                if(secondCompletes && item==2)continue;
                var q=frame.latest.getAsJsonObject(""+item);q.addProperty("low",110);q.addProperty("high",140);
            }
            for(var flow:frame.flows) {
                if(secondCompletes && flow.itemId==2)flow.lowVolume=frame.availableAt>=f.start+900?1000:0;
                else {
                    flow.lowVolume=frame.availableAt==f.start+600?300:frame.availableAt>f.start+600?1000:0;
                    if(frame.availableAt>f.start+600)flow.low=110;
                    flow.high=140;
                }
            }
        }
        return input;
    }
    AccountReplay.Entries pausedEntries(long until) {
        return (m,a,l,o,at)->at.getEpochSecond()>=f.start+600 && at.getEpochSecond()<f.start+until
            ?PortfolioPlan.unavailable(a.getCorrelationId(),PortfolioPlanOutcome.NO_ELIGIBLE_CANDIDATE,"Controlled replacement refusal",at.getEpochSecond())
            :boundedEntries().plan(m,a,l,o,at);
    }
    @Test public void aRefusedReplacementCannotHideAnotherCollectedExitDuringManualVisits()throws Exception {
        var input=twoPending(true);var result=run(input,pausedEntries(1800));
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("SELL 2 ") && Long.parseLong(a.split(" ")[0])<f.start+1800));
    }
    @Test public void twoCancelledRemaindersSurviveTheSameRestartAndReceiveSeparateFreshReplacements()throws Exception {
        var input=twoPending(false);input.restartAt=List.of(f.start+1000);
        var result=run(input,pausedEntries(1800));
        assertEquals(result.actions.toString(),2,result.replacementBuys);
        for(int item:new int[]{1,2})assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("BUY "+item+" 7 @ 111")));
    }
}
