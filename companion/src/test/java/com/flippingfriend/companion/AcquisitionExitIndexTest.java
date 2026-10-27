package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Full-reader comparisons retain original context, including retroactive duplicate-advice refusal. */
public class AcquisitionExitIndexTest {
    private final DurableExitContextTest fixture=new DurableExitContextTest();
    private final long at=DurableExitContextTest.AT;
    private OfferEvent modern(String episode,String advice,long time) {
        return fixture.independentOpen(episode,advice,time,time+1,0);
    }
    private OfferEvent renameLegacy(OfferEvent e,String id) {
        var json=new Gson();var raw=json.toJsonTree(e).getAsJsonObject();raw.addProperty("eventId",id);
        raw.addProperty("predictionSourceEventId",id);raw.getAsJsonObject("exitContext").addProperty("sourceEventId",id);
        return json.fromJson(raw,OfferEvent.class);
    }
    private void equal(AcquisitionExitEvidence.Index index,List<OfferEvent> history) {
        assertEquals(AcquisitionExitEvidence.read(history),index.read(history));
    }
    @Test public void aLaterConflictingOriginalRevokesPriorContextWithoutMutatingAnEarlierSnapshot() {
        var index=new AcquisitionExitEvidence.Index();var a=modern("one","same-advice",at);
        List<OfferEvent> first=List.of(a,fixture.independentFill(a,at+3,2,202));
        index.appendCanonical(null,first);equal(index,first);var snapshot=index.read(first);assertEquals(2,snapshot.size());
        long revision=index.existingContextRevision();
        var values=new ArrayList<>(first);values.add(modern("two","same-advice",at+4));List<OfferEvent> next=List.copyOf(values);
        index.appendCanonical(first,next);equal(index,next);assertTrue(index.read(next).isEmpty());assertEquals(2,snapshot.size());
        assertTrue(index.existingContextRevision()>revision);
        try{snapshot.clear();fail("A consumer cannot corrupt prepared context");}catch(UnsupportedOperationException expected){}
    }
    @Test public void duplicateAdviceCannotEraseALaterLegacyContributionWithTheSameEventId() {
        var index=new AcquisitionExitEvidence.Index();var a=modern("one","same-advice",at);
        var legacy=renameLegacy(fixture.open("legacy",at+2,new AcquisitionExitContext(150,95,90)),a.getEventId());
        List<OfferEvent> first=List.of(a,legacy);index.appendCanonical(null,first);equal(index,first);
        List<OfferEvent> next=List.of(a,legacy,modern("two","same-advice",at+4));index.appendCanonical(first,next);equal(index,next);
        assertEquals(legacy.getExitContext(),index.read(next).get(a.getEventId()));
    }
    @Test public void invalidLaterRowsKeepPreviouslyQualifiedEventsButCannotQualifyTheFuture() {
        var index=new AcquisitionExitEvidence.Index();var a=modern("one","advice",at);
        var good=fixture.independentFill(a,at+3,2,202);var bad=fixture.independentFill(a,at+4,3,303).withAcceptedBuyPlanEvidenceVersion("2");
        var later=fixture.independentFill(a,at+5,4,404);List<OfferEvent> previous=null;
        for(var history:List.of(List.of(a),List.of(a,good),List.of(a,good,bad),List.of(a,good,bad,later))) {
            index.appendCanonical(previous,history);equal(index,history);previous=history;
        }
        assertEquals(Set.of(a.getEventId(),good.getEventId()),index.read(previous).keySet());
    }
    @Test public void validatedAppendsNeverRereadPreparedRowsAndReplacementPrefixesReset() {
        var index=new AcquisitionExitEvidence.Index();var a=modern("one","advice",at);List<OfferEvent> first=List.of(a);
        index.appendCanonical(null,first);equal(index,first);var fill=fixture.independentFill(a,at+3,10,1010);
        List<OfferEvent> tailOnly=new AbstractList<OfferEvent>() {
            public int size(){return 2;}public OfferEvent get(int i){assertEquals("Old prefix is already prepared",1,i);return fill;}
        };
        index.appendCanonical(first,tailOnly);assertEquals(AcquisitionExitEvidence.read(List.of(a,fill)),index.read(tailOnly));
        for(int i=0;i<10;i++)assertEquals(2,index.read(tailOnly).size());assertEquals(2,index.processedCount());
        var replacement=List.of(fill);index.appendCanonical(null,replacement);equal(index,replacement);assertTrue(index.read(replacement).isEmpty());
        assertTrue(index.read(null).isEmpty());equal(index,first);
    }
    @Test public void mixedEvolvingPrefixesMatchTheOriginalReader() {
        var index=new AcquisitionExitEvidence.Index();var random=new Random(42319);var values=new ArrayList<OfferEvent>();
        List<OfferEvent> previous=null;var origins=new ArrayList<OfferEvent>();
        for(int i=0;i<150;i++) {
            OfferEvent event;
            if(!origins.isEmpty() && random.nextBoolean()) {
                var origin=origins.get(random.nextInt(origins.size()));event=fixture.independentFill(origin,at+1000+i,2,202);
                if(random.nextInt(5)==0)event=event.withAcceptedBuyPlanEvidenceVersion("2");
            } else {
                event=random.nextBoolean()?modern("episode-"+i,"advice-"+(i%11),at+i*3)
                    :fixture.open("legacy-"+i,at+i*3,new AcquisitionExitContext(150,95,90));origins.add(event);
            }
            values.add(event);List<OfferEvent> current=List.copyOf(values);index.appendCanonical(previous,current);equal(index,current);previous=current;
        }
    }
}
