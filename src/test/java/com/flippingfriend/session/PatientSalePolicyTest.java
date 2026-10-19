package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Healthy stock must be listed promptly without abandoning the profit it was acquired for. */
public class PatientSalePolicyTest {
    final Instant now=Instant.ofEpochSecond(1800000600);
    final TaxCalculator tax=new TaxCalculator();
    final SellTimingEngine exits=new SellTimingEngine(new FillModel(),tax);
    final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
    Position stock(long age) {
        var p=new Position(561,"Nature rune",1000,1000000,now.getEpochSecond()-age*60,true);
        p.setTargetSellPrice(1120);p.setStopPrice(950);p.setPredictedSellMinutes(120);return p;
    }
    LatestPrice market(int high,long age) {return new LatestPrice(high,now.getEpochSecond()-age,high-20,now.getEpochSecond()-age);}
    List<Candle> series(int high) {
        var result=new ArrayList<Candle>();long end=now.getEpochSecond()/300*300;
        for(int i=100;i>0;i--)result.add(new Candle(end-i*300,high,high-20,10000,10000));
        return result;
    }
    @Test public void initialHealthyListingKeepsThePlannedProfitInsteadOfOptimizingItAway() {
        var p=stock(4);var decision=exits.evaluateManagedExit(p,market(1021,0),null,series(1021),horizon,now,40000,true,false,false);
        assertTrue(decision.isSell());assertTrue("An immediate listing must retain its intended exit",decision.getPrice()>=1120);
    }
    @Test public void quietInitialListingKeepsItsTargetWithoutClaimingAFill() {
        var decision=exits.evaluateManagedExit(stock(4),market(1021,1200),null,series(1021),horizon,now,40000,true,false,false);
        assertTrue(decision.isSell());assertTrue(decision.getPrice()>=1120);assertTrue(Double.isNaN(decision.getExpectedMinutes()));
    }
    @Test public void healthyMaintenanceDoesNotJumpToBreakEvenWhenTheQuoteDips() {
        var p=stock(130);
        var quote=maintenance(p,1120,1000,1021,130,130,false);
        if(quote!=null)assertTrue("Do not erase the planned profit on one routine adjustment",quote.price>tax.breakEvenSellPriceForCost(561,1000000,1000));
    }
    SaleMaintenancePricing.Quote maintenance(Position p,int ask,int remaining,int high,long age,long idle,boolean sellOnly) {
        return new SaleMaintenancePricing().quote(561,ask,remaining,p,market(high,0),series(high),horizon,now,sellOnly,exits,tax,
            now.getEpochSecond()-age*60,now.getEpochSecond()-idle*60);
    }
    @Test public void fullPredictionAndRecentFillsProtectWorkingOffers() {
        assertNull(maintenance(stock(300),1200,1000,1140,119,119,false));
        assertNull(maintenance(stock(300),1200,900,1140,150,1,false));
        assertNotNull(maintenance(stock(300),1200,900,1140,150,10,false));
        assertEquals(1120,maintenance(stock(300),1200,900,1100,150,10,false).price);
    }
    @Test public void stopAndPlayerWindDownRemainPromptEvenJustAfterAFill() {
        assertTrue(maintenance(stock(1),1200,900,900,1,0,false).lossCut);
        assertNotNull(maintenance(stock(1),1200,900,1100,1,0,true));
    }
    @Test public void windingDownDoesNotRelaxTheExistingNonStopMaintenanceCostFloor() {
        var q=maintenance(stock(1),1200,900,980,1,0,true);
        assertNotNull(q);assertFalse(q.lossCut);assertTrue(tax.netProceeds(561,q.price,900)>=900000);
    }
    @Test public void expiredHorizonPermitsNonLossRecoveryButNotAMissingOrSinglePrintTrend() {
        var p=stock(400);var q=maintenance(p,1120,1000,1021,180,180,false);
        assertNotNull(q);assertTrue(q.price>=tax.breakEvenSellPriceForCost(561,1000000,1000));
        for(var bars:List.of(List.<Candle>of(),List.of(series(1021).get(99)),series(1200)))
            assertNull(new SaleMaintenancePricing().quote(561,1120,1000,p,market(1021,0),bars,horizon,now,false,exits,tax,
                now.getEpochSecond()-180*60,now.getEpochSecond()-180*60));
    }
    @Test public void noOriginalTargetKeepsAHealthyListingAndNeverInventsProfitForPersonalStock() {
        var p=stock(200);p.setTargetSellPrice(0);
        assertNull(maintenance(p,1200,1000,1100,200,200,false));
        var personal=maintenance(null,1200,1000,1100,200,200,false);
        assertNotNull(personal);assertFalse(personal.costKnown);
    }
    @Test public void matureTargetCanBeRepricedDownWithoutSurrenderingTheTargetOrCurrentHigherMarket() {
        var q=maintenance(stock(200),1300,1000,1100,200,200,false);
        assertNotNull(q);assertEquals(1120,q.price);
        var decision=exits.evaluateManagedExit(stock(4),market(1150,0),null,series(1150),horizon,now,40000,true,false,false);
        assertEquals(1150,decision.getPrice());
    }
    @Test public void missingFutureTimingAndQuietQuotesDoNotAuthorizeNormalMaintenance() {
        var p=stock(400);
        for(long[] timing:new long[][]{{0,0},{now.getEpochSecond()+1,now.getEpochSecond()+1},{now.getEpochSecond()-200*60,Long.MAX_VALUE}})
            assertNull(new SaleMaintenancePricing().quote(561,1120,1000,p,market(1021,0),series(1021),horizon,now,false,exits,tax,timing[0],timing[1]));
        assertNull(new SaleMaintenancePricing().quote(561,1120,1000,p,market(1021,301),series(1021),horizon,now,false,exits,tax,
            now.getEpochSecond()-200*60,now.getEpochSecond()-200*60));
    }
    @Test public void explicitLongHorizonIsNotReplacedByTheProfileHoldLimit() {
        var longer=TradingHorizon.of(RiskProfile.LOW,CheckInterval.EIGHT_HOURS,1000);
        var decision=exits.evaluateManagedExit(stock(100),market(1021,0),null,series(1021),longer,now,40000,true,false,false);
        assertTrue(decision.getPrice()>=1120);
    }
    @Test public void expiredHorizonDoesNotUndercutAHigherCurrentAskForAModeledFastFill() {
        var decision=exits.evaluateManagedExit(stock(400),market(1150,0),null,series(1021),horizon,now,40000,true,false,false);
        assertEquals("Elapsed time alone must not discount a current profitable ask",1150,decision.getPrice());
    }
}
