package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Multi-item, elapsed-time regressions against the actual synthetic ledger and manual visits. */
public class AccountReplayPriorityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final AccountReplayTest f=new AccountReplayTest();
    AccountReplay.Result run(AccountReplayInput input,AccountReplay.Entries entries)throws Exception {
        try(var replay=new AccountReplay(input,5,100,folder.newFolder().toPath().resolve("synthetic.db"),entries)){return replay.run();}
    }
    long at(String action){return Long.parseLong(action.split(" ")[0])-f.start;}
    @Test public void aCollectedExitPrecedesUnrelatedOutbidBuyMaintenance()throws Exception {
        var input=f.input(2,12);
        for(var frame:input.frames) {
            if(frame.availableAt>=f.start+600) {
                var q=frame.latest.getAsJsonObject("2");q.addProperty("low",110);q.addProperty("high",140);
            }
            for(var flow:frame.flows)if(flow.itemId==2)flow.lowVolume=0;
        }
        var result=run(input,f.fixtureEntries(10));
        String sale=result.actions.stream().filter(a->a.contains("SELL 1 ")).findFirst().orElseThrow();
        assertFalse(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("CANCEL ") && at(a)<at(sale)));
    }
    @Test public void aFreshStopOutranksAHigherProfitWinnerAlreadyInTheSameInventory()throws Exception {
        var input=f.input(2,12);
        for(var frame:input.frames)if(frame.availableAt>=f.start+600) {
            var q=frame.latest.getAsJsonObject("2");q.addProperty("low",70);q.addProperty("high",80);
        }
        var result=run(input,f.fixtureEntries(10));
        String first=result.actions.stream().filter(a->a.contains("SELL ")).findFirst().orElseThrow();
        assertTrue(result.actions.toString(),first.contains("SELL 2 "));
    }
    @Test public void unpricedUnlistedStockReservesTheLastSlotUntilItsQuoteArrives()throws Exception {
        var input=f.input(9,17);
        for(var frame:input.frames) {
            if(frame.availableAt>=f.start+600 && frame.availableAt<f.start+900)frame.latest.remove("1");
            for(var flow:frame.flows)if(flow.itemId!=1)flow.lowVolume=0;
        }
        var result=run(input,f.fixtureEntries(10));
        assertFalse(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("BUY 9 ") && at(a)<900));
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->a.contains("SELL 1 ") && at(a)<1000));
    }
    @Test public void newlyAvailableAdviceWakesAnAttendingPlayerButNeverAnAbsentOne()throws Exception {
        for(int available:new int[]{60,180}) {
            var input=f.input(1,4);
            AccountReplay.Entries entries=(m,a,l,o,at)->at.getEpochSecond()<f.start+available
                ?PortfolioPlan.unavailable(a.getCorrelationId(),PortfolioPlanOutcome.NO_ELIGIBLE_CANDIDATE,"fixture",at.getEpochSecond())
                :f.fixtureEntries(10).plan(m,a,l,o,at);
            var result=run(input,entries);
            if(available==60)assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->at(a)==75 && a.contains("BUY ")));
            else assertTrue(result.actions.toString(),result.actions.isEmpty());
        }
        var missed=f.input(1,4);missed.missedVisitIndices=List.of(0);
        assertTrue(run(missed,f.fixtureEntries(10)).actions.isEmpty());
    }
    @Test public void aStrandedSaleIsMaintainedBeforeALessStrandedBuy()throws Exception {
        var input=f.input(2,20);
        for(var frame:input.frames) {
            if(frame.availableAt>=f.start+900) {
                var sell=frame.latest.getAsJsonObject("1");sell.addProperty("low",70);sell.addProperty("high",80);
                var buy=frame.latest.getAsJsonObject("2");buy.addProperty("low",110);buy.addProperty("high",140);
            }
            for(var flow:frame.flows) {
                if(flow.itemId==2)flow.lowVolume=0;
                flow.highVolume=0;
            }
        }
        var result=run(input,f.fixtureEntries(10));
        var after=new ArrayList<String>();for(var action:result.actions)if(at(action)>900)after.add(action);
        assertTrue(after.toString(),after.size()>=3);
        assertTrue(after.toString(),after.get(0).contains("CANCEL "));
        assertTrue(after.toString(),after.get(1).contains("COLLECT "));
        assertTrue(after.toString(),after.get(2).contains("SELL 1 10 @ "));
        assertEquals(1,result.relistedSales);
    }
    @Test public void aPartialPurchaseThroughItsStopCancelsDuringTheCurrentVisitBeforeAddingMore()throws Exception {
        var input=f.input(1,12);
        for(var frame:input.frames) {
            frame.flows.clear();
            if(frame.availableAt>=f.start+360) {
                var q=frame.latest.getAsJsonObject("1");q.addProperty("low",70);q.addProperty("high",80);
            }
        }
        var flow=new AccountReplayInput.Flow();flow.itemId=1;flow.bucketStart=f.start+60;flow.bucketEnd=f.start+360;
        flow.low=100;flow.high=120;flow.lowVolume=300;flow.highVolume=0;input.frames.get(6).flows.add(flow);
        var result=run(input,f.fixtureEntries(10));
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->at(a)==375 && a.contains("CANCEL ") && a.endsWith("remaining 7")));
        assertTrue(result.actions.toString(),result.actions.stream().anyMatch(a->at(a)==405 && a.contains("SELL 1 3 @ ")));
        assertEquals(0,result.replacementBuys);
        assertFalse(result.actions.toString(),result.actions.stream().anyMatch(a->at(a)>360 && a.contains("BUY ")));
        input.restartAt=List.of(f.start+370);
        var restarted=run(input,f.fixtureEntries(10));
        assertTrue(restarted.actions.toString(),restarted.actions.stream().anyMatch(a->at(a)==385 && a.contains("CANCEL ")));
        assertTrue(restarted.actions.toString(),restarted.actions.stream().anyMatch(a->at(a)==415 && a.contains("SELL 1 3 @ ")));
    }
    @Test public void oldTradePricesCannotTriggerAStopOrAnInventedDisposal()throws Exception {
        var input=f.input(1,12);
        for(var frame:input.frames) {
            frame.flows.clear();
            if(frame.availableAt>=f.start+360) {
                var q=frame.latest.getAsJsonObject("1");q.addProperty("low",70);q.addProperty("high",80);
                q.addProperty("lowTime",f.start);q.addProperty("highTime",f.start);
            }
        }
        var flow=new AccountReplayInput.Flow();flow.itemId=1;flow.bucketStart=f.start+60;flow.bucketEnd=f.start+360;
        flow.low=100;flow.high=120;flow.lowVolume=300;flow.highVolume=0;input.frames.get(6).flows.add(flow);
        var result=run(input,f.fixtureEntries(10));
        assertEquals(result.actions.toString(),1,result.interactions);
        assertEquals(300,result.inventoryCost);assertEquals(700,result.lockedBuyCoins);
        assertEquals(0,result.realizedProfit);assertEquals(0,result.saleUpdates);
        assertFalse(result.terminalMarksComplete);
    }
}
