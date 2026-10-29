package com.flippingfriend.session;

import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.SaleAccounting;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.List;

/** Pure sale-maintenance authority shared by the client, companion and chronological replay. */
public final class SaleMaintenancePricing {
    public static final String VERSION="supported-sale-maintenance-12";
    public enum Status {
        ELIGIBLE, INVALID_QUANTITY, MISSING_PRICES, STALE_PRICES, IMMATERIAL_CHANGE,
        UNPROVEN_QUANTITY, UNKNOWN_CLOCKS, PLACEMENT_PATIENCE, RECENT_PROGRESS,
        MARKET_SUPPORT, COST_FLOOR, ACCOUNT_HORIZON_HOLD
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
        public final int minimumReduction;
        public final boolean fineTick;
        public final String reason;
        Quote(int price,int floor,long cost,boolean known,boolean cut,String reason) {
            this(price,floor,cost,known,cut,reason,1,false);
        }
        Quote(int price,int floor,long cost,boolean known,boolean cut,String reason,int minimumReduction,boolean fineTick) {
            this.price=price;this.floor=floor;allocatedCost=cost;costKnown=known;lossCut=cut;this.reason=reason;
            this.minimumReduction=minimumReduction;this.fineTick=fineTick;
        }
        /** Includes a saved small reduction whose optional supporting history has disappeared. */
        public boolean requiresExactPrice(int standing) {
            return fineTick || price<standing && standing-(long)price<TradingHorizon.outbidDeadbandForPrice(standing);
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
        return evaluateRelist(item,offerPrice,remaining,position,latest,series,horizon,now,sellOnly,exits,tax,firstObservedAt,lastProgressAt,null);
    }
    public Evaluation evaluateRelist(int item,int offerPrice,int remaining,Position position,LatestPrice latest,
        List<Candle> series,TradingHorizon horizon,Instant now,boolean sellOnly,
        SellTimingEngine exits,TaxCalculator tax,long firstObservedAt,long lastProgressAt,
        com.flippingfriend.core.SalePolicyReceipt accepted) {
        return evaluateRelist(item,offerPrice,remaining,position,latest,series,horizon,now,sellOnly,exits,tax,
            firstObservedAt,lastProgressAt,accepted,0);
    }
    public Evaluation evaluateRelist(int item,int offerPrice,int remaining,Position position,LatestPrice latest,
        List<Candle> series,TradingHorizon horizon,Instant now,boolean sellOnly,
        SellTimingEngine exits,TaxCalculator tax,long firstObservedAt,long lastProgressAt,
        com.flippingfriend.core.SalePolicyReceipt accepted,long feedObservedAt) {
        long patience=-1;
        if(accepted!=null) {
            if(!accepted.isWellFormed() || accepted.getItemId()!=item || accepted.getStandingPrice()!=offerPrice
                || remaining>accepted.getQuantity() || accepted.getFirstSeenAt()!=firstObservedAt
                || accepted.getProgressAt()!=lastProgressAt || accepted.getDecidedAt()>now.getEpochSecond())
                return Evaluation.waitFor(Status.UNKNOWN_CLOCKS);
            patience=accepted.getMode()==com.flippingfriend.core.SalePolicyReceipt.Mode.ROUTINE?accepted.getPatienceSeconds():0;
        }
        var reduction=evaluate(item,offerPrice,remaining,position,latest,series,horizon,now,sellOnly,exits,tax,
            firstObservedAt,lastProgressAt,patience,false);
        // A cancelled loss exit whose supporting evidence vanished must still get its returned
        // stock listed. This grants a fresh exact non-loss limit, never stale loss permission.
        // Keeping this completion readable also permits safe runtime rollback after a later policy.
        boolean currentFeed=feedObservedAt>0 && feedObservedAt<=now.getEpochSecond() && now.getEpochSecond()-feedObservedAt<=120;
        if(reduction.quote==null && accepted!=null && accepted.isLossCut() && currentFeed && !sellOnly
            && (reduction.status==Status.COST_FLOOR || reduction.status==Status.MARKET_SUPPORT
                || reduction.status==Status.IMMATERIAL_CHANGE || reduction.status==Status.STALE_PRICES)
            && position!=null && position.isCostKnown() && remaining<=position.getQuantity()
            && firstObservedAt>0 && firstObservedAt<=lastProgressAt && lastProgressAt<=now.getEpochSecond()
            && latest!=null && latest.isComplete() && latest.getHighTime()!=null && latest.getHighTime()>0
            && latest.getLowTime()!=null && latest.getLowTime()>0 && latest.secondsSinceHigh(now)>=0 && latest.secondsSinceLow(now)>=0) {
            long cost=SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining);
            int floor=tax.breakEvenSellPriceForCost(item,cost,remaining);
            if(floor>0)return new Evaluation(Status.ELIGIBLE,new Quote(Math.max(floor,latest.getHigh()),floor,cost,true,false,
                "The earlier loss exit is no longer confirmed. Relist at this non-loss limit, protecting the exact remaining cost after tax. A fill is not guaranteed."),0);
        }
        boolean quiet=reduction.status==Status.STALE_PRICES && feedObservedAt>0 && feedObservedAt<=now.getEpochSecond()
            && now.getEpochSecond()-feedObservedAt<=120 && latest!=null && latest.isComplete()
            && latest.getHighTime()!=null && latest.getHighTime()>0 && latest.getLowTime()!=null && latest.getLowTime()>0
            && latest.secondsSinceHigh(now)>=0 && latest.secondsSinceLow(now)>=0;
        if(reduction.quote!=null || reduction.status!=Status.MARKET_SUPPORT && reduction.status!=Status.IMMATERIAL_CHANGE && !quiet)
            return reduction;
        if(position==null || !position.isCostKnown() || remaining>position.getQuantity())return reduction;
        if(firstObservedAt<=0 || firstObservedAt>now.getEpochSecond() || lastProgressAt<firstObservedAt
            || lastProgressAt>now.getEpochSecond())return Evaluation.waitFor(Status.UNKNOWN_CLOCKS);
        // Cancellation has already removed a supported routine offer. Complete that exact
        // accepted reduction as a non-loss limit if optional bucket history disappears.
        // An old stop/wind-down receipt never grants a fresh loss exit through this path.
        if(accepted!=null && accepted.getMode()==com.flippingfriend.core.SalePolicyReceipt.Mode.ROUTINE
            && !sellOnly && (reduction.status==Status.MARKET_SUPPORT || quiet)) {
            long cost=SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining);
            int floor=tax.breakEvenSellPriceForCost(item,cost,remaining);
            if(floor<=0)return Evaluation.waitFor(Status.COST_FLOOR);
            int price=Math.max(floor,accepted.getProposedPrice());
            if(price<offerPrice)return new Evaluation(Status.ELIGIBLE,new Quote(price,floor,cost,true,false,
                quiet?"Relist at the saved non-loss limit while market trades are quiet. The exact remaining purchase cost is protected after tax."
                    :"Complete the accepted sale reduction at its saved limit. The exact remaining acquisition cost is protected after tax; current fills at this limit are not guaranteed."),0);
        }
        if(quiet)return reduction;
        var decision=exits.evaluateManagedExit(position,latest,null,series,horizon,now,0,true,sellOnly,false);
        if(!decision.isSell())return reduction;
        boolean cut=decision.getAction()==SellDecision.Action.CUT;
        long cost=SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining);
        int floor=cut?latest.getLow():tax.breakEvenSellPriceForCost(item,cost,remaining);
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
        return evaluate(item,offerPrice,remaining,position,latest,series,horizon,now,sellOnly,exits,tax,firstObservedAt,lastProgressAt,-1,true);
    }
    private Evaluation evaluate(int item,int offerPrice,int remaining,Position position,LatestPrice latest,
        List<Candle> series,TradingHorizon horizon,Instant now,boolean sellOnly,
        SellTimingEngine exits,TaxCalculator tax,long firstObservedAt,long lastProgressAt,long acceptedPatience,boolean working) {
        if(remaining<=0)return Evaluation.waitFor(Status.INVALID_QUANTITY);
        if(latest==null || !latest.isComplete())return Evaluation.waitFor(Status.MISSING_PRICES);
        if(position!=null && remaining>position.getQuantity())return Evaluation.waitFor(Status.UNPROVEN_QUANTITY);
        boolean known=position!=null && position.isCostKnown();
        if(latest.getHighTime()==null || latest.getLowTime()==null || latest.getHighTime()<=0 || latest.getLowTime()<=0
            || latest.secondsSinceHigh(now)<0 || latest.secondsSinceLow(now)<0 || latest.secondsSinceHigh(now)>300
            || latest.secondsSinceLow(now)>300 && (!known || sellOnly))return Evaluation.waitFor(Status.STALE_PRICES);
        long cost=known?SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining):0;
        int floor=known?tax.breakEvenSellPriceForCost(item,cost,remaining):1;
        boolean cut=known && StopLossEvidence.confirmed(position,latest,series,now);
        int minimumReduction=horizon.outbidDeadband(offerPrice);
        // A small spread can be the whole available profit. Only repeated actual buyer-paid
        // trades may replace the coarse deadband, never a spot wobble or a buy-side reprice.
        boolean fineTick=known && !cut && !sellOnly && offerPrice>latest.getHigh()
            && offerPrice-(long)latest.getHigh()<minimumReduction;
        if(fineTick)minimumReduction=1;
        if(offerPrice-(long)latest.getHigh()<minimumReduction)return Evaluation.waitFor(Status.IMMATERIAL_CHANGE);
        int price;
        String reason;
        if(cut || sellOnly) {
            SellDecision exit=known?exits.evaluate(position,latest,null,series,horizon,now,0,false,sellOnly,false):null;
            // A fresh stop or explicit player wind-down is urgent, even just after placement/fill.
            price=exit==null?Math.max(1,latest.getHigh()-1):exit.getPrice();
            if(cut)floor=latest.getLow();
            else if(known) {
                // Preserve the existing maintenance loss guard. Explicit wind-down bypasses
                // patience/target protection, not this exact remaining-cost floor.
                if(floor<=0)return Evaluation.waitFor(Status.COST_FLOOR);price=Math.max(price,floor);
            }
            reason=cut?"Fresh trades on both sides and a completed five-minute volume bucket confirm the recorded stop loss. This exit may realize a loss; no model discount below the latest reported instant-sell price is allowed."
                :"Finish session (sell only) is enabled; seeking a faster exit as requested.";
        } else {
            long at=now.getEpochSecond();
            int supported=supportedLowerAsk(series,offerPrice,minimumReduction,fineTick?latest.getHigh():Integer.MAX_VALUE,at);
            if(firstObservedAt<=0 || firstObservedAt>at || lastProgressAt<firstObservedAt || lastProgressAt>at)
                return Evaluation.waitFor(Status.UNKNOWN_CLOCKS);
            long patience=acceptedPatience>=0?acceptedPatience:horizon.staleOfferMinutes()*60L;
            long reassess=Math.max(firstObservedAt,lastProgressAt)+patience;
            if(at-firstObservedAt<patience)return new Evaluation(Status.PLACEMENT_PATIENCE,null,reassess);
            if(at-lastProgressAt<patience)return new Evaluation(Status.RECENT_PROGRESS,null,reassess);
            if(supported<=0)return Evaluation.waitFor(Status.MARKET_SUPPORT);
            if(known) {
                if(floor<=0)return Evaluation.waitFor(Status.COST_FLOOR);
            }
            price=Math.max(floor,Math.max(latest.getHigh()-1,supported));
            int forecastPrice=known && !fineTick?exits.maintenanceLimit(position,remaining,latest,series,horizon,now,
                price,floor,offerPrice-minimumReduction):price;
            reason=fineTick ? "The offer has no recent fills, and buyers repeatedly traded just below this ask in two completed market buckets. This small reduction reaches the supported trading level and protects the exact remaining cost after tax."
                :!known ? "The offer has no recent fills, and two completed market buckets support a lower ask. Acquisition cost and profit remain unknown."
                :"The offer has no recent fills, and two completed market buckets support a profitable reduction. The exact after-tax acquisition cost remains protected; the original target is a reference, not a fill guarantee.";
            if(forecastPrice!=price)reason="The near-term public-price forecast and estimated quantity-specific fill time favor this supported limit over copying the latest quote. "+reason;
            price=forecastPrice;
            if(working && known && exits.preferWorkingSale(position,remaining,offerPrice,price,floor,latest,series,horizon,now))
                return Evaluation.waitFor(Status.ACCOUNT_HORIZON_HOLD);
        }
        if(fineTick && price>latest.getHigh())return Evaluation.waitFor(Status.COST_FLOOR);
        if(offerPrice-(long)price<minimumReduction)
            return Evaluation.waitFor(known && !cut && price==floor?Status.COST_FLOOR:Status.IMMATERIAL_CHANGE);
        return new Evaluation(Status.ELIGIBLE,new Quote(price,floor,cost,known,cut,reason,minimumReduction,fineTick),0);
    }
    /** Two distinct, completed recent 5m buckets with actual buy-side trades; never one spot print. */
    private static int supportedLowerAsk(List<Candle> series,int ask,int minimumReduction,int maximumHigh,long now) {
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
                || bar.getAvgHighPrice()>maximumHigh || ask-(long)bar.getAvgHighPrice()<minimumReduction)return 0;
            supported=Math.min(supported,Math.max(1,bar.getAvgHighPrice()-1));
            previous=bar.getTimestamp();if(++count==2)return supported;
        }
        return 0;
    }
}
