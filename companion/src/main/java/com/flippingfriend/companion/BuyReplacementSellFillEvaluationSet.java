package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable complete sell-fill evidence for every preserved positive-net-margin candidate. */
final class BuyReplacementSellFillEvaluationSet {
 private final BuyReplacementSellFillInputContext source; private final List<BuyReplacementSellFillEvaluation> evaluations; private final long evaluatedAt;
 BuyReplacementSellFillEvaluationSet(BuyReplacementSellFillInputContext s,List<BuyReplacementSellFillEvaluation> e,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));evaluatedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getSlot(){return source.getSlot();} int getItemId(){return source.getItemId();} String getItemName(){return source.getItemName();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} long getHistoryObservedAt(){return source.getHistoryObservedAt();} long getFillInputComposedAt(){return source.getComposedAt();} List<BuyReplacementPositiveNetMarginCandidate> getCandidates(){return source.getCandidates();} List<BuyReplacementSellFillEvaluation> getEvaluations(){return evaluations;} long getEvaluatedAt(){return evaluatedAt;}
}
