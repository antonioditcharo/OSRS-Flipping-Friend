package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.*;

/** Offline common-horizon selector. Public scenarios are neither fill evidence nor loss authority. */
public final class AccountHorizonPricing {
    public static final String VERSION="ACCOUNT_HORIZON_PRICING_SHADOW_V1";
    private AccountHorizonPricing(){}
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
