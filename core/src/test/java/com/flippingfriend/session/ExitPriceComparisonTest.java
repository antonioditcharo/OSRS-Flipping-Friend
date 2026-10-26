package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExitPriceComparisonTest {
    final long at=1800000600L;
    final TaxCalculator tax=new TaxCalculator();
    LatestPrice quote(){return new LatestPrice(110,at,100,at);}
    List<Candle> rows(){return List.of(new Candle(at-600,110,100,1000,1000),new Candle(at-300,110,100,1000,1000));}
    FillEstimate fill(double probability,double hours){return new FillEstimate(probability,hours,100,0);}
    FillModel model(Map<Integer,FillEstimate> values) {
        return new FillModel(){@Override public FillEstimate estimateSell(FillCurve curve,int price,int quantity,double hours) {
            return values.getOrDefault(price,FillEstimate.never());
        }};
    }
    ExitPriceComparison.Result compare(FillModel model,int... prices) {
        return ExitPriceComparison.compare(561,10,1000,110,1,Integer.MAX_VALUE,prices,quote(),rows(),1,
            Instant.ofEpochSecond(at),model,tax);
    }
    @Test public void fasterPositiveRatioCannotHideWorseValueWhenTheOrderRemainsUnfilled() {
        var result=compare(model(Map.of(110,fill(.9,1),130,fill(.1,.02))),130);
        assertTrue(result.available());assertEquals(2,result.limits.size());
        assertTrue(result.limits.get(1).profitPerHour>result.baseline.profitPerHour);
        assertTrue(result.limits.get(1).markedProfit.compareTo(result.baseline.markedProfit)<0);
        assertEquals(110,result.selectedPrice);
    }
    @Test public void supportedPatientPremiumCanBeatCopyingTheLatestHighTrade() {
        var result=compare(model(Map.of(110,fill(.9,1),120,fill(.7,.2))),120);
        assertEquals(120,result.selectedPrice);assertTrue(result.selectedPrice>quote().getHigh());
        assertTrue(result.selected.markedProfit.compareTo(result.baseline.markedProfit)>0);
        long mark=tax.netProceeds(561,100,10),sold=tax.netProceeds(561,120,10);
        assertEquals(BigDecimal.valueOf(mark-1000).add(BigDecimal.valueOf(sold-mark).multiply(new BigDecimal("0.7"))),result.selected.markedProfit);
    }
    @Test public void missingOptionalEvidenceRetainsTheExistingLimitWithoutInventingPrices() {
        var model=model(Map.of(110,fill(.9,1),120,fill(.7,.2)));
        var stale=new LatestPrice(110,at-121,100,at);
        var result=ExitPriceComparison.compare(561,10,1000,110,1,150,new int[]{120},stale,rows(),1,
            Instant.ofEpochSecond(at),model,tax);
        assertFalse(result.available());assertEquals(110,result.selectedPrice);
        var conflict=new ArrayList<>(rows());conflict.add(new Candle(at-300,111,100,1000,1000));
        result=ExitPriceComparison.compare(561,10,1000,110,1,150,new int[]{120},quote(),conflict,1,
            Instant.ofEpochSecond(at),model,tax);
        assertFalse(result.available());assertEquals(110,result.selectedPrice);
        assertFalse(ExitPriceComparison.compare(561,10,1000,110,1,150,new int[17],quote(),rows(),1,
            Instant.ofEpochSecond(at),model,tax).available());
    }
    @Test public void observedLossBaselineCannotGrantLossOrAnAlternativeOrder() {
        var result=ExitPriceComparison.compare(561,10,1500,110,1,200,new int[]{120,160},quote(),rows(),1,
            Instant.ofEpochSecond(at),model(Map.of(110,fill(.9,1),120,fill(.7,.2),160,fill(.9,.1))),tax);
        assertTrue(result.available());assertTrue(result.baseline.profit<0);
        assertEquals(110,result.selectedPrice);assertFalse(result.limits.stream().anyMatch(l->l.price==120));
    }
    @Test public void exactPooledSubsetCostRejectsTheIntegerAveragePriceFloor() {
        var exits=new SellTimingEngine(model(Map.of(3500,fill(.8,1),3403,fill(.99,.1))),tax);
        var position=new Position(561,"Rune",3,10007,at-3600,true);
        var result=exits.compareMaintenance(position,2,3500,3403,new LatestPrice(3500,at,3300,at),rows(),
            TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES),Instant.ofEpochSecond(at));
        assertNotNull(result);assertEquals(6671,result.allocatedCost);
        assertEquals(1,result.limits.size());assertEquals(3500,result.selectedPrice);
        assertNull(exits.compareMaintenance(new Position(561,"Personal",3,0,at-3600,false),2,3500,3403,quote(),rows(),
            TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES),Instant.ofEpochSecond(at)));
    }
    @Test public void probabilityAndMarkArithmeticStayExactForLargeValuesAndNegativeHalfRounding() {
        int quantity=10_000_000,price=1_900_000_000;long cost=9_000_000_000_000_001L;
        var result=ExitPriceComparison.compare(561,quantity,cost,price,1,Integer.MAX_VALUE,new int[0],
            new LatestPrice(price,at,price-1,at),rows(),1,Instant.ofEpochSecond(at),model(Map.of(price,fill(.7,1))),tax);
        long mark=tax.netProceeds(561,price-1,quantity),net=tax.netProceeds(561,price,quantity);
        assertEquals(BigDecimal.valueOf(mark-cost).add(BigDecimal.valueOf(net-mark).multiply(new BigDecimal("0.7"))),result.baseline.markedProfit);
        var negative=ExitPriceComparison.compare(561,1,100,101,1,200,new int[0],new LatestPrice(101,at,100,at),rows(),1,
            Instant.ofEpochSecond(at),model(Map.of(101,fill(.5,1))),tax);
        assertEquals(-2,negative.baseline.roundedMarkedProfit());
    }
    @Test public void comparisonUsesOneQuantityAndHorizonAndExpiresWithItsOldestTrade() {
        List<Double> hours=new ArrayList<>();List<Integer> quantities=new ArrayList<>();
        var model=new FillModel(){@Override public FillEstimate estimateSell(FillCurve curve,int price,int quantity,double horizon) {
            hours.add(horizon);quantities.add(quantity);return fill(.8,1);
        }};
        var result=ExitPriceComparison.compare(561,10,1000,110,1,150,new int[]{110,120,120},new LatestPrice(110,at-119,100,at),rows(),1.25,
            Instant.ofEpochSecond(at),model,tax);
        assertEquals(List.of(1.25,1.25),hours);assertEquals(List.of(10,10),quantities);
        assertEquals(2,result.limits.size());assertTrue(result.currentAt(at+1));assertFalse(result.currentAt(at+2));
        assertFalse(result.currentAt(at-1));
    }
    @Test public void estimatorFailureOrInvalidProbabilityNeverWithdrawsTheExistingLimit() {
        assertEquals(110,compare(model(Map.of(110,fill(.9,1),120,fill(Double.NaN,.1))),120).selectedPrice);
        var failure=new FillModel(){@Override public FillEstimate estimateSell(FillCurve c,int p,int q,double h){throw new IllegalStateException("optional model failure");}};
        var result=compare(failure,120);assertEquals(110,result.selectedPrice);assertFalse(result.available());
    }
    @Test public void aProfitableAlternativeCannotCreateADiscountBelowTheCurrentLowTrade() {
        var result=ExitPriceComparison.compare(561,10,100,110,1,150,new int[]{90},quote(),rows(),1,
            Instant.ofEpochSecond(at),model(Map.of(110,fill(.5,1),90,fill(1,.01))),tax);
        assertTrue(result.available());assertEquals(1,result.limits.size());assertEquals(110,result.selectedPrice);
    }
}
