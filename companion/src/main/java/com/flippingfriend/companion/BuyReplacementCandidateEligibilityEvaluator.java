package com.flippingfriend.companion;

import com.flippingfriend.model.PriceOffset;
import java.util.ArrayList;
import java.util.List;

/** Pure eligibility interpretation preserving complete evidence without selecting a candidate. */
final class BuyReplacementCandidateEligibilityEvaluator
{
    BuyReplacementCandidateEligibilityAssessment evaluate(
        BuyReplacementCandidateAffordabilitySet source)
    {
        if (source == null || invalid(source) || source.getAssessments().isEmpty()) return null;
        List<BuyReplacementCandidateAffordability> complete =
            new ArrayList<>(source.getAssessments().size());
        List<BuyReplacementAffordableCandidate> affordable = new ArrayList<>();
        for (int index = 0; index < source.getAssessments().size(); index++)
        {
            BuyReplacementCandidateAffordability value = source.getAssessments().get(index);
            if (!valid(value, source, index)) return null;
            complete.add(value);
            if (value.getOutcome() == BuyReplacementAffordabilityOutcome.AFFORDABLE)
                affordable.add(new BuyReplacementAffordableCandidate(value.getOffsetIndex(),
                    value.getOffset(), value.getBuyPrice(), value.getExactRemainderQuantity(),
                    value.getTotalCost()));
        }
        BuyReplacementCandidateEligibilityOutcome outcome = affordable.isEmpty()
            ? BuyReplacementCandidateEligibilityOutcome.INELIGIBLE
            : BuyReplacementCandidateEligibilityOutcome.ELIGIBLE;
        return new BuyReplacementCandidateEligibilityAssessment(source, outcome, complete, affordable);
    }

    private static boolean valid(BuyReplacementCandidateAffordability value,
        BuyReplacementCandidateAffordabilitySet source, int index)
    {
        if (value == null || value.getOffsetIndex() != index || !Double.isFinite(value.getOffset())
            || value.getBuyPrice() <= 0
            || value.getExactRemainderQuantity() != source.getExactRemainderQuantity()
            || value.getTotalCost() <= 0 || value.getOutcome() == null
            || PriceOffset.apply(source.getCurrentLowPrice(), value.getOffset())
                != value.getBuyPrice()) return false;
        long cost = (long) value.getBuyPrice() * source.getExactRemainderQuantity();
        if (value.getTotalCost() != cost) return false;
        BuyReplacementAffordabilityOutcome expected = cost <= source.getSpendableCoins()
            ? BuyReplacementAffordabilityOutcome.AFFORDABLE
            : BuyReplacementAffordabilityOutcome.UNAFFORDABLE;
        return value.getOutcome() == expected;
    }

    private static boolean invalid(BuyReplacementCandidateAffordabilitySet c)
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
            || c.getAssessments() == null;
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
