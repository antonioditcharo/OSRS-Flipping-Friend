package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PerformanceCompletionIndexTest {
    OfferEvent event(String id,String offer,int item,String type,int total,int filled,long at) {
        return OfferEvent.builder("fixture",at,type).eventIdentity(id,"session",offer).sequence(at)
            .item(item,"Fixture").buying(type.equals("BUYING") || type.equals("BOUGHT"))
            .price(100).quantities(total,filled).spent(filled*100L).build();
    }
    Set<String> completed(PerformanceCompletionIndex index,List<OfferEvent> history,Set<String> sources,long now) {
        return index.completed("account",1,10,history,sources,Set.of(),now);
    }
    @Test public void terminalSoldCountsOnceAfterFullSellingMoneyDeltaAndPartialsNeverCount() {
        var index=new PerformanceCompletionIndex();
        var first=event("partial","sale",561,"SELLING",3,1,10);
        var full=event("full","sale",561,"SELLING",3,3,20);
        var sold=event("terminal","sale",561,"SOLD",3,3,30);
        assertTrue(completed(index,List.of(first,full),Set.of("partial","full"),100).isEmpty());
        assertEquals(Set.of("sale"),completed(index,List.of(first,full,sold),Set.of("partial","full"),100));
        var repeated=event("terminal-again","sale",561,"SOLD",3,3,40);
        assertEquals(Set.of("sale"),completed(index,List.of(first,full,sold,repeated),Set.of("partial","full"),100));
        assertEquals(4,index.processedCount());
    }
    @Test public void futureSourcesAndRollbackClocksKeepConservativeOriginalCompletionBounds() {
        var index=new PerformanceCompletionIndex();
        var futureDisposal=event("money-future","sale",561,"SELLING",3,3,200);
        var earlyTerminal=event("sold-early-clock","sale",561,"SOLD",3,3,100);
        var history=List.of(futureDisposal,earlyTerminal);
        assertTrue(completed(index,history,Set.of("money-future"),150).isEmpty());
        assertEquals(Set.of("sale"),completed(index,history,Set.of("money-future"),200));
        assertTrue(completed(index,history,Set.of("money-future"),199).isEmpty());
        assertEquals(2,index.processedCount());
        // A later recorded disposal cannot qualify an earlier terminal retrospectively.
        var laterDisposal=event("money-later-row","other",561,"SELLING",3,3,50);
        var terminalBeforeMoney=event("sold-before-money","other",561,"SOLD",3,3,100);
        assertEquals(Set.of("sale"),completed(index,List.of(futureDisposal,earlyTerminal,terminalBeforeMoney,laterDisposal),
            Set.of("money-future","money-later-row"),200));
    }
    @Test public void lateFinancialSourceAndChangedGenerationRebuildWithoutBorrowingOtherItemsOrAccounts() {
        var index=new PerformanceCompletionIndex();
        var money=event("money","sale",561,"SELLING",3,3,10);var sold=event("sold","sale",561,"SOLD",3,3,20);
        var history=List.of(money,sold);
        assertTrue(index.completed("one",1,10,history,Set.of(),Set.of(),100).isEmpty());
        assertEquals(Set.of("sale"),index.completed("one",1,11,history,Set.of("money"),Set.of(),100));
        assertTrue(index.completed("one",2,11,history,Set.of("money"),Set.of("sale"),100).isEmpty());
        assertEquals(Set.of("sale"),index.completed("one",3,11,history,Set.of("money"),Set.of(),100));
        assertTrue(index.completed("two",3,11,history,Set.of(),Set.of(),100).isEmpty());
        var wrongItem=event("sold-wrong-item","sale",562,"SOLD",3,3,20);
        assertTrue(index.completed("one",4,11,List.of(money,wrongItem),Set.of("money"),Set.of(),100).isEmpty());
    }
    @Test public void appendedHeartbeatsProcessOnlyTailAndRetainOnlyDisposedEpisodes() {
        var index=new PerformanceCompletionIndex();var history=new ArrayList<OfferEvent>();
        var money=event("money","sale",561,"SELLING",3,3,10);var sold=event("sold","sale",561,"SOLD",3,3,20);
        history.add(money);history.add(sold);
        assertEquals(Set.of("sale"),completed(index,List.copyOf(history),Set.of("money"),100));
        var unchanged=List.copyOf(history);
        for(int repeat=0;repeat<100;repeat++)assertEquals(Set.of("sale"),completed(index,unchanged,Set.of("money"),100));
        assertEquals(2,index.processedCount());
        for(int i=0;i<1000;i++)history.add(event("heartbeat-"+i,"unfunded-"+i,562,"SELLING",1,0,30+i));
        assertEquals(Set.of("sale"),completed(index,List.copyOf(history),Set.of("money"),2000));
        assertEquals(1002,index.processedCount());assertEquals(1,index.retainedEpisodeCount());
        completed(index,List.copyOf(history),Set.of("money"),2000);assertEquals(1002,index.processedCount());
    }
    @Test public void invalidHistoryAndChangedPrefixCannotKeepPriorCompletions() {
        var index=new PerformanceCompletionIndex();var money=event("money","sale",561,"SOLD",3,3,10);
        assertEquals(Set.of("sale"),completed(index,List.of(money),Set.of("money"),100));
        assertTrue(completed(index,null,Set.of("money"),100).isEmpty());
        assertEquals(Set.of("sale"),completed(index,List.of(money),Set.of("money"),100));
        var changed=event("unfunded","sale",561,"SOLD",3,3,10);
        assertTrue(completed(index,List.of(changed),Set.of("money"),100).isEmpty());
        assertTrue(completed(index,Arrays.asList(changed,null),Set.of("money"),100).isEmpty());
        assertEquals(0,index.retainedEpisodeCount());
    }
    @Test public void incrementalBoundsMatchRecordedOrderScanAcrossOutOfOrderClocksAndPartialTerminals() {
        var random=new Random(72591);
        for(int sample=0;sample<60;sample++) {
            var index=new PerformanceCompletionIndex();var history=new ArrayList<OfferEvent>();var disposal=new HashSet<String>();
            for(int row=0;row<18;row++) {
                String id="sample-"+sample+"-row-"+row;
                String type=List.of("SELLING","SOLD","BUYING","BOUGHT").get(random.nextInt(4));
                int total=random.nextInt(4),filled=random.nextInt(4);
                history.add(event(id,"offer-"+random.nextInt(3),561+random.nextInt(2),type,total,filled,random.nextInt(40)));
                if(random.nextBoolean())disposal.add(id);
            }
            Set<String> gaps=Set.of("offer-2");
            for(int length=0;length<=history.size();length++) {
                var prefix=List.copyOf(history.subList(0,length));
                for(long now:new long[]{0,10,20,40,15,5,40}) {
                    var disposed=new HashSet<String>();var expected=new HashSet<String>();
                    for(var event:prefix) {
                        if(event.getObservedAt()>now)continue;
                        String key=event.getOfferIdentity()+":"+event.getItemId();
                        if(disposal.contains(event.getEventId()) && !event.isBuying())disposed.add(key);
                        if(disposed.contains(key) && !gaps.contains(event.getOfferIdentity()) && !event.isBuying()
                                && "SOLD".equals(event.getEventType()) && event.getTotalQuantity()>0
                                && event.getFilledQuantity()==event.getTotalQuantity())expected.add(event.getOfferIdentity());
                    }
                    assertEquals("Sample "+sample+", prefix "+length+", clock "+now,expected,
                        index.completed("account",1,10,prefix,disposal,gaps,now));
                }
            }
            assertEquals(history.size(),index.processedCount());
        }
    }
}
