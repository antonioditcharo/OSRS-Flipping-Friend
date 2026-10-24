package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MaintenanceTimingTest {
    final long at=1800000600;
    final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
    final TaxCalculator tax=new TaxCalculator();
    LatestPrice price(int high,int low){return new LatestPrice(high,at,low,at);}
    @Test public void originalAndFallbackBuyDeadlinesFreezeAndRealProgressOnlyDefersAction() {
        var buy=new TrackedOffer(0,561,true,1000,10,at-1200);
        assertEquals(at-600,buy.freezeBuyMaintenanceDeadline(10,180,at));
        assertEquals(at-600,buy.freezeBuyMaintenanceDeadline(120,240,at+100));
        assertTrue(BuyRepricePricing.overdue(buy.getFirstSeen(),buy.getFirstSeen(),buy.getBuyMaintenanceDeadlineAt(),price(1100,1000),horizon,at));
        buy.observeFillAt(at-60);
        assertFalse(BuyRepricePricing.overdue(buy.getFirstSeen(),buy.getLastFillObservedAt(),buy.getBuyMaintenanceDeadlineAt(),price(1100,1000),horizon,at));
        assertEquals(buy.getBuyMaintenanceDeadlineAt(),buy.copyObservation().getBuyMaintenanceDeadlineAt());
        var legacy=new TrackedOffer(1,561,true,1000,10,at-10000);
        assertEquals(at+180*60,legacy.freezeBuyMaintenanceDeadline(Double.NaN,180,at));
        assertEquals(at+180*60,legacy.freezeBuyMaintenanceDeadline(0,300,at+600));
    }
    @Test public void repriceRequiresMaterialImprovementCorrectSideAndNoRecentFills() {
        long first=at-3600;
        var rising=new Candle(at-300,1100,1010,100,100);
        assertNull(BuyRepricePricing.quote(561,1000,100,first,first,price(1100,1010),rising,horizon,tax,at,1000,1));
        assertNotNull(BuyRepricePricing.quote(561,1000,100,first,first,price(1100,1010),rising,horizon,tax,at,1011,1));
        assertNull(BuyRepricePricing.quote(561,1000,100,first,at-1,price(1100,1010),rising,horizon,tax,at,1011,1));
        var lowSideNotRising=new Candle(at-300,1100,990,100,100);
        assertNull(BuyRepricePricing.quote(561,1000,100,first,first,price(1100,1010),lowSideNotRising,horizon,tax,at,1011,1));
        for(var unsupported:List.of(new Candle(at,1100,1010,100,100),new Candle(at-300,1100,1010,100,0),new Candle(at-1200,1100,1010,100,100)))
            assertNull("Incomplete, volume-free or stale bars cannot corroborate a chase",BuyRepricePricing.quote(561,1000,100,first,first,price(1100,1010),unsupported,horizon,tax,at,1011,1));
        var stability=new AdvisedPriceTracker();assertEquals(1000,stability.price(561,true,1000,at));
        assertEquals("The old 2% budget cannot retain an unchanged outbid buy",1011,stability.buyReprice(561,1000,1011,at+1));
    }
    @Test public void supportedSellReductionDoesNotFollowAnUncorroboratedLowSpotThroughItsMargin() {
        var position=new Position(561,"Nature rune",10,10000,at-1200,true);
        position.setTargetSellPrice(1400);position.setPredictedSellMinutes(180);
        var bars=List.of(new Candle(at-300,1300,1250,100,100),new Candle(at-600,1290,1250,100,100));
        var quote=new SaleMaintenancePricing().quote(561,1400,10,position,price(1100,1090),bars,horizon,
            Instant.ofEpochSecond(at),false,new SellTimingEngine(new FillModel(),tax),tax,at-1200,at-1200);
        assertNotNull(quote);assertEquals(1289,quote.price);assertFalse(quote.lossCut);
        assertTrue(tax.netProceeds(561,quote.price,10)>=position.getTotalCost());
    }
    @Test public void absentOriginalStopCannotInventLossPermissionFromCurrentRiskProfile() {
        var position=new Position(561,"Nature rune",10,10000,at-1200,true);
        var exits=new SellTimingEngine(new FillModel(),tax);
        var decision=exits.evaluateManagedExit(position,price(800,790),null,List.of(),horizon,Instant.ofEpochSecond(at),0,true,false,false);
        assertEquals(SellDecision.Action.SELL,decision.getAction());
        assertTrue(tax.netProceeds(561,decision.getPrice(),10)>=position.getTotalCost());
        position.setStopPrice(850);
        assertEquals(SellDecision.Action.CUT,exits.evaluateManagedExit(position,price(800,790),null,List.of(new Candle(at-300,800,790,100,100)),horizon,
            Instant.ofEpochSecond(at),0,true,false,false).getAction());
    }
    @Test public void supportedMaintenanceHonorsEachActualAttendanceInterval() {
        for(var interval:List.of(CheckInterval.FIVE_MINUTES,CheckInterval.THIRTY_MINUTES,CheckInterval.TWO_HOURS,CheckInterval.EIGHT_HOURS)) {
            var h=TradingHorizon.of(RiskProfile.MODERATE,interval);
            long wait=h.staleOfferMinutes()*60;
            var position=new Position(561,"Nature rune",10,10000,at-wait,true);
            position.setTargetSellPrice(1400);position.setPredictedSellMinutes(900);
            var bars=List.of(new Candle(at-300,1300,1250,100,100),new Candle(at-600,1290,1250,100,100));
            var pricing=new SaleMaintenancePricing();var exits=new SellTimingEngine(new FillModel(),tax);
            assertNull(pricing.quote(561,1400,10,position,price(1290,1250),bars,h,Instant.ofEpochSecond(at),false,exits,tax,at-wait+1,at-wait+1));
            assertNotNull(pricing.quote(561,1400,10,position,price(1290,1250),bars,h,Instant.ofEpochSecond(at),false,exits,tax,at-wait,at-wait));
        }
    }
}
