package com.flippingfriend.session;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.*;
public final class BuyRecoveryEvidence {
private BuyRecoveryEvidence() {}
    public static boolean recovered(int item,int price,int quantity,long acceptedAt,long notBefore,LatestPrice quote,List<Candle> series,
            TradingHorizon horizon,TaxCalculator tax,long minimum,long now) {
        var at=Instant.ofEpochSecond(now);
        if(item<=0 || price<=0 || quantity<=0 || acceptedAt<=0 || notBefore<acceptedAt || now<notBefore || quote==null || !quote.isComplete() || quote.getLow()<=0 || quote.getLow()>=Integer.MAX_VALUE
            || quote.getHigh()<quote.getLow() || quote.secondsSinceLow(at)<0 || quote.secondsSinceLow(at)>300
            || quote.secondsSinceHigh(at)<0 || quote.secondsSinceHigh(at)>300
            || Math.abs((long)quote.getLow()+1-price)<horizon.outbidDeadband(price)
            || tax.netProfit(item,quote.getLow()+1,Math.max(1,quote.getHigh()-1),quantity)<Math.max(1,minimum) || series==null)return false;
        // New completed evidence is required; timestamp-only heartbeats cannot revive a rejected order.
        var bars=new TreeMap<Long,Candle>();
        for(var bar:series)if(bar!=null && bar.getTimestamp()>acceptedAt && bar.getTimestamp()%300==0
            && bar.getTimestamp()<=now-300 && now-bar.getTimestamp()<=900) {
            if(bars.putIfAbsent(bar.getTimestamp(),bar)!=null)return false;
        }
        if(bars.size()<2)return false;
        var newest=bars.lastEntry();var previous=bars.lowerEntry(newest.getKey());
        if(previous==null || newest.getKey()-previous.getKey()!=300)return false;
        for(var bar:List.of(newest.getValue(),previous.getValue()))if(bar.getAvgLowPrice()==null || bar.getAvgHighPrice()==null
            || bar.getAvgLowPrice()<=0 || bar.getAvgHighPrice()<bar.getAvgLowPrice() || bar.getLowPriceVolume()<=0 || bar.getHighPriceVolume()<=0
            || tax.netProfit(item,quote.getLow()+1,Math.max(1,bar.getAvgHighPrice()-1),quantity)<Math.max(1,minimum))return false;
        return true;
    }
}
