package com.flippingfriend.companion;

import com.flippingfriend.model.PriceOffset;
import java.util.ArrayList;
import java.util.List;

/** Pure ordered generation of non-selected replacement buy-price candidates. */
final class BuyReplacementPriceCandidateComposer
{
    BuyReplacementPriceCandidateSet compose(BuyReplacementPricingStrategyContext strategy)
    {
        if (strategy == null || invalid(strategy)) return null;
        double[] offsets = strategy.getBuyOffsets();
        if (offsets.length == 0) return null;
        List<BuyReplacementPriceCandidate> candidates = new ArrayList<>(offsets.length);
        for (int index = 0; index < offsets.length; index++)
        {
            double offset = offsets[index];
            if (!Double.isFinite(offset)) return null;
            int price = PriceOffset.apply(strategy.getCurrentLowPrice(), offset);
            if (price <= 0) return null;
            candidates.add(new BuyReplacementPriceCandidate(index, offset, price));
        }
        return new BuyReplacementPriceCandidateSet(strategy, candidates);
    }

    private static boolean invalid(BuyReplacementPricingStrategyContext c)
    {
        long at = c.getEvaluatedAt();
        long oldest = Math.min(c.getAccountObservedAt(), c.getMarketObservedAt());
        return blank(c.getReadinessAssessmentId()) || blank(c.getReadinessPolicyVersion())
            || blank(c.getIntentId()) || blank(c.getOriginalOfferIdentity())
            || blank(c.getRecommendationId()) || blank(c.getItemName())
            || blank(c.getAppetiteName()) || c.getSlot() < 0 || c.getItemId() <= 0
            || c.getExactRemainderQuantity() <= 0 || c.getHistoricalOriginalPrice() <= 0
            || c.getCurrentLowPrice() <= 0 || c.getCurrentHighPrice() <= c.getCurrentLowPrice()
            || c.getBuyLimitRemaining() < c.getExactRemainderQuantity()
            || c.getSpendableCoins() <= 0 || c.getIntentCreatedAt() < 0
            || c.getIntentExpiresAt() <= c.getIntentCreatedAt() || c.getIntentExpiresAt() < at
            || c.getAccountObservedAt() < 0 || c.getMarketObservedAt() < 0
            || c.getInputObservedAt() != oldest || at < 0 || oldest > at
            || c.getAccountObservedAt() > at || c.getMarketObservedAt() > at
            || c.getAccountObservedAt() + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS < at
            || c.getMarketObservedAt() + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS < at
            || !Double.isFinite(c.getEffectiveHorizonHours()) || c.getEffectiveHorizonHours() <= 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
