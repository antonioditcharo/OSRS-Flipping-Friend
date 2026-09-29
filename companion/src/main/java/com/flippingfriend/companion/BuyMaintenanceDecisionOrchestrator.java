package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PolicyDecision;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/** Pure non-activated orchestration of current open-buy evidence into one policy decision. */
final class BuyMaintenanceDecisionOrchestrator
{
    private final BuyMaintenanceContextComposer composer;
    private final CompanionBuyMaintenancePolicy policy;

    BuyMaintenanceDecisionOrchestrator()
    {
        this(new BuyMaintenanceContextComposer(), new CompanionBuyMaintenancePolicy());
    }
    BuyMaintenanceDecisionOrchestrator(BuyMaintenanceContextComposer composer,
        CompanionBuyMaintenancePolicy policy)
    {
        if (composer == null || policy == null) throw new IllegalArgumentException("dependencies are required");
        this.composer = composer;
        this.policy = policy;
    }
    PolicyDecision decide(Collection<OfferEvent> activeOffers,
        Map<Integer, BuyMaintenanceMarketInput> marketByItem,
        Map<Integer, Integer> buyLimitRemaining,
        String currentRecommendationId, long decidedAt)
    {
        if (activeOffers == null || marketByItem == null || buyLimitRemaining == null) return null;
        OfferEvent selected = activeOffers.stream()
            .filter(BuyMaintenanceDecisionOrchestrator::isOpenBuy)
            .min(Comparator.comparingInt(OfferEvent::getSlot))
            .orElse(null);
        if (selected == null) return null;
        BuyMaintenanceMarketInput market = marketByItem.get(selected.getItemId());
        Integer remaining = buyLimitRemaining.get(selected.getItemId());
        if (market == null || remaining == null) return null;
        BuyMaintenancePolicyContext context = composer.compose(selected, market, remaining,
            currentRecommendationId, decidedAt);
        return context == null ? null : policy.decide(context);
    }
    private static boolean isOpenBuy(OfferEvent offer)
    {
        return offer != null && offer.isBuying() && "BUYING".equals(offer.getEventType())
            && offer.getSlot() >= 0 && offer.getFilledQuantity() < offer.getTotalQuantity();
    }
}
