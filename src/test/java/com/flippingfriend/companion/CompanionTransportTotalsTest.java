package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Diagnostic arithmetic uses deterministic clocks; no wall-clock performance assertions. */
public class CompanionTransportTotalsTest {
    private static long ms(long millis){return millis*1_000_000L;}
    private static CompanionTransportTotals.Row row(CompanionTransportTotals.Report report,String group) {
        return report.rows.stream().filter(r->r.group.equals(group)).findFirst().orElseThrow();
    }
    private static long histogramTotal(CompanionTransportTotals.Row row) {
        long sum=0;for(long count:row.latencyHistogram)sum+=count;return sum;
    }
    private static void partition(CompanionTransportTotals.Row row) {
        assertEquals(row.attempts,row.bodyReadAndScoped2xx+row.failed2xx+row.httpFailures+row.noResponse);
        assertEquals(row.attempts,histogramTotal(row)+row.invalidTiming);
    }
    @Test public void everyCompletedTransportCountsIncludingFastFailuresAndInvalidTiming() {
        var totals=new CompanionTransportTotals();
        totals.complete(totals.begin("a",1,"events/ge-offer",0),ms(1),202,true,ms(1));
        totals.complete(totals.begin("a",1,"events/ge-offer",ms(1)),ms(2),503,false,ms(3));
        totals.complete(totals.begin("a",1,"events/ge-offer",ms(3)),ms(3),-1,false,ms(6));
        totals.complete(totals.begin("a",1,"events/ge-offer",ms(6)),ms(4),200,false,ms(10));
        totals.complete(totals.begin("a",1,"events/ge-offer",ms(10)),-1,200,true,ms(11));
        var report=totals.snapshot("a",1,ms(11));var row=row(report,"OFFERS");
        assertEquals(5,row.attempts);assertEquals(2,row.bodyReadAndScoped2xx);assertEquals(1,row.failed2xx);
        assertEquals(1,row.httpFailures);assertEquals(1,row.noResponse);assertEquals(1,row.invalidTiming);
        assertEquals(11,report.elapsedWindowMillis);partition(row);
    }
    @Test public void rateLimitedReportsAreCumulativePrefixesOfOneWindow() {
        var totals=new CompanionTransportTotals();var first=totals.complete(totals.begin("a",1,"ledger/current",0),ms(1),200,true,ms(1));
        assertNotNull(first);
        assertNull(totals.complete(totals.begin("a",1,"ledger/current",ms(1)),ms(1),200,true,ms(60000)));
        var next=totals.complete(totals.begin("a",1,"ledger/current",ms(60000)),ms(1),200,true,ms(60001));
        assertNotNull(next);assertEquals(first.measurementId,next.measurementId);assertEquals(first.window,next.window);
        assertEquals(1,row(first,"LEDGER").attempts);assertEquals(3,row(next,"LEDGER").attempts);
        assertTrue(next.semantics.contains("Cumulative"));assertTrue(next.semantics.contains("not durable ACK"));
    }
    @Test public void histogramP95IsAnUpperBoundAndRetainsOverflow() {
        var totals=new CompanionTransportTotals();
        for(int i=0;i<100;i++)totals.complete(totals.begin("a",1,"policy/entry",0),ms(i<94?1:100),200,true,ms(100));
        var row=row(totals.snapshot("a",1,ms(100)),"POLICY");
        assertEquals(Long.valueOf(100),row.p95UpperBoundMillis);assertFalse(row.p95AboveLargestBound);partition(row);
        var overflow=new CompanionTransportTotals();
        overflow.complete(overflow.begin("a",1,"policy/entry",0),ms(60000)+1,200,true,ms(60001));
        var tooLong=row(overflow.snapshot("a",1,ms(60001)),"POLICY");
        assertNull(tooLong.p95UpperBoundMillis);assertTrue(tooLong.p95AboveLargestBound);assertEquals(1,tooLong.latencyHistogram[15]);
    }
    @Test public void emptyOrInvalidTimingNeverInventsZeroLatency() {
        var totals=new CompanionTransportTotals();assertTrue(totals.snapshot(null,0,0).rows.isEmpty());
        totals.complete(totals.begin("a",1,"unknown",0),-1,-1,false,0);
        var row=row(totals.snapshot("a",1,0),"OTHER");assertNull(row.p95UpperBoundMillis);
        assertFalse(row.p95AboveLargestBound);assertEquals(1,row.invalidTiming);partition(row);
    }
    @Test public void oldAccountCompletionsCannotReplaceOrPolluteNewAccountWindows() {
        var totals=new CompanionTransportTotals();var old=totals.begin("a",1,"events/ge-offer",0);
        var newer=totals.begin("b",2,"portfolio/current",1);
        totals.complete(newer,ms(5),200,true,ms(5));var late=totals.complete(old,ms(10),200,false,ms(10));
        assertEquals("a",late.account);assertEquals(1,late.generation);
        assertNull(totals.snapshot("a",1,ms(10)));assertEquals(1,row(totals.snapshot("b",2,ms(10)),"PLAN").attempts);
        var returnToA=totals.complete(totals.begin("a",3,"events/ge-offer",ms(10)),ms(1),202,true,ms(11));
        assertNotEquals(late.window,returnToA.window);assertEquals(1,row(returnToA,"OFFERS").attempts);
        // A delayed old begin, including an inconsistent same-epoch scope, cannot resurrect A1 or B2.
        totals.complete(totals.begin("b",2,"ledger/current",0),ms(1),200,true,ms(12));
        totals.complete(totals.begin("b",3,"ledger/current",0),ms(1),200,true,ms(12));
        assertNull(totals.snapshot("b",3,ms(12)));assertEquals(returnToA.window,totals.snapshot("a",3,ms(12)).window);
    }
    @Test public void publicRequestsRemainInOneSeparateGlobalWindowAcrossAccountSwitches() {
        var totals=new CompanionTransportTotals();
        var global=totals.complete(totals.begin(null,1,"health",ms(10)),ms(1),200,true,ms(11));
        totals.complete(totals.begin("a",1,"ledger/current",ms(12)),ms(1),200,true,ms(13));
        totals.complete(totals.begin("b",2,"ledger/current",ms(14)),ms(1),200,true,ms(15));
        totals.complete(totals.begin(null,2,"market/snapshot",ms(20)),ms(2),503,false,ms(22));
        var current=totals.snapshot(null,999,ms(30));assertEquals("GLOBAL",current.account);
        assertEquals(global.window,current.window);assertEquals(20,current.elapsedWindowMillis);assertEquals(2,current.rows.size());
        assertEquals(1,row(current,"HEALTH").attempts);assertEquals(1,row(current,"MARKET").httpFailures);
    }
    @Test public void snapshotsAreDefensiveAndContainNoArbitraryRouteData() {
        var totals=new CompanionTransportTotals();
        var report=totals.complete(totals.begin("account-hash",1,"policy/entry?token=secret&cash=123",0),ms(1),200,true,ms(1));
        String json=new Gson().toJson(report);assertFalse(json.contains("secret"));assertFalse(json.contains("cash"));assertFalse(json.contains("policy/entry"));
        report.histogramUpperBoundsMillis[0]=-99;row(report,"POLICY").latencyHistogram[0]=999;
        var next=totals.snapshot("account-hash",1,ms(2));assertEquals(1,next.histogramUpperBoundsMillis[0]);
        assertEquals(1,row(next,"POLICY").latencyHistogram[0]);
        try{report.rows.clear();fail("Rows must be immutable");}catch(UnsupportedOperationException expected){}
        assertEquals(CompanionTransportTotals.Group.OTHER,CompanionTransportTotals.group(null));
        for(int i=0;i<500;i++)totals.complete(totals.begin("account-hash",1,"arbitrary/"+i,ms(2)),ms(1),404,false,ms(3));
        assertEquals(2,totals.snapshot("account-hash",1,ms(3)).rows.size());
    }
    @Test public void concurrentCompletionsAndSnapshotsPreserveExactPartitions()throws Exception {
        var totals=new CompanionTransportTotals();var pool=Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> work=new ArrayList<>();
            for(int thread=0;thread<4;thread++)work.add(pool.submit(()->{
                for(int i=0;i<250;i++) {
                    totals.complete(totals.begin("a",1,"events/ge-offer",0),ms(i%10),i%2==0?202:503,i%2==0,ms(10));
                    for(var row:totals.snapshot("a",1,ms(10)).rows)partition(row);
                }
            }));
            for(var future:work)future.get(15,TimeUnit.SECONDS);
            var row=row(totals.snapshot("a",1,ms(10)),"OFFERS");assertEquals(1000,row.attempts);
            assertEquals(500,row.bodyReadAndScoped2xx);assertEquals(500,row.httpFailures);partition(row);
        }finally{pool.shutdownNow();}
    }
}
