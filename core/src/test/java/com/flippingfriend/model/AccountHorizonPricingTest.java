package com.flippingfriend.model;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccountHorizonPricingTest {
    final long now=1_800_000_000;
    final TaxCalculator tax=new TaxCalculator();
    final LatestPrice quote=new LatestPrice(130,now,100,now);
    List<Candle> history() {var rows=new ArrayList<Candle>();for(int i=48;i>0;i--)rows.add(new Candle(now-i*300,i>2?180:130,100,10000,10000));return rows;}
    FillModel fills(boolean tradeoff) {return new FillModel(){
        @Override public FillEstimate estimateSell(FillCurve curve,int price,int quantity,double hours) {
            return new FillEstimate(tradeoff && price>=150?.8:.99,tradeoff && price>=150?.4:.1,100,0.01);
        }
    };}
    @Test public void wholeHorizonValueCanPreferAMoreValuableSlowerLessCertainOrder() {
        var selected=AccountHorizonPricing.sale(4151,3,300,130,103,180,new int[]{150},5,2,quote,history(),null,Instant.ofEpochSecond(now),fills(true),tax);
        assertNotNull(selected.comparison);assertEquals(150,selected.selectedPrice);
        assertTrue(selected.comparison.preferred.expectedNetValue.compareTo(selected.comparison.baseline.expectedNetValue)>0);
        assertEquals("UNQUALIFIED_PUBLIC_SCENARIOS_NO_REPLACEMENT_INCOME",selected.evidence);
    }
    @Test public void exactSubsetFloorUnsupportedCadenceAndOldQuotesRetainBaseline() {
        var selected=AccountHorizonPricing.sale(4151,3,401,150,1,180,new int[]{100,130},5,2,quote,history(),null,Instant.ofEpochSecond(now),fills(false),tax);
        assertEquals(150,selected.selectedPrice);assertNotNull(selected.comparison);assertEquals(1,selected.comparison.limits.size());
        assertNull(AccountHorizonPricing.sale(4151,3,300,130,103,180,new int[]{150},60,2,quote,history(),null,Instant.ofEpochSecond(now),fills(true),tax).comparison);
        assertNull(AccountHorizonPricing.sale(4151,3,300,130,103,180,new int[]{150},5,2,new LatestPrice(130,now-121,100,now),history(),null,Instant.ofEpochSecond(now),fills(true),tax).comparison);
        assertEquals(5+5/30.0,AccountHorizonPricing.hours(2.5,5),0);
        assertEquals(48,AccountHorizonPricing.hours(24,480),0);
    }
    @Test public void shadowHoldCannotStrandAnAlreadyCancelledSaleOrChangeTheLiveDefault() {
        var horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
        var position=new Position(4151,"Whip",3,300,now-3600,true);
        var shadow=new SellTimingEngine(fills(false),tax,true,SellTimingEngine.PricePolicy.ACCOUNT_HORIZON_SHADOW);
        var baseline=new SellTimingEngine(fills(false),tax,true);
        var maintenance=new SaleMaintenancePricing();
        assertEquals(SaleMaintenancePricing.Status.ACCOUNT_HORIZON_HOLD,maintenance.evaluate(4151,160,3,position,quote,history(),horizon,Instant.ofEpochSecond(now),false,shadow,tax,now-3600,now-3600).status);
        assertNotNull(maintenance.quote(4151,160,3,position,quote,history(),horizon,Instant.ofEpochSecond(now),false,baseline,tax,now-3600,now-3600));
        assertNotNull(maintenance.evaluateRelist(4151,160,3,position,quote,history(),horizon,Instant.ofEpochSecond(now),false,shadow,tax,now-3600,now-3600).quote);
        assertFalse(baseline.preferWorkingSale(position,3,160,130,103,quote,history(),horizon,Instant.ofEpochSecond(now)));
    }
    @Test public void liveAsyncPolicyCannotEnableTheShadowSelector() {
        try {new SellTimingEngine(new FillModel(),tax,false,SellTimingEngine.PricePolicy.ACCOUNT_HORIZON_SHADOW);fail();}
        catch(IllegalArgumentException expected){}
    }
}
