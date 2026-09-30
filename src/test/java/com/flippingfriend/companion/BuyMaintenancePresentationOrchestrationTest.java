package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.model.SuggestionType;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenancePresentationOrchestrationTest
{
    @Test public void bindsCanonicalOfferLineageThroughRetrievalAndPresentation() throws Exception
    {
        CompanionClient client = client();
        OfferEvent offer = offer("offer-1", "plan-1", "BUYING", true, 500, 100);
        String[] requested = new String[2];
        PresentedBuyMaintenanceDecision result = client.nextBuyMaintenancePresentation(offer, (identity, plan) ->
        {
            requested[0] = identity;
            requested[1] = plan;
            return action(OfferLifecycleAction.CANCEL_BUY, identity, plan);
        });
        Assert.assertEquals("offer-1", requested[0]);
        Assert.assertEquals("plan-1", requested[1]);
        Assert.assertNotNull(result);
        Assert.assertEquals(SuggestionType.CANCEL, result.getSuggestion().getType());
        Assert.assertEquals(400, result.getSuggestion().getQuantity());
        Assert.assertEquals(1, result.getSuggestion().getSlot());
        Assert.assertEquals(100, result.getSuggestion().getPrice());
        Assert.assertTrue(result.getSuggestion().getDetail().contains("does not authorize a replacement"));
    }

    @Test public void holdAndExplicitWaitRemainVisible() throws Exception
    {
        CompanionClient client = client();
        OfferEvent offer = offer("offer-1", "plan-1", "BUYING", true, 500, 100);
        Assert.assertEquals(SuggestionType.WAIT, client.nextBuyMaintenancePresentation(offer,
            (identity, plan) -> action(OfferLifecycleAction.HOLD, identity, plan)).getSuggestion().getType());
        PolicyDecision wait = PolicyDecision.abstain("1", "policy", "wait",
            PolicyDecisionType.BUY_MAINTENANCE, PolicyAbstentionReason.MARKET_DATA_STALE,
            "MARKET_STALE", 20, 10, "offer-1", "plan-1");
        Assert.assertEquals(SuggestionType.WAIT, client.nextBuyMaintenancePresentation(offer,
            (identity, plan) -> wait).getSuggestion().getType());
    }

    @Test public void invalidOfferAndFailuresFailClosedWithoutInventingFallback() throws Exception
    {
        CompanionClient client = client();
        int[] calls = {0};
        CompanionClient.BuyMaintenanceDecisionFetcher fetcher = (identity, plan) ->
        {
            calls[0]++;
            return action(OfferLifecycleAction.HOLD, identity, plan);
        };
        Assert.assertNull(client.nextBuyMaintenancePresentation(null, fetcher));
        Assert.assertNull(client.nextBuyMaintenancePresentation(offer(null, "plan-1", "BUYING", true, 5, 0), fetcher));
        Assert.assertNull(client.nextBuyMaintenancePresentation(offer("offer-1", null, "BUYING", true, 5, 0), fetcher));
        Assert.assertNull(client.nextBuyMaintenancePresentation(offer("offer-1", "plan-1", "SELLING", false, 5, 0), fetcher));
        Assert.assertNull(client.nextBuyMaintenancePresentation(offer("offer-1", "plan-1", "BUYING", true, 5, 5), fetcher));
        Assert.assertNull(client.nextBuyMaintenancePresentation(offer("offer-1", "plan-1", "BUYING", true, 5, 0), null));
        Assert.assertEquals(0, calls[0]);
        OfferEvent valid = offer("offer-1", "plan-1", "BUYING", true, 5, 0);
        Assert.assertNull(client.nextBuyMaintenancePresentation(valid, (identity, plan) -> null));
        Assert.assertNull(client.nextBuyMaintenancePresentation(valid, (identity, plan) ->
            action(OfferLifecycleAction.HOLD, "other-offer", plan)));
        Assert.assertNull(client.nextBuyMaintenancePresentation(valid, (identity, plan) ->
            action(OfferLifecycleAction.HOLD, identity, "other-plan")));
    }

    private static CompanionClient client()
    {
        return new CompanionClient(org.mockito.Mockito.mock(PluginStorage.class),
            new Gson(), new SuggestionLedger());
    }

    private static PolicyDecision action(OfferLifecycleAction action, String offer, String plan)
    {
        return PolicyDecision.action("1", "companion-buy-maintenance-1", "decision-" + action,
            PolicyDecisionType.BUY_MAINTENANCE, action, "REASON", 20, 10, offer, plan);
    }

    private static OfferEvent offer(String identity, String recommendation, String type, boolean buying,
        int total, int filled)
    {
        return OfferEvent.builder("correlation", 10, type).eventIdentity("event", "session", identity)
            .slot(1).item(561, "Nature rune").buying(buying).price(100).quantities(total, filled)
            .recommendation(recommendation, 100, total, 10, 30).build();
    }
}
