package com.flippingfriend.ui;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaleMaintenanceTextTest {
    final long at=1800000610L;
    SaleMaintenanceDiagnostic row(double minutes,long progress) {
        var offer=new TrackedOffer(2,561,false,150,3,at-3600);offer.setItemName("Nature rune");offer.setState("SELLING");offer.observeFillAt(progress);
        var tax=new TaxCalculator();var price=new LatestPrice(120,at,110,at);
        var eval=new SaleMaintenancePricing().evaluate(561,150,3,new Position(561,"Rune",3,300,at-3600,true),price,
            List.of(new Candle(at/300*300-300,120,110,100,100),new Candle(at/300*300-600,120,110,100,100)),
            TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES),Instant.ofEpochSecond(at),false,
            new SellTimingEngine(new FillModel(),tax),tax,offer.getFirstSeen(),offer.getLastFillObservedAt());
        return new SaleMaintenanceDiagnostic(offer,eval,null,price,at,at+151,minutes,false);
    }
    @Test public void overdueEstimateDoesNotImplyRepricingAuthority() {
        var text=SaleMaintenanceText.render(List.of(row(12,at-1)),at,true);
        assertTrue(text.contains("Slot 3 · Nature rune"));assertTrue(text.contains("Remaining: 3"));
        assertTrue(text.contains("Recent observed fills"));assertTrue(text.contains("Clock-only reassessment"));
        assertTrue(text.contains("Past that estimate; this alone does not authorize repricing"));
        assertTrue(text.contains("final companion authority"));
    }
    @Test public void missingOriginalEstimateAndLogoutStayExplicit() {
        var rows=List.of(row(Double.NaN,at-3600));
        assertTrue(SaleMaintenanceText.render(rows,at,true).contains("Original actual-order estimate: unavailable"));
        assertFalse(SaleMaintenanceText.render(rows,at,false).contains("Nature rune"));
        assertTrue(SaleMaintenanceText.render(rows,at+30,true).contains("evaluation expired"));
    }
}
