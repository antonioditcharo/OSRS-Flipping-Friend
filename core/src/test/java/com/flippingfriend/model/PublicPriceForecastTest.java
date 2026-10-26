package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PublicPriceForecastTest {
    @Test public void liveMissReturnsFallbackWithoutRunningAnalysisOnTheCallingThreadAndCopiesInputs()throws Exception {
        var field=PublicPriceForecast.class.getDeclaredField("WORKER");field.setAccessible(true);
        var worker=(java.util.concurrent.ThreadPoolExecutor)field.get(null);
        var started=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        worker.execute(()->{started.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));
        List<Candle> hours=rows(3600,366,-1),original=new ArrayList<>();
        for(Candle c:hours)original.add(new Candle(c.getTimestamp(),c.getAvgHighPrice(),c.getAvgLowPrice(),c.getHighPriceVolume(),c.getLowPriceVolume()));
        long now=hours.get(365).getTimestamp()+3600;
        try {
            assertNull(PublicPriceForecast.ready(List.of(),hours,now));
            assertNull(PublicPriceForecast.cached(List.of(),original,now));
            hours.get(0).setTimestamp(hours.get(1).getTimestamp());
        }finally{release.countDown();}
        worker.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        var ready=PublicPriceForecast.cached(List.of(),original,now);assertNotNull(ready);
        assertTrue(ready.at(true,28800,93700).available());
        assertFalse(PublicPriceForecast.fit(List.of(),hours,now).at(true,28800,93700).available());
    }
    private static List<Candle> rows(int bucket,int count,int direction) {
        List<Candle> rows=new ArrayList<>();
        for(int i=0;i<count;i++){int price=100000+direction*i*20;rows.add(new Candle(1_700_006_400L+(long)i*bucket,price+1000,price,100,100));}
        return rows;
    }
    @Test public void separatesSidesAndHorizonAndDoesNotForceAFlatPath() {
        List<Candle> shortRows=rows(300,365,1),hours=rows(3600,365,1);long now=hours.get(364).getTimestamp()+3600;
        // Align the completed short history with the same observation time.
        long offset=now-shortRows.get(364).getTimestamp()-300;
        List<Candle> shifted=new ArrayList<>();for(Candle c:shortRows)shifted.add(new Candle(c.getTimestamp()+offset,c.getAvgHighPrice(),c.getAvgLowPrice(),100,100));
        PublicPriceForecast model=PublicPriceForecast.fit(shifted,hours,now);
        var near=model.at(false,300,107280);var far=model.at(true,28800,108280);
        assertTrue(near.available());assertTrue(far.available());assertTrue(near.directional);assertTrue(far.directional);
        assertTrue(near.median>107280);assertTrue(far.median>108280);assertTrue(far.median-108280>near.median-107280);
    }
    @Test public void missingAndConflictingEvidenceCannotCreatePricesOrCompressTime() {
        List<Candle> hours=rows(3600,365,0);long now=hours.get(364).getTimestamp()+3600;
        assertTrue(PublicPriceForecast.fit(List.of(),hours,now).at(true,28800,101000).available());
        var conflict=new ArrayList<>(hours);conflict.add(new Candle(hours.get(20).getTimestamp(),9,8,1,1));
        assertFalse(PublicPriceForecast.fit(List.of(),conflict,now).at(true,28800,101000).available());
        assertFalse(PublicPriceForecast.fit(List.of(),hours,now+10801).at(true,28800,101000).available());
        assertFalse(PublicPriceForecast.fit(List.of(),hours.subList(350,365),now).at(true,28800,101000).available());
        var sparse=new ArrayList<Candle>();for(int i=0;i<hours.size();i+=9)sparse.add(hours.get(i));
        assertFalse(PublicPriceForecast.fit(List.of(),sparse,now).at(true,28800,101000).available());
    }
    @Test public void futureRowsAreExcludedAndMutableInputsInvalidateCache() {
        List<Candle> hours=rows(3600,365,1);long now=hours.get(364).getTimestamp()+3600;
        var first=PublicPriceForecast.fit(List.of(),hours,now).at(true,28800,108280);
        var future=new ArrayList<>(hours);future.add(new Candle(now+3600,1,1,1,1));
        assertEquals(first.median,PublicPriceForecast.fit(List.of(),future,now).at(true,28800,108280).median,0);
        hours.get(10).setTimestamp(hours.get(11).getTimestamp());
        assertFalse(PublicPriceForecast.fit(List.of(),hours,now).at(true,28800,108280).available());
    }
    @Test public void constantHistoryChoosesPersistenceWithoutInventingProfitOrVariance() {
        List<Candle> hours=rows(3600,365,0);long now=hours.get(364).getTimestamp()+3600;
        var result=PublicPriceForecast.fit(List.of(),hours,now).at(true,28800,101000);
        assertEquals("PERSISTENCE",result.model);assertFalse(result.directional);assertEquals(101000,result.median,1e-8);
        assertEquals(result.median,result.lower,1e-8);assertEquals(result.median,result.upper,1e-8);
        assertFalse(PublicPriceForecast.fit(List.of(),hours,now).at(true,28800,Integer.MAX_VALUE).directional);
    }
}
