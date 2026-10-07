package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered calibrated durations retaining the entire upstream context. */
final class BuyReplacementQuantityCalibratedDurationEvaluationSet {
 private final BuyReplacementQuantityDurationCalibrationInputContext source;
 private final List<BuyReplacementQuantityCalibratedDurationEvaluation> evaluations; private final long evaluatedAt;
 BuyReplacementQuantityCalibratedDurationEvaluationSet(BuyReplacementQuantityDurationCalibrationInputContext s,List<BuyReplacementQuantityCalibratedDurationEvaluation> e,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));evaluatedAt=at;}
 BuyReplacementQuantityDurationCalibrationInputContext getSource(){return source;}
 String getIntentId(){return source.getIntentId();}
 String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();}
 String getRecommendationId(){return source.getRecommendationId();}
 int getItemId(){return source.getItemId();}
 int getExactRemainderQuantity(){return source.getExactRemainderQuantity();}
 String getAppetiteName(){return source.getAppetiteName();}
 double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();}
 double getSeasonalMultiplier(){return source.getSeasonalMultiplier();}
 long getHistoryObservedAt(){return source.getHistoryObservedAt();}
 long getMarketContextObservedAt(){return source.getMarketContextObservedAt();}
 long getFillInputComposedAt(){return source.getFillInputComposedAt();}
 long getFillEvaluatedAt(){return source.getFillEvaluatedAt();}
 long getFillViabilityAssessedAt(){return source.getFillViabilityAssessedAt();}
 long getRoundTripCompletionEvaluatedAt(){return source.getRoundTripCompletionEvaluatedAt();}
 List<BuyReplacementQuantityFillEvaluation> getCompleteFillEvaluations(){return source.getCompleteFillEvaluations();}
 List<BuyReplacementQuantityFillViableCandidate> getViableCandidates(){return source.getViableCandidates();}
 boolean isLearningDisabled(){return source.isLearningDisabled();}
 double getWaitMultiplier(){return source.getWaitMultiplier();}
 long getCalibrationInputComposedAt(){return source.getComposedAt();}
 List<BuyReplacementQuantityRoundTripCompletionEvaluation> getCompletionEvaluations(){return source.getEvaluations();}
 List<BuyReplacementQuantityCalibratedDurationEvaluation> getEvaluations(){return evaluations;} long getEvaluatedAt(){return evaluatedAt;}
}
