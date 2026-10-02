package com.flippingfriend.companion;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest;
/** Pure, non-activated gate from validated eligibility inputs to later pricing readiness. */
final class CompanionBuyReplacementReadinessPolicy {
 static final String SCHEMA_VERSION="1", POLICY_VERSION="companion-buy-replacement-readiness-1"; static final long MAX_INPUT_AGE_SECONDS=CompanionBuyMaintenancePolicy.MAX_INPUT_AGE_SECONDS;
 BuyReplacementReadinessAssessment assess(BuyReplacementEligibilityContext c){
  if(c==null)return result("none","none","none",-1,0,0,BuyReplacementReadinessOutcome.ABSTAIN,"CONTEXT_REQUIRED",0,0);
  long at=c.getEvaluatedAt(), observed=Math.min(c.getAccountObservedAt(),c.getMarketObservedAt());
  if(blank(c.getIntentId())||blank(c.getOfferIdentity())||blank(c.getRecommendationId())||blank(c.getItemName())||c.getSlot()<0||c.getItemId()<=0||c.getRemainderQuantity()<=0||c.getOriginalPrice()<=0||c.getLowPrice()<=0||c.getHighPrice()<=0||at<0||observed<0||observed>at||c.getCreatedAt()<0||c.getExpiresAt()<=c.getCreatedAt()||c.getExpiresAt()<at)return result(c,BuyReplacementReadinessOutcome.ABSTAIN,"INPUT_INCONSISTENT",Math.max(0,at),Math.max(0,Math.min(Math.max(0,at),observed)));
  if(c.getAccountObservedAt()+MAX_INPUT_AGE_SECONDS<at||c.getMarketObservedAt()+MAX_INPUT_AGE_SECONDS<at)return result(c,BuyReplacementReadinessOutcome.ABSTAIN,"INPUT_STALE",at,observed);
  if(c.getFreeSlots()<=0||c.getSpendableCoins()<=0)return result(c,BuyReplacementReadinessOutcome.ABSTAIN,"ACCOUNT_CAPACITY_UNAVAILABLE",at,observed);
  if(c.getBuyLimitRemaining()<c.getRemainderQuantity())return result(c,BuyReplacementReadinessOutcome.INELIGIBLE,"BUY_LIMIT_BELOW_REMAINDER",at,observed);
  return result(c,BuyReplacementReadinessOutcome.READY_FOR_PRICING,"EXACT_REMAINDER_READY_FOR_PRICING",at,observed);
 }
 private static BuyReplacementReadinessAssessment result(BuyReplacementEligibilityContext c,BuyReplacementReadinessOutcome o,String reason,long at,long observed){return result(c.getIntentId(),c.getOfferIdentity(),c.getRecommendationId(),c.getSlot(),c.getItemId(),c.getRemainderQuantity(),o,reason,at,observed);}
 private static BuyReplacementReadinessAssessment result(String intent,String offer,String recommendation,int slot,int item,int remainder,BuyReplacementReadinessOutcome o,String reason,long at,long observed){String canonical=POLICY_VERSION+'|'+intent+'|'+offer+'|'+recommendation+'|'+slot+'|'+item+'|'+remainder+'|'+o+'|'+reason+'|'+at+'|'+observed;return new BuyReplacementReadinessAssessment(SCHEMA_VERSION,POLICY_VERSION,"buy-replacement-readiness-"+hash(canonical),intent,offer,recommendation,slot,item,remainder,o,reason,at,observed);}
 private static String hash(String value){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(int i=0;i<16;i++)s.append(String.format("%02x",d[i]));return s.toString();}catch(Exception e){throw new IllegalStateException("SHA-256 is unavailable",e);}} private static boolean blank(String v){return v==null||v.trim().isEmpty();}
}
