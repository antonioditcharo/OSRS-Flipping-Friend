package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Warm analysis must exactly match cold policy after cash, horizon, limit, quote and bar changes. */
public class HistoryAnalysisParityTest {
    final long now=1700090000L;final int item=2361;
    List<Candle> bars(int n,int step,int volume) {
        List<Candle> out=new ArrayList<>();for(int i=0;i<n;i++){int drift=(int)Math.round(40*Math.sin(i*.37));out.add(new Candle(now-(long)(n-i)*step,2120+drift,2000+drift,volume,volume));}return out;
    }
    @Test public void warmAndColdGenerationStayIdenticalAcrossChangingResourcesAndHistory() {
        List<Candle> shortBars=bars(300,300,4000),longBars=bars(400,3600,48000);
        SeriesSource series=(id,step)->step.equals("5m")?shortBars:longBars;
        var warm=new CandidateFactory(series);var gson=new Gson();
        for(int phase=0;phase<8;phase++) {
            if(phase==4)shortBars.set(100,new Candle(shortBars.get(100).getTimestamp(),2100,1980,3000,2000));
            if(phase==5)longBars.get(50).setTimestamp(now-500000);
            int low=phase>=6?2001:2000;
            var quoted=new CandidateFactory.QuotedItem(new MarketIngestionService.Item(item,"Adamant bar",30000),
                new LatestPrice(2120,now-60,low,now-60),new Candle(now,2120,low,4000,4000),new Candle(now,2120,low,48000,48000));
            long cash=phase%2==0?1000000000:10000000;double horizon=phase%3==0?2.5:.4;
            var limit=Map.of(item,phase%2==0?30000:8000);var appetite=phase%2==0?RiskAppetite.BALANCED:RiskAppetite.CAUTIOUS;
            warm.setRiskAppetite(appetite);var cold=new CandidateFactory(series);cold.setRiskAppetite(appetite);
            var actual=warm.build(List.of(quoted),horizon,limit,cash,true,Instant.ofEpochSecond(now+phase));
            var expected=cold.build(List.of(quoted),horizon,limit,cash,true,Instant.ofEpochSecond(now+phase));
            assertEquals("Phase "+phase,gson.toJsonTree(expected),gson.toJsonTree(actual));
            if(phase==0)assertFalse(actual.isEmpty());
        }
    }
    @Test public void oversizedPublicItemCannotBecomeAWrappedCandidateOrSuppressItsValidNeighbour() {
        var shortBars=bars(300,300,4000);var longBars=bars(400,3600,48000);
        SeriesSource series=(id,step)->step.equals("5m")?shortBars:longBars;var gson=new Gson();
        var latest=new com.google.gson.JsonObject();var five=new com.google.gson.JsonObject();var hour=new com.google.gson.JsonObject();
        for(int id:List.of(1,2)) {
            var quote=new com.google.gson.JsonObject();quote.addProperty("low",id==1?4294969296L:2000L);
            quote.addProperty("high",id==1?4294969416L:2120L);quote.addProperty("lowTime",now-60);quote.addProperty("highTime",now-60);
            latest.add(""+id,quote);five.add(""+id,gson.toJsonTree(new Candle(now,2120,2000,4000,4000)));
            hour.add(""+id,gson.toJsonTree(new Candle(now,2120,2000,48000,48000)));
        }
        var metadata=Map.of(1,new MarketIngestionService.Item(1,"Unsupported",30000),2,new MarketIngestionService.Item(2,"Supported",30000));
        var mixed=new MarketIngestionService.MarketState(metadata,latest,five,hour,now);
        var actual=new CandidateFactory(series).build(mixed,2.5,Map.of(1,30000,2,30000),1000000000,true,Instant.ofEpochSecond(now));
        latest=latest.deepCopy();latest.remove("1");
        var good=new MarketIngestionService.MarketState(metadata,latest,five,hour,now);
        var expected=new CandidateFactory(series).build(good,2.5,Map.of(1,30000,2,30000),1000000000,true,Instant.ofEpochSecond(now));
        assertFalse(expected.isEmpty());assertTrue(actual.stream().allMatch(c->c.getItemId()==2));
        assertEquals(gson.toJsonTree(expected),gson.toJsonTree(actual));
        assertEquals(4294969296L,mixed.latest.getAsJsonObject("1").get("low").getAsLong());
    }
}
