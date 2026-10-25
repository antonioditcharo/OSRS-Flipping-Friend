package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HistoricalFundingReadBudgetTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("historical-funding-budget");
    static final class Counted extends AbstractList<OfferEvent> {
        final List<OfferEvent> values;int reads;
        Counted(List<OfferEvent> values){this.values=List.copyOf(values);}
        public int size(){return values.size();}
        public OfferEvent get(int index){reads++;return values.get(index);}
    }
    OfferEvent buy(String id,int item,String episode,int quantity,int filled,long spent,long sequence) {
        return OfferEvent.builder("fixture",1000+sequence,"BUYING").accountId(account)
            .eventIdentity(id,"session",episode).slot(item==561?0:1).sequence(sequence)
            .authenticatedObservation(true).item(item,"Item").buying(true).price(100)
            .quantities(quantity,filled).spent(spent).build();
    }
    OfferEvent empty(String id,long sequence) {
        return OfferEvent.builder("fixture",1000+sequence,"EMPTY").accountId(account)
            .eventIdentity(id,"session",null).slot(0).sequence(sequence).authenticatedObservation(true).build();
    }
    @Test public void repeatedFundingReadsDoNotWalkAnUnchangedSourcePrefix()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("budget.db"))) {
            var read=store.planningProjections(account);read.tradeScope.unsafeItems.add(561);
            var history=new Counted(List.of(buy("opening",561,"first",10,0,0,1),
                buy("partial",561,"first",10,5,500,2),empty("collected",3),buy("next",561,"second",20,0,0,4)));
            read.history=history;
            assertEquals(Long.valueOf(3000),BoundedHistoricalExposure.calculate(read).committedByItem.get(561));
            int qualifiedReads=history.reads;
            for(int i=0;i<30;i++)assertEquals(Long.valueOf(3000),BoundedHistoricalExposure.calculate(read).committedByItem.get(561));
            assertEquals("Reports and guidance must not repeatedly scan the same historical funding evidence",qualifiedReads,history.reads);
        }
    }
    @Test public void newSourcesScopeAndContradictoryFundingCannotBorrowAnEarlierBound() {
        var read=new SqliteStore.PlanningProjections(List.of(),List.of());read.tradeScope=new AutomaticTradeScope();
        read.tradeScope.unsafeItems.add(561);read.historicalFundingMemo=new BoundedHistoricalExposure.Memo();
        var sources=new ArrayList<OfferEvent>(List.of(buy("first",561,"first",10,0,0,1),
            buy("unrelated",562,"other",20,1,2001,2)));
        var original=new Counted(sources);read.history=original;
        assertEquals(Long.valueOf(1000),BoundedHistoricalExposure.calculate(read).committedByItem.get(561));
        read.tradeScope.unsafeItems.add(562);
        assertNull("Newly paused evidence must qualify its own funding",BoundedHistoricalExposure.calculate(read));
        int failedReads=original.reads;assertNull(BoundedHistoricalExposure.calculate(read));assertEquals(failedReads,original.reads);
        read.tradeScope.unsafeItems.remove(562);
        assertEquals(Long.valueOf(1000),BoundedHistoricalExposure.calculate(read).committedByItem.get(561));
        sources.add(empty("clear",3));sources.add(buy("second",561,"new",20,0,0,4));read.history=new Counted(sources);
        assertEquals(Long.valueOf(3000),BoundedHistoricalExposure.calculate(read).committedByItem.get(561));
        read.history=new Counted(List.of(buy("replacement",561,"different",40,0,0,1)));
        assertEquals(Long.valueOf(4000),BoundedHistoricalExposure.calculate(read).committedByItem.get(561));
        read.history=null;assertNull(BoundedHistoricalExposure.calculate(read));
        read.history=original;read.tradeScope.hasUnboundedReconciliation=true;
        assertNull("Global uncertainty cannot reuse bounded history",BoundedHistoricalExposure.calculate(read));
    }
    @Test public void everyReadStillChecksCurrentCostsUnknownHoldingsAndOfferReservations() {
        var memo=new BoundedHistoricalExposure.Memo();var history=new Counted(List.of(buy("old",561,"old",10,0,0,1)));
        var scope=new AutomaticTradeScope();scope.unsafeItems.add(561);
        var first=new SqliteStore.PlanningProjections(List.of(),List.of());
        first.tradeScope=scope;first.history=history;first.historicalFundingMemo=memo;
        assertEquals(Long.valueOf(1000),BoundedHistoricalExposure.calculate(first).committedByItem.get(561));int qualified=history.reads;
        for(long cost:List.of(600L,700L)) {
            var read=new SqliteStore.PlanningProjections(List.of(PositionProjection.ready(562,"Stock",2,cost,100,"source","offer")),
                List.of(OfferLifecycleProjection.restore(OfferLifecycleState.BUY_PARTIAL,1,"working","session",2,1002,
                    563,"Working buy",true,200,10,3,600,null)));
            read.tradeScope=scope;read.history=history;read.historicalFundingMemo=memo;
            var current=BoundedHistoricalExposure.calculate(read);assertEquals(Long.valueOf(cost),current.committedByItem.get(562));
            assertEquals(Long.valueOf(1400),current.committedByItem.get(563));assertEquals(1400,current.reservedBuyCoins);
        }
        var unknown=new SqliteStore.PlanningProjections(List.of(PositionProjection.reconcile(562,"Unknown",2,1,false,100,100,"source","offer","Unproven cost")),List.of());
        unknown.tradeScope=scope;unknown.history=history;unknown.historicalFundingMemo=memo;
        assertNull("Current unknown money stays unknown",BoundedHistoricalExposure.calculate(unknown));
        var error=new SqliteStore.PlanningProjections(List.of(),List.of(OfferLifecycleProjection.restore(
            OfferLifecycleState.ERROR_RECONCILIATION,1,"error","session",2,1002,563,"Uncertain offer",true,200,10,3,600,null)));
        error.tradeScope=scope;error.history=history;error.historicalFundingMemo=memo;
        assertNull("Current unknown exposure stays refused",BoundedHistoricalExposure.calculate(error));
        scope.positions.put(562,PositionProjection.ready(562,"Large current cost",1,Long.MAX_VALUE,100,"large","offer"));
        assertNull("Current aggregate overflow still refuses after historical funding is cached",BoundedHistoricalExposure.calculate(first));
        scope.positions.clear();assertNotNull(BoundedHistoricalExposure.calculate(first));
        assertEquals("Fresh state must be checked without repeating immutable historical work",qualified,history.reads);
    }
    @Test public void historicalOverflowCannotBecomeAZeroOrPartiallyAccumulatedBound() {
        var read=new SqliteStore.PlanningProjections(List.of(),List.of());read.tradeScope=new AutomaticTradeScope();
        read.tradeScope.unsafeItems.add(561);read.historicalFundingMemo=new BoundedHistoricalExposure.Memo();
        var sources=new ArrayList<OfferEvent>();
        for(int i=0;i<3;i++) {
            sources.add(empty("clear-"+i,i*2+1));
            sources.add(OfferEvent.builder("fixture",1100+i,"BUYING").accountId(account)
                .eventIdentity("large-"+i,"session","episode-"+i).slot(0).sequence(i*2+2)
                .item(561,"Large envelope").buying(true).price(Integer.MAX_VALUE).quantities(Integer.MAX_VALUE,0).build());
        }
        read.history=new Counted(sources);assertNull(BoundedHistoricalExposure.calculate(read));
        assertNull(BoundedHistoricalExposure.calculate(read));
        read.history=new Counted(sources.subList(0,2));
        assertEquals(Long.valueOf((long)Integer.MAX_VALUE*Integer.MAX_VALUE),BoundedHistoricalExposure.calculate(read).committedByItem.get(561));
    }
    @Test public void validatedCanonicalAppendReadsOnlyTheTailAndKeepsOldResultsImmutable() {
        var memo=new BoundedHistoricalExposure.Memo();var values=new ArrayList<OfferEvent>();
        for(int i=0;i<20000;i++)values.add(buy("old-"+i,561,"same",10,0,0,i+1));
        var prefix=new Counted(values);var original=memo.funding(prefix,Set.of(561));assertEquals(Long.valueOf(1000),original.get(561));
        values.add(empty("clear",20001));values.add(buy("new",561,"new",20,0,0,20002));
        var appended=new Counted(values);memo.appendCanonical(prefix,appended);
        assertEquals(2,appended.reads);assertEquals(Long.valueOf(3000),memo.funding(appended,Set.of(561)).get(561));
        assertEquals(2,appended.reads);assertEquals(Long.valueOf(1000),original.get(561));
        var expected=new BoundedHistoricalExposure.Memo().funding(List.copyOf(values),Set.of(561));
        assertEquals(expected,memo.funding(appended,Set.of(561)));
        assertEquals(Map.of(),memo.funding(appended,Set.of(562)));assertTrue(appended.reads>20000);
        memo.reset();int before=appended.reads;assertEquals(expected,memo.funding(appended,Set.of(561)));assertTrue(appended.reads>before);
    }
    @Test public void unqualifiedReplacementAndInvalidAppendedFundingCannotBorrowAPreviousResult() {
        var memo=new BoundedHistoricalExposure.Memo();var prefix=new Counted(List.of(buy("one",561,"one",10,0,0,1)));
        assertEquals(Long.valueOf(1000),memo.funding(prefix,Set.of(561)).get(561));
        var unrelated=new Counted(List.of(buy("other",561,"other",40,0,0,1)));
        memo.appendCanonical(new Counted(prefix.values),unrelated);
        assertEquals(Long.valueOf(4000),memo.funding(unrelated,Set.of(561)).get(561));
        var bad=new Counted(List.of(unrelated.get(0),buy("bad",561,"other",40,1,4001,2)));
        memo.appendCanonical(unrelated,bad);assertNull(memo.funding(bad,Set.of(561)));assertEquals(1,bad.reads);
        memo.reset();assertNull(memo.funding(bad,Set.of(561)));assertEquals(Map.of(),memo.funding(bad,Set.of(562)));
    }
}
