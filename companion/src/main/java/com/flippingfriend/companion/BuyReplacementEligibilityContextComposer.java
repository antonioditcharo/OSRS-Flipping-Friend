package com.flippingfriend.companion;
import com.flippingfriend.core.AccountSnapshot; import com.flippingfriend.core.BuyReplacementIntent; import com.flippingfriend.core.BuyReplacementIntentState;
final class BuyReplacementEligibilityContextComposer {
 static final long MAX_INPUT_AGE_SECONDS=CompanionBuyMaintenancePolicy.MAX_INPUT_AGE_SECONDS;
 BuyReplacementEligibilityContext compose(BuyReplacementIntent i,BuyMaintenanceMarketInput m,AccountSnapshot a,int limit,long at){
  if(i==null||m==null||a==null||at<0||i.getState()!=BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING)return null;
  if(blank(i.getIntentId())||blank(i.getOriginalOfferIdentity())||blank(i.getRecommendationId())||blank(i.getItemName())||i.getSlot()<0||i.getItemId()<=0||i.getOriginalPrice()<=0||i.getRemainderQuantity()<=0)return null;
  if(i.getCreatedAt()<0||i.getCreatedAt()>at||i.getExpiresAt()<=i.getCreatedAt()||i.getExpiresAt()<at)return null;
  if(m.getItemId()!=i.getItemId()||m.getLowPrice()<=0||m.getHighPrice()<=0||stale(m.getObservedAt(),at))return null;
  if(stale(a.getObservedAt(),at)||!a.isBankSeen()||a.getSpendableCoins()<=0||a.getFreeSlots()<=0||a.getTotalSlots()<=0||a.getFreeSlots()>a.getTotalSlots()||limit<=0)return null;
  return new BuyReplacementEligibilityContext(i.getIntentId(),i.getOriginalOfferIdentity(),i.getRecommendationId(),i.getSlot(),i.getItemId(),i.getItemName(),i.getRemainderQuantity(),i.getOriginalPrice(),m.getLowPrice(),m.getHighPrice(),limit,a.getSpendableCoins(),a.getFreeSlots(),a.isMembers(),i.getCreatedAt(),i.getExpiresAt(),a.getObservedAt(),m.getObservedAt(),at);
 }
 private static boolean stale(long observed,long at){return observed<0||observed>at||observed+MAX_INPUT_AGE_SECONDS<at;} private static boolean blank(String v){return v==null||v.trim().isEmpty();}
}
