package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarketPriceProjectionTest {
    final long start=1800000000;final int bucket=3600;
    List<Candle> history(int count) {
        var rows=new ArrayList<Candle>();
        for(int i=0;i<count;i++)rows.add(new Candle(start+(long)i*bucket,1200+i*3,1000+i*3,10,12));
        return rows;
    }
    @Test public void projectionsUseIndependentSidesAndHonestOrderedQuantiles() {
        var p=new MarketPriceProjection().project(history(200),bucket,start+200L*bucket,24);
        assertTrue(p.reason,p.isAvailable());assertEquals(25,p.points.size());assertEquals(start+200L*bucket,p.originAt);
        for(var point:p.points){assertTrue(point.low.lower<=point.low.median);assertTrue(point.low.median<=point.low.upper);assertTrue(point.high.lower<=point.high.median);}
        assertTrue(p.points.get(24).low.median>p.points.get(0).low.median);
    }
    @Test public void futureAndOpenBucketsCannotChangeTheForecast() {
        var model=new MarketPriceProjection();var past=history(150);long now=start+150L*bucket;
        var a=model.project(past,bucket,now,8);
        past.add(new Candle(now,2000000,1000000,100000,100000));
        past.add(new Candle(now+bucket,2100000,1100000,100000,100000));
        var b=model.project(past,bucket,now,8);
        assertEquals(a.points.get(8).low.median,b.points.get(8).low.median,0);
    }
    @Test public void gapsAndMissingSidesAreNotForwardFilledAndConflictsRefuseProjection() {
        var model=new MarketPriceProjection();var sparse=history(100);
        for(int i=0;i<sparse.size();i+=2)sparse.set(i,new Candle(start+(long)i*bucket,null,1000,0,10));
        assertFalse(model.project(sparse,bucket,start+100L*bucket,8).isAvailable());
        var duplicate=history(100);duplicate.add(duplicate.get(0));assertTrue(model.project(duplicate,bucket,start+100L*bucket,8).isAvailable());
        duplicate.add(new Candle(start,9000,8000,1,1));
        assertEquals("Conflicting history buckets",model.project(duplicate,bucket,start+100L*bucket,8).reason);
    }
    @Test public void quietAndInsufficientHistoryStaysUnavailableInsteadOfDrawingInventedBands() {
        var model=new MarketPriceProjection();assertFalse(model.project(history(20),bucket,start+20L*bucket,8).isAvailable());
        assertFalse(model.project(history(100),bucket,start+104L*bucket,8).isAvailable());
    }
    @Test public void evaluationUsesEarlierPrefixesAndRetainsUnobservedOutcomes() {
        var model=new MarketPriceProjection();var data=history(200);
        var result=model.evaluate(data,bucket,start+200L*bucket,8);
        assertTrue(result.known>0);assertTrue(result.unknown>0);assertNotNull(result.knownOutcomeProjectionMaePercent);
        assertTrue(result.knownOutcomeRangeCoverage>=0 && result.knownOutcomeRangeCoverage<=1);
        assertTrue(result.coverageLower<=result.coverageUpper);assertTrue(result.coverageUpper<=1);
        data.add(new Candle(start+250L*bucket,2000000,1900000,1,1));
        var unchanged=model.evaluate(data,bucket,start+200L*bucket,8);
        assertEquals(result.knownOutcomeProjectionMaePercent,unchanged.knownOutcomeProjectionMaePercent);
        assertEquals("EXPERIMENTAL_DISPLAY_ONLY",result.promotionStatus);
    }
    @Test public void quoteAnchoringRequiresActualRecentTimestampedMarketEvidence() {
        var model=new MarketPriceProjection();long now=start+200L*bucket;var base=model.project(history(200),bucket,now,8);
        var quote=new com.flippingfriend.data.LatestPrice(1500,now-60,1300,now-90);
        var anchored=model.anchor(base,quote,now-10,now);
        assertEquals(now-10,anchored.originAt);assertEquals(1300,anchored.points.get(0).low.median,0);
        assertFalse(model.anchor(base,new com.flippingfriend.data.LatestPrice(1500,now+1,1300,now-90),now,now).isAvailable());
        assertFalse(model.anchor(base,quote,now-151,now).isAvailable());
    }
}
