package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered generated-quantity Kelly-input evidence without sizing arithmetic. */
final class BuyReplacementQuantityKellySizingInputContext {
    private final BuyReplacementQuantityWorstLossEvaluationSet source; private final List<BuyReplacementQuantityKellySizingInput> inputs;
    private final double kellyShare, minimumKellyFraction; private final double[] sizeGrid; private final long composedAt;
    BuyReplacementQuantityKellySizingInputContext(BuyReplacementQuantityWorstLossEvaluationSet source,List<BuyReplacementQuantityKellySizingInput> inputs,double share,double minimum,double[] grid,long at) {
        this.source=source; this.inputs=Collections.unmodifiableList(new ArrayList<>(inputs));
        kellyShare=share; minimumKellyFraction=minimum; sizeGrid=grid.clone(); composedAt=at;
    }
    BuyReplacementQuantityWorstLossEvaluationSet getSource() { return source; }
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
    long getInputComposedAt() { return source.getInputComposedAt(); }
    List<BuyReplacementQuantityWorstLossEvaluation> getWorstLossEvaluations() { return source.getEvaluations(); }
    List<BuyReplacementQuantityKellySizingInput> getInputs() { return inputs; }
    double getKellyShare() { return kellyShare; }
    double getMinimumKellyFraction() { return minimumKellyFraction; }
    double[] getSizeGrid() { return sizeGrid.clone(); }
    long getWorstLossEvaluatedAt() { return source.getEvaluatedAt(); }
    long getComposedAt() { return composedAt; }
}
