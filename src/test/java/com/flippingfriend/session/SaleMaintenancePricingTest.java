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
    SaleMaintenancePricing.Evaluation working(Position p,int ask,int remaining,LatestPrice price,List<Candle> bars,long first,long progress,boolean windDown) {
        return new SaleMaintenancePricing().evaluate(1,ask,remaining,p,price,bars,horizon,now,windDown,exits,tax,first,progress);
    }
    List<Candle> buckets(int high) {
        long end=now.getEpochSecond()/300*300;
        return List.of(new Candle(end-300,high,3000,100,100),new Candle(end-600,high,3000,100,100));
    }
    @Test public void repeatedNearAskBuyersCanReceiveAnExactSmallProfitableAdjustmentDespiteAQuietBid() {
        long at=now.getEpochSecond(),first=at-3600;
        var position=new Position(1,"Synthetic item",11,37000,first,true);position.setTargetSellPrice(3650);position.setStopPrice(3100);
        var price=new LatestPrice(3599,at,3500,at-901);
        var result=working(position,3600,7,price,buckets(3599),first,first,false);
        assertEquals(SaleMaintenancePricing.Status.ELIGIBLE,result.status);var quote=result.quote;
        assertEquals(3598,quote.price);assertEquals(1,quote.minimumReduction);assertTrue(quote.fineTick);assertFalse(quote.lossCut);
        assertEquals(com.flippingfriend.core.SaleAccounting.allocateCost(37000,11,7),quote.allocatedCost);
        assertTrue(tax.netProceeds(1,quote.price,7)>quote.allocatedCost);
        assertEquals(3650,position.getTargetSellPrice());assertEquals(3100,position.getStopPrice());
        var tracker=new AdvisedPriceTracker();assertEquals(3600,tracker.price(1,false,3600,at-1));
        assertEquals("A remembered target cannot erase supported fine repricing",3598,tracker.saleMaintenance(1,quote,at));
        assertEquals(3598,tracker.price(1,false,3599,at+1));
    }
    @Test public void smallChangesStillRequirePatienceRecentConsecutiveVolumeAndReachTheObservedMarket() {
        long at=now.getEpochSecond(),first=at-3600,end=at/300*300;
        var position=new Position(1,"Synthetic item",7,23000,first,true);
        var price=new LatestPrice(3599,at,3500,at);
        for(var unsupported:List.<List<Candle>>of(List.of(),List.of(buckets(3599).get(0)),
            List.of(new Candle(end-300,3599,3500,0,10),buckets(3599).get(1)),
            List.of(buckets(3599).get(0),new Candle(end-900,3599,3500,100,100)),
            List.of(buckets(3599).get(0),new Candle(end-600,3600,3500,100,100)),
            List.of(buckets(3599).get(0),buckets(3599).get(1),new Candle(end-600,3598,3500,100,100)))) {
            assertEquals(SaleMaintenancePricing.Status.MARKET_SUPPORT,working(position,3600,7,price,unsupported,first,first,false).status);
        }
        assertEquals(SaleMaintenancePricing.Status.MARKET_SUPPORT,working(position,3600,7,
            new LatestPrice(3598,at,3500,at),buckets(3599),first,first,false).status);
        assertEquals(SaleMaintenancePricing.Status.PLACEMENT_PATIENCE,working(position,3600,7,price,buckets(3599),at-100,at-100,false).status);
        assertEquals(SaleMaintenancePricing.Status.RECENT_PROGRESS,working(position,3600,7,price,buckets(3599),first,at-100,false).status);
        assertEquals(SaleMaintenancePricing.Status.UNKNOWN_CLOCKS,working(position,3600,7,price,buckets(3599),0,first,false).status);
        assertEquals(SaleMaintenancePricing.Status.IMMATERIAL_CHANGE,working(null,3600,7,price,buckets(3599),first,first,false).status);
        assertEquals(SaleMaintenancePricing.Status.IMMATERIAL_CHANGE,working(position,3600,7,price,buckets(3599),first,first,true).status);
    }
    @Test public void askOnlyEvidenceCannotAuthorizeLossUnknownCostStaleAskOrUnprovenQuantity() {
        long at=now.getEpochSecond(),first=at-3600;
        var position=new Position(1,"Synthetic item",7,23000,first,true);
        for(var price:List.of(new LatestPrice(3599,at-301,3500,at),new LatestPrice(3599,at,3500,at+1),
                new LatestPrice(3599,at+1,3500,at),new LatestPrice(3599,0L,3500,at)))
            assertEquals(SaleMaintenancePricing.Status.STALE_PRICES,working(position,3600,7,price,buckets(3599),first,first,false).status);
        var quietBid=new LatestPrice(3599,at,3500,at-901);
        assertEquals(SaleMaintenancePricing.Status.STALE_PRICES,working(null,3600,7,quietBid,buckets(3599),first,first,false).status);
        assertEquals(SaleMaintenancePricing.Status.STALE_PRICES,working(position,3600,7,quietBid,buckets(3599),first,first,true).status);
        assertEquals(SaleMaintenancePricing.Status.UNPROVEN_QUANTITY,working(position,3600,8,quietBid,buckets(3599),first,first,false).status);
        var taxPlateau=new Position(1,"Synthetic item",7,tax.netProceeds(1,3600,7),first,true);
        var freeTick=working(taxPlateau,3600,7,quietBid,buckets(3599),first,first,false).quote;
        assertNotNull(freeTick);assertEquals(3599,freeTick.price);assertEquals(0,tax.netProceeds(1,freeTick.price,7)-freeTick.allocatedCost);
        var expensive=new Position(1,"Synthetic item",7,tax.netProceeds(1,3601,7),first,true);
        expensive.setStopPrice(3800);
        assertEquals(SaleMaintenancePricing.Status.COST_FLOOR,working(expensive,3600,7,quietBid,buckets(3599),first,first,false).status);
        assertEquals("Recent buyers cannot let the exact cost floor land above their price",SaleMaintenancePricing.Status.COST_FLOOR,
            working(expensive,3601,7,quietBid,buckets(3599),first,first,false).status);
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
    @Test public void savedRoutineLimitStillRechecksExactCostQuantityAndQuoteFreshness() {
        long at=now.getEpochSecond(),first=at-900;
        String account="rs-"+"1".repeat(64);
        var source=com.flippingfriend.core.OfferEvent.builder("saved",at,"SELLING").accountId(account).authenticatedObservation(true)
            .eventIdentity("saved-source","session","sale").sequence(1).slot(0).item(1,"Item").price(150)
            .quantities(3,0).spent(0).firstSeenAt(first).build();
        var accepted=com.flippingfriend.companion.SalePolicyAcceptanceDeliveryTest.receipt(account,"saved-reduction",source,119,at);
        var p=new Position(1,"Item",3,371,first,true);p.setTargetSellPrice(140);p.setStopPrice(90);
        var quote=new LatestPrice(120,at,120,at);
        var pricing=new SaleMaintenancePricing();
        var recovered=pricing.evaluateRelist(1,150,2,p,quote,List.of(),horizon,now,false,exits,tax,first,first,accepted).quote;
        assertNotNull(recovered);assertFalse(recovered.lossCut);assertTrue(recovered.price>=119);
        assertTrue(tax.netProceeds(1,recovered.price,2)>=com.flippingfriend.core.SaleAccounting.allocateCost(371,3,2));
        assertNull(pricing.evaluateRelist(1,150,4,p,quote,List.of(),horizon,now,false,exits,tax,first,first,accepted).quote);
        assertNull(pricing.evaluateRelist(1,150,2,p,new LatestPrice(120,at-301,120,at),List.of(),horizon,now,false,exits,tax,first,first,accepted).quote);
        assertNull(pricing.evaluateRelist(1,150,2,p,quote,List.of(),horizon,now,false,exits,tax,first,first+1,accepted).quote);
    }
    @Test public void quietCollectedLimitRequiresFreshFeedAndCannotReuseStaleLossPermission() {
        long at=now.getEpochSecond(),first=at-3600;String account="rs-"+"1".repeat(64);
        var source=com.flippingfriend.core.OfferEvent.builder("quiet",at,"SELLING").accountId(account).authenticatedObservation(true)
            .eventIdentity("quiet-source","session","quiet-sale").sequence(1).slot(0).item(1,"Synthetic item").price(150)
            .quantities(7,0).spent(0).firstSeenAt(first).build();
        var accepted=com.flippingfriend.companion.SalePolicyAcceptanceDeliveryTest.receipt(account,"quiet-reduction",source,119,at);
        var position=new Position(1,"Synthetic item",7,819,first,true);
        position.setTargetSellPrice(150);position.setStopPrice(90);
        var quiet=new LatestPrice(100,at-666,95,at-111);var pricing=new SaleMaintenancePricing();
        var result=pricing.evaluateRelist(1,150,7,position,quiet,List.of(),horizon,now,false,exits,tax,first,first,accepted,at);
        assertNotNull(result.quote);assertEquals(119,result.quote.price);assertFalse(result.quote.lossCut);
        assertEquals(0,tax.netProceeds(1,result.quote.price,7)-result.quote.allocatedCost);
        assertEquals(150,position.getTargetSellPrice());assertEquals(90,position.getStopPrice());
        for(long feed:new long[]{0,at-121,at+1})assertNull(pricing.evaluateRelist(1,150,7,position,quiet,List.of(),horizon,now,false,exits,tax,first,first,accepted,feed).quote);
        assertNull(pricing.evaluateRelist(1,150,7,position,quiet,List.of(),horizon,now,false,exits,tax,first,first,null,at).quote);
        assertNull(pricing.evaluateRelist(1,150,7,position,new LatestPrice(100,at+1,95,at),List.of(),horizon,now,false,exits,tax,first,first,accepted,at).quote);
        var json=new com.google.gson.Gson();var raw=json.toJsonTree(accepted).getAsJsonObject();
        raw.addProperty("lossCut",true);raw.addProperty("mode","STOP");
        var stop=json.fromJson(raw,com.flippingfriend.core.SalePolicyReceipt.class);assertTrue(stop.isWellFormed());
        assertNull(pricing.evaluateRelist(1,150,7,position,quiet,List.of(),horizon,now,false,exits,tax,first,first,stop,at).quote);
    }
    @Test public void freshStopsAndExplicitWindDownKeepTheirSeparateRelistRules() {
        long first=now.getEpochSecond()-900;
        var supported=List.of(new Candle(now.getEpochSecond()-300,80,80,100,100));
        var cut=relist(planned(),80,supported,first,first).quote;
        assertNotNull(cut);assertTrue(cut.lossCut);assertEquals(80,cut.floor);
        var windDown=new SaleMaintenancePricing().evaluateRelist(1,150,2,planned(),new LatestPrice(80,now.getEpochSecond(),80,now.getEpochSecond()),
            supported,horizon,now,true,exits,tax,first,first).quote;
        assertNotNull(windDown);assertTrue("A proven stop remains urgent even in wind-down",windDown.lossCut);
        var noStop=planned();noStop.setStopPrice(0);
        windDown=new SaleMaintenancePricing().evaluateRelist(1,150,2,noStop,new LatestPrice(80,now.getEpochSecond(),80,now.getEpochSecond()),
            List.of(),horizon,now,true,exits,tax,first,first).quote;
        assertNotNull(windDown);assertFalse(windDown.lossCut);assertTrue(tax.netProceeds(1,windDown.price,2)>=windDown.allocatedCost);
    }
}
