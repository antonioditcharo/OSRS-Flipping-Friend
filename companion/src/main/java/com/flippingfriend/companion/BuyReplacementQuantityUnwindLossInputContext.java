package com.flippingfriend.companion;

import java.util.List;

/** Immutable binding of ordered quantity evidence to external unwind inputs, without loss arithmetic. */
final class BuyReplacementQuantityUnwindLossInputContext {
    private final BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet source;
    private final int currentLowPrice;
    private final double volatility;
    private final int bucketSeconds;
    private final long marketObservedAt;
    private final long composedAt;

    BuyReplacementQuantityUnwindLossInputContext(BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet source, int low,
            double volatility, int bucketSeconds, long marketObservedAt, long composedAt) {
        this.source = source;
        currentLowPrice = low;
        this.volatility = volatility;
        this.bucketSeconds = bucketSeconds;
        this.marketObservedAt = marketObservedAt;
        this.composedAt = composedAt;
    }
    BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet getSource() { return source; }
    String getIntentId() { return source.getIntentId(); }
    String getOriginalOfferIdentity() { return source.getOriginalOfferIdentity(); }
    String getRecommendationId() { return source.getRecommendationId(); }
    int getItemId() { return source.getItemId(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    String getAppetiteName() { return source.getAppetiteName(); }
    double getEffectiveHorizonHours() { return source.getEffectiveHorizonHours(); }
    double getSeasonalMultiplier() { return source.getSeasonalMultiplier(); }
    long getHistoryObservedAt() { return source.getHistoryObservedAt(); }
    long getMarketContextObservedAt() { return source.getMarketContextObservedAt(); }
    long getFillInputComposedAt() { return source.getFillInputComposedAt(); }
    long getFillEvaluatedAt() { return source.getFillEvaluatedAt(); }
    long getFillViabilityAssessedAt() { return source.getFillViabilityAssessedAt(); }
    long getRoundTripCompletionEvaluatedAt() { return source.getRoundTripCompletionEvaluatedAt(); }
    List<BuyReplacementQuantityFillEvaluation> getCompleteFillEvaluations() { return source.getCompleteFillEvaluations(); }
    List<BuyReplacementQuantityFillViableCandidate> getViableCandidates() { return source.getViableCandidates(); }
    boolean isLearningDisabled() { return source.isLearningDisabled(); }
    double getWaitMultiplier() { return source.getWaitMultiplier(); }
    long getCalibrationInputComposedAt() { return source.getCalibrationInputComposedAt(); }
    List<BuyReplacementQuantityRoundTripCompletionEvaluation> getCompletionEvaluations() { return source.getCompletionEvaluations(); }
    List<BuyReplacementQuantityCalibratedDurationEvaluation> getCalibratedDurationEvaluations() { return source.getCalibratedDurationEvaluations(); }
    long getCalibratedDurationEvaluatedAt() { return source.getCalibratedDurationEvaluatedAt(); }
    List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> getEvaluations() { return source.getEvaluations(); }
    long getExpectedSlotOccupancyEvaluatedAt() { return source.getEvaluatedAt(); }
    int getCurrentLowPrice() { return currentLowPrice; }
    double getVolatility() { return volatility; }
    int getBucketSeconds() { return bucketSeconds; }
    long getMarketObservedAt() { return marketObservedAt; }
    long getComposedAt() { return composedAt; }
}
