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
}
