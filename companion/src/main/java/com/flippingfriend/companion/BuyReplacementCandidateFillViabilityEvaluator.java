package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.List;

/** Pure fail-closed interpretation using the shared FillEstimate plausibility contract. */
final class BuyReplacementCandidateFillViabilityEvaluator
{
    BuyReplacementCandidateFillViabilityAssessment evaluate(
        BuyReplacementCandidateFillEvaluationSet source, long assessedAt)
    {
        if (source == null || assessedAt < source.getEvaluatedAt()
            || assessedAt > source.getIntentExpiresAt()
            || source.getEvaluatedAt() + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS < assessedAt
            || source.getEvaluations() == null || source.getEvaluations().isEmpty()
            || source.getAffordableCandidates() == null
            || source.getAffordableCandidates().size() != source.getEvaluations().size()) return null;
        List<BuyReplacementCandidateFillEvaluation> complete =
            new ArrayList<>(source.getEvaluations().size());
        List<BuyReplacementFillViableCandidate> viable = new ArrayList<>();
        for (int i = 0; i < source.getEvaluations().size(); i++)
        {
            BuyReplacementCandidateFillEvaluation value = source.getEvaluations().get(i);
            BuyReplacementAffordableCandidate candidate = source.getAffordableCandidates().get(i);
            if (!valid(value, candidate, source)) return null;
            complete.add(value);
            if (value.getProbability() > 0 && Double.isFinite(value.getExpectedHours()))
                viable.add(new BuyReplacementFillViableCandidate(value));
        }
        return new BuyReplacementCandidateFillViabilityAssessment(source,
            viable.isEmpty() ? BuyReplacementCandidateFillViabilityOutcome.NOT_VIABLE
                : BuyReplacementCandidateFillViabilityOutcome.VIABLE,
            complete, viable, assessedAt);
    }

    private static boolean valid(BuyReplacementCandidateFillEvaluation value,
        BuyReplacementAffordableCandidate candidate, BuyReplacementCandidateFillEvaluationSet source)
    {
        return value != null && candidate != null
            && value.getOffsetIndex() == candidate.getOffsetIndex()
            && Double.compare(value.getOffset(), candidate.getOffset()) == 0
            && value.getBuyPrice() == candidate.getBuyPrice()
            && value.getExactRemainderQuantity() == source.getExactRemainderQuantity()
            && value.getExactRemainderQuantity() == candidate.getExactRemainderQuantity()
            && value.getTotalCost() == candidate.getTotalCost()
            && Double.isFinite(value.getProbability()) && value.getProbability() >= 0
            && value.getProbability() <= 1 && !Double.isNaN(value.getExpectedHours())
            && value.getExpectedHours() >= 0 && Double.isFinite(value.getUnitsPerHour())
            && value.getUnitsPerHour() >= 0 && !Double.isNaN(value.getWaitHours())
            && value.getWaitHours() >= 0;
    }
}
