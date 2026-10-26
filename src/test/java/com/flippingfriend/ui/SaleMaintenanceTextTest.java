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
    @Test public void immaterialReductionShowsActualTradeInputsAndRequiredChangeWithoutGrantingAuthority() {
        var offer=new TrackedOffer(4,26241,false,3751665,1,at-3600);offer.setItemName("Virtus mask");offer.setState("SELLING");
        var price=new LatestPrice(3737524,at-40,3585195,at-20);var tax=new TaxCalculator();
        var evaluation=new SaleMaintenancePricing().evaluate(26241,offer.getPrice(),1,
            null,price,List.of(),
            TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES),Instant.ofEpochSecond(at),false,
            new SellTimingEngine(new FillModel(),tax),tax,offer.getFirstSeen(),offer.getLastFillObservedAt());
        assertEquals(SaleMaintenancePricing.Status.IMMATERIAL_CHANGE,evaluation.status);assertNull(evaluation.quote);
        var diagnostic=new SaleMaintenanceDiagnostic(offer,evaluation,null,price,at,at+151,12,false);
        var text=SaleMaintenanceText.render(List.of(diagnostic),at+1,true);
        assertTrue(text.contains("Latest buyer-paid trade: 3737524 gp, 41s old"));
        assertTrue(text.contains("Latest seller-received trade: 3585195 gp, 21s old"));
        assertTrue(text.contains("Minimum material reduction: 18758 gp per item"));
        assertTrue(text.contains("Standing ask minus latest buyer-paid trade: 14141 gp per item"));
        assertTrue(text.contains("Small profitable adjustments need two completed buyer-volume buckets"));
        assertFalse(text.contains("eligible for further checks"));
        var expired=SaleMaintenanceText.render(List.of(diagnostic),at+30,true);
        assertTrue(expired.contains("evaluation expired"));assertFalse(expired.contains("3737524 gp"));
    }
    @Test public void missingAndStaleSidesAreReportedSeparatelyWithoutInventingQuotes() {
        var offer=new TrackedOffer(0,561,false,150,3,at-3600);offer.setState("SELLING");
        var price=new LatestPrice(null,null,110,at-301);var tax=new TaxCalculator();
        var evaluation=new SaleMaintenancePricing().evaluate(561,150,3,null,price,List.of(),
            TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES),Instant.ofEpochSecond(at),false,
            new SellTimingEngine(new FillModel(),tax),tax,offer.getFirstSeen(),offer.getLastFillObservedAt());
        var row=new SaleMaintenanceDiagnostic(offer,evaluation,null,price,at,at+151,Double.NaN,false);
        var text=SaleMaintenanceText.render(List.of(row),at,true);
        assertTrue(text.contains("Latest buyer-paid trade: unavailable"));
        assertTrue(text.contains("Latest seller-received trade: 110 gp, 5m 1s old (stale for maintenance)"));
        assertTrue(text.contains("Waiting for complete two-sided market prices"));assertFalse(text.contains("trade: 0 gp"));
    }
    @Test public void missingOriginalEstimateAndLogoutStayExplicit() {
        var rows=List.of(row(Double.NaN,at-3600));
        assertTrue(SaleMaintenanceText.render(rows,at,true).contains("Original actual-order estimate: unavailable"));
        assertFalse(SaleMaintenanceText.render(rows,at,false).contains("Nature rune"));
        assertTrue(SaleMaintenanceText.render(rows,at+30,true).contains("evaluation expired"));
    }
    @Test public void comparisonShowsOneSharedHorizonAndNeverPresentsAProfitOrFillGuarantee() {
        var existing=row(12,at-3600);var offer=existing.offer();var price=new LatestPrice(120,at,110,at);
        var model=new FillModel(){@Override public FillEstimate estimateSell(FillCurve c,int p,int q,double h) {
            return new FillEstimate(.75,.5,100,0);
        }};
        var comparison=ExitPriceComparison.compare(561,3,300,150,1,200,new int[]{120},price,
            List.of(new Candle(at/300*300-300,120,110,100,100),new Candle(at/300*300-600,120,110,100,100)),
            2,Instant.ofEpochSecond(at),model,new TaxCalculator());
        var diagnostic=new SaleMaintenanceDiagnostic(offer,existing.evaluation,null,price,at,at+151,12,false,comparison);
        var text=SaleMaintenanceText.render(List.of(diagnostic),at,true);
        assertTrue(text.contains("Price comparison over 120m (unvalidated public-flow estimates)"));
        assertTrue(text.contains("Standing 150 gp · 75% estimated completion"));
        assertTrue(text.contains("Supported alternative 120 gp"));
        assertTrue(text.contains("Est. horizon P/L"));
        assertTrue(text.contains("Unsold remainder uses the same current 110 gp"));
        assertTrue(text.contains("No replacement-trade profit is credited"));
        assertTrue(text.contains("grants no loss or cancellation permission"));
        assertFalse(SaleMaintenanceText.render(List.of(diagnostic),at+30,true).contains("75% estimated completion"));
        var other=new TrackedOffer(2,562,false,150,3,at-3600);other.setState("SELLING");
        var foreign=new SaleMaintenanceDiagnostic(other,existing.evaluation,null,price,at,at+151,12,false,comparison);
        assertFalse(SaleMaintenanceText.render(List.of(foreign),at,true).contains("75% estimated completion"));
        offer.setQuantityFilled(1);
        var changed=new SaleMaintenanceDiagnostic(offer,existing.evaluation,null,price,at,at+151,12,false,comparison);
        assertFalse(SaleMaintenanceText.render(List.of(changed),at,true).contains("75% estimated completion"));
    }
}
