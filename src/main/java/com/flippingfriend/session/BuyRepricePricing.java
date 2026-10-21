package com.flippingfriend.session;

import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;

/** Shared live/replay outbid-buy proposal. It cannot authorize a replacement entry. */
public final class BuyRepricePricing {
    public static final class Quote {
        public final int price,target;
        public final long profit;
        public final boolean replace;
        private Quote(int price,int target,long profit,boolean replace){this.price=price;this.target=target;this.profit=profit;this.replace=replace;}
    }
    public static Quote quote(int item,int standing,int quantity,long firstSeen,LatestPrice price,Candle trend,
            TradingHorizon horizon,TaxCalculator tax,long at,int stabilizedBuy,long minimumProfit) {
        return quote(item,standing,quantity,firstSeen,firstSeen,price,trend,horizon,tax,at,stabilizedBuy,minimumProfit);
    }
    public static Quote quote(int item,int standing,int quantity,long firstSeen,long lastProgressAt,LatestPrice price,Candle trend,
            TradingHorizon horizon,TaxCalculator tax,long at,int stabilizedBuy,long minimumProfit) {
        var now=Instant.ofEpochSecond(at);
        if(quantity<=0 || firstSeen<=0 || firstSeen>at || lastProgressAt<firstSeen || lastProgressAt>at
            || at-lastProgressAt<horizon.staleOfferMinutes()*60 || (at-firstSeen)/60<horizon.staleOfferMinutes()
            || price==null || !price.isComplete() || price.secondsSinceLow(now)<0 || price.secondsSinceLow(now)>300
            || price.secondsSinceHigh(now)<0 || price.secondsSinceHigh(now)>300
            || trend==null || trend.getTimestamp()<=0 || trend.getTimestamp()%300!=0
            || trend.getTimestamp()>at-300 || at-trend.getTimestamp()>900
            || trend.getAvgLowPrice()==null || trend.getAvgLowPrice()<=0 || trend.getLowPriceVolume()<=0
            || price.getLow()>=Integer.MAX_VALUE || stabilizedBuy<=0
            || stabilizedBuy-(long)standing<horizon.outbidDeadband(standing)
            || !horizon.isOutbid(true,standing,price,trend))return null;
        int target=Math.max(1,price.getHigh()-1);
        long profit=tax.netProfit(item,stabilizedBuy,target,quantity);
        return new Quote(stabilizedBuy,target,profit,profit>=Math.max(1,minimumProfit));
    }
    public static boolean overdue(long firstSeen,long lastProgressAt,long deadline,LatestPrice price,TradingHorizon horizon,long at) {
        var now=Instant.ofEpochSecond(at);
        return firstSeen>0 && firstSeen<=lastProgressAt && lastProgressAt<=at && deadline>=firstSeen && deadline<=at
            && at-lastProgressAt>=horizon.staleOfferMinutes()*60 && price!=null && price.isComplete()
            && price.secondsSinceLow(now)>=0 && price.secondsSinceLow(now)<=300
            && price.secondsSinceHigh(now)>=0 && price.secondsSinceHigh(now)<=300;
    }
    private BuyRepricePricing(){}
}
