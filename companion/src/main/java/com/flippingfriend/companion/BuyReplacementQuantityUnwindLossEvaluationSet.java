package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable ordered results retaining the entire input context and original timestamps. */
final class BuyReplacementQuantityUnwindLossEvaluationSet {
    private final BuyReplacementQuantityUnwindLossInputContext source;
    private final List<BuyReplacementQuantityUnwindLossEvaluation> evaluations;
    private final long evaluatedAt;
    BuyReplacementQuantityUnwindLossEvaluationSet(BuyReplacementQuantityUnwindLossInputContext source, List<BuyReplacementQuantityUnwindLossEvaluation> evaluations, long at) {
        this.source = source;
        this.evaluations = Collections.unmodifiableList(new ArrayList<>(evaluations));
        evaluatedAt = at;
    }
    BuyReplacementQuantityUnwindLossInputContext getSource() { return source; }
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
    long getInputComposedAt() { return source.getComposedAt(); }
    List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> getSlotOccupancyEvaluations() { return source.getEvaluations(); }
    List<BuyReplacementQuantityUnwindLossEvaluation> getEvaluations() { return evaluations; }
    long getEvaluatedAt() { return evaluatedAt; }
}
