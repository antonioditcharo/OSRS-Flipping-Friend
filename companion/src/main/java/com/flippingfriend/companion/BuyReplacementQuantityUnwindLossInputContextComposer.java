package com.flippingfriend.companion;

import com.flippingfriend.model.ItemFeatures;

/** Pure fail-closed input binding; no fetching, exit arithmetic, or runtime consumer. */
final class BuyReplacementQuantityUnwindLossInputContextComposer {
    BuyReplacementQuantityUnwindLossInputContext compose(BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet source,
            int currentLowPrice, ItemFeatures features, int bucketSeconds, long marketObservedAt, long at) {
        if (source == null || features == null) return null;
        try {
            return composeValid(source, currentLowPrice, features, bucketSeconds, marketObservedAt, at);
        } catch (NullPointerException | IllegalArgumentException malformedEvidence) {
            return null;
        }
    }

    private BuyReplacementQuantityUnwindLossInputContext composeValid(BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet source,
            int currentLowPrice, ItemFeatures features, int bucketSeconds, long marketObservedAt, long at) {
        long previous = source.getEvaluatedAt();
        if (!fresh(previous, at) || !fresh(marketObservedAt, at)
                || marketObservedAt > previous || currentLowPrice <= 0 || bucketSeconds <= 0
                || features.getItemId() != source.getItemId()
                || source.getEvaluations() == null || source.getEvaluations().isEmpty()) return null;
        double volatility = features.getVolatility();
        if (!Double.isFinite(volatility) || volatility < 0) return null;
        // Revalidate at the original timestamp, preserving rather than refreshing upstream authority.
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet verified = new BuyReplacementQuantityExpectedSlotOccupancyEvaluator()
                .evaluate(source.getSource(), previous);
        if (verified == null || verified.getEvaluations().size() != source.getEvaluations().size()) return null;
        for (int i = 0; i < source.getEvaluations().size(); i++) {
            BuyReplacementQuantityExpectedSlotOccupancyEvaluation e = source.getEvaluations().get(i);
            BuyReplacementQuantityExpectedSlotOccupancyEvaluation v = verified.getEvaluations().get(i);
            if (e == null || e.getSource() != v.getSource()
                    || !Double.isFinite(e.getExpectedSlotHours()) || e.getExpectedSlotHours() < 1.0 / 60.0
                    || Double.compare(e.getExpectedSlotHours(), v.getExpectedSlotHours()) != 0) return null;
        }
        return new BuyReplacementQuantityUnwindLossInputContext(source, currentLowPrice, volatility, bucketSeconds, marketObservedAt, at);
    }
    private static boolean fresh(long observed, long at) {
        return observed >= 0 && at >= observed
                && at - observed <= CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
    }
}
