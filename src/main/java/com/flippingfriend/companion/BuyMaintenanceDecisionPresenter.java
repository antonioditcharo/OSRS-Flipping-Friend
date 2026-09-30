package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;

/** Pure identity-safe conversion of validated buy maintenance into manual presentation. */
final class BuyMaintenanceDecisionPresenter
{
    private BuyMaintenanceDecisionPresenter() { }
    static PresentedBuyMaintenanceDecision present(PolicyDecision decision, OfferEvent offer)
    {
        if (!valid(decision, offer)) return null;
        Suggestion suggestion;
        if (decision.getAction() == OfferLifecycleAction.CANCEL_BUY)
        {
            int remaining = offer.getTotalQuantity() - offer.getFilledQuantity();
            suggestion = Suggestion.builder(SuggestionType.CANCEL)
                .item(offer.getItemId(), offer.getItemName())
                .slot(offer.getSlot()).price(offer.getPrice()).quantity(remaining)
                .headline("Cancel your " + offer.getItemName() + " buy offer")
                .detail("The companion says this open buy should be cancelled. It does not authorize a replacement yet.")
                .build();
        }
        else
        {
            String headline = decision.getAction() == OfferLifecycleAction.HOLD
                ? "Keep your " + offer.getItemName() + " buy offer open"
                : "Wait before changing your " + offer.getItemName() + " buy offer";
            suggestion = Suggestion.builder(SuggestionType.WAIT)
                .item(offer.getItemId(), offer.getItemName()).slot(offer.getSlot())
                .price(offer.getPrice()).quantity(offer.getTotalQuantity() - offer.getFilledQuantity())
                .headline(headline).detail(decision.getReasonCode()).build();
        }
        return new PresentedBuyMaintenanceDecision(decision, suggestion);
    }
    private static boolean valid(PolicyDecision decision, OfferEvent offer)
    {
        if (decision == null || offer == null || decision.getDecisionType() != PolicyDecisionType.BUY_MAINTENANCE
            || !offer.isBuying() || !"BUYING".equals(offer.getEventType()) || offer.getOfferIdentity() == null
            || !offer.getOfferIdentity().equals(decision.getCandidateId())
            || offer.getRecommendationId() == null
            || !offer.getRecommendationId().equals(decision.getRecommendationId())
            || offer.getTotalQuantity() <= offer.getFilledQuantity()) return false;
        if (decision.getAction() == OfferLifecycleAction.WAIT)
            return decision.getAbstentionReason() != PolicyAbstentionReason.NONE;
        return (decision.getAction() == OfferLifecycleAction.HOLD
            || decision.getAction() == OfferLifecycleAction.CANCEL_BUY)
            && decision.getAbstentionReason() == PolicyAbstentionReason.NONE;
    }
}
