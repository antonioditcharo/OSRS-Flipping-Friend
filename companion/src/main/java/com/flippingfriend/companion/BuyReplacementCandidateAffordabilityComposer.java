package com.flippingfriend.companion;

import com.flippingfriend.model.PriceOffset;
import java.util.ArrayList;
import java.util.List;

/** Pure exact-remainder affordability classification without filtering or selection. */
final class BuyReplacementCandidateAffordabilityComposer
{
    BuyReplacementCandidateAffordabilitySet compose(BuyReplacementPriceCandidateSet source)
    {
        if (source == null || invalid(source) || source.getCandidates().isEmpty()) return null;
        int quantity = source.getExactRemainderQuantity();
        List<BuyReplacementCandidateAffordability> results =
            new ArrayList<>(source.getCandidates().size());
        for (int index = 0; index < source.getCandidates().size(); index++)
        {
            BuyReplacementPriceCandidate candidate = source.getCandidates().get(index);
            if (candidate == null || candidate.getOffsetIndex() != index
                || !Double.isFinite(candidate.getOffset()) || candidate.getBuyPrice() <= 0
                || PriceOffset.apply(source.getCurrentLowPrice(), candidate.getOffset())
                    != candidate.getBuyPrice()) return null;
            long cost = (long) candidate.getBuyPrice() * quantity;
            BuyReplacementAffordabilityOutcome outcome = cost <= source.getSpendableCoins()
                ? BuyReplacementAffordabilityOutcome.AFFORDABLE
                : BuyReplacementAffordabilityOutcome.UNAFFORDABLE;
            results.add(new BuyReplacementCandidateAffordability(index, candidate.getOffset(),
                candidate.getBuyPrice(), quantity, cost, outcome));
        }
        return new BuyReplacementCandidateAffordabilitySet(source, results);
    }

    private static boolean invalid(BuyReplacementPriceCandidateSet c)
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
            || !Double.isFinite(c.getEffectiveHorizonHours()) || c.getEffectiveHorizonHours() <= 0
            || c.getCandidates() == null;
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
