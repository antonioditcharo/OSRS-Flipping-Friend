package com.flippingfriend.core;

import org.junit.Assert;
import org.junit.Test;

public class OfferEventIdentityTest
{
    @Test
    public void exposesCanonicalIdentityMetadataWithoutChangingExistingFields()
    {
        OfferEvent event = OfferEvent.builder("trace-1", 1_700_000_010L, "FILL")
            .eventIdentity("event-7", "session-3", "offer-2")
            .slot(4)
            .item(4151, "Abyssal whip")
            .buying(true)
            .price(1_500_000)
            .quantities(10, 3)
            .spent(4_500_000L)
            .sequence(7)
            .firstSeenAt(1_700_000_000L)
            .recommendation("plan-5", 1_500_000, 10, 12, 4.5, 0.75)
            .build();
        Assert.assertEquals("1", event.getSchemaVersion());
        Assert.assertEquals("trace-1", event.getCorrelationId());
        Assert.assertEquals("event-7", event.getEventId());
        Assert.assertEquals("session-3", event.getSessionId());
        Assert.assertEquals("offer-2", event.getOfferIdentity());
        Assert.assertEquals(7, event.getSequence());
        Assert.assertEquals("plan-5", event.getRecommendationId());
        Assert.assertEquals(1_500_000, event.averageFillPrice());
        Assert.assertEquals(10, event.secondsOpen());
        Assert.assertTrue(event.wasRecommended());
        Assert.assertFalse(event.isComplete());
    }

    @Test
    public void identityFieldsCanBeAssignedIndependently()
    {
        OfferEvent event = OfferEvent.builder("trace-2", 20, "EMPTY")
            .eventId("event-8")
            .sessionId("session-4")
            .offerIdentity("offer-9")
            .build();
        Assert.assertEquals("event-8", event.getEventId());
        Assert.assertEquals("session-4", event.getSessionId());
        Assert.assertEquals("offer-9", event.getOfferIdentity());
    }

    @Test
    public void legacyBuilderRemainsValidWithAbsentCanonicalIdentity()
    {
        OfferEvent event = OfferEvent.builder("legacy-trace", 30, "OBSERVED")
            .slot(1)
            .sequence(2)
            .build();
        Assert.assertEquals("1", event.getSchemaVersion());
        Assert.assertEquals("legacy-trace", event.getCorrelationId());
        Assert.assertNull(event.getEventId());
        Assert.assertNull(event.getSessionId());
        Assert.assertNull(event.getOfferIdentity());
        Assert.assertEquals(2, event.getSequence());
    }

    @Test
    public void canonicalMetadataDoesNotChangeOfferArithmetic()
    {
        OfferEvent event = OfferEvent.builder("trace-3", 90, "BOUGHT")
            .eventIdentity(null, null, null)
            .quantities(5, 5)
            .spent(500)
            .firstSeenAt(100)
            .build();
        Assert.assertEquals(100, event.averageFillPrice());
        Assert.assertEquals(0, event.secondsOpen());
        Assert.assertTrue(event.isComplete());
        Assert.assertFalse(event.wasRecommended());
    }
    @Test public void predictionCopiesRetainEconomicIdentityAndAccountScope() {
        String account=AccountIdentity.fromProfileKey("evidence");
        OfferEvent origin=OfferEvent.builder("trace",100,"BUYING").accountId(account)
            .eventIdentity("original","session","offer").authenticatedObservation(true).slot(2).item(561,"Nature rune")
            .buying(true).price(100).quantities(10,0).sequence(1).firstSeenAt(100)
            .recommendation("original-plan",100,10,3,12,.75).predictionEvidence("original",99,true).build();
        OfferEvent later=OfferEvent.builder("other-trace",200,"BOUGHT").accountId(account)
            .eventIdentity("later","session","offer").authenticatedObservation(true).slot(2).item(561,"Nature rune")
            .buying(true).price(100).quantities(10,10).spent(990).sequence(2).firstSeenAt(100)
            .recommendation("new-plan",100,10,0,120,.9).build();
        OfferEvent bound=later.withBoundPrediction(origin,true).withAccountId(account);
        Assert.assertEquals("original-plan",bound.getRecommendationId());Assert.assertEquals(12,bound.getPredictedMinutes(),0);
        Assert.assertEquals("1",bound.getPredictionEvidenceVersion());Assert.assertEquals("original",bound.getPredictionSourceEventId());
        Assert.assertTrue(bound.isPredictionComparable());Assert.assertEquals(99,bound.getAdviceObservedAt());
        Assert.assertEquals(later.getEventId(),bound.getEventId());Assert.assertEquals(later.getSequence(),bound.getSequence());
        Assert.assertEquals(later.getSpent(),bound.getSpent());Assert.assertEquals(later.getFilledQuantity(),bound.getFilledQuantity());
        Assert.assertEquals(later.getObservedAt(),bound.getObservedAt());Assert.assertTrue(bound.isAuthenticatedObservation());
        Assert.assertNull(later.getPredictionEvidenceVersion());
        Assert.assertEquals(0,later.withBoundPrediction(null,false).getPredictedMinutes(),0);
        try {later.withAccountId(AccountIdentity.fromProfileKey("foreign")).withBoundPrediction(origin,true);Assert.fail("foreign origin accepted");}
        catch(IllegalArgumentException expected){ }
    }
}
