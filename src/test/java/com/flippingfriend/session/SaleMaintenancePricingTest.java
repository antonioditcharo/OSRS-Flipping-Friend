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
            List.of(),horizon,now,false,exits,tax);
    }
    @Test public void exactSubsetFloorPreservesTheExtraCoin() {
        var p=new Position(1,"Item",3,301,now.getEpochSecond()-60,true);
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
}
