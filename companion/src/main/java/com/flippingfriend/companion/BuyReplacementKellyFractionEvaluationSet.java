package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered production-equivalent fractional-Kelly evidence. */
final class BuyReplacementKellyFractionEvaluationSet {
 private final BuyReplacementKellySizingInputContext source; private final List<BuyReplacementKellyFractionEvaluation> evaluations; private final long evaluatedAt;
 BuyReplacementKellyFractionEvaluationSet(BuyReplacementKellySizingInputContext s,List<BuyReplacementKellyFractionEvaluation> e,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));evaluatedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getItemId(){return source.getItemId();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double[] getSizeGrid(){return source.getSizeGrid();} List<BuyReplacementKellySizingInput> getInputs(){return source.getInputs();} List<BuyReplacementKellyFractionEvaluation> getEvaluations(){return evaluations;} long getInputComposedAt(){return source.getComposedAt();} long getEvaluatedAt(){return evaluatedAt;}
}
