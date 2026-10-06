package com.flippingfriend.companion;
import java.util.List;
/** Immutable production worst-loss inputs without applying the loss calculation. */
final class BuyReplacementWorstLossInputContext {
 private final BuyReplacementExpectedProfitEvaluationSet source; private final double lossCutPct; private final long composedAt;
 BuyReplacementWorstLossInputContext(BuyReplacementExpectedProfitEvaluationSet s,double loss,long at){source=s;lossCutPct=loss;composedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getSlot(){return source.getSlot();} int getItemId(){return source.getItemId();} String getItemName(){return source.getItemName();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} long getExpectedProfitEvaluatedAt(){return source.getEvaluatedAt();} List<BuyReplacementExpectedProfitEvaluation> getEvaluations(){return source.getEvaluations();} double getLossCutPct(){return lossCutPct;} long getComposedAt(){return composedAt;}
}
