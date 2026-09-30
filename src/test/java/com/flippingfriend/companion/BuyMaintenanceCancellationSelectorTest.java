package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceCancellationSelectorTest
{
    @Test public void selectsExactCompanionCancellationForModifyBuyAndCancel()
    {
        OfferEvent offer = offer("offer-1", "plan-1", 500, 100);
        PresentedBuyMaintenanceDecision companion = presented(offer, decision(
            OfferLifecycleAction.CANCEL_BUY, PolicyAbstentionReason.NONE, "offer-1", "plan-1"));
        Suggestion modify = suggestion(SuggestionType.MODIFY_BUY, offer, 400);
        Suggestion cancel = suggestion(SuggestionType.CANCEL, offer, 400);
        Assert.assertSame(companion.getSuggestion(),
            BuyMaintenanceCancellationSelector.select(modify, offer, companion));
        Assert.assertSame(companion.getSuggestion(),
            BuyMaintenanceCancellationSelector.select(cancel, offer, companion));
    }

    @Test public void preservesEveryOtherLocalResponsibility()
    {
        OfferEvent offer = offer("offer-1", "plan-1", 5, 0);
        PresentedBuyMaintenanceDecision companion = presented(offer, decision(
            OfferLifecycleAction.CANCEL_BUY, PolicyAbstentionReason.NONE, "offer-1", "plan-1"));
        SuggestionType[] protectedTypes = {SuggestionType.BUY, SuggestionType.SELL,
            SuggestionType.MODIFY_SELL, SuggestionType.COLLECT, SuggestionType.PACK,
            SuggestionType.UNPACK, SuggestionType.DECANT, SuggestionType.WAIT};
        for (SuggestionType type : protectedTypes)
        {
            Suggestion existing = suggestion(type, offer, 5);
            Assert.assertSame(type.name(), existing,
                BuyMaintenanceCancellationSelector.select(existing, offer, companion));
        }
        Assert.assertNull(BuyMaintenanceCancellationSelector.select(null, offer, companion));
    }

    @Test public void preservesMismatchedLocalMaintenance()
    {
        OfferEvent offer = offer("offer-1", "plan-1", 5, 0);
        PresentedBuyMaintenanceDecision companion = presented(offer, decision(
            OfferLifecycleAction.CANCEL_BUY, PolicyAbstentionReason.NONE, "offer-1", "plan-1"));
        assertPreserved(suggestion(SuggestionType.CANCEL, offer, 4), offer, companion);
        assertPreserved(mutated(SuggestionType.CANCEL, offer, 562, "Chaos rune", 1, 100, 5), offer, companion);
        assertPreserved(mutated(SuggestionType.CANCEL, offer, 561, "Nature rune", 2, 100, 5), offer, companion);
        assertPreserved(mutated(SuggestionType.CANCEL, offer, 561, "Nature rune", 1, 101, 5), offer, companion);
    }

    @Test public void rejectsHoldWaitAndMismatchedDecisionLineage()
    {
        OfferEvent offer = offer("offer-1", "plan-1", 5, 0);
        Suggestion existing = suggestion(SuggestionType.MODIFY_BUY, offer, 5);
        Assert.assertSame(existing, BuyMaintenanceCancellationSelector.select(existing, offer,
            presented(offer, decision(OfferLifecycleAction.HOLD, PolicyAbstentionReason.NONE,
                "offer-1", "plan-1"))));
        Assert.assertSame(existing, BuyMaintenanceCancellationSelector.select(existing, offer,
            presented(offer, PolicyDecision.abstain("1", "companion-buy-maintenance-1", "wait",
                PolicyDecisionType.BUY_MAINTENANCE, PolicyAbstentionReason.MARKET_DATA_STALE,
                "STALE", 20, 10, "offer-1", "plan-1"))));
        Assert.assertSame(existing, BuyMaintenanceCancellationSelector.select(existing, offer,
            new PresentedBuyMaintenanceDecision(decision(OfferLifecycleAction.CANCEL_BUY,
                PolicyAbstentionReason.NONE, "other", "plan-1"), companionSuggestion(offer))));
        Assert.assertSame(existing, BuyMaintenanceCancellationSelector.select(existing, offer,
            new PresentedBuyMaintenanceDecision(decision(OfferLifecycleAction.CANCEL_BUY,
                PolicyAbstentionReason.NONE, "offer-1", "other"), companionSuggestion(offer))));
    }

    @Test public void rejectsMismatchedPresentationAndMalformedOffer()
    {
        OfferEvent offer = offer("offer-1", "plan-1", 5, 0);
        Suggestion existing = suggestion(SuggestionType.CANCEL, offer, 5);
        PolicyDecision decision = decision(OfferLifecycleAction.CANCEL_BUY,
            PolicyAbstentionReason.NONE, "offer-1", "plan-1");
        Assert.assertSame(existing, BuyMaintenanceCancellationSelector.select(existing, offer,
            new PresentedBuyMaintenanceDecision(decision,
                mutated(SuggestionType.CANCEL, offer, 561, "Nature rune", 1, 100, 4))));
        OfferEvent terminal = offer("offer-1", "plan-1", 5, 5);
        Assert.assertSame(existing, BuyMaintenanceCancellationSelector.select(existing, terminal,
            new PresentedBuyMaintenanceDecision(decision, companionSuggestion(offer))));
    }

    private static void assertPreserved(Suggestion existing, OfferEvent offer,
        PresentedBuyMaintenanceDecision companion)
    {
        Assert.assertSame(existing,
            BuyMaintenanceCancellationSelector.select(existing, offer, companion));
    }

    private static PresentedBuyMaintenanceDecision presented(OfferEvent offer, PolicyDecision decision)
    {
        PresentedBuyMaintenanceDecision result = BuyMaintenanceDecisionPresenter.present(decision, offer);
        Assert.assertNotNull(result);
        return result;
    }

    private static PolicyDecision decision(OfferLifecycleAction action,
        PolicyAbstentionReason abstention, String offer, String plan)
    {
        if (action == OfferLifecycleAction.WAIT)
            return PolicyDecision.abstain("1", "companion-buy-maintenance-1", "decision",
                PolicyDecisionType.BUY_MAINTENANCE, abstention, "REASON", 20, 10, offer, plan);
        return PolicyDecision.action("1", "companion-buy-maintenance-1", "decision",
            PolicyDecisionType.BUY_MAINTENANCE, action, "REASON", 20, 10, offer, plan);
    }

    private static Suggestion suggestion(SuggestionType type, OfferEvent offer, int quantity)
    {
        return mutated(type, offer, offer.getItemId(), offer.getItemName(), offer.getSlot(),
            offer.getPrice(), quantity);
    }

    private static Suggestion mutated(SuggestionType type, OfferEvent offer, int itemId,
        String itemName, int slot, int price, int quantity)
    {
        return Suggestion.builder(type).item(itemId, itemName).slot(slot).price(price)
            .quantity(quantity).headline("Local").detail("Local").build();
    }

    private static Suggestion companionSuggestion(OfferEvent offer)
    {
        return Suggestion.builder(SuggestionType.CANCEL).item(offer.getItemId(), offer.getItemName())
            .slot(offer.getSlot()).price(offer.getPrice())
            .quantity(offer.getTotalQuantity() - offer.getFilledQuantity())
            .headline("Cancel").detail("No replacement").build();
    }

    private static OfferEvent offer(String identity, String plan, int total, int filled)
    {
        return OfferEvent.builder("correlation", 10, "BUYING")
            .eventIdentity("event", "session", identity).slot(1).item(561, "Nature rune")
            .buying(true).price(100).quantities(total, filled)
            .recommendation(plan, 100, total, 10, 30).build();
    }
}
