package com.flippingfriend.model;

import com.flippingfriend.data.*;
import com.flippingfriend.session.Position;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarketDropDetectorTest {
    final Instant now=Instant.ofEpochSecond(1800000600);
    MarketSnapshot market(int current,int reference,long lowAt,long bucket,int volume) {
        return new MarketSnapshot(Map.of(1,new ItemMetadata(1,"Watch item",true,100,0)),
            Map.of(1,new LatestPrice(120,lowAt,current,lowAt)),
            Map.of(1,new Candle(bucket,120,reference,volume,volume)),Map.of(),now);
    }
    @Test public void scansMarketWithoutOwningTheItemAndDoesNotConfuseSpreadWithDrop() {
        var detector=new MarketDropDetector();
        assertTrue(detector.detect(market(100,100,now.getEpochSecond(),now.getEpochSecond()-600,20),now).isEmpty());
        assertEquals(1,detector.detect(market(80,100,now.getEpochSecond(),now.getEpochSecond()-600,20),now).size());
    }
    @Test public void refusesStaleFutureThinAndOverlappingBuckets() {
        var detector=new MarketDropDetector();long t=now.getEpochSecond();
        assertTrue(detector.detect(market(80,100,t-121,t-600,20),now).isEmpty());
        assertTrue(detector.detect(market(80,100,t+1,t-600,20),now).isEmpty());
        assertTrue(detector.detect(market(80,100,t,t-100,20),now).isEmpty());
        assertTrue(detector.detect(market(80,100,t,t-600,9),now).isEmpty());
    }
    @Test public void accountAndManagedQuantityControlAlertsWithoutAWebhook() {
        var alerts=new HeldStockDropAlerts();long t=now.getEpochSecond();
        var drops=new MarketDropDetector().detect(market(80,100,t,t-600,20),now);
        var stock=List.of(new Position(1,"Watch item",2,200,t-100,true));
        assertTrue(alerts.update("a",List.of(),drops).isEmpty());
        assertEquals(1,alerts.update("a",stock,drops).size());
        assertTrue(alerts.update("a",stock,drops).isEmpty());
        assertTrue(alerts.update("a",stock,List.of()).isEmpty());
        assertTrue(alerts.update("a",stock,drops).isEmpty());
        assertEquals(1,alerts.update("b",stock,drops).size());
        assertTrue(alerts.update(null,stock,drops).isEmpty());
    }
}
