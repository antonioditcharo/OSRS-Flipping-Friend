package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceCancellationCompositionTest
{
    @Test public void composesCanonicalPresentationIntoAcceptedCancellation()
    {
        CompanionClient client = client();
        OfferEvent offer = offer("offer-1", "plan-1", 500, 100);
        Suggestion existing = suggestion(SuggestionType.MODIFY_BUY, offer, 400);
        String[] requested = new String[2];
        Suggestion selected = client.selectBuyMaintenanceCancellation(existing, offer,
            (identity, plan) ->
            {
                requested[0] = identity;
                requested[1] = plan;
                return action(OfferLifecycleAction.CANCEL_BUY,
                    PolicyAbstentionReason.NONE, identity, plan);
            });
        Assert.assertEquals("offer-1", requested[0]);
        Assert.assertEquals("plan-1", requested[1]);
        Assert.assertNotSame(existing, selected);
        Assert.assertEquals(SuggestionType.CANCEL, selected.getType());
        Assert.assertEquals(561, selected.getItemId());
        Assert.assertEquals("Nature rune", selected.getItemName());
        Assert.assertEquals(1, selected.getSlot());
        Assert.assertEquals(100, selected.getPrice());
        Assert.assertEquals(400, selected.getQuantity());
        Assert.assertTrue(selected.getDetail().contains("does not authorize a replacement"));
    }

    @Test public void missingCanonicalAuthorityPreservesExactExistingWithoutFetching()
    {
        CompanionClient client = client();
        Suggestion existing = local(SuggestionType.MODIFY_BUY);
        int[] calls = {0};
        Suggestion selected = client.selectBuyMaintenanceCancellation(existing, null,
            (identity, plan) ->
            {
                calls[0]++;
                return null;
            });
        Assert.assertSame(existing, selected);
        Assert.assertEquals(0, calls[0]);
        Assert.assertSame(existing, client.selectBuyMaintenanceCancellation(existing));
    }

    @Test public void retrievalHoldWaitAndLineageFailuresPreserveExactExisting()
    {
        CompanionClient client = client();
        OfferEvent offer = offer("offer-1", "plan-1", 5, 0);
        Suggestion existing = suggestion(SuggestionType.CANCEL, offer, 5);
        Assert.assertSame(existing, client.selectBuyMaintenanceCancellation(existing, offer,
            (identity, plan) -> null));
        Assert.assertSame(existing, client.selectBuyMaintenanceCancellation(existing, offer,
            (identity, plan) -> action(OfferLifecycleAction.HOLD,
                PolicyAbstentionReason.NONE, identity, plan)));
        Assert.assertSame(existing, client.selectBuyMaintenanceCancellation(existing, offer,
            (identity, plan) -> PolicyDecision.abstain("1", "companion-buy-maintenance-1", "wait",
                PolicyDecisionType.BUY_MAINTENANCE, PolicyAbstentionReason.MARKET_DATA_STALE,
                "MARKET_STALE", 20, 10, identity, plan)));
        Assert.assertSame(existing, client.selectBuyMaintenanceCancellation(existing, offer,
            (identity, plan) -> action(OfferLifecycleAction.CANCEL_BUY,
                PolicyAbstentionReason.NONE, "other-offer", plan)));
        Assert.assertSame(existing, client.selectBuyMaintenanceCancellation(existing, offer,
            (identity, plan) -> action(OfferLifecycleAction.CANCEL_BUY,
                PolicyAbstentionReason.NONE, identity, "other-plan")));
    }

    @Test public void everyProtectedLocalResponsibilityAndMismatchIsPreserved()
    {
        CompanionClient client = client();
        OfferEvent offer = offer("offer-1", "plan-1", 5, 0);
        CompanionClient.BuyMaintenanceDecisionFetcher fetcher = (identity, plan) ->
            action(OfferLifecycleAction.CANCEL_BUY, PolicyAbstentionReason.NONE, identity, plan);
        SuggestionType[] protectedTypes = {SuggestionType.BUY, SuggestionType.SELL,
            SuggestionType.MODIFY_SELL, SuggestionType.COLLECT, SuggestionType.PACK,
            SuggestionType.UNPACK, SuggestionType.DECANT, SuggestionType.WAIT};
        for (SuggestionType type : protectedTypes)
        {
            Suggestion existing = suggestion(type, offer, 5);
            Assert.assertSame(type.name(), existing,
                client.selectBuyMaintenanceCancellation(existing, offer, fetcher));
        }
        Assert.assertNull(client.selectBuyMaintenanceCancellation(null, offer, fetcher));
        Suggestion unrelated = Suggestion.builder(SuggestionType.MODIFY_BUY)
            .item(562, "Chaos rune").slot(2).price(101).quantity(5)
            .headline("Local").detail("Local").build();
        Assert.assertSame(unrelated,
            client.selectBuyMaintenanceCancellation(unrelated, offer, fetcher));
    }

    private static CompanionClient client()
    {
        return new CompanionClient(org.mockito.Mockito.mock(PluginStorage.class),
            new Gson(), new SuggestionLedger());
    }

    private static PolicyDecision action(OfferLifecycleAction action,
        PolicyAbstentionReason abstention, String offer, String plan)
    {
        return PolicyDecision.action("1", "companion-buy-maintenance-1", "decision-" + action,
            PolicyDecisionType.BUY_MAINTENANCE, action, "REASON", 20, 10, offer, plan);
    }

    private static Suggestion local(SuggestionType type)
    {
        return Suggestion.builder(type).item(561, "Nature rune").slot(1)
            .price(100).quantity(5).headline("Local").detail("Local").build();
    }

    private static Suggestion suggestion(SuggestionType type, OfferEvent offer, int quantity)
    {
        return Suggestion.builder(type).item(offer.getItemId(), offer.getItemName())
            .slot(offer.getSlot()).price(offer.getPrice()).quantity(quantity)
            .headline("Local").detail("Local").build();
    }

    private static OfferEvent offer(String identity, String recommendation, int total, int filled)
    {
        return OfferEvent.builder("correlation", 10, "BUYING")
            .eventIdentity("event", "session", identity).slot(1).item(561, "Nature rune")
            .buying(true).price(100).quantities(total, filled)
            .recommendation(recommendation, 100, total, 10, 30).build();
    }
}
