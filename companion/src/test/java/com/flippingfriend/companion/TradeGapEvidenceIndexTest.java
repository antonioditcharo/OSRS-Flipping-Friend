package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reuses only a validated immutable prefix; new gaps can change earlier clear semantics. */
public class TradeGapEvidenceIndexTest {
    private final String account=AccountIdentity.fromProfileKey("scope-index");
    private OfferEvent event(String id,String type,int item,long sequence) {
        var b=OfferEvent.builder("fixture",100+sequence,type).accountId(account)
            .eventIdentity(id,"session",item==0?null:"offer-"+item).slot(0).sequence(sequence);
        if(item>0)b.item(item,"Item").buying(true).price(100).quantities(10,"BOUGHT".equals(type)?10:0)
            .spent("BOUGHT".equals(type)?1000:0);
        return b.build();
    }
    private List<OfferEvent> prefix(List<OfferEvent> values,int forbiddenBefore) {
        return new AbstractList<OfferEvent>() {
            public int size(){return values.size();}
            public OfferEvent get(int i){assertTrue("Prepared prefix must not be read again",i>=forbiddenBefore);return values.get(i);}
        };
    }
    @Test public void fillsAndRepeatedReadsProcessOnlyTheAppendedTail() {
        var index=new TradeGapEvidence.Index();var first=List.of(event("a","BUYING",561,1),event("gap","EMPTY",0,2));
        index.appendCanonical(null,first);assertEquals(Set.of(561),index.read(first,Map.of("gap",0),account).get("gap"));
        var values=new ArrayList<>(first);values.add(event("next","BUYING",562,3));
        var tail=prefix(values,first.size());index.appendCanonical(first,tail);
        for(int i=0;i<20;i++)assertEquals(Set.of(561),index.read(tail,Map.of("gap",0,"future",0),account).get("gap"));
        assertEquals(3,index.processedCount());
        var result=index.read(tail,Map.of("gap",0,"future",0),account);result.clear();
        assertEquals(2,index.read(tail,Map.of("gap",0,"future",0),account).size());
    }
    @Test public void aNewGapOnAnEarlierClearRebuildsTheCausalEpisode() {
        var history=List.of(event("a","BUYING",561,1),event("done","BOUGHT",561,2),event("clear","EMPTY",0,3),
            event("b","BUYING",562,4),event("later","EMPTY",0,5));
        var index=new TradeGapEvidence.Index();index.appendCanonical(null,history);
        assertEquals(Set.of(562),index.read(history,Map.of("later",0),account).get("later"));
        var gaps=Map.of("clear",0,"later",0);
        assertEquals(TradeGapEvidence.scope(history,gaps,account),index.read(history,gaps,account));
        assertEquals(Set.of(561,562),index.read(history,gaps,account).get("later"));
        assertEquals(10,index.processedCount());
        assertEquals(TradeGapEvidence.scope(history,Map.of("later",0),account),index.read(history,Map.of("later",0),account));
        assertEquals(15,index.processedCount());
    }
    @Test public void replacementForeignAccountsAndInvalidTailCannotReusePriorProof() {
        var good=List.of(event("a","BUYING",561,1),event("gap","EMPTY",0,2));
        for(OfferEvent invalid:Arrays.asList(good.get(0),null, event("foreign","BUYING",562,3)
                .withAccountId(AccountIdentity.fromProfileKey("someone-else")))) {
            var index=new TradeGapEvidence.Index();index.appendCanonical(null,good);index.read(good,Map.of("gap",0),account);
            var bad=new ArrayList<>(good);bad.add(invalid);index.appendCanonical(good,bad);
            assertTrue(index.read(bad,Map.of("gap",0),account).get("gap").isEmpty());
            assertTrue(index.read(bad,Map.of("gap",0),account).get("gap").isEmpty());
            var replacement=new ArrayList<>(good);index.appendCanonical(null,replacement);
            assertEquals(Set.of(561),index.read(replacement,Map.of("gap",0),account).get("gap"));
            assertTrue(index.read(replacement,Map.of("gap",0),AccountIdentity.fromProfileKey("other")).get("gap").isEmpty());
            assertEquals(Set.of(561),index.read(replacement,Map.of("gap",0),account).get("gap"));
        }
    }
    @Test public void evolvingGapMapsAndSourcePrefixesMatchTheFullCausalReader() {
        var random=new Random(83117);var index=new TradeGapEvidence.Index();List<OfferEvent> previous=null;
        var values=new ArrayList<OfferEvent>();var gaps=new HashMap<String,Integer>();
        for(int i=1;i<=160;i++) {
            int phase=i%4,item=phase==0?0:561+(i/4)%3;
            values.add(event("e"+i,phase==0?"EMPTY":phase==3?"BOUGHT":"BUYING",item,i));
            List<OfferEvent> current=List.copyOf(values);index.appendCanonical(previous,current);
            if(random.nextBoolean()){var selected=values.get(random.nextInt(values.size()));gaps.put(selected.getEventId(),selected.getItemId());}
            if(!gaps.isEmpty() && random.nextInt(5)==0)gaps.remove(gaps.keySet().iterator().next());
            assertEquals(TradeGapEvidence.scope(current,gaps,account),index.read(current,gaps,account));previous=current;
        }
        index.reset();assertTrue(index.read(null,Map.of("missing",0),account).get("missing").isEmpty());
    }
}
