package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.util.Objects;

/** Pure fail-closed selection of an identity-safe companion buy cancellation. */
final class BuyMaintenanceCancellationSelector
{
    private static final String SCHEMA_VERSION = "1";
    private static final String POLICY_VERSION = "companion-buy-maintenance-1";

    private BuyMaintenanceCancellationSelector() { }

    static Suggestion select(Suggestion existing, OfferEvent canonicalOpenBuy,
        PresentedBuyMaintenanceDecision companion)
    {
        if (!eligibleExisting(existing, canonicalOpenBuy) || !validOffer(canonicalOpenBuy)
            || companion == null) return existing;
        PolicyDecision decision = companion.getDecision();
        Suggestion presented = companion.getSuggestion();
        if (!validDecision(decision, canonicalOpenBuy)
            || !validPresentation(presented, canonicalOpenBuy)) return existing;
        return presented;
    }

    private static boolean eligibleExisting(Suggestion existing, OfferEvent offer)
    {
        if (existing == null || offer == null) return false;
        SuggestionType type = existing.getType();
        if (type != SuggestionType.MODIFY_BUY && type != SuggestionType.CANCEL) return false;
        return sameOffer(existing, offer);
    }

    private static boolean validOffer(OfferEvent offer)
    {
        return offer != null && offer.isBuying() && "BUYING".equals(offer.getEventType())
            && offer.getSlot() >= 0 && offer.getItemId() > 0 && !blank(offer.getItemName())
            && offer.getPrice() > 0 && offer.getTotalQuantity() > 0
            && offer.getFilledQuantity() >= 0
            && offer.getFilledQuantity() < offer.getTotalQuantity()
            && !blank(offer.getOfferIdentity()) && !blank(offer.getRecommendationId());
    }

    private static boolean validDecision(PolicyDecision decision, OfferEvent offer)
    {
        return decision != null && SCHEMA_VERSION.equals(decision.getSchemaVersion())
            && POLICY_VERSION.equals(decision.getPolicyVersion())
            && !blank(decision.getDecisionId())
            && decision.getDecisionType() == PolicyDecisionType.BUY_MAINTENANCE
            && decision.getAction() == OfferLifecycleAction.CANCEL_BUY
            && decision.getAbstentionReason() == PolicyAbstentionReason.NONE
            && Objects.equals(offer.getOfferIdentity(), decision.getCandidateId())
            && Objects.equals(offer.getRecommendationId(), decision.getRecommendationId());
    }

    private static boolean validPresentation(Suggestion suggestion, OfferEvent offer)
    {
        return suggestion != null && suggestion.getType() == SuggestionType.CANCEL
            && sameOffer(suggestion, offer);
    }

    private static boolean sameOffer(Suggestion suggestion, OfferEvent offer)
    {
        int remaining = offer.getTotalQuantity() - offer.getFilledQuantity();
        return remaining > 0 && suggestion.getItemId() == offer.getItemId()
            && Objects.equals(suggestion.getItemName(), offer.getItemName())
            && suggestion.getSlot() == offer.getSlot()
            && suggestion.getPrice() == offer.getPrice()
            && suggestion.getQuantity() == remaining;
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
