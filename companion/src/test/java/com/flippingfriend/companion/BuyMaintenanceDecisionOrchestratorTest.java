package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyDecision;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BuyMaintenanceDecisionOrchestratorTest
{
    private final BuyMaintenanceDecisionOrchestrator orchestrator = new BuyMaintenanceDecisionOrchestrator();
    @Test public void lowestSlotOpenBuyIsComposedAndDecided()
    {
        PolicyDecision decision = orchestrator.decide(Arrays.asList(offer(4, 4151, "plan-1"),
            offer(1, 561, "plan-1")), markets(), limits(100), "plan-1", 2_000);
        assertEquals(OfferLifecycleAction.HOLD, decision.getAction());
        assertEquals("offer-1", decision.getCandidateId());
        assertEquals("plan-1", decision.getRecommendationId());
    }
    @Test public void authoritativeLimitFlowsIntoPolicy()
    {
        PolicyDecision decision = orchestrator.decide(Collections.singletonList(offer(1, 561, "plan-1")),
            markets(), limits(0), "plan-1", 2_000);
        assertEquals(OfferLifecycleAction.CANCEL_BUY, decision.getAction());
        assertEquals("BUY_LIMIT_EXHAUSTED", decision.getReasonCode());
    }
    @Test public void missingOrMismatchedAuthoritativeInputsFailClosed()
    {
        assertNull(orchestrator.decide(Collections.singletonList(offer(1, 561, "plan-1")),
            Collections.emptyMap(), limits(100), "plan-1", 2_000));
        assertNull(orchestrator.decide(Collections.singletonList(offer(1, 561, "plan-1")),
            markets(), Collections.emptyMap(), "plan-1", 2_000));
        assertNull(orchestrator.decide(Collections.singletonList(offer(1, 561, "plan-1")),
            markets(), limits(100), "other-plan", 2_000));
    }
    @Test public void noOpenBuyProducesNoMaintenanceDecision()
    {
        OfferEvent sold = OfferEvent.builder("c", 1_900, "SELLING")
            .eventIdentity("event", "session", "sell-offer").slot(1).item(561, "Nature rune")
            .buying(false).price(110).quantities(500, 0).recommendation("plan-1", 110, 500, 10, 30).build();
        assertNull(orchestrator.decide(Collections.singletonList(sold), markets(), limits(100),
            "plan-1", 2_000));
    }
    private static OfferEvent offer(int slot, int item, String recommendation)
    {
        return OfferEvent.builder("c", 1_900, "BUYING")
            .eventIdentity("event-" + slot, "session", "offer-" + slot).slot(slot)
            .item(item, "Item " + item).buying(true).price(100).quantities(500, 100)
            .recommendation(recommendation, 100, 500, 10, 30).build();
    }
    private static Map<Integer, BuyMaintenanceMarketInput> markets()
    {
        Map<Integer, BuyMaintenanceMarketInput> result = new HashMap<>();
        result.put(561, new BuyMaintenanceMarketInput(561, 105, 110, 1_950));
        result.put(4151, new BuyMaintenanceMarketInput(4151, 2_000_000, 2_010_000, 1_950));
        return result;
    }
    private static Map<Integer, Integer> limits(int natureRuneRemaining)
    {
        Map<Integer, Integer> result = new HashMap<>();
        result.put(561, natureRuneRemaining);
        result.put(4151, 10);
        return result;
    }
}
