package com.flippingfriend.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AcceptedBuyPlanProofTest {
    final long at=1800000000;
    final String account=AccountIdentity.fromProfileKey("accepted-buy-plan");
    AcceptedBuyPlanProof accepted(){return AcceptedBuyPlanProof.accepted(account,"immutable-advice",561,100,10,at,
        CaptureOrder.next(),new AcquisitionExitContext(140,90,480),null);}
    OfferEvent source(long observed,int filled,long spent){return OfferEvent.builder("fixture",observed,"BUYING")
        .accountId(account).authenticatedObservation(true).eventIdentity("source","session","episode").sequence(1)
        .slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,filled).spent(spent).firstSeenAt(at).build();}
    @Test public void copiedCallbackPublishedAcrossSecondBoundaryKeepsExactPlanWithoutLearning() {
        var original=accepted();var captured=original.captured(CaptureOrder.next());var raw=source(at+1,0,0);
        var proof=captured.bindTo(raw);assertNotNull(proof);assertTrue(proof.qualifiesOriginal(raw));
        assertEquals(at,proof.getFirstSeenAt());assertEquals(at+1,proof.getCapturedAt());
        var event=raw.withAcceptedBuyPlanProof(proof);assertEquals(proof.getExitContext(),event.getExitContext());
        assertFalse(event.wasRecommended());assertFalse(event.isPredictionComparable());assertNull(event.getPredictionSourceEventId());
        assertTrue(proof.hasSameAcceptance(original));
        assertEquals(proof,event.withAccountId(account).getAcceptedBuyPlanProof());
    }
    @Test public void laterAdviceFilledFirstSourceAndPublicationExpiryCannotQualify() {
        var before=CaptureOrder.next();var after=accepted();
        assertNull(after.captured(before).bindTo(source(at,0,0)));
        assertNull(accepted().captured(CaptureOrder.next()).bindTo(source(at+1,1,100)));
        assertNull(accepted().captured(CaptureOrder.next()).bindTo(source(at+1,0,1)));
        assertNull(accepted().captured(CaptureOrder.next()).bindTo(source(at+601,0,0)));
        var raw=source(at,0,0);var foreign=raw.withAccountId(AccountIdentity.fromProfileKey("foreign"));
        assertNull(accepted().captured(CaptureOrder.next()).bindTo(foreign));
    }
    @Test public void exactBoundInputsCannotBeChanged() {
        var proof=accepted().captured(CaptureOrder.next()).bindTo(source(at,0,0));
        var changed=OfferEvent.builder("changed",at+2,"BUYING").accountId(account).authenticatedObservation(true)
            .eventIdentity("source-2","session","episode").sequence(2).slot(0).item(561,"Nature rune")
            .buying(true).price(101).quantities(10,0).firstSeenAt(at).build();
        assertFalse(proof.matchesEpisode(changed));
        var next=AcceptedBuyPlanProof.accepted(account,"other-advice",561,100,10,at,CaptureOrder.next(),
            new AcquisitionExitContext(140,90,480),null);
        assertFalse(proof.hasSameAcceptance(next));
    }
}
