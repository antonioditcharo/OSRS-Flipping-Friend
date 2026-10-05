package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.PriceOffset;
import java.util.ArrayList;
import java.util.List;

/** Pure fail-closed preparation of history-backed inputs without invoking the fill model. */
final class BuyReplacementCandidateFillInputContextComposer
{
    BuyReplacementCandidateFillInputContext compose(
        BuyReplacementCandidateEligibilityAssessment source, List<Candle> history,
        long historyObservedAt, long composedAt)
    {
        if (invalid(source, history, historyObservedAt, composedAt)) return null;
        List<BuyReplacementCandidateAffordability> complete =
            new ArrayList<>(source.getAssessments().size());
        List<BuyReplacementAffordableCandidate> affordable =
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
                BuyReplacementAffordableCandidate projected =
                    source.getAffordableCandidates().get(affordableIndex++);
                if (!matches(projected, value)) return null;
                affordable.add(projected);
            }
        }
        if (affordableIndex != source.getAffordableCandidates().size() || affordable.isEmpty()) return null;

        List<Candle> copied = copyHistory(history, historyObservedAt);
        if (copied == null) return null;
        FillCurve curve = FillCurve.overRecentHistory(copied);
        if (curve.isEmpty()) return null;
        return new BuyReplacementCandidateFillInputContext(source, complete, affordable, curve,
            historyObservedAt, composedAt);
    }

    private static boolean invalid(BuyReplacementCandidateEligibilityAssessment source,
        List<Candle> history, long historyObservedAt, long composedAt)
    {
        return source == null || source.getOutcome() != BuyReplacementCandidateEligibilityOutcome.ELIGIBLE
            || source.getAssessments() == null || source.getAssessments().isEmpty()
            || source.getAffordableCandidates() == null || source.getAffordableCandidates().isEmpty()
            || history == null || history.size() < 2 || historyObservedAt < 0 || composedAt < 0
            || historyObservedAt > composedAt || source.getEvaluatedAt() > composedAt
            || source.getIntentExpiresAt() < composedAt
            || historyObservedAt + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS < composedAt
            || source.getEvaluatedAt() + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS < composedAt
            || source.getExactRemainderQuantity() <= 0
            || !Double.isFinite(source.getEffectiveHorizonHours())
            || source.getEffectiveHorizonHours() <= 0;
    }

    private static boolean valid(BuyReplacementCandidateAffordability value,
        BuyReplacementCandidateEligibilityAssessment source, int index)
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

    private static List<Candle> copyHistory(List<Candle> history, long observedAt)
    {
        List<Candle> copy = new ArrayList<>(history.size());
        long previous = -1;
        for (Candle candle : history)
        {
            if (candle == null || candle.getTimestamp() < 0 || candle.getTimestamp() > observedAt
                || candle.getTimestamp() < previous || candle.getHighPriceVolume() < 0
                || candle.getLowPriceVolume() < 0) return null;
            previous = candle.getTimestamp();
            copy.add(new Candle(candle.getTimestamp(), candle.getAvgHighPrice(), candle.getAvgLowPrice(),
                candle.getHighPriceVolume(), candle.getLowPriceVolume()));
        }
        return copy;
    }
}
