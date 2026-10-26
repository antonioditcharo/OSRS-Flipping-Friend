package com.flippingfriend.session;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.TaxCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/** Optional public-flow scenario comparison. Never accounting, risk, or loss permission. */
public final class ExitPriceComparison {
    public static final String VERSION="common-horizon-exit-1";
    public static final class Limit {
        public final int price;
        public final long proceeds,profit;
        public final double probability,expectedMinutes,profitPerHour;
        /** Expected value above the same current taxed bid mark, avoiding large-cost cancellation. */
        private final BigDecimal valueAboveMark;
        public final BigDecimal markedProfit;
        private Limit(int price,long proceeds,long profit,FillEstimate fill,long mark,long cost) {
            this.price=price;this.proceeds=proceeds;this.profit=profit;
            probability=fill.getProbability();expectedMinutes=fill.getExpectedMinutes();
            profitPerHour=profit<=0?0:profit*probability/Math.max(1.0/60,fill.getExpectedHours());
            valueAboveMark=BigDecimal.valueOf(proceeds-mark).multiply(BigDecimal.valueOf(probability));
            markedProfit=valueAboveMark.add(BigDecimal.valueOf(Math.subtractExact(mark,cost)));
        }
        public long roundedMarkedProfit(){return markedProfit.setScale(0,RoundingMode.HALF_UP).longValueExact();}
    }
    public static final class Result {
        public final String reason;
        public final int itemId,selectedPrice,quantity,terminalPrice;
        public final long allocatedCost;
        public final long observedAt,validUntil;
        public final double horizonHours;
        public final Limit baseline,selected;
        public final List<Limit> limits;
        private Result(String reason,int price,int quantity,long cost,int mark,double hours,
                Limit baseline,Limit selected,List<Limit> limits,long at,long expiry,int item) {
            this.reason=reason;selectedPrice=price;this.quantity=quantity;allocatedCost=cost;
            terminalPrice=mark;horizonHours=hours;this.baseline=baseline;this.selected=selected;
            this.limits=List.copyOf(limits);observedAt=at;validUntil=expiry;itemId=item;
        }
        public boolean available(){return baseline!=null;}
        public boolean currentAt(long now){return available() && now>=observedAt && now<validUntil;}
    }
    private ExitPriceComparison(){}

    /**
     * Compare the same exact remaining quantity/cost over one horizon. Unsold stock keeps the same
     * current taxed low-side mark in every full-fill/no-completion scenario; no replacement-trade
     * income is credited and partial-fill outcomes are not predicted. New alternatives cannot
     * discount below that reported low-side trade. It is not a promised executable depth quote.
     * Completion probabilities are unvalidated public-flow estimates, not observed execution rates.
     * A forecast alternative must improve the existing profit/time score by 10% and must not worsen
     * marked end-of-horizon value versus either the baseline or the best retained alternative.
     * Missing optional evidence returns the original limit immediately and grants no new authority.
     */
    public static Result compare(int item,int quantity,long cost,int fallback,int minimum,int maximum,
            int[] alternatives,LatestPrice quote,List<Candle> history,double hours,Instant now,
            FillModel fills,TaxCalculator tax) {
        try {
            if(item<=0 || quantity<=0 || cost<0 || fallback<=0 || minimum<=0 || maximum<minimum
                || alternatives==null || alternatives.length>16 || fills==null || tax==null || now==null
                || !Double.isFinite(hours) || hours<=0 || hours>24)
                return missing("Comparison inputs are unavailable",fallback,quantity,cost,hours);
            if(quote==null || !quote.isComplete() || quote.getLow()>quote.getHigh()
                || quote.getLowTime()==null || quote.getHighTime()==null || quote.getLowTime()<=0 || quote.getHighTime()<=0
                || quote.secondsSinceLow(now)<0 || quote.secondsSinceHigh(now)<0 || quote.stalestSideSeconds(now)>120)
                return missing("Fresh two-sided trades are unavailable for comparison",fallback,quantity,cost,hours);
            List<Candle> completed=completed(history,now.getEpochSecond());
            if(completed==null || completed.size()<2)
                return missing("Completed public-flow evidence is unavailable",fallback,quantity,cost,hours);
            int exactFloor=tax.breakEvenSellPriceForCost(item,cost,quantity);
            if(exactFloor<=0)return missing("No legal exact-cost limit is available",fallback,quantity,cost,hours);
            int floor=Math.max(quote.getLow(),Math.max(minimum,exactFloor));
            FillCurve curve=FillCurve.overRecentHistory(completed);
            long mark=tax.netProceeds(item,quote.getLow(),quantity);
            Limit baseline=limit(item,quantity,cost,fallback,mark,hours,curve,fills,tax);
            if(baseline==null)return missing("The standing limit has no supported completion estimate",fallback,quantity,cost,hours);
            List<Limit> limits=new ArrayList<>();limits.add(baseline);Limit best=baseline;
            Set<Integer> seen=new HashSet<>();seen.add(fallback);
            for(int price:alternatives) {
                if(price<floor || price>maximum || !seen.add(price))continue;
                Limit candidate=limit(item,quantity,cost,price,mark,hours,curve,fills,tax);
                if(candidate==null)continue;limits.add(candidate);
                // An observed loss-making baseline may be displayed, but cannot authorize a new loss.
                if(fallback<floor || baseline.profit<0 || candidate.profit<0)continue;
                if(candidate.profitPerHour>best.profitPerHour*1.10 && candidate.profitPerHour>0
                    && candidate.valueAboveMark.compareTo(baseline.valueAboveMark)>=0
                    && candidate.valueAboveMark.compareTo(best.valueAboveMark)>=0)best=candidate;
            }
            return new Result(best==baseline?"Retain the existing limit in this comparison":
                "Alternative improves the estimated rate without worsening marked horizon value",
                best.price,quantity,cost,quote.getLow(),hours,baseline,best,limits,now.getEpochSecond(),
                Math.min(now.getEpochSecond()+30,Math.min(Math.min(quote.getLowTime(),quote.getHighTime())+121,
                    completed.get(completed.size()-1).getTimestamp()+901)),item);
        }catch(RuntimeException unsupported) {
            return missing("Comparison evidence is inconsistent",fallback,quantity,cost,hours);
        }
    }
    private static Limit limit(int item,int quantity,long cost,int price,long mark,double hours,
            FillCurve curve,FillModel fills,TaxCalculator tax) {
        FillEstimate f=fills.estimateSell(curve,price,quantity,hours);
        if(f==null || !Double.isFinite(f.getProbability()) || f.getProbability()<=0 || f.getProbability()>1
            || !Double.isFinite(f.getExpectedHours()) || f.getExpectedHours()<=0
            || !Double.isFinite(f.getUnitsPerHour()) || f.getUnitsPerHour()<=0
            || !Double.isFinite(f.getWaitHours()) || f.getWaitHours()<0)return null;
        long proceeds=tax.netProceeds(item,price,quantity);
        return new Limit(price,proceeds,Math.subtractExact(proceeds,cost),f,mark,cost);
    }
    private static List<Candle> completed(List<Candle> history,long now) {
        if(history==null || history.size()>2048)return null;
        var rows=new TreeMap<Long,Candle>();
        for(Candle c:history) {
            if(c==null || c.getTimestamp()<0 || c.getTimestamp()%300!=0 || c.getHighPriceVolume()<0 || c.getLowPriceVolume()<0)return null;
            if(c.getTimestamp()>now-300)continue;
            Candle old=rows.putIfAbsent(c.getTimestamp(),c);
            if(old!=null && (!Objects.equals(old.getAvgHighPrice(),c.getAvgHighPrice())
                || !Objects.equals(old.getAvgLowPrice(),c.getAvgLowPrice()) || old.getHighPriceVolume()!=c.getHighPriceVolume()
                || old.getLowPriceVolume()!=c.getLowPriceVolume()))return null;
        }
        if(rows.isEmpty() || now-rows.lastKey()-300>600)return null;
        return new ArrayList<>(rows.values());
    }
    private static Result missing(String reason,int price,int quantity,long cost,double hours) {
        return new Result(reason,price,quantity,cost,0,hours,null,null,List.of(),0,0,0);
    }
}
