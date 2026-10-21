package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.flippingfriend.session.SaleMaintenancePricing.Status.*;

public class SaleMaintenanceDiagnosticTest {
    final long at=1800000610L;
    final TaxCalculator tax=new TaxCalculator();
    final SellTimingEngine exits=new SellTimingEngine(new FillModel(),tax);
    final SaleMaintenancePricing pricing=new SaleMaintenancePricing();
    final TradingHorizon h=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
    LatestPrice price(long age){return new LatestPrice(120,at-age,110,at-age);}
    List<Candle> bars(){return List.of(new Candle(at/300*300-300,120,110,100,100),new Candle(at/300*300-600,120,110,100,100));}
    Position position(long cost){return new Position(561,"Rune",3,cost,at-3600,true);}
    SaleMaintenancePricing.Evaluation evaluate(Position p,LatestPrice price,List<Candle> bars,long first,long progress,TradingHorizon horizon) {
        return pricing.evaluate(561,150,2,p,price,bars,horizon,Instant.ofEpochSecond(at),false,exits,tax,first,progress);
    }
    @Test public void placementAndProgressBoundaryRemainSeparateAtEveryCadence() {
        for(var interval:List.of(CheckInterval.FIVE_MINUTES,CheckInterval.THIRTY_MINUTES,CheckInterval.TWO_HOURS,CheckInterval.EIGHT_HOURS)) {
            var horizon=TradingHorizon.of(RiskProfile.MODERATE,interval);long wait=horizon.staleOfferMinutes()*60L;
            var before=evaluate(position(301),price(0),bars(),at-wait+1,at-wait+1,horizon);
            assertEquals(PLACEMENT_PATIENCE,before.status);assertEquals(at+1,before.reassessAt);assertNull(before.quote);
            var progress=evaluate(position(301),price(0),bars(),at-wait-10,at-wait+1,horizon);
            assertEquals(RECENT_PROGRESS,progress.status);assertEquals(at+1,progress.reassessAt);
            var due=evaluate(position(301),price(0),bars(),at-wait,at-wait,horizon);
            assertEquals(ELIGIBLE,due.status);assertEquals(200,due.quote.allocatedCost);
            assertEquals(119,due.quote.price);assertTrue(tax.netProceeds(561,due.quote.price,2)>=200);
        }
    }
    @Test public void marketWaitsHaveNoInventedTimeAndStalePricesCannotApprove() {
        assertEquals(MISSING_PRICES,evaluate(position(301),null,bars(),at-3600,at-3600,h).status);
        assertEquals(STALE_PRICES,evaluate(position(301),price(301),bars(),at-3600,at-3600,h).status);
        assertEquals(STALE_PRICES,evaluate(position(301),price(-1),bars(),at-3600,at-3600,h).status);
        for(var unsupported:List.of(List.<Candle>of(),List.of(bars().get(0)),
                List.of(new Candle(at/300*300-300,120,110,0,100),bars().get(1)))) {
            var result=evaluate(position(301),price(0),unsupported,at-3600,at-3600,h);
            assertEquals(MARKET_SUPPORT,result.status);assertEquals(0,result.reassessAt);assertNull(result.quote);
        }
    }
    @Test public void exactCostUnknownPlansAndExtrasRetainExistingProtection() {
        var floor=evaluate(position(600),price(0),bars(),at-3600,at-3600,h);
        assertEquals(COST_FLOOR,floor.status);assertNull(floor.quote);
        var unknownPlan=evaluate(position(301),price(0),bars(),at-3600,at-3600,h);
        assertEquals(ELIGIBLE,unknownPlan.status);assertFalse(unknownPlan.quote.lossCut);
        assertTrue(unknownPlan.quote.price>=unknownPlan.quote.floor);
        assertEquals(UNPROVEN_QUANTITY,evaluate(new Position(561,"Rune",1,100,at-3600,true),price(0),bars(),at-3600,at-3600,h).status);
        assertEquals(UNKNOWN_CLOCKS,evaluate(position(301),price(0),bars(),at+1,at+1,h).status);
    }
    TrackedOffer sale(){var offer=new TrackedOffer(0,561,false,150,3,at-3600);offer.setState("SELLING");return offer;}
    @Test public void copiedOfferSurvivesHeartbeatButProgressSlotReuseAndExpiryWithdraw() {
        var offer=sale();var row=new SaleMaintenanceDiagnostic(offer,null,"unavailable",price(0),at,at+151,Double.NaN,false);
        assertTrue(row.matches(offer.copyObservation(),at));
        offer.setLastChanged(at+1);assertTrue(row.matches(offer,at+1));
        offer.setQuantityFilled(1);assertFalse(row.matches(offer,at+1));
        assertFalse(row.matches(new TrackedOffer(0,561,false,150,3,at-1),at+1));
        assertFalse(row.matches(sale(),at+30));assertFalse(row.matches(sale(),at-1));
        offer=sale();offer.observeFillAt(at-1);assertFalse(row.matches(offer,at));
    }
    @Test public void evidenceAndPatienceBoundariesExpireBeforeWorkerRefresh() {
        var offer=sale();
        var row=new SaleMaintenanceDiagnostic(offer,null,"unavailable",price(300),at,at+151,Double.NaN,false);
        assertTrue(row.matches(offer,at));assertFalse(row.matches(offer,at+1));
        var waiting=evaluate(position(301),price(0),bars(),at-h.staleOfferMinutes()*60+1,at-h.staleOfferMinutes()*60+1,h);
        row=new SaleMaintenanceDiagnostic(offer,waiting,null,price(0),at,at+151,Double.NaN,false);
        assertEquals(at+1,row.validUntil);
        assertEquals(at+1,new SaleMaintenanceDiagnostic(offer,null,"unavailable",price(0),at,at+1,Double.NaN,false).validUntil);
    }
}
