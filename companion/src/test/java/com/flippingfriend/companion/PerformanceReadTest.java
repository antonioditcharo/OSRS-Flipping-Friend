package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PerformanceReadTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("performance");
    RecordedSale row(long id,String offer,long at,long cost,Long low,Long high) {
        return new RecordedSale(id,at,561,"Nature rune",1,cost,"source-"+id,offer,120L,2L,2L,low,high,SaleAccounting.CURRENT_POLICY,null);
    }
    @Test public void everySaleContributesBeyondTheHundredRowInvestigationLimit() {
        List<RecordedSale> rows=new ArrayList<>();for(int i=1;i<=205;i++)rows.add(row(i,"sale-"+i,100+i,100,18L,18L));
        var report=PerformanceRead.aggregate(account,"ALL",null,500,rows,Set.of("sale-1","sale-2"));
        assertEquals(205,report.saleUpdates);assertEquals(3690,report.profitLower);assertEquals(20500,report.appliedCost);
        assertEquals(2,report.completedFlips);assertEquals(205,report.sales.size());assertEquals(305,report.sales.get(0).observedAt);
    }
    @Test public void partialsDoNotInflateFlipsAndUnknownsNeverBecomeZeroProfit() {
        var rows=List.of(row(1,"same",101,100,18L,18L),row(2,"same",102,100,18L,18L),row(3,"unknown",103,0,null,null));
        var report=PerformanceRead.aggregate(account,"ALL",null,200,rows,Set.of("same","unknown"));
        assertEquals(3,report.saleUpdates);assertEquals(1,report.unresolvedSales);assertEquals(1,report.completedFlips);
        assertEquals(36,report.profitLower);assertEquals(200,report.appliedCost);assertEquals(2,report.sales.size());
        assertNull(report.sales.get(0).profitLower);assertEquals(2,report.sales.get(1).quantity);
        assertEquals(0,PerformanceRead.aggregate(account,"ALL",null,200,rows,Set.of()).completedFlips);
    }
    @Test public void sessionUsesDurableCursorRatherThanHistoricalObservationTimes() {
        var session=new PerformanceRead.Session("session",200,2);
        var report=PerformanceRead.aggregate(account,"SESSION",session,500,List.of(row(1,"old",400,100,18L,18L),
            row(2,"old",400,100,18L,18L),row(3,"later-accepted",100,100,18L,18L)),Set.of("later-accepted"));
        assertEquals(1,report.saleUpdates);assertEquals(200,report.startedAt);assertEquals(18,report.profitLower);
    }
    @Test public void rollingWindowsAndProfitRangesKeepTheirDenominators() {
        var uncertain=new RecordedSale(3,100000,561,"Nature rune",2,200,"source-3","range",240L,4L,5L,35L,36L,SaleAccounting.CURRENT_POLICY,"range");
        var rows=List.of(row(1,"old",1,100,18L,18L),row(2,"recent",99999,100,18L,18L),uncertain);
        var report=PerformanceRead.aggregate(account,"DAY",null,100001,rows,Set.of());
        assertEquals(2,report.saleUpdates);assertEquals(53,report.profitLower);assertEquals(54,report.profitUpper);assertEquals(300,report.appliedCost);
        assertEquals(3,PerformanceRead.aggregate(account,"WEEK",null,100001,rows,Set.of()).saleUpdates);
    }
    @Test public void groupedSalesKeepAmountsAndReasonsWhenOnePartialHasUnmatchedCost() {
        String reason="COST_BASIS_UNRESOLVED; disposal has no durable position";
        var unknown=new RecordedSale(2,102,561,"Nature rune",1,0,"source-2","same",120L,2L,2L,null,null,SaleAccounting.CURRENT_POLICY,reason);
        var report=PerformanceRead.aggregate(account,"ALL",null,200,List.of(row(1,"same",101,100,18L,18L),unknown),Set.of("same"));
        var sale=report.sales.get(0);assertNull(sale.profitLower);assertEquals(Long.valueOf(240),sale.gross);
        assertEquals(Long.valueOf(100),sale.matchedCost);assertEquals(Long.valueOf(4),sale.taxLower);
        assertEquals(List.of(reason),sale.accountingReasons);assertEquals(18,report.profitLower);assertEquals(0,report.completedFlips);
        assertTrue(report.isValid());
    }
    @Test public void sessionResetIsIdempotentDurableAndCannotResetRiskOrFinancialEffects()throws Exception {
        var database=folder.newFolder().toPath().resolve("session.db");
        try(var store=new SqliteStore(database)) {
            store.recordEvent(100,"risk-original",RiskWindow.EVENT_TYPE,"{}");
            store.performanceSession(account,100,false,"start");
            store.performanceSession(account,200,false,"ignored");
            assertEquals("start",store.performance(account,"SESSION",300).sessionId);
            store.performanceSession(account,200,true,"reset");store.performanceSession(account,250,true,"reset");
            assertEquals(2,store.eventCount(PerformanceRead.SESSION_EVENT));assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
            assertEquals(0,store.positionEventCount());assertEquals(200,store.performance(account,"SESSION",300).startedAt);
        }
        try(var store=new SqliteStore(database)) {
            store.performanceSession(account,400,false,"restart");
            assertEquals("reset",store.performance(account,"SESSION",500).sessionId);assertEquals(200,store.performance(account,"SESSION",500).startedAt);
        }
    }
}
