package com.flippingfriend.companion;
import java.util.List;
/** Immutable binding of round-trip completion evidence to one effective wait multiplier. */
final class BuyReplacementDurationCalibrationInputContext {
 private final BuyReplacementRoundTripCompletionEvaluationSet source; private final boolean learningDisabled; private final double waitMultiplier; private final long composedAt;
 BuyReplacementDurationCalibrationInputContext(BuyReplacementRoundTripCompletionEvaluationSet s,boolean d,double m,long at){source=s;learningDisabled=d;waitMultiplier=m;composedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getSlot(){return source.getSlot();} int getItemId(){return source.getItemId();} String getItemName(){return source.getItemName();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} long getRoundTripCompletionEvaluatedAt(){return source.getEvaluatedAt();} List<BuyReplacementRoundTripCompletionEvaluation> getEvaluations(){return source.getEvaluations();} boolean isLearningDisabled(){return learningDisabled;} double getWaitMultiplier(){return waitMultiplier;} long getComposedAt(){return composedAt;}
}
