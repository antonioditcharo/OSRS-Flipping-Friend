package com.flippingfriend.session;

import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.SaleAccounting;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.List;

/** Pure sale-maintenance authority shared by the client, companion and chronological replay. */
public final class SaleMaintenancePricing {
    public static final String VERSION="supported-sale-maintenance-4";
    public enum Status {
        ELIGIBLE, INVALID_QUANTITY, MISSING_PRICES, STALE_PRICES, IMMATERIAL_CHANGE,
        UNPROVEN_QUANTITY, UNKNOWN_CLOCKS, PLACEMENT_PATIENCE, RECENT_PROGRESS,
        MARKET_SUPPORT, COST_FLOOR
    }
    /** The same decision supplies both pricing and read-only investigation. No trading permission. */
    public static final class Evaluation {
        public final Status status;
        public final Quote quote;
        /** Earliest clock-only reassessment, not a promise of market support or authority. */
        public final long reassessAt;
        private Evaluation(Status status,Quote quote,long reassessAt) {
            this.status=status;this.quote=quote;this.reassessAt=reassessAt;
        }
        static Evaluation waitFor(Status status){return new Evaluation(status,null,0);}
    }
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
        return evaluate(item,offerPrice,remaining,position,latest,series,horizon,now,sellOnly,exits,tax,
            firstObservedAt,lastProgressAt).quote;
    }
    /** A collected reduction may recover with the market, but missing support cannot restore its target. */
    public Evaluation evaluateRelist(int item,int offerPrice,int remaining,Position position,LatestPrice latest,
        List<Candle> series,TradingHorizon horizon,Instant now,boolean sellOnly,
        SellTimingEngine exits,TaxCalculator tax,long firstObservedAt,long lastProgressAt) {
        var reduction=evaluate(item,offerPrice,remaining,position,latest,series,horizon,now,sellOnly,exits,tax,
            firstObservedAt,lastProgressAt);
        if(reduction.quote!=null || reduction.status!=Status.MARKET_SUPPORT && reduction.status!=Status.IMMATERIAL_CHANGE)
            return reduction;
        if(position==null || !position.isCostKnown() || remaining>position.getQuantity())return reduction;
        if(firstObservedAt<=0 || firstObservedAt>now.getEpochSecond() || lastProgressAt<firstObservedAt
            || lastProgressAt>now.getEpochSecond())return Evaluation.waitFor(Status.UNKNOWN_CLOCKS);
        var decision=exits.evaluateManagedExit(position,latest,null,series,horizon,now,0,true,sellOnly,false);
        if(!decision.isSell())return reduction;
        boolean cut=decision.getAction()==SellDecision.Action.CUT;
        long cost=SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining);
        int floor=cut?1:tax.breakEvenSellPriceForCost(item,cost,remaining);
        if(floor<=0)return Evaluation.waitFor(Status.COST_FLOOR);
        int price=Math.max(floor,decision.getPrice());
        // Fresh trades must support the actual recovered limit. A protected old target above
        // that market is not a recovery, even if a history refresh failed during collection.
        if(!cut && !sellOnly && price>latest.getHigh())return reduction;
        String reason=cut?decision.getReason():sellOnly?decision.getReason()
            :"Fresh market trades now support this non-loss relist price. The original target is not being restored above the current sell-side trade.";
        return new Evaluation(Status.ELIGIBLE,new Quote(price,floor,cost,true,cut,reason),0);
    }
    public Evaluation evaluate(int item,int offerPrice,int remaining,Position position,LatestPrice latest,
        List<Candle> series,TradingHorizon horizon,Instant now,boolean sellOnly,
        SellTimingEngine exits,TaxCalculator tax,long firstObservedAt,long lastProgressAt) {
        if(remaining<=0)return Evaluation.waitFor(Status.INVALID_QUANTITY);
        if(latest==null || !latest.isComplete())return Evaluation.waitFor(Status.MISSING_PRICES);
        if(latest.secondsSinceHigh(now)<0
            || latest.secondsSinceLow(now)<0 || latest.stalestSideSeconds(now)>300
            )return Evaluation.waitFor(Status.STALE_PRICES);
        if(offerPrice-latest.getHigh()<horizon.outbidDeadband(offerPrice))return Evaluation.waitFor(Status.IMMATERIAL_CHANGE);
        if(position!=null && remaining>position.getQuantity())return Evaluation.waitFor(Status.UNPROVEN_QUANTITY);
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
                if(floor<=0)return Evaluation.waitFor(Status.COST_FLOOR);price=Math.max(price,floor);
            }
            reason=cut?"Fresh market evidence has crossed the recorded stop loss."
                :"Finish session (sell only) is enabled; seeking a faster exit as requested.";
        } else {
            long at=now.getEpochSecond();
            int supported=supportedLowerAsk(series,offerPrice,horizon,at);
            if(firstObservedAt<=0 || firstObservedAt>at || lastProgressAt<firstObservedAt || lastProgressAt>at)
                return Evaluation.waitFor(Status.UNKNOWN_CLOCKS);
            long patience=horizon.staleOfferMinutes()*60L;
            long reassess=Math.max(firstObservedAt,lastProgressAt)+patience;
            if(at-firstObservedAt<patience)return new Evaluation(Status.PLACEMENT_PATIENCE,null,reassess);
            if(at-lastProgressAt<patience)return new Evaluation(Status.RECENT_PROGRESS,null,reassess);
            if(supported<=0)return Evaluation.waitFor(Status.MARKET_SUPPORT);
            if(known) {
                if(floor<=0)return Evaluation.waitFor(Status.COST_FLOOR);
            }
            price=Math.max(floor,Math.max(latest.getHigh()-1,supported));
            reason=!known ? "The offer has no recent fills, and two completed market buckets support a lower ask. Acquisition cost and profit remain unknown."
                :"The offer has no recent fills, and two completed market buckets support a profitable reduction. The exact after-tax acquisition cost remains protected; the original target is a reference, not a fill guarantee.";
        }
        if(offerPrice-(long)price<horizon.outbidDeadband(offerPrice))
            return Evaluation.waitFor(known && !cut && price==floor?Status.COST_FLOOR:Status.IMMATERIAL_CHANGE);
        return new Evaluation(Status.ELIGIBLE,new Quote(price,floor,cost,known,cut,reason),0);
    }
    /** Two distinct, completed recent 5m buckets with actual buy-side trades; never one spot print. */
    private static int supportedLowerAsk(List<Candle> series,int ask,TradingHorizon horizon,long now) {
        if(series==null)return 0;
        var recent=new java.util.TreeMap<Long,Candle>();
        for(var bar:series)if(bar!=null && bar.getTimestamp()>0 && bar.getTimestamp()%300==0
            && bar.getTimestamp()<=now-300 && bar.getTimestamp()>=now-1800) {
            var old=recent.put(bar.getTimestamp(),bar);
            if(old!=null && (!java.util.Objects.equals(old.getAvgHighPrice(),bar.getAvgHighPrice())
                || old.getHighPriceVolume()!=bar.getHighPriceVolume()))return 0;
        }
        if(recent.size()<2)return 0;
        int count=0,supported=Integer.MAX_VALUE;long previous=0;
        for(var bar:recent.descendingMap().values()) {
            if(count==0 && now-bar.getTimestamp()>900 || count>0 && previous-bar.getTimestamp()!=300)return 0;
            if(bar.getAvgHighPrice()==null || bar.getAvgHighPrice()<=0 || bar.getHighPriceVolume()<=0
                || ask-(long)bar.getAvgHighPrice()<horizon.outbidDeadband(ask))return 0;
            supported=Math.min(supported,Math.max(1,bar.getAvgHighPrice()-1));
            previous=bar.getTimestamp();if(++count==2)return supported;
        }
        return 0;
    }
}
