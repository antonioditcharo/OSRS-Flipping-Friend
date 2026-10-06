package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered production-equivalent worst-loss evidence. */
final class BuyReplacementWorstLossEvaluationSet {
 private final BuyReplacementWorstLossInputContext source; private final List<BuyReplacementWorstLossEvaluation> evaluations; private final long evaluatedAt;
 BuyReplacementWorstLossEvaluationSet(BuyReplacementWorstLossInputContext s,List<BuyReplacementWorstLossEvaluation> e,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));evaluatedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getSlot(){return source.getSlot();} int getItemId(){return source.getItemId();} String getItemName(){return source.getItemName();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} double getLossCutPct(){return source.getLossCutPct();} long getInputComposedAt(){return source.getComposedAt();} List<BuyReplacementExpectedProfitEvaluation> getExpectedProfitEvaluations(){return source.getEvaluations();} List<BuyReplacementWorstLossEvaluation> getEvaluations(){return evaluations;} long getEvaluatedAt(){return evaluatedAt;}
}
