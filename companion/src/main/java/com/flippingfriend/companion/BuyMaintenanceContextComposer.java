package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;

/** Pure fail-closed adapter from authoritative companion inputs to policy context. */
final class BuyMaintenanceContextComposer
{
    BuyMaintenancePolicyContext compose(OfferEvent offer, BuyMaintenanceMarketInput market,
        int buyLimitRemaining, String currentRecommendationId, long decidedAt)
    {
        if (offer == null || market == null || decidedAt < 0) return null;
        if (!"BUYING".equals(offer.getEventType()) || !offer.isBuying()) return null;
        if (offer.getSlot() < 0 || offer.getItemId() <= 0 || offer.getItemId() != market.getItemId())
            return null;
        if (offer.getTotalQuantity() <= 0 || offer.getFilledQuantity() < 0
            || offer.getFilledQuantity() >= offer.getTotalQuantity() || offer.getPrice() <= 0)
            return null;
        if (market.getLowPrice() <= 0 || market.getHighPrice() <= 0
            || offer.getObservedAt() < 0 || market.getObservedAt() < 0
            || offer.getObservedAt() > decidedAt || market.getObservedAt() > decidedAt)
            return null;
        String offerIdentity = offer.getOfferIdentity();
        if (blank(offerIdentity)) return null;
        String recommendationId = offer.getRecommendationId();
        if (blank(recommendationId)) recommendationId = currentRecommendationId;
        if (blank(recommendationId)) return null;
        if (!blank(offer.getRecommendationId()) && !blank(currentRecommendationId)
            && !offer.getRecommendationId().equals(currentRecommendationId)) return null;
        return new BuyMaintenancePolicyContext(recommendationId, offerIdentity, offer.getSlot(),
            offer.getItemId(), offer.getTotalQuantity() - offer.getFilledQuantity(),
            offer.getPrice(), market.getLowPrice(), market.getHighPrice(),
            Math.max(0, buyLimitRemaining), offer.getObservedAt(), market.getObservedAt(),
            decidedAt, true);
    }
    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
