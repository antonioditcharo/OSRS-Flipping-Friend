package com.flippingfriend.model;

import com.flippingfriend.core.*;
import com.flippingfriend.data.LatestPrice;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccountPriceScenariosTest {
    private final long now=1_800_000_000;
    private final TaxCalculator tax=new TaxCalculator();
    private PortfolioCandidate candidate(int quantity,double buyP,double sellP) {
        var b=new FillEstimate(buyP,.2,quantity*10.0,.1);
        var s=new FillEstimate(sellP,.2,quantity*10.0,.1);
        return new PortfolioCandidate(4151,"Whip","",100,100,100,130,130,130,quantity,quantity,
            tax.netProceeds(4151,130,quantity)-100L*quantity,5L*quantity,4L*quantity,buyP,sellP,.2,.2,2,now+60)
            .withForecasts(forecast(true,100,quantity,b),forecast(false,130,quantity,s));
    }
    private ExecutionForecast forecast(boolean buying,int price,int quantity,FillEstimate estimate) {
        return new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.1,1,1,false,4151,buying,price,quantity,2,1,
            14400,48,40,100000,estimate,1,.2);
    }
    private AccountPriceScenarios.Result result(PortfolioCandidate c,int cadence,double hours) {
        return AccountPriceScenarios.entry(c,cadence,hours,new LatestPrice(130,now,100,now),now,tax);
    }
    @Test public void partialOutcomesConserveUnitsAndMoneyWithoutCreditingReplacementIncome() {
        var r=result(candidate(50,.8,.6),30,4);assertNotNull(r);
        assertEquals(3,r.stresses.size());
        for(var s:r.stresses) {
            assertEquals(81,s.paths);assertTrue(s.expectedBought<=50);assertTrue(s.expectedSold<=s.expectedBought);
            assertEquals(s.expectedBought-s.expectedSold,s.expectedRemaining,1e-10);
            assertTrue(s.expectedRemaining>0);assertTrue(s.expectedNetValue.doubleValue()<=28*s.expectedSold);
            assertEquals(s.expectedNetValue.doubleValue()/4,s.netValuePerElapsedHour,1e-10);
            assertTrue(s.expectedReusableCapitalHours>=0);assertTrue(s.expectedReusableSlotHours>=0);
        }
        assertEquals(r.buyForecast,candidate(50,.8,.6).getBuyForecast().getFingerprint());
        try{r.stresses.clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void attendanceAndTooShortCommonHorizonsCannotInventEarlierSales() {
        var c=candidate(500,.8,.8);
        var frequent=result(c,5,4);var absent=result(c,480,4);
        assertTrue(frequent.stresses.get(0).expectedSold>0);
        assertEquals(0,absent.stresses.get(0).expectedSold,0);
        assertEquals(0,result(c,5,1.0/3600).stresses.get(0).expectedBought,0);
    }
    @Test public void unsupportedOrStaleInputsReturnNoChallengerWithoutAlteringBaseline() {
        var c=candidate(50,.8,.8);assertNull(result(c,60,4));assertNull(result(c,5,Double.NaN));
        assertNull(AccountPriceScenarios.entry(c,5,4,new LatestPrice(130,now-121,100,now),now,tax));
        var bare=new PortfolioCandidate(4151,"Whip","",100,100,100,130,130,130,50,50,1400,250,100,.8,.8,.2,.2,2,now+60);
        assertNull(result(bare,5,4));assertTrue(c.expectedGpPerSlotHour()>0);
    }
    @Test public void exactTaxAndLargeQuantitiesRemainFiniteAndCannotClaimMoneyForNoSale() {
        var c=candidate(1_000_000_000,.8,.8);var r=result(c,480,4);assertNotNull(r);
        assertTrue(Double.isFinite(r.score()));assertTrue(r.stresses.get(0).expectedNetValue.signum()<0);
        assertEquals(0,r.stresses.get(0).expectedSold,0);
    }
    @Test public void saleComparisonConservesExactRemainderCostAndCanDescribeLossWithoutAuthorizingIt() {
        var rows=new java.util.ArrayList<com.flippingfriend.data.Candle>();
        for(int i=48;i>0;i--)rows.add(new com.flippingfriend.data.Candle(now-i*300,130,100,10000,10000));
        var r=AccountPriceScenarios.sale(4151,3,401,150,new int[]{130,100},5,2,
            new LatestPrice(130,now,100,now),rows,null,now,new FillModel(),tax);
        assertNotNull(r);assertEquals(401,r.allocatedCost);assertEquals(3,r.limits.size());
        assertEquals(150,r.baseline.price);assertEquals("PUBLIC_SCENARIOS_WITHOUT_LOSS_AUTHORITY",r.evidence);
        for(var limit:r.limits) {
            assertEquals(tax.netProceeds(4151,limit.price,3)-401,limit.exactFullProfit);
            assertEquals(3,limit.expectedSold+limit.expectedRemaining,1e-10);
            assertTrue(limit.expectedNetValue.doubleValue()<=limit.exactFullProfit || limit.expectedRemaining>0);
        }
        assertTrue(r.preferred.exactFullProfit<0); // This result has no route into live loss permission.
        assertNull(AccountPriceScenarios.sale(4151,3,-1,150,new int[]{130},5,2,
            new LatestPrice(130,now,100,now),rows,null,now,new FillModel(),tax));
    }
}
