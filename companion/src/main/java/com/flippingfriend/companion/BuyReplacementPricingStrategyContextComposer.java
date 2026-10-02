package com.flippingfriend.companion;
import com.flippingfriend.core.AccountSnapshot; import com.flippingfriend.model.RiskAppetite;
/** Binds Package 3.30 pricing lineage to matching authoritative account strategy. */
final class BuyReplacementPricingStrategyContextComposer {
 BuyReplacementPricingStrategyContext compose(BuyReplacementPricingContext c,AccountSnapshot a){
  if(c==null||a==null||invalid(c)||a.getObservedAt()!=c.getAccountObservedAt()||a.getSpendableCoins()!=c.getSpendableCoins()||a.isMembers()!=c.isMembers()||!a.isBankSeen()||a.getFreeSlots()<=0||a.getTotalSlots()<=0||a.getFreeSlots()>a.getTotalSlots())return null;
  RiskAppetite r=RiskAppetite.forName(a.getRiskAppetite()); double[] o=r.getBuyOffsets(); double h=PortfolioPlanner.horizonFor(a,r);
  if(o.length==0||!Double.isFinite(h)||h<=0)return null; for(double x:o)if(!Double.isFinite(x))return null;
  return new BuyReplacementPricingStrategyContext(c,r.getName(),o,h);
 }
 private static boolean invalid(BuyReplacementPricingContext c){long at=c.getEvaluatedAt(),old=Math.min(c.getAccountObservedAt(),c.getMarketObservedAt());return blank(c.getReadinessAssessmentId())||blank(c.getReadinessPolicyVersion())||blank(c.getIntentId())||blank(c.getOriginalOfferIdentity())||blank(c.getRecommendationId())||blank(c.getItemName())||c.getSlot()<0||c.getItemId()<=0||c.getExactRemainderQuantity()<=0||c.getHistoricalOriginalPrice()<=0||c.getCurrentLowPrice()<=0||c.getCurrentHighPrice()<=c.getCurrentLowPrice()||c.getBuyLimitRemaining()<c.getExactRemainderQuantity()||c.getSpendableCoins()<=0||c.getIntentCreatedAt()<0||c.getIntentExpiresAt()<=c.getIntentCreatedAt()||c.getIntentExpiresAt()<at||c.getAccountObservedAt()<0||c.getMarketObservedAt()<0||c.getInputObservedAt()!=old||at<0||old>at||c.getAccountObservedAt()>at||c.getMarketObservedAt()>at||c.getAccountObservedAt()+CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS<at||c.getMarketObservedAt()+CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS<at;}
 private static boolean blank(String v){return v==null||v.trim().isEmpty();}
}
