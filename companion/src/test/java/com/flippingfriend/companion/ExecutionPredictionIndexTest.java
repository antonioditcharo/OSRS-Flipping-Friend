package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExecutionPredictionIndexTest {
    private final DurableExitContextTest fixture=new DurableExitContextTest();
    private final long at=DurableExitContextTest.AT;
    private OfferEvent change(OfferEvent event,String key,Object value) {
        var gson=new Gson();var raw=gson.toJsonTree(event).getAsJsonObject();raw.add(key,gson.toJsonTree(value));return gson.fromJson(raw,OfferEvent.class);
    }
    /** Exact #186 qualification loop; narrowing must not change its global first-ID stop. */
    private double reference(List<OfferEvent> history,OfferEvent event) {
        OfferEvent origin=null;
        for(OfferEvent recorded:history) {
            boolean same=Objects.equals(event.getAccountId(),recorded.getAccountId()) && Objects.equals(event.getSessionId(),recorded.getSessionId())
                && Objects.equals(event.getOfferIdentity(),recorded.getOfferIdentity()) && event.getSlot()==recorded.getSlot();
            if(same) {
                if(!TradeGapEvidence.valid(recorded) || recorded.getItemId()!=event.getItemId() || recorded.isBuying()!=event.isBuying()
                    || recorded.getFirstSeenAt()!=event.getFirstSeenAt() || recorded.getPrice()!=event.getPrice()
                    || recorded.getTotalQuantity()!=event.getTotalQuantity() || !"1".equals(recorded.getPredictionEvidenceVersion())
                    || !recorded.isPredictionComparable() || !event.getPredictionSourceEventId().equals(recorded.getPredictionSourceEventId()))return 0;
                if(origin==null) {
                    origin=recorded;
                    if(!origin.getEventId().equals(event.getPredictionSourceEventId()) || !ExecutionObservationLedger.qualifiesOriginal(origin))return 0;
                }
                if(recorded.getSequence()<origin.getSequence() || recorded.getObservedAt()<origin.getObservedAt())return 0;
            }
            if(event.getEventId().equals(recorded.getEventId()))return origin==null?0:origin.getPredictedMinutes();
        }
        return 0;
    }
    private void equal(ExecutionPredictionIndex index,List<OfferEvent> history,OfferEvent target) {
        assertEquals(reference(history,target),reference(index.throughFirstTarget(history,target),target),0);
    }
    @Test public void aLargeUnrelatedArchiveDoesNotEnterTheQualificationLoop() {
        var origin=fixture.open("one",at,new AcquisitionExitContext(140,90,60));
        var end=fixture.event("BOUGHT","one",at,at+30,10,10,1010,origin);var values=new ArrayList<OfferEvent>();
        for(int i=0;i<20000;i++)values.add(fixture.open("other-"+i,at+i+1,new AcquisitionExitContext(150,90,60)));
        values.add(origin);values.add(end);var index=new ExecutionPredictionIndex();equal(index,values,end);
        assertEquals(2,index.throughFirstTarget(values,end).size());assertEquals(10,reference(index.throughFirstTarget(values,end),end),0);
    }
    @Test public void firstGlobalIdStillStopsBeforeLaterEpisodeRowsAndContradictions() {
        var origin=fixture.open("one",at,new AcquisitionExitContext(140,90,60));
        var end=fixture.event("BOUGHT","one",at,at+30,10,10,1010,origin);
        var foreign=change(end,"offerIdentity","different-episode");var invalid=change(end,"price",999);
        for(var history:List.of(List.of(foreign,origin,end),List.of(origin,foreign,invalid,end),List.of(origin,end,invalid))) {
            var index=new ExecutionPredictionIndex();equal(index,history,end);
        }
        assertEquals(0,reference(List.of(foreign,origin,end),end),0);
        assertEquals(10,reference(List.of(origin,foreign,invalid,end),end),0);
    }
    @Test public void onlyNewRowsArePreparedAndAReplacedPrefixCannotKeepAnOldOrigin() {
        var origin=fixture.open("one",at,new AcquisitionExitContext(140,90,60));
        var end=fixture.event("BOUGHT","one",at,at+30,10,10,1010,origin);var index=new ExecutionPredictionIndex();
        List<OfferEvent> first=List.of(origin);index.appendCanonical(null,first);index.throughFirstTarget(first,origin);
        List<OfferEvent> tail=new AbstractList<OfferEvent>() {
            public int size(){return 2;}public OfferEvent get(int i){assertEquals(1,i);return end;}
        };
        index.appendCanonical(first,tail);assertEquals(10,reference(index.throughFirstTarget(tail,end),end),0);
        assertEquals(2,index.processedCount());
        List<OfferEvent> replacement=List.of(end);index.appendCanonical(null,replacement);equal(index,replacement,end);
        assertEquals(0,reference(index.throughFirstTarget(replacement,end),end),0);
    }
    @Test public void variedSourceOrderAndIdentityBoundariesMatchTheReference() {
        var random=new Random(8291);var values=new ArrayList<OfferEvent>();var origins=new ArrayList<OfferEvent>();
        var index=new ExecutionPredictionIndex();List<OfferEvent> previous=null;
        for(int i=0;i<100;i++) {
            OfferEvent event;
            if(origins.isEmpty() || random.nextBoolean()) {event=fixture.open("episode-"+i,at+i,new AcquisitionExitContext(140,90,60));origins.add(event);}
            else {
                var origin=origins.get(random.nextInt(origins.size()));event=fixture.event("BOUGHT",origin.getOfferIdentity(),origin.getFirstSeenAt(),at+200+i,10,10,1010,origin);
                if(random.nextInt(5)==0)event=change(event,"price",999);
            }
            values.add(event);List<OfferEvent> current=List.copyOf(values);index.appendCanonical(previous,current);
            for(var target:values)equal(index,current,target);previous=current;
        }
    }
}
