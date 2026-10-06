package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered production-equivalent quantity-grid evidence. */
final class BuyReplacementQuantityGridCandidateSet {
 private final BuyReplacementKellyFractionEvaluationSet source; private final List<BuyReplacementQuantityGridCandidate> candidates; private final long composedAt;
 BuyReplacementQuantityGridCandidateSet(BuyReplacementKellyFractionEvaluationSet s,List<BuyReplacementQuantityGridCandidate> c,long at){source=s;candidates=Collections.unmodifiableList(new ArrayList<>(c));composedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getItemId(){return source.getItemId();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double[] getSizeGrid(){return source.getSizeGrid();} List<BuyReplacementKellyFractionEvaluation> getEvaluations(){return source.getEvaluations();} List<BuyReplacementQuantityGridCandidate> getCandidates(){return candidates;} long getFractionEvaluatedAt(){return source.getEvaluatedAt();} long getComposedAt(){return composedAt;}
}
