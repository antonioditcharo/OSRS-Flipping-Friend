package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.*;

/** Adds observed, forecast-supported limit alternatives; never removes the verified local grid. */
public final class PredictivePriceChoices {
    private PredictivePriceChoices(){}
    public static int horizon(double hours){return (int)Math.max(300,Math.min(28800,Math.round(hours*3600)));}
    public static int[] prices(boolean buying,LatestPrice quote,List<Candle> recent,PublicPriceForecast forecast,
            int seconds,double[] offsets,long now) {
        int anchor=buying?quote.getLow():quote.getHigh();Set<Integer> prices=new LinkedHashSet<>();
        for(double offset:offsets)prices.add(PriceOffset.apply(anchor,offset));
        if(forecast==null || quote.getLow()>quote.getHigh() || quote.secondsSinceHigh(Instant.ofEpochSecond(now))<0
            || quote.secondsSinceLow(Instant.ofEpochSecond(now))<0 || quote.stalestSideSeconds(Instant.ofEpochSecond(now))>120)
            return array(prices);
        var estimate=forecast.at(!buying,seconds,anchor);
        if(!estimate.available() || !estimate.directional)return array(prices);
        long minimum=Long.MAX_VALUE,maximum=0;int buckets=0;Set<Long> seen=new HashSet<>();
        for(Candle c:recent==null?List.<Candle>of():recent) {
            if(c==null || c.getTimestamp()>now-300 || c.getTimestamp()<now-14400 || !seen.add(c.getTimestamp()))continue;
            Integer price=buying?c.getAvgLowPrice():c.getAvgHighPrice();int volume=buying?c.getLowPriceVolume():c.getHighPriceVolume();
            if(price==null || price<=0 || volume<=0)continue;
            minimum=Math.min(minimum,price);maximum=Math.max(maximum,price);buckets++;
        }
        if(buckets>=24) {
            // At most two forecast prices, bounded by observed movement/spread. The existing fill
            // curve must still price the actual order; no forecast creates volume at an unseen level.
            long reach=Math.max(maximum-minimum,2L*(quote.getHigh()-quote.getLow()));
            for(double value:new double[]{estimate.median,buying?estimate.lower:estimate.upper}) {
                long price=Math.round(value);price=Math.max(Math.max(1,anchor-reach),Math.min(Math.min(Integer.MAX_VALUE,anchor+reach),price));
                if(price>0 && price<=Integer.MAX_VALUE)prices.add((int)price);
            }
        }
        return array(prices);
    }
    private static int[] array(Set<Integer> values){return values.stream().mapToInt(Integer::intValue).toArray();}
}
