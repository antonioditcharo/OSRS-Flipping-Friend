package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable ordered slot-time results retaining the complete calibrated-duration context. */
final class BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet {
    private final BuyReplacementQuantityCalibratedDurationEvaluationSet source;
    private final List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> evaluations;
    private final long evaluatedAt;
    BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(BuyReplacementQuantityCalibratedDurationEvaluationSet source,
            List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> evaluations, long evaluatedAt) {
        this.source = source;
        this.evaluations = Collections.unmodifiableList(new ArrayList<>(evaluations));
        this.evaluatedAt = evaluatedAt;
    }
    BuyReplacementQuantityCalibratedDurationEvaluationSet getSource() { return source; }
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
    List<BuyReplacementQuantityCalibratedDurationEvaluation> getCalibratedDurationEvaluations() { return source.getEvaluations(); }
    long getCalibratedDurationEvaluatedAt() { return source.getEvaluatedAt(); }
    List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> getEvaluations() { return evaluations; }
    long getEvaluatedAt() { return evaluatedAt; }
}
