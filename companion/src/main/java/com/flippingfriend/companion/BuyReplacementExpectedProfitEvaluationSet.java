package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered production-equivalent expected-profit evidence. */
final class BuyReplacementExpectedProfitEvaluationSet {
 private final BuyReplacementUnwindLossEvaluationSet source; private final List<BuyReplacementExpectedProfitEvaluation> evaluations; private final long evaluatedAt;
 BuyReplacementExpectedProfitEvaluationSet(BuyReplacementUnwindLossEvaluationSet s,List<BuyReplacementExpectedProfitEvaluation> e,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));evaluatedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getSlot(){return source.getSlot();} int getItemId(){return source.getItemId();} String getItemName(){return source.getItemName();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} long getUnwindLossEvaluatedAt(){return source.getEvaluatedAt();} List<BuyReplacementUnwindLossEvaluation> getUnwindLossEvaluations(){return source.getEvaluations();} List<BuyReplacementExpectedProfitEvaluation> getEvaluations(){return evaluations;} long getEvaluatedAt(){return evaluatedAt;}
}
