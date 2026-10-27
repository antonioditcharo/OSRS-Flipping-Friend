package com.flippingfriend.companion;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImmutablePrefixMapTest {
    @Test public void additionsReplacementsAndRevocationsPreserveEveryPreviouslyPublishedSnapshot() {
        var expected=new HashMap<String,Integer>();ImmutablePrefixMap<String,Integer> actual=ImmutablePrefixMap.empty();
        var random=new Random(913);var snapshots=new ArrayList<Map<String,Integer>>();var oracle=new ArrayList<Map<String,Integer>>();
        for(int i=0;i<20000;i++) {
            String key="event-"+random.nextInt(5000);
            if(random.nextInt(4)==0){expected.remove(key);actual=actual.without(key);}
            else {expected.put(key,i);actual=actual.with(key,i);}
            if(i%1000==0){snapshots.add(actual);oracle.add(new HashMap<>(expected));assertEquals(expected,actual);assertEquals(actual,expected);}
        }
        assertEquals(expected,actual);
        for(int i=0;i<snapshots.size();i++)assertEquals(oracle.get(i),snapshots.get(i));
        try{actual.put("mutation",1);fail();}catch(UnsupportedOperationException expectedFailure){}
        try{actual.entrySet().iterator().next().setValue(1);fail();}catch(UnsupportedOperationException expectedFailure){}
    }
    @Test public void collidingHashesAndAllHashBitsRetainDistinctKeysAcrossRemoval() {
        assertEquals("Aa".hashCode(),"BB".hashCode());
        var a=ImmutablePrefixMap.<String,Integer>empty().with("Aa",1).with("BB",2).with("AaAa",3).with("BBBB",4);
        assertEquals(Map.of("Aa",1,"BB",2,"AaAa",3,"BBBB",4),a);
        var b=a.without("Aa").without("AaAa");assertEquals(Map.of("BB",2,"BBBB",4),b);
        assertEquals(1,a.get("Aa").intValue());assertEquals(3,a.get("AaAa").intValue());
        var extreme=ImmutablePrefixMap.<Integer,Integer>empty();var expected=new HashMap<Integer,Integer>();
        for(int key:new int[]{0,Integer.MIN_VALUE,Integer.MAX_VALUE,-1,1,32,1024,1<<30}) {extreme=extreme.with(key,key);expected.put(key,key);}
        assertEquals(expected,extreme);
        for(int key:new ArrayList<>(expected.keySet())){expected.remove(key);extreme=extreme.without(key);assertEquals(expected,extreme);}
        assertTrue(extreme.isEmpty());
    }
    @Test public void equalUpdatesAndMissingRemovalsReuseTheSnapshotAndIterationDoesNotMaterializeHistory() {
        var a=ImmutablePrefixMap.<String,String>empty().with("source","context");
        assertSame(a,a.with("source",new String("context")));assertSame(a,a.without("missing"));
        assertNull(a.get(null));assertNull(a.get("missing"));assertFalse(a.containsKey("missing"));
        var iterator=a.entrySet().iterator();assertEquals(Map.entry("source","context"),iterator.next());assertFalse(iterator.hasNext());
        try{iterator.next();fail();}catch(NoSuchElementException expected){}
    }
}
