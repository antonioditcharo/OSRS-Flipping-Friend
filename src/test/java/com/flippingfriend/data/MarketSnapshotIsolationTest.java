package com.flippingfriend.data;

import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarketSnapshotIsolationTest {
    @Test public void publisherReplacementCannotEmptyOrChangeAnExistingPlanningSnapshot() {
        var metadata=new LinkedHashMap<Integer,ItemMetadata>();metadata.put(561,new ItemMetadata(561,"Rune",false,1000,0));
        var latest=new LinkedHashMap<Integer,LatestPrice>();latest.put(561,new LatestPrice(100,1000L,90,1000L));
        var bars=new LinkedHashMap<Integer,Candle>();bars.put(561,new Candle(600,100,90,1,1));
        var snapshot=new MarketSnapshot(metadata,latest,bars,bars,Instant.ofEpochSecond(1000),900);
        metadata.clear();latest.clear();bars.clear();
        assertTrue(snapshot.isUsable());assertEquals(Integer.valueOf(100),snapshot.latest(561).getHigh());
        assertNotNull(snapshot.fiveMinute(561));assertNotNull(snapshot.hourly(561));assertEquals(900,snapshot.getLatestFeedObservedAt());
    }
}
