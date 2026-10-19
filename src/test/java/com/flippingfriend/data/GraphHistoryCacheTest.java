package com.flippingfriend.data;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GraphHistoryCacheTest {
    final long now=1800000000000L;
    List<Candle> bars(int price){return List.of(new Candle(now/1000-3600,price+10,price,10,10));}
    void await(java.util.function.BooleanSupplier condition)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(5);
        assertTrue("Graph request did not settle",condition.getAsBoolean());
    }
    @Test public void aFreshWarmHitAvoidsAnUpstreamRequestAndDuplicateCallsShareOneFetch()throws Exception {
        AtomicInteger warm=new AtomicInteger(),network=new AtomicInteger();AtomicLong time=new AtomicLong(now);
        try(var cache=new GraphHistoryCache((i,s)->{warm.incrementAndGet();return bars(100);},(i,s)->{network.incrementAndGet();return bars(101);},time::get)) {
            cache.get(1,"1h");await(()->!cache.get(1,"1h").isEmpty());
            for(int i=0;i<10;i++)assertEquals(100,cache.get(1,"1h").get(0).getAvgLowPrice().intValue());
            assertEquals(1,warm.get());assertEquals(0,network.get());
        }
    }
    @Test public void staleWarmHistoryFallsBackToPublicDataAndFailuresRetainGoodBars()throws Exception {
        AtomicLong time=new AtomicLong(now);AtomicBoolean fail=new AtomicBoolean();AtomicInteger calls=new AtomicInteger();
        try(var cache=new GraphHistoryCache((i,s)->List.of(new Candle(now/1000-20000,1,1,1,1)),(i,s)->{
            calls.incrementAndGet();if(fail.get())throw new java.io.IOException("fixture");return bars(150);},time::get)) {
            cache.get(1,"1h");await(()->!cache.get(1,"1h").isEmpty());assertEquals(1,calls.get());
            time.addAndGet(301000);fail.set(true);cache.get(1,"1h");await(()->cache.problem(1,"1h")!=null);
            assertEquals(150,cache.get(1,"1h").get(0).getAvgLowPrice().intValue());assertEquals(2,calls.get());
        }
    }
    @Test public void emptyOrMalformedResultsReleaseTheReservationAndRetryAfterBackoff()throws Exception {
        for(boolean malformed:new boolean[]{false,true}) {
            AtomicLong time=new AtomicLong(now);AtomicInteger calls=new AtomicInteger();
            try(var cache=new GraphHistoryCache((i,s)->null,(i,s)->calls.incrementAndGet()==1
                ?malformed?Arrays.asList((Candle)null):List.of():bars(170),time::get)) {
                cache.get(1,"1h");await(()->cache.problem(1,"1h")!=null);
                assertTrue(cache.get(1,"1h").isEmpty());assertEquals(1,calls.get());
                time.addAndGet(120001);cache.get(1,"1h");await(()->!cache.get(1,"1h").isEmpty());assertEquals(2,calls.get());
            }
        }
    }
    @Test public void changingItemsDropsAbandonedQueuedFetchesAndCloseCannotResurrectThem()throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger oldCalls=new AtomicInteger();
        try(var cache=new GraphHistoryCache((item,step)->{
            if(item==1){oldCalls.incrementAndGet();entered.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}}
            return bars(180);},(i,s)->bars(180),()->now)) {
            cache.get(1,"1h");assertTrue(entered.await(2,TimeUnit.SECONDS));cache.get(1,"6h");cache.get(2,"1h");
            release.countDown();await(()->!cache.get(2,"1h").isEmpty());assertEquals(1,oldCalls.get());
            cache.close();assertTrue(cache.get(2,"1h").isEmpty());cache.start();cache.get(2,"1h");await(()->!cache.get(2,"1h").isEmpty());
        }finally{release.countDown();}
    }
}
