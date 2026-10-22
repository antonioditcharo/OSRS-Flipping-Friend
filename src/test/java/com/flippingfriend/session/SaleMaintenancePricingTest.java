package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaleMaintenancePricingTest {
    final Instant now=Instant.ofEpochSecond(1800000600);
    final TaxCalculator tax=new TaxCalculator();
    final SellTimingEngine exits=new SellTimingEngine(new FillModel(),tax);
    final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
    SaleMaintenancePricing.Quote quote(Position p,int ask,int remaining,int high) {
        return new SaleMaintenancePricing().quote(1,ask,remaining,p,new LatestPrice(high,now.getEpochSecond(),high,now.getEpochSecond()),
            List.of(new Candle(now.getEpochSecond()/300*300-300,high,high,100,100),new Candle(now.getEpochSecond()/300*300-600,high,high,100,100)),
            horizon,now,false,exits,tax,now.getEpochSecond()-400*60,now.getEpochSecond()-400*60);
    }
    @Test public void exactSubsetFloorPreservesTheExtraCoin() {
        var p=new Position(1,"Item",3,301,now.getEpochSecond()-400*60,true);
        var quote=quote(p,150,2,102);
        assertNotNull(quote);assertEquals(200,quote.allocatedCost);assertEquals(102,quote.floor);
        assertTrue(tax.netProceeds(1,quote.price,2)>=quote.allocatedCost);
    }
    @Test public void mixedUnknownUnitsCannotRemoveKnownCostProtection() {
        assertNull(quote(new Position(1,"Item",1,100,now.getEpochSecond(),true),150,2,120));
    }
    @Test public void personalSaleKeepsCostUnknownAndStopCanAuthorizeLoss() {
        assertFalse(quote(null,150,2,120).costKnown);
        var p=new Position(1,"Item",2,200,now.getEpochSecond(),true);p.setStopPrice(90);
        assertTrue(quote(p,150,2,80).lossCut);
    }
    Position planned() {
        var position=new Position(1,"Item",3,301,now.getEpochSecond()-900,true);
        position.setTargetSellPrice(140);position.setStopPrice(90);return position;
    }
    SaleMaintenancePricing.Evaluation relist(Position p,int high,List<Candle> series,long first,long progress) {
        return new SaleMaintenancePricing().evaluateRelist(1,150,2,p,new LatestPrice(high,now.getEpochSecond(),high,now.getEpochSecond()),
            series,horizon,now,false,exits,tax,first,progress);
    }
    @Test public void missingOrInsufficientBucketsCannotRestoreAnAbandonedTarget() {
        var p=planned();assertEquals(119,quote(p,150,2,120).price);
        long first=now.getEpochSecond()-900,end=now.getEpochSecond()/300*300;
        for(var series:List.<List<Candle>>of(List.of(),List.of(new Candle(end-300,120,120,100,100)),
                List.of(new Candle(end-300,120,120,0,100),new Candle(end-600,120,120,0,100)))) {
            var waiting=relist(p,120,series,first,first);
            assertNull("Unsupported history must not silently restore target140",waiting.quote);
            assertEquals(SaleMaintenancePricing.Status.MARKET_SUPPORT,waiting.status);
        }
    }
    @Test public void genuinelyRecoveredFreshMarketCanRelistWithoutInventingBucketSupport() {
        long first=now.getEpochSecond()-900;
        var recovered=relist(planned(),150,List.of(),first,first).quote;
        assertNotNull(recovered);assertEquals(150,recovered.price);assertEquals(200,recovered.allocatedCost);
        assertFalse(recovered.lossCut);assertTrue(tax.netProceeds(1,recovered.price,2)>=recovered.allocatedCost);
        assertNull(relist(planned(),120,List.of(),first,now.getEpochSecond()+1).quote);
        var stale=new LatestPrice(150,now.getEpochSecond()-301,150,now.getEpochSecond());
        assertEquals(SaleMaintenancePricing.Status.STALE_PRICES,new SaleMaintenancePricing().evaluateRelist(1,150,2,planned(),stale,
            List.of(),horizon,now,false,exits,tax,first,first).status);
    }
    @Test public void freshStopsAndExplicitWindDownKeepTheirSeparateRelistRules() {
        long first=now.getEpochSecond()-900;
        var cut=relist(planned(),80,List.of(),first,first).quote;
        assertNotNull(cut);assertTrue(cut.lossCut);assertEquals(1,cut.floor);
        var windDown=new SaleMaintenancePricing().evaluateRelist(1,150,2,planned(),new LatestPrice(80,now.getEpochSecond(),80,now.getEpochSecond()),
            List.of(),horizon,now,true,exits,tax,first,first).quote;
        assertNotNull(windDown);assertTrue("A proven stop remains urgent even in wind-down",windDown.lossCut);
        var noStop=planned();noStop.setStopPrice(0);
        windDown=new SaleMaintenancePricing().evaluateRelist(1,150,2,noStop,new LatestPrice(80,now.getEpochSecond(),80,now.getEpochSecond()),
            List.of(),horizon,now,true,exits,tax,first,first).quote;
        assertNotNull(windDown);assertFalse(windDown.lossCut);assertTrue(tax.netProceeds(1,windDown.price,2)>=windDown.allocatedCost);
    }
}
