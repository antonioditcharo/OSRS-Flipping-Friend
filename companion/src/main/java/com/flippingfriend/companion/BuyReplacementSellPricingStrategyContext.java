package com.flippingfriend.companion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Immutable non-authorizing inputs for later sell-price candidate generation. */
final class BuyReplacementSellPricingStrategyContext {
 private final BuyReplacementCandidateFillViabilityAssessment source; private final List<BuyReplacementCandidateFillEvaluation> evaluations; private final List<BuyReplacementFillViableCandidate> viableCandidates; private final double[] sellOffsets; private final int currentHighPrice; private final long composedAt;
 BuyReplacementSellPricingStrategyContext(BuyReplacementCandidateFillViabilityAssessment s,List<BuyReplacementCandidateFillEvaluation> e,List<BuyReplacementFillViableCandidate> v,double[] o,int h,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));viableCandidates=Collections.unmodifiableList(new ArrayList<>(v));sellOffsets=o.clone();currentHighPrice=h;composedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getSlot(){return source.getSlot();} int getItemId(){return source.getItemId();} String getItemName(){return source.getItemName();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} long getFillEvaluatedAt(){return source.getFillEvaluatedAt();} long getViabilityAssessedAt(){return source.getAssessedAt();} List<BuyReplacementCandidateFillEvaluation> getEvaluations(){return evaluations;} List<BuyReplacementFillViableCandidate> getViableCandidates(){return viableCandidates;} double[] getSellOffsets(){return sellOffsets.clone();} int getCurrentHighPrice(){return currentHighPrice;} long getComposedAt(){return composedAt;}
}
