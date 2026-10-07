package com.flippingfriend.companion;

import java.util.List;

/** Immutable binding of quantity expected-profit evidence to the production loss-cut scalar. */
final class BuyReplacementQuantityWorstLossInputContext {
    private final BuyReplacementQuantityExpectedProfitEvaluationSet source;
    private final double lossCutPct;
    private final long composedAt;
    BuyReplacementQuantityWorstLossInputContext(BuyReplacementQuantityExpectedProfitEvaluationSet source, double lossCutPct, long composedAt) {
        this.source = source; this.lossCutPct = lossCutPct; this.composedAt = composedAt;
    }
    BuyReplacementQuantityExpectedProfitEvaluationSet getSource() { return source; }
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
    long getExpectedSlotOccupancyEvaluatedAt() { return source.getExpectedSlotOccupancyEvaluatedAt(); }
    int getCurrentLowPrice() { return source.getCurrentLowPrice(); }
    double getVolatility() { return source.getVolatility(); }
    int getBucketSeconds() { return source.getBucketSeconds(); }
    long getMarketObservedAt() { return source.getMarketObservedAt(); }
    long getInputComposedAt() { return source.getInputComposedAt(); }
    List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> getSlotOccupancyEvaluations() { return source.getSlotOccupancyEvaluations(); }
    long getUnwindLossEvaluatedAt() { return source.getUnwindLossEvaluatedAt(); }
    List<BuyReplacementQuantityUnwindLossEvaluation> getUnwindLossEvaluations() { return source.getUnwindLossEvaluations(); }
    long getExpectedProfitEvaluatedAt() { return source.getEvaluatedAt(); }
    List<BuyReplacementQuantityExpectedProfitEvaluation> getEvaluations() { return source.getEvaluations(); }
    double getLossCutPct() { return lossCutPct; }
    long getComposedAt() { return composedAt; }
}
