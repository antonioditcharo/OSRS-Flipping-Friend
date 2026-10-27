package com.flippingfriend.model;

import com.flippingfriend.core.ExecutionForecast;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.LatestPrice;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/** Public-flow decision experiment. Outcomes are scenarios, never execution or loss authority. */
public final class AccountPriceScenarios {
    public static final String VERSION="ACCOUNT_PRICE_SCENARIOS_V1";
    private static final int QUANTILES=9;
    private static final double[] FLOW_STRESS={1,.75,.5};
    private AccountPriceScenarios(){}
    public static final class Result {
        public final String version=VERSION,evidence="UNVALIDATED_PUBLIC_FLOW_SCENARIOS";
        public final int itemId,buyPrice,sellPrice,quantity,cadenceMinutes;
        public final long observedAt,terminalNetPerUnit;
        public final double horizonHours;
        public final String buyForecast,sellForecast,marketHistory;
        public final List<Stress> stresses;
        private Result(PortfolioCandidate candidate,int cadence,long at,double hours,long terminal,List<Stress> stresses) {
            itemId=candidate.getItemId();buyPrice=candidate.getTargetBuyPrice();sellPrice=candidate.getTargetSellPrice();
            quantity=candidate.getQuantity();cadenceMinutes=cadence;observedAt=at;horizonHours=hours;
            terminalNetPerUnit=terminal;buyForecast=candidate.getBuyForecast().getFingerprint();
            sellForecast=candidate.getSellForecast().getFingerprint();
            marketHistory=candidate.getMarketPriceEvidence()==null?null:candidate.getMarketPriceEvidence().historyFingerprint;
            this.stresses=List.copyOf(stresses);
        }
        /** Same elapsed horizon for every account alternative; unused capital earns zero. */
        public double score(){return stresses.get(0).netValuePerElapsedHour;}
    }
    public static final class Stress {
        public final double flowMultiplier,netValuePerElapsedHour,expectedBought,expectedSold,expectedRemaining;
        public final double expectedReusableSlotHours,expectedReusableCapitalHours;
        public final BigDecimal expectedNetValue;
        public final int paths;
        private Stress(double flow,BigDecimal value,double hours,double bought,double sold,double slot,double capital,int paths) {
            flowMultiplier=flow;expectedNetValue=value;netValuePerElapsedHour=value.doubleValue()/hours;
            expectedBought=bought/paths;expectedSold=sold/paths;expectedRemaining=(bought-sold)/paths;
            expectedReusableSlotHours=slot/paths;expectedReusableCapitalHours=capital/paths;this.paths=paths;
        }
    }
    public static final class SaleLimit {
        public final int price;
        public final long exactFullProceeds,exactFullProfit;
        public final BigDecimal expectedNetValue;
        public final double expectedSold,expectedRemaining,reusableSlotHours;
        private SaleLimit(int price,long proceeds,long cost,BigDecimal value,double sold,int quantity,double slots) {
            this.price=price;exactFullProceeds=proceeds;exactFullProfit=Math.subtractExact(proceeds,cost);
            expectedNetValue=value;expectedSold=sold;expectedRemaining=quantity-sold;reusableSlotHours=slots;
        }
    }
    public static final class SaleResult {
        public final String version=VERSION,evidence="PUBLIC_SCENARIOS_WITHOUT_LOSS_AUTHORITY";
        public final int itemId,quantity,standingPrice,cadenceMinutes,terminalPrice;
        public final long allocatedCost,observedAt,lowTradeAt,highTradeAt;
        public final double horizonHours;
        public final String marketHistory;
        public final List<SaleLimit> limits;
        public final SaleLimit baseline,preferred;
        private SaleResult(int item,int quantity,int standing,int cadence,int terminal,long cost,long at,
                LatestPrice quote,double hours,String history,List<SaleLimit> limits,SaleLimit baseline,SaleLimit preferred) {
            itemId=item;this.quantity=quantity;standingPrice=standing;cadenceMinutes=cadence;terminalPrice=terminal;
            allocatedCost=cost;observedAt=at;lowTradeAt=quote.getLowTime();highTradeAt=quote.getHighTime();horizonHours=hours;
            marketHistory=history;this.limits=List.copyOf(limits);this.baseline=baseline;this.preferred=preferred;
        }
    }
    /** Compare holding, adjusting and exiting the exact remainder. An apparent loss advantage is analysis only. */
    public static SaleResult sale(int item,int quantity,long cost,int standing,int[] alternatives,int cadence,double hours,
            LatestPrice quote,List<com.flippingfriend.data.Candle> history,PublicPriceForecast model,long now,FillModel fills,TaxCalculator tax) {
        try {
            if(item<=0 || quantity<=0 || cost<0 || standing<=0 || alternatives==null || alternatives.length>16
                || !List.of(5,30,120,480).contains(cadence) || !Double.isFinite(hours) || hours<=0 || hours>48
                || quote==null || !quote.isComplete() || quote.getLowTime()==null || quote.getHighTime()==null
                || quote.getLow()>quote.getHigh() || quote.secondsSinceLow(java.time.Instant.ofEpochSecond(now))<0
                || quote.secondsSinceHigh(java.time.Instant.ofEpochSecond(now))<0
                || quote.stalestSideSeconds(java.time.Instant.ofEpochSecond(now))>120 || history==null || history.size()>2048
                || fills==null || tax==null)return null;
            var completed=new TreeMap<Long,com.flippingfriend.data.Candle>();
            for(var bar:history) {
                if(bar==null || bar.getTimestamp()<0 || bar.getTimestamp()%300!=0 || bar.getHighPriceVolume()<0 || bar.getLowPriceVolume()<0)return null;
                if(bar.getTimestamp()>now-300)continue;
                if(completed.putIfAbsent(bar.getTimestamp(),bar)!=null)return null;
            }
            if(completed.size()<2 || now-completed.lastKey()>900)return null;
            int terminal=quote.getLow();String fingerprint=null;
            if(model!=null) {
                if(model.observedAt>now || now-model.observedAt>300)return null;
                var low=model.at(false,PredictivePriceChoices.horizon(hours),terminal);
                if(low.available()){terminal=(int)Math.max(1,Math.min(terminal,Math.floor(low.lower)));fingerprint=model.historyFingerprint;}
            }
            terminal=(int)Math.max(1,terminal*9L/10);
            var curve=FillCurve.overRecentHistory(new ArrayList<>(completed.values()));
            var prices=new LinkedHashSet<Integer>();prices.add(standing);for(int price:alternatives)if(price>0)prices.add(price);
            var limits=new ArrayList<SaleLimit>();SaleLimit baseline=null,best=null;
            for(int price:prices) {
                var estimate=fills.estimateSell(curve,price,quantity,hours);
                var forecast=estimate==null || !estimate.isPlausible()?null:fills.forecast(item,false,curve,price,quantity,hours,1,estimate,1,estimate.getExpectedHours());
                if(forecast!=null && !forecast.matches(item,false,price,quantity))return null;
                BigDecimal value=BigDecimal.ZERO;double soldSum=0,slots=0;
                for(int i=0;i<QUANTILES;i++) {
                    double completion=forecast==null?Double.POSITIVE_INFINITY:completion(forecast,(i+.5)/QUANTILES,1);
                    int sold=forecast==null?0:filled(forecast,quantity,hours*3600,completion);
                    long proceeds=Math.addExact(tax.netProceeds(item,price,sold),tax.netProceeds(item,terminal,quantity-sold));
                    value=value.add(BigDecimal.valueOf(Math.subtractExact(proceeds,cost)));soldSum+=sold;
                    if(sold==quantity)slots+=Math.max(0,hours-nextVisit(completion,cadence*60.0)/3600);
                }
                var limit=new SaleLimit(price,tax.netProceeds(item,price,quantity),cost,
                    value.divide(BigDecimal.valueOf(QUANTILES),MathContext.DECIMAL128),soldSum/QUANTILES,quantity,slots/QUANTILES);
                limits.add(limit);if(baseline==null)baseline=limit;
                if(best==null || limit.expectedNetValue.compareTo(best.expectedNetValue)>0)best=limit;
            }
            return new SaleResult(item,quantity,standing,cadence,terminal,cost,now,quote,hours,fingerprint,limits,baseline,best);
        }catch(RuntimeException unsupported){return null;}
    }
    /**
     * Integrates a fixed quantile grid over buy/sell completion-time scenarios. The marginal
     * completion CDF matches the supplied public estimate at its own horizon; wait/working shares
     * determine partial quantities. Independence, exponential tails and linear partial throughput
     * are explicit unvalidated assumptions. Attendance delays collection/placing the second leg.
     * Reusable slots/capital are reported, but hypothetical replacement income is never credited.
     */
    public static Result entry(PortfolioCandidate candidate,int cadence,double hours,LatestPrice quote,
            long now,TaxCalculator tax) {
        try {
            if(candidate==null || tax==null || !List.of(5,30,120,480).contains(cadence)
                || !Double.isFinite(hours) || hours<=0 || hours>48 || quote==null || !quote.isComplete()
                || quote.getLow()>quote.getHigh() || quote.secondsSinceLow(java.time.Instant.ofEpochSecond(now))<0
                || quote.secondsSinceHigh(java.time.Instant.ofEpochSecond(now))<0
                || quote.stalestSideSeconds(java.time.Instant.ofEpochSecond(now))>120 || candidate.getExpiresAt()<=now)return null;
            int item=candidate.getItemId(),q=candidate.getQuantity();
            ExecutionForecast buy=candidate.getBuyForecast(),sell=candidate.getSellForecast();
            if(buy==null || sell==null || !buy.matches(item,true,candidate.getTargetBuyPrice(),q)
                || !sell.matches(item,false,candidate.getTargetSellPrice(),q))return null;
            // The mark is a common stressed assumption, not a current executable price or risk mark.
            long terminalPrice=quote.getLow();var market=candidate.getMarketPriceEvidence();
            if(market!=null) {
                if(market.observedAt>now || now-market.observedAt>300 || !market.low.available() || !market.high.available())return null;
                terminalPrice=Math.min(terminalPrice,(long)Math.floor(market.low.lower));
            }
            terminalPrice=Math.max(1,terminalPrice*9/10);
            long terminal=tax.netProceeds(item,(int)terminalPrice,1);
            long exactProfit=Math.subtractExact(tax.netProceeds(item,candidate.getTargetSellPrice(),q),candidate.getCapitalRequired());
            if(exactProfit!=candidate.getNetProfit() || exactProfit<=0 || q>candidate.getBuyLimitRemaining())return null;
            double horizon=hours*3600,visit=cadence*60.0;
            List<Stress> stresses=new ArrayList<>();
            for(double flow:FLOW_STRESS) {
                BigDecimal total=BigDecimal.ZERO;double boughtSum=0,soldSum=0,slots=0,capital=0;int paths=0;
                for(int b=0;b<QUANTILES;b++)for(int s=0;s<QUANTILES;s++) {
                    double buyTime=completion(buy,(b+.5)/QUANTILES,flow);
                    double buyStop=Math.min(horizon,Math.min(buyTime,buy.getHorizonSeconds()));
                    int bought=filled(buy,q,buyStop,buyTime);
                    double saleAt=nextVisit(buyStop,visit)+15;
                    double sellTime=completion(sell,(s+.5)/QUANTILES,flow);
                    double sellBudget=Math.max(0,Math.min(sell.getHorizonSeconds(),horizon-saleAt));
                    int sold=filled(sell,bought,sellBudget,sellTime);
                    long paid=Math.multiplyExact((long)bought,candidate.getTargetBuyPrice());
                    long proceeds=tax.netProceeds(item,candidate.getTargetSellPrice(),sold);
                    long remaining=tax.netProceeds(item,(int)terminalPrice,bought-sold);
                    total=total.add(BigDecimal.valueOf(Math.subtractExact(Math.addExact(proceeds,remaining),paid)));
                    boughtSum+=bought;soldSum+=sold;paths++;
                    double released=bought==0?nextVisit(buyStop,visit):sold==bought?nextVisit(saleAt+completionFor(sell,bought,sellTime),visit):horizon;
                    slots+=Math.max(0,horizon-released)/3600;
                    // Only actually freed purchase cash/proceeds are reusable; remaining stock is not cash.
                    capital+=(candidate.getCapitalRequired()-paid)*Math.max(0,horizon-nextVisit(buyStop,visit))/3600;
                    if(sold==bought)capital+=proceeds*Math.max(0,horizon-released)/3600;
                }
                stresses.add(new Stress(flow,total.divide(BigDecimal.valueOf(paths),MathContext.DECIMAL128),hours,
                    boughtSum,soldSum,slots,capital,paths));
            }
            return new Result(candidate,cadence,now,hours,terminal,stresses);
        }catch(RuntimeException inconsistent){return null;}
    }
    private static double nextVisit(double at,double cadence){return Math.ceil(at/cadence)*cadence;}
    private static double completion(ExecutionForecast f,double quantile,double flow) {
        double p=f.getCompletionProbability();
        if(p<=0)return Double.POSITIVE_INFINITY;
        if(p>=1)return f.getExpectedTotalSeconds()/flow;
        return f.getHorizonSeconds()*Math.log1p(-quantile)/Math.log1p(-p)/flow;
    }
    private static int filled(ExecutionForecast f,int ordered,double elapsed,double completion) {
        if(ordered<=0 || elapsed<=0 || !Double.isFinite(completion) || completion<=0)return 0;
        double waitShare=Math.min(1,Math.max(0,(f.getRawWaitSeconds()*f.getWaitMultiplier()+f.getFloorOverheadSeconds())/f.getExpectedTotalSeconds()));
        double waiting=completion*waitShare,working=completion-waiting;
        if(elapsed>=completionFor(f,ordered,completion))return ordered;
        if(working<=0)return 0;
        return (int)Math.max(0,Math.min(ordered,Math.floor(f.getQuantity()*Math.max(0,elapsed-waiting)/working)));
    }
    private static double completionFor(ExecutionForecast f,int ordered,double completion) {
        double waiting=Math.min(1,Math.max(0,(f.getRawWaitSeconds()*f.getWaitMultiplier()+f.getFloorOverheadSeconds())/f.getExpectedTotalSeconds()));
        return completion*(waiting+(1-waiting)*ordered/f.getQuantity());
    }
}
