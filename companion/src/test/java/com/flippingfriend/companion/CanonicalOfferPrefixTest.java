package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CanonicalOfferPrefixTest {
    private OfferEvent row(int i){return OfferEvent.builder("c",1800000000+i,"EMPTY").slot(i%8).build();}
    @Test public void appendingAcrossBoundariesPreservesOldSnapshotsAndSourceOrder() {
        var rows=new ArrayList<OfferEvent>();var retained=new ArrayList<CanonicalOfferPrefix>();
        CanonicalOfferPrefix current=CanonicalOfferPrefix.append(null,List.of());
        for(int i=0;i<1300;i++) {
            var event=row(i);var next=CanonicalOfferPrefix.append(current,List.of(event));rows.add(event);
            assertEquals(rows,next);assertEquals(i,current.size());
            assertEquals(i/256,next.sharedBlocks(current));
            retained.add(current);current=next;
        }
        for(var prefix:retained)assertEquals(rows.subList(0,prefix.size()),prefix);
        assertSame(current,CanonicalOfferPrefix.append(current,List.of()));
        try{current.set(0,row(9));fail();}catch(UnsupportedOperationException expected){}
        try{current.get(current.size());fail();}catch(IndexOutOfBoundsException expected){}
    }
    @Test public void mutableInputsAndLargeAppendsCannotMutatePriorOrPreparedPrefixes() {
        var old=new ArrayList<OfferEvent>();for(int i=0;i<193526;i++)old.add(row(i));
        var first=CanonicalOfferPrefix.append(old,List.of());
        var tail=new ArrayList<OfferEvent>();for(int i=0;i<600;i++)tail.add(row(i));
        var second=CanonicalOfferPrefix.append(first,tail);
        assertEquals(193526,first.size());assertEquals(194126,second.size());
        assertEquals(first.size()/256,first.sharedBlocks(second));
        var original=first.get(0);old.clear();tail.clear();assertSame(original,first.get(0));assertSame(original,second.get(0));
        assertEquals(first,second.subList(0,first.size()));
    }
}
