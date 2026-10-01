package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementWorkflowTest
{
    @Test public void exactCancellationCreatesDurableIdentitySafeIntent() throws Exception
    {
        Path database = Files.createTempDirectory("replacement-workflow").resolve("db");
        try (SqliteStore store = new SqliteStore(database))
        {
            BuyReplacementWorkflow workflow = new BuyReplacementWorkflow(store);
            BuyReplacementIntent first = workflow.recordCancellation(decision(OfferLifecycleAction.CANCEL_BUY,
                "offer-1", "plan-1"), offer("offer-1", "plan-1"), 100);
            BuyReplacementIntent replay = workflow.recordCancellation(decision(OfferLifecycleAction.CANCEL_BUY,
                "offer-1", "plan-1"), offer("offer-1", "plan-1"), 100);
            assertNotNull(first); assertEquals(first.getIntentId(), replay.getIntentId());
            assertEquals(400, first.getRemainderQuantity());
            assertEquals(1, store.buyReplacementIntents().size());
        }
        try (SqliteStore reopened = new SqliteStore(database))
        {
            assertEquals("offer-1", reopened.buyReplacementIntents().get(0).getOriginalOfferIdentity());
            assertEquals("plan-1", reopened.buyReplacementIntents().get(0).getRecommendationId());
        }
    }

    @Test public void nonCancellationAndLineageMismatchFailClosedWithoutWriting() throws Exception
    {
        Path database = Files.createTempDirectory("replacement-workflow-closed").resolve("db");
        try (SqliteStore store = new SqliteStore(database))
        {
            BuyReplacementWorkflow workflow = new BuyReplacementWorkflow(store);
            assertNull(workflow.recordCancellation(decision(OfferLifecycleAction.HOLD, "offer-1", "plan-1"), offer("offer-1", "plan-1"), 100));
            assertNull(workflow.recordCancellation(decision(OfferLifecycleAction.CANCEL_BUY, "other", "plan-1"), offer("offer-1", "plan-1"), 100));
            assertNull(workflow.recordCancellation(decision(OfferLifecycleAction.CANCEL_BUY, "offer-1", "other"), offer("offer-1", "plan-1"), 100));
            assertTrue(store.buyReplacementIntents().isEmpty());
        }
    }

    private static PolicyDecision decision(OfferLifecycleAction action, String offer, String plan)
    {
        return PolicyDecision.action("1", "companion-buy-maintenance-1", "decision-1",
            PolicyDecisionType.BUY_MAINTENANCE, action, "REASON", 100, 90, offer, plan);
    }
    private static OfferEvent offer(String identity, String recommendation)
    {
        return OfferEvent.builder("correlation", 90, "BUYING")
            .eventIdentity("event-1", "session-1", identity).slot(2)
            .item(4151, "Abyssal whip").buying(true).price(1000).quantities(500, 100)
            .recommendation(recommendation, 1000, 500, 10, 30).build();
    }
}
