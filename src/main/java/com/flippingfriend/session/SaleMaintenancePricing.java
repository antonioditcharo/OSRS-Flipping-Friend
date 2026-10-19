package com.flippingfriend.session;

import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.SaleAccounting;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.List;

/** Pure sale-maintenance authority shared by the client, companion and chronological replay. */
public final class SaleMaintenancePricing {
    public static final String VERSION="patient-sale-maintenance-2";
    public static final class Quote {
        public final int price, floor;
        public final long allocatedCost;
        public final boolean costKnown, lossCut;
        public final String reason;
        Quote(int price,int floor,long cost,boolean known,boolean cut,String reason) {
            this.price=price;this.floor=floor;allocatedCost=cost;costKnown=known;lossCut=cut;this.reason=reason;
        }
    }
    public Quote quote(int item,int offerPrice,int remaining,Position position,LatestPrice latest,
        List<Candle> series,TradingHorizon horizon,Instant now,boolean sellOnly,
        SellTimingEngine exits,TaxCalculator tax,long firstObservedAt,long lastProgressAt) {
        if(remaining<=0 || latest==null || !latest.isComplete() || latest.secondsSinceHigh(now)<0
            || latest.secondsSinceLow(now)<0 || latest.stalestSideSeconds(now)>300
            || offerPrice-latest.getHigh()<horizon.outbidDeadband(offerPrice)
            || position!=null && remaining>position.getQuantity()) return null;
        boolean known=position!=null && position.isCostKnown();
        long cost=known?SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining):0;
        int floor=known?tax.breakEvenSellPriceForCost(item,cost,remaining):1;
        boolean cut=known && SellTimingEngine.throughStop(position,latest.getHigh(),horizon.getProfile());
        int price;
        String reason;
        if(cut || sellOnly) {
            SellDecision exit=known?exits.evaluate(position,latest,null,series,horizon,now,0,false,sellOnly,false):null;
            // A fresh stop or explicit player wind-down is urgent, even just after placement/fill.
            price=exit==null?Math.max(1,latest.getHigh()-1):exit.getPrice();
            if(cut)floor=1;
            else if(known) {
                // Preserve the existing maintenance loss guard. Explicit wind-down bypasses
                // patience/target protection, not this exact remaining-cost floor.
                if(floor<=0)return null;price=Math.max(price,floor);
            }
            reason=cut?"Fresh market evidence has crossed the recorded stop loss."
                :"Finish session (sell only) is enabled; seeking a faster exit as requested.";
        } else {
            long at=now.getEpochSecond();
            if(firstObservedAt<=0 || firstObservedAt>at || lastProgressAt<firstObservedAt || lastProgressAt>at
                || (at-firstObservedAt)/60.0<horizon.patienceMinutes(known?position.getPredictedSellMinutes():0)
                || at-lastProgressAt<horizon.staleOfferMinutes()*60
                || !sustainedLowerAsk(series,offerPrice,horizon,at))return null;
            if(known) {
                if(floor<=0)return null;
                floor=SellTimingEngine.protectedPrice(position,horizon,now,floor);
                // Without an original target, do not invent permission to discard a healthy
                // listing's profit. Reassess at the configured horizon, or on a real stop.
                if(position.getTargetSellPrice()<=0 && position.minutesHeld(at)<horizon.maxHoldMinutes())
                    floor=Math.max(floor,offerPrice);
            }
            price=Math.max(floor,latest.getHigh()-1);
            reason=!known ? "The full expected sell time has elapsed without recent fills, and two completed market buckets support a lower ask. Acquisition cost and profit remain unknown."
                : position.minutesHeld(at)>=horizon.maxHoldMinutes()
                ?"The holding horizon has elapsed, the offer has stalled, and two completed market buckets support a lower ask. The exact after-tax cost floor remains protected."
                :"The full expected sell time has elapsed without recent fills, and two completed market buckets support a lower ask. The original target remains protected.";
        }
        if(offerPrice-(long)price<horizon.outbidDeadband(offerPrice))return null;
        return new Quote(price,floor,cost,known,cut,reason);
    }
    /** Two distinct, completed recent 5m buckets with actual buy-side trades; never one spot print. */
    private static boolean sustainedLowerAsk(List<Candle> series,int ask,TradingHorizon horizon,long now) {
        if(series==null)return false;
        var recent=new java.util.TreeMap<Long,Candle>();
        for(var bar:series)if(bar!=null && bar.getTimestamp()>0 && bar.getTimestamp()%300==0
            && bar.getTimestamp()<=now-300 && bar.getTimestamp()>=now-1800) {
            var old=recent.put(bar.getTimestamp(),bar);
            if(old!=null && (!java.util.Objects.equals(old.getAvgHighPrice(),bar.getAvgHighPrice())
                || old.getHighPriceVolume()!=bar.getHighPriceVolume()))return false;
        }
        if(recent.size()<2)return false;
        int count=0;long previous=0;
        for(var bar:recent.descendingMap().values()) {
            if(count==0 && now-bar.getTimestamp()>900 || count>0 && previous-bar.getTimestamp()!=300)return false;
            if(bar.getAvgHighPrice()==null || bar.getAvgHighPrice()<=0 || bar.getHighPriceVolume()<=0
                || ask-(long)bar.getAvgHighPrice()<horizon.outbidDeadband(ask))return false;
            previous=bar.getTimestamp();if(++count==2)return true;
        }
        return false;
    }
}
