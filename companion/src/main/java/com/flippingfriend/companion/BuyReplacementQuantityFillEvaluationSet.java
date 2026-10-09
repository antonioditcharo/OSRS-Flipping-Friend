package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable complete two-leg fill evidence for every generated replacement quantity. */
final class BuyReplacementQuantityFillEvaluationSet {
 private final BuyReplacementQuantityFillInputContext source; private final List<BuyReplacementQuantityFillEvaluation> evaluations; private final long evaluatedAt;
 BuyReplacementQuantityFillEvaluationSet(BuyReplacementQuantityFillInputContext s,List<BuyReplacementQuantityFillEvaluation> e,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));evaluatedAt=at;}
 BuyReplacementQuantityFillInputContext getSource(){return source;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getItemId(){return source.getItemId();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} double getSeasonalMultiplier(){return source.getSeasonalMultiplier();} long getHistoryObservedAt(){return source.getHistoryObservedAt();} long getMarketContextObservedAt(){return source.getMarketContextObservedAt();} long getFillInputComposedAt(){return source.getComposedAt();} List<BuyReplacementQuantityFillInput> getInputs(){return source.getInputs();} List<BuyReplacementQuantityFillEvaluation> getEvaluations(){return evaluations;} long getEvaluatedAt(){return evaluatedAt;}
}
