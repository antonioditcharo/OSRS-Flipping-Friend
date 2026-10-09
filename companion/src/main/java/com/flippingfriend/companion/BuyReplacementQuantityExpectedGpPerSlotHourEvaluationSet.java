package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered quantity rate evidence retaining Package 3.72. */
final class BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet {
    private final BuyReplacementQuantityKellyFractionEvaluationSet source; private final List<BuyReplacementQuantityExpectedGpPerSlotHourEvaluation> evaluations; private final long evaluatedAt;
    BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet(BuyReplacementQuantityKellyFractionEvaluationSet source,List<BuyReplacementQuantityExpectedGpPerSlotHourEvaluation> evaluations,long at) { this.source=source; this.evaluations=Collections.unmodifiableList(new ArrayList<>(evaluations)); evaluatedAt=at; }
    BuyReplacementQuantityKellyFractionEvaluationSet getSource() { return source; }
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
    List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> getSlotOccupancyEvaluations() { return source.getSlotOccupancyEvaluations(); }
    long getUnwindLossEvaluatedAt() { return source.getUnwindLossEvaluatedAt(); }
    List<BuyReplacementQuantityUnwindLossEvaluation> getUnwindLossEvaluations() { return source.getUnwindLossEvaluations(); }
    long getExpectedProfitEvaluatedAt() { return source.getExpectedProfitEvaluatedAt(); }
    double getLossCutPct() { return source.getLossCutPct(); }
    List<BuyReplacementQuantityExpectedProfitEvaluation> getExpectedProfitEvaluations() { return source.getExpectedProfitEvaluations(); }
    List<BuyReplacementQuantityWorstLossEvaluation> getWorstLossEvaluations() { return source.getWorstLossEvaluations(); }
    double getKellyShare() { return source.getKellyShare(); }
    double getMinimumKellyFraction() { return source.getMinimumKellyFraction(); }
    double[] getSizeGrid() { return source.getSizeGrid(); }
    long getWorstLossEvaluatedAt() { return source.getWorstLossEvaluatedAt(); }
    List<BuyReplacementQuantityKellySizingInput> getInputs() { return source.getInputs(); }
    long getInputComposedAt() { return source.getInputComposedAt(); }
    List<BuyReplacementQuantityKellyFractionEvaluation> getKellyFractionEvaluations() { return source.getEvaluations(); }
    List<BuyReplacementQuantityExpectedGpPerSlotHourEvaluation> getEvaluations() { return evaluations; }
    long getKellyFractionEvaluatedAt() { return source.getEvaluatedAt(); }
    long getEvaluatedAt() { return evaluatedAt; }
}
