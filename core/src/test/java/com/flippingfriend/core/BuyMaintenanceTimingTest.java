package com.flippingfriend.core;

import com.flippingfriend.model.FillEstimate;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyMaintenanceTimingTest {
    final long at=1800000000;
    ExecutionForecast forecast(int price) {
        return new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,561,true,price,10,3,1,
            3600,12,12,120,new FillEstimate(.75,.2,100,.1),1,.2);
    }
    OfferEvent original(ExecutionForecast forecast,boolean accepted,boolean comparable) {
        var advice=CaptureOrder.next();var capture=CaptureOrder.next();
        var raw=OfferEvent.builder("fixture",at,"BUYING").accountId(AccountIdentity.fromProfileKey("timing-only"))
            .authenticatedObservation(true).slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,0)
            .firstSeenAt(at).eventIdentity("origin","session","episode").sequence(1)
            .recommendation("original-advice",100,10,0,12,.75).predictionEvidence(null,at,comparable)
            .executionForecast(forecast).acceptedAdviceProof(accepted?new AcceptedAdviceProof(advice,capture):null).build();
        return raw.withBoundPrediction(raw,comparable);
    }
    @Test public void sameSecondAcceptedForecastSurvivesInheritedFillsWithoutLearningQualification() {
        var first=original(forecast(100),true,false);
        assertFalse(first.isPredictionComparable());assertEquals(0,first.getPredictedMinutes(),0);
        assertEquals(12,BuyMaintenanceTiming.originalExpectedMinutes(first),1e-9);
        var partial=OfferEvent.builder("later",at+600,"BUYING").accountId(first.getAccountId())
            .authenticatedObservation(true).slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,2).spent(200)
            .firstSeenAt(at).eventIdentity("partial","session","episode").sequence(2).build().withBoundPrediction(first,false);
        assertFalse(partial.isPredictionComparable());assertEquals(first.getPredictionSourceEventId(),partial.getPredictionSourceEventId());
        assertEquals(12,BuyMaintenanceTiming.originalExpectedMinutes(partial),1e-9);
    }
    @Test public void missingProofForecastOrMatchingOrderCannotInventAnOriginalDuration() {
        assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(null)));
        assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(original(null,true,false))));
        assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(original(forecast(101),true,false))));
        assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(original(forecast(100),false,false))));
    }
    @Test public void retainedComparableLegacyEstimateDoesNotRequireANewForecast() {
        assertEquals(12,BuyMaintenanceTiming.originalExpectedMinutes(original(null,false,true)),0);
    }
    @Test public void independentAcceptedPlanKeepsImmutableDurationWithoutPredictionAttribution() {
        var raw=OfferEvent.builder("fixture",at+1,"BUYING").accountId(AccountIdentity.fromProfileKey("timing-only"))
            .authenticatedObservation(true).slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,0)
            .firstSeenAt(at).eventIdentity("independent","session","episode").sequence(1).build();
        var accepted=AcceptedBuyPlanProof.accepted(raw.getAccountId(),"new-acceptance",561,100,10,at,CaptureOrder.next(),
            new AcquisitionExitContext(140,90,480),forecast(100));
        var proof=accepted.captured(CaptureOrder.next()).bindTo(raw);var first=raw.withAcceptedBuyPlanProof(proof);
        assertNull(first.getPredictionSourceEventId());assertFalse(first.wasRecommended());
        assertEquals(12,BuyMaintenanceTiming.originalExpectedMinutes(first),1e-9);
        var partial=OfferEvent.builder("later",at+3600,"BUYING").accountId(first.getAccountId())
            .authenticatedObservation(true).slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,2).spent(200)
            .firstSeenAt(at).eventIdentity("partial","session","episode").sequence(2).build().withAcceptedBuyPlanProof(proof);
        assertEquals(12,BuyMaintenanceTiming.originalExpectedMinutes(partial),1e-9);
    }
    @Test public void versionedUnknownPlanPreservesOnlyIndependentlyComparableOriginalTiming() {
        var comparable=original(null,false,true).withAcceptedBuyPlanEvidenceVersion("1");
        assertNull(comparable.getAcceptedBuyPlanProof());assertTrue(comparable.isPredictionComparable());
        assertEquals(12,BuyMaintenanceTiming.originalExpectedMinutes(comparable),0);
        assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(comparable.withAcceptedBuyPlanEvidenceVersion("2"))));
        var ambiguous=original(null,true,false).withAcceptedBuyPlanEvidenceVersion("1");
        assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(ambiguous)));
        var accepted=AcceptedBuyPlanProof.accepted(comparable.getAccountId(),"no-original-forecast",561,100,10,at,
            CaptureOrder.next(),new AcquisitionExitContext(140,90,480),null);
        var proof=accepted.captured(CaptureOrder.next()).bindTo(comparable);assertNotNull(proof);
        var independent=comparable.withAcceptedBuyPlanProof(proof);
        assertTrue("A scalar association cannot replace a missing independent plan forecast",independent.isPredictionComparable());
        assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(independent)));
    }
}
