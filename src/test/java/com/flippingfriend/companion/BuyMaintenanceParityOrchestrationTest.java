package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceParityOrchestrationTest
{
    @Test public void composesCanonicalPresentationIntoExactParityEvidence()
    {
        CompanionClient client = client();
        OfferEvent offer = offer("offer-1", "plan-1", "BUYING", true, 500, 100);
        Suggestion existing = cancel(1, 100, 400,
            "Cancel your Nature rune buy offer",
            "The companion says this open buy should be cancelled. It does not authorize a replacement yet.");
        String[] requested = new String[2];
        BuyMaintenanceParityResult result = client.compareBuyMaintenancePresentation(
            existing, offer, (identity, plan) ->
            {
                requested[0] = identity;
                requested[1] = plan;
                return decision(identity, plan);
            });
        Assert.assertEquals("offer-1", requested[0]);
        Assert.assertEquals("plan-1", requested[1]);
        Assert.assertEquals(BuyMaintenanceParityStatus.MATCH, result.getStatus());
        Assert.assertEquals("decision", result.getDecisionId());
        Assert.assertEquals("policy", result.getPolicyVersion());
        Assert.assertEquals("offer-1", result.getOfferIdentity());
        Assert.assertEquals("plan-1", result.getRecommendationId());
    }

    @Test public void propagatesComparatorMismatchClassifications()
    {
        CompanionClient client = client();
        OfferEvent offer = offer("offer-1", "plan-1", "BUYING", true, 500, 100);
        CompanionClient.BuyMaintenanceDecisionFetcher fetcher =
            (identity, plan) -> decision(identity, plan);
        Assert.assertEquals(BuyMaintenanceParityStatus.ACTION_MISMATCH,
            client.compareBuyMaintenancePresentation(wait(1, 100, 400), offer, fetcher).getStatus());
        Assert.assertEquals(BuyMaintenanceParityStatus.SOURCE_OFFER_MISMATCH,
            client.compareBuyMaintenancePresentation(cancel(2, 100, 400,
                "Cancel your Nature rune buy offer",
                "The companion says this open buy should be cancelled. It does not authorize a replacement yet."),
                offer, fetcher).getStatus());
        Assert.assertEquals(BuyMaintenanceParityStatus.PRESENTATION_MISMATCH,
            client.compareBuyMaintenancePresentation(cancel(1, 100, 400, "Different", "Different"),
                offer, fetcher).getStatus());
    }

    @Test public void missingAndInvalidShadowEvidenceRemainExplicit()
    {
        CompanionClient client = client();
        Suggestion existing = cancel(1, 100, 400, "Cancel", "No replacement");
        Assert.assertEquals(BuyMaintenanceParityStatus.LEGACY_RESULT_MISSING,
            client.compareBuyMaintenancePresentation(null, null, null).getStatus());
        Assert.assertEquals(BuyMaintenanceParityStatus.SHADOW_RESULT_MISSING,
            client.compareBuyMaintenancePresentation(existing, null, null).getStatus());
        OfferEvent invalid = offer(null, "plan-1", "BUYING", true, 5, 0);
        Assert.assertEquals(BuyMaintenanceParityStatus.SHADOW_RESULT_MISSING,
            client.compareBuyMaintenancePresentation(existing, invalid,
                (identity, plan) -> decision(identity, plan)).getStatus());
        OfferEvent valid = offer("offer-1", "plan-1", "BUYING", true, 5, 0);
        Assert.assertEquals(BuyMaintenanceParityStatus.SHADOW_RESULT_MISSING,
            client.compareBuyMaintenancePresentation(existing, valid,
                (identity, plan) -> null).getStatus());
        Assert.assertEquals(BuyMaintenanceParityStatus.SHADOW_RESULT_MISSING,
            client.compareBuyMaintenancePresentation(existing, valid,
                (identity, plan) -> decision("other-offer", plan)).getStatus());
        Assert.assertEquals(BuyMaintenanceParityStatus.SHADOW_RESULT_MISSING,
            client.compareBuyMaintenancePresentation(existing, valid,
                (identity, plan) -> decision(identity, "other-plan")).getStatus());
    }

    private static CompanionClient client()
    {
        return new CompanionClient(org.mockito.Mockito.mock(PluginStorage.class),
            new Gson(), new SuggestionLedger());
    }

    private static PolicyDecision decision(String offer, String plan)
    {
        return PolicyDecision.action("1", "policy", "decision",
            PolicyDecisionType.BUY_MAINTENANCE, OfferLifecycleAction.CANCEL_BUY,
            "REASON", 20, 10, offer, plan);
    }

    private static Suggestion cancel(int slot, int price, int quantity, String headline, String detail)
    {
        return Suggestion.builder(SuggestionType.CANCEL).item(561, "Nature rune").slot(slot)
            .price(price).quantity(quantity).headline(headline).detail(detail).build();
    }

    private static Suggestion wait(int slot, int price, int quantity)
    {
        return Suggestion.builder(SuggestionType.WAIT).item(561, "Nature rune").slot(slot)
            .price(price).quantity(quantity).headline("Wait").detail("Wait").build();
    }

    private static OfferEvent offer(String identity, String recommendation, String type,
        boolean buying, int total, int filled)
    {
        return OfferEvent.builder("correlation", 10, type)
            .eventIdentity("event", "session", identity).slot(1).item(561, "Nature rune")
            .buying(buying).price(100).quantities(total, filled)
            .recommendation(recommendation, 100, total, 10, 30).build();
    }
}
