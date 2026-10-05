package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable complete raw sell-price pairing grid. */
final class BuyReplacementSellPriceCandidateSet {
 private final BuyReplacementSellPricingStrategyContext strategy; private final List<BuyReplacementSellPriceCandidate> candidates;
 BuyReplacementSellPriceCandidateSet(BuyReplacementSellPricingStrategyContext s,List<BuyReplacementSellPriceCandidate> c){strategy=s;candidates=Collections.unmodifiableList(new ArrayList<>(c));}
 String getIntentId(){return strategy.getIntentId();} String getOriginalOfferIdentity(){return strategy.getOriginalOfferIdentity();} String getRecommendationId(){return strategy.getRecommendationId();} int getSlot(){return strategy.getSlot();} int getItemId(){return strategy.getItemId();} String getItemName(){return strategy.getItemName();} int getExactRemainderQuantity(){return strategy.getExactRemainderQuantity();} String getAppetiteName(){return strategy.getAppetiteName();} double getEffectiveHorizonHours(){return strategy.getEffectiveHorizonHours();} int getCurrentHighPrice(){return strategy.getCurrentHighPrice();} long getStrategyComposedAt(){return strategy.getComposedAt();} List<BuyReplacementCandidateFillEvaluation> getEvaluations(){return strategy.getEvaluations();} List<BuyReplacementFillViableCandidate> getViableCandidates(){return strategy.getViableCandidates();} List<BuyReplacementSellPriceCandidate> getCandidates(){return candidates;}
}
