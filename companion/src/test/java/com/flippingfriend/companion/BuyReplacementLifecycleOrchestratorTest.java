package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementLifecycleOrchestratorTest
{
    private final BuyReplacementLifecycleOrchestrator orchestrator =
        new BuyReplacementLifecycleOrchestrator();

    @Test public void exactCancellationAndCollectionStopPendingFreshEligibility()
    {
        BuyReplacementIntent intent = intent(100, 300);
        OfferLifecycleProjection open = apply(null, event("BUYING", 110, 1, "event-1")).getProjection();
        OfferLifecycleTransition cancelled = apply(open,
            event("CANCELLED_BUY", 120, 2, "event-2"));
        intent = orchestrator.advance(intent, open, cancelled);
        assertEquals(BuyReplacementIntentState.COLLECTION_REQUIRED, intent.getState());

        OfferLifecycleProjection terminal = cancelled.getProjection();
        OfferLifecycleTransition cleared = apply(terminal, empty(130, 3, "event-3"));
        intent = orchestrator.advance(intent, terminal, cleared);
        assertEquals(BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING, intent.getState());
        assertNotEquals(BuyReplacementIntentState.REPLACEMENT_AUTHORIZED, intent.getState());
    }

    @Test public void replayAndAlreadyAdvancedStatesAreIdempotent()
    {
        BuyReplacementIntent intent = intent(100, 300);
        OfferLifecycleProjection open = apply(null, event("BUYING", 110, 1, "event-1")).getProjection();
        OfferLifecycleTransition cancelled = apply(open,
            event("CANCELLED_BUY", 120, 2, "event-2"));
        BuyReplacementIntent required = orchestrator.advance(intent, open, cancelled);
        assertSame(required, orchestrator.advance(required, open, cancelled));

        OfferLifecycleProjection terminal = cancelled.getProjection();
        OfferLifecycleTransition cleared = apply(terminal, empty(130, 3, "event-3"));
        BuyReplacementIntent pending = orchestrator.advance(required, terminal, cleared);
        assertSame(pending, orchestrator.advance(pending, terminal, cleared));
    }

    @Test public void rejectedPrematureAndMismatchedEvidenceFailClosed()
    {
        BuyReplacementIntent intent = intent(100, 300);
        OfferLifecycleProjection open = apply(null, event("BUYING", 110, 1, "event-1")).getProjection();
        OfferLifecycleTransition premature = apply(open, empty(120, 2, "event-2"));
        assertFalse(premature.isAccepted());
        assertSame(intent, orchestrator.advance(intent, open, premature));

        OfferLifecycleProjection other = OfferLifecycleProjection.restore(
            OfferLifecycleState.BUY_OPEN, 2, "other", "session", 1, 110,
            4151, "Abyssal whip", true, 1000, 500, 100, 100000, "plan-1");
        OfferLifecycleTransition cancelled = apply(other,
            event("CANCELLED_BUY", 120, 2, "event-3", "other", "plan-1"));
        assertSame(intent, orchestrator.advance(intent, other, cancelled));
    }

    @Test public void expiredIntentAbandonsOnlyOnMatchingCanonicalEvidence()
    {
        BuyReplacementIntent intent = intent(100, 115);
        OfferLifecycleProjection open = apply(null, event("BUYING", 110, 1, "event-1")).getProjection();
        OfferLifecycleTransition cancelled = apply(open,
            event("CANCELLED_BUY", 120, 2, "event-2"));
        BuyReplacementIntent result = orchestrator.advance(intent, open, cancelled);
        assertEquals(BuyReplacementIntentState.ABANDONED, result.getState());
    }

    @Test public void sellSideAndWrongLineageDoNotAdvance()
    {
        BuyReplacementIntent intent = intent(100, 300);
        OfferLifecycleProjection sell = OfferLifecycleProjection.restore(
            OfferLifecycleState.SELL_OPEN, 2, "offer-1", "session", 1, 110,
            4151, "Abyssal whip", false, 1000, 500, 100, 100000, "plan-1");
        OfferLifecycleTransition cancelledSell = apply(sell,
            event("CANCELLED_SELL", false, 120, 2, "event-2", "offer-1", "plan-1"));
        assertSame(intent, orchestrator.advance(intent, sell, cancelledSell));

        OfferLifecycleProjection wrongPlan = OfferLifecycleProjection.restore(
            OfferLifecycleState.BUY_OPEN, 2, "offer-1", "session", 1, 110,
            4151, "Abyssal whip", true, 1000, 500, 100, 100000, "other-plan");
        OfferLifecycleTransition wrong = apply(wrongPlan,
            event("CANCELLED_BUY", 120, 2, "event-3", "offer-1", "other-plan"));
        assertSame(intent, orchestrator.advance(intent, wrongPlan, wrong));
    }

    private static BuyReplacementIntent intent(long createdAt, long expiresAt)
    {
        return BuyReplacementIntent.cancelAuthorized("intent-1", "offer-1", "plan-1", 2,
            4151, "Abyssal whip", 1000, 500, 100, createdAt, expiresAt);
    }

    private static OfferLifecycleTransition apply(OfferLifecycleProjection previous, OfferEvent event)
    {
        return OfferLifecycleReducer.apply(previous, event);
    }

    private static OfferEvent event(String state, long observedAt, long sequence, String eventId)
    {
        return event(state, true, observedAt, sequence, eventId, "offer-1", "plan-1");
    }

    private static OfferEvent event(String state, long observedAt, long sequence, String eventId,
        String identity, String recommendation)
    {
        return event(state, true, observedAt, sequence, eventId, identity, recommendation);
    }

    private static OfferEvent event(String state, boolean buying, long observedAt, long sequence,
        String eventId, String identity, String recommendation)
    {
        return OfferEvent.builder("trace", observedAt, state)
            .eventIdentity(eventId, "session", identity).slot(2).buying(buying).sequence(sequence)
            .item(4151, "Abyssal whip").price(1000).quantities(500, 100).spent(100000)
            .recommendation(recommendation, 1000, 500, 10, 30).build();
    }

    private static OfferEvent empty(long observedAt, long sequence, String eventId)
    {
        return OfferEvent.builder("trace", observedAt, "EMPTY")
            .eventIdentity(eventId, "session", null).slot(2).buying(false).sequence(sequence).build();
    }
}
