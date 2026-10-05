package com.flippingfriend.companion;

import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.PriceOffset;
import java.util.ArrayList;
import java.util.List;

/** Pure fail-closed measured buy-fill evaluation without filtering or choosing candidates. */
final class BuyReplacementCandidateFillEvaluator
{
    private final FillModel fillModel;

    BuyReplacementCandidateFillEvaluator(FillModel fillModel)
    {
        this.fillModel = fillModel;
    }

    BuyReplacementCandidateFillEvaluationSet evaluate(
        BuyReplacementCandidateFillInputContext source, long evaluatedAt)
    {
        if (invalid(source, evaluatedAt)) return null;
        List<BuyReplacementCandidateAffordability> complete =
            new ArrayList<>(source.getAssessments().size());
        List<BuyReplacementAffordableCandidate> affordable =
            new ArrayList<>(source.getAffordableCandidates().size());
        List<BuyReplacementCandidateFillEvaluation> evaluations =
            new ArrayList<>(source.getAffordableCandidates().size());
        int affordableIndex = 0;
        for (int index = 0; index < source.getAssessments().size(); index++)
        {
            BuyReplacementCandidateAffordability value = source.getAssessments().get(index);
            if (!valid(value, source, index)) return null;
            complete.add(value);
            if (value.getOutcome() == BuyReplacementAffordabilityOutcome.AFFORDABLE)
            {
                if (affordableIndex >= source.getAffordableCandidates().size()) return null;
                BuyReplacementAffordableCandidate candidate =
                    source.getAffordableCandidates().get(affordableIndex++);
                if (!matches(candidate, value)) return null;
                FillEstimate estimate = fillModel.estimateBuy(source.getFillCurve(),
                    candidate.getBuyPrice(), candidate.getExactRemainderQuantity(),
                    source.getEffectiveHorizonHours());
                if (!valid(estimate)) return null;
                affordable.add(candidate);
                evaluations.add(new BuyReplacementCandidateFillEvaluation(
                    candidate.getOffsetIndex(), candidate.getOffset(), candidate.getBuyPrice(),
                    candidate.getExactRemainderQuantity(), candidate.getTotalCost(),
                    estimate.getProbability(), estimate.getExpectedHours(), estimate.getUnitsPerHour(),
                    estimate.getWaitHours()));
            }
        }
        if (affordableIndex != source.getAffordableCandidates().size() || evaluations.isEmpty())
            return null;
        return new BuyReplacementCandidateFillEvaluationSet(source, complete, affordable,
            evaluations, evaluatedAt);
    }

    private boolean invalid(BuyReplacementCandidateFillInputContext source, long evaluatedAt)
    {
        return source == null || fillModel == null || evaluatedAt < 0
            || blank(source.getReadinessAssessmentId()) || blank(source.getReadinessPolicyVersion())
            || blank(source.getIntentId()) || blank(source.getOriginalOfferIdentity())
            || blank(source.getRecommendationId()) || blank(source.getItemName())
            || blank(source.getAppetiteName()) || source.getSlot() < 0 || source.getItemId() <= 0
            || source.getExactRemainderQuantity() <= 0 || source.getHistoricalOriginalPrice() <= 0
            || source.getCurrentLowPrice() <= 0 || source.getCurrentHighPrice() <= source.getCurrentLowPrice()
            || source.getBuyLimitRemaining() < source.getExactRemainderQuantity()
            || source.getSpendableCoins() <= 0 || source.getIntentCreatedAt() < 0
            || source.getIntentExpiresAt() <= source.getIntentCreatedAt()
            || source.getIntentExpiresAt() < evaluatedAt
            || source.getAccountObservedAt() < 0 || source.getMarketObservedAt() < 0
            || source.getInputObservedAt() != Math.min(source.getAccountObservedAt(), source.getMarketObservedAt())
            || source.getEligibilityEvaluatedAt() < source.getInputObservedAt()
            || source.getHistoryObservedAt() < 0 || source.getHistoryObservedAt() > source.getComposedAt()
            || source.getComposedAt() < source.getEligibilityEvaluatedAt()
            || source.getComposedAt() > evaluatedAt
            || source.getComposedAt() + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS < evaluatedAt
            || !Double.isFinite(source.getEffectiveHorizonHours())
            || source.getEffectiveHorizonHours() <= 0 || source.getFillCurve() == null
            || source.getFillCurve().isEmpty() || source.getAssessments() == null
            || source.getAssessments().isEmpty() || source.getAffordableCandidates() == null
            || source.getAffordableCandidates().isEmpty();
    }

    private static boolean valid(BuyReplacementCandidateAffordability value,
        BuyReplacementCandidateFillInputContext source, int index)
    {
        if (value == null || value.getOffsetIndex() != index || !Double.isFinite(value.getOffset())
            || value.getBuyPrice() <= 0
            || value.getExactRemainderQuantity() != source.getExactRemainderQuantity()
            || value.getTotalCost() <= 0 || value.getOutcome() == null
            || PriceOffset.apply(source.getCurrentLowPrice(), value.getOffset()) != value.getBuyPrice())
            return false;
        long cost = (long) value.getBuyPrice() * source.getExactRemainderQuantity();
        BuyReplacementAffordabilityOutcome expected = cost <= source.getSpendableCoins()
            ? BuyReplacementAffordabilityOutcome.AFFORDABLE
            : BuyReplacementAffordabilityOutcome.UNAFFORDABLE;
        return value.getTotalCost() == cost && value.getOutcome() == expected;
    }

    private static boolean matches(BuyReplacementAffordableCandidate candidate,
        BuyReplacementCandidateAffordability value)
    {
        return candidate != null && candidate.getOffsetIndex() == value.getOffsetIndex()
            && Double.compare(candidate.getOffset(), value.getOffset()) == 0
            && candidate.getBuyPrice() == value.getBuyPrice()
            && candidate.getExactRemainderQuantity() == value.getExactRemainderQuantity()
            && candidate.getTotalCost() == value.getTotalCost();
    }

    private static boolean valid(FillEstimate estimate)
    {
        return estimate != null && Double.isFinite(estimate.getProbability())
            && estimate.getProbability() >= 0 && estimate.getProbability() <= 1
            && !Double.isNaN(estimate.getExpectedHours()) && estimate.getExpectedHours() >= 0
            && Double.isFinite(estimate.getUnitsPerHour()) && estimate.getUnitsPerHour() >= 0
            && !Double.isNaN(estimate.getWaitHours()) && estimate.getWaitHours() >= 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
