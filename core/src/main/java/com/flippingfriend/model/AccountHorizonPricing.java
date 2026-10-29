package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.*;

/** Offline common-horizon selector. Public scenarios are neither fill evidence nor loss authority. */
public final class AccountHorizonPricing {
    public static final String VERSION="ACCOUNT_HORIZON_PRICING_SHADOW_V2_BUDGET_DIVERSITY";
    private AccountHorizonPricing(){}
    /** Preserve affordable choices for the existing joint optimizer; never increase its branch budget. */
    public static List<com.flippingfriend.core.PortfolioCandidate> entryAlternatives(
            List<com.flippingfriend.core.PortfolioCandidate> raw,int maximum) {
        if(maximum<1 || maximum>32 || raw==null || raw.size()>4096)throw new IllegalArgumentException("Bounded per-item alternatives required");
        var supported=new ArrayList<com.flippingfriend.core.PortfolioCandidate>();
        for(var c:raw)if(c!=null && c.getAccountPriceScenarios()!=null && c.getCapitalRequired()>0
                && c.getCompletionProbability()>0 && c.getNetProfit()>0
                && Double.isFinite(c.getAccountPriceScenarios().score()) && value(c).signum()>0)supported.add(c);
        if(supported.isEmpty())return List.of();
        var reference=supported.get(0).getAccountPriceScenarios();
        for(var c:supported) {
            var r=c.getAccountPriceScenarios();
            if(c.getItemId()!=reference.itemId || !c.getGroup().equals(supported.get(0).getGroup()) || r.cadenceMinutes!=reference.cadenceMinutes
                || r.observedAt!=reference.observedAt || Double.compare(r.horizonHours,reference.horizonHours)!=0)
                throw new IllegalArgumentException("Alternatives must share item, observation and elapsed horizon");
        }
        Comparator<com.flippingfriend.core.PortfolioCandidate> rank=(a,b)->{
            int compared=value(b).compareTo(value(a));if(compared!=0)return compared;
            compared=Long.compare(a.getCapitalRequired(),b.getCapitalRequired());if(compared!=0)return compared;
            compared=Integer.compare(a.getQuantity(),b.getQuantity());if(compared!=0)return compared;
            compared=Integer.compare(a.getTargetBuyPrice(),b.getTargetBuyPrice());
            return compared!=0?compared:Integer.compare(a.getTargetSellPrice(),b.getTargetSellPrice());
        };
        supported.sort(rank);
        var frontier=new ArrayList<com.flippingfriend.core.PortfolioCandidate>();
        for(var c:supported)if(supported.stream().noneMatch(other->other!=c && dominates(other,c)))frontier.add(c);
        if(frontier.size()<=maximum)return List.copyOf(frontier);
        var selected=new ArrayList<com.flippingfriend.core.PortfolioCandidate>();selected.add(frontier.get(0));
        var cheapest=frontier.stream().min(Comparator.comparingLong(com.flippingfriend.core.PortfolioCandidate::getCapitalRequired)
            .thenComparing(rank)).orElseThrow();
        if(maximum>1 && cheapest!=selected.get(0))selected.add(cheapest);
        while(selected.size()<maximum) {
            com.flippingfriend.core.PortfolioCandidate next=null;double separation=-1;
            for(var c:frontier)if(!selected.contains(c)) {
                double distance=Double.POSITIVE_INFINITY;
                for(var prior:selected)distance=Math.min(distance,Math.abs(Math.log(c.getCapitalRequired())-Math.log(prior.getCapitalRequired())));
                if(next==null || distance>separation+1e-12){next=c;separation=distance;}
            }
            selected.add(next);
        }
        selected.sort(rank);return List.copyOf(selected);
    }
    private static java.math.BigDecimal value(com.flippingfriend.core.PortfolioCandidate c) {
        return c.getAccountPriceScenarios().stresses.get(0).expectedNetValue;
    }
    private static boolean dominates(com.flippingfriend.core.PortfolioCandidate a,com.flippingfriend.core.PortfolioCandidate b) {
        // Only resource/preference feasibility is used for pruning. Fill/time metrics are not a
        // requirement to prefer a forecast; their effects are already in the shared horizon value.
        return value(a).compareTo(value(b))>=0 && a.getCapitalRequired()<=b.getCapitalRequired()
            && a.getWorstLoss()<=b.getWorstLoss() && a.getQuantity()<=b.getQuantity()
            && a.getNetProfit()>=b.getNetProfit() && a.getExpiresAt()>=b.getExpiresAt()
            && (value(a).compareTo(value(b))>0 || a.getCapitalRequired()<b.getCapitalRequired()
                || a.getWorstLoss()<b.getWorstLoss() || a.getQuantity()<b.getQuantity()
                || a.getNetProfit()>b.getNetProfit() || a.getExpiresAt()>b.getExpiresAt());
    }
    public static double hours(double legHours,int cadence) {
        if(!List.of(5,30,120,480).contains(cadence) || !Double.isFinite(legHours) || legHours<=0)return Double.NaN;
        return Math.min(48,2*legHours+cadence/30.0);
    }
    public static final class Selection {
        public final String policy=VERSION,evidence="UNQUALIFIED_PUBLIC_SCENARIOS_NO_REPLACEMENT_INCOME";
        public final int selectedPrice;
        public final AccountPriceScenarios.SaleResult comparison;
        private Selection(int selected,AccountPriceScenarios.SaleResult comparison){selectedPrice=selected;this.comparison=comparison;}
    }
    /** Existing exact target/cost bounds remain hard constraints; apparent loss gains stay diagnostic. */
    public static Selection sale(int item,int quantity,long cost,int baseline,int minimum,int maximum,int[] alternatives,
            int cadence,double hours,LatestPrice quote,List<Candle> history,PublicPriceForecast model,Instant now,
            FillModel fills,TaxCalculator tax) {
        if(tax==null || now==null || minimum<=0 || maximum<minimum || alternatives==null || alternatives.length>16)
            return new Selection(baseline,null);
        int floor=tax.breakEvenSellPriceForCost(item,cost,quantity);
        if(floor<=0 || baseline<Math.max(floor,minimum) || baseline>maximum)return new Selection(baseline,null);
        int[] allowed=Arrays.stream(alternatives).filter(p->p>=Math.max(floor,minimum) && p<=maximum).distinct().toArray();
        var comparison=AccountPriceScenarios.sale(item,quantity,cost,baseline,allowed,cadence,hours,quote,history,model,
            now.getEpochSecond(),fills,tax);
        if(comparison==null)return new Selection(baseline,null);
        var best=comparison.preferred;
        // Strictly better common-horizon equity is required; ties retain the current order. This
        // may trade some completion probability for value, without a metric-by-metric veto.
        int selected=best.exactFullProfit>=0 && best.price>=Math.max(floor,minimum) && best.price<=maximum
            && best.expectedNetValue.compareTo(comparison.baseline.expectedNetValue)>0?best.price:baseline;
        return new Selection(selected,comparison);
    }
}
