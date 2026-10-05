package com.flippingfriend.companion;
import com.flippingfriend.model.RiskAppetite;
import java.util.ArrayList;
/** Pure fail-closed binding of viable fill evidence to authoritative appetite sell offsets. */
final class BuyReplacementSellPricingStrategyContextComposer {
 BuyReplacementSellPricingStrategyContext compose(BuyReplacementCandidateFillViabilityAssessment s,int high,long at){
  if(s==null||s.getOutcome()!=BuyReplacementCandidateFillViabilityOutcome.VIABLE||high<=0||at<s.getAssessedAt()||s.getFillEvaluatedAt()>s.getAssessedAt()||s.getViableCandidates()==null||s.getViableCandidates().isEmpty()||s.getEvaluations()==null||s.getEvaluations().isEmpty())return null;
  RiskAppetite r=RiskAppetite.forName(s.getAppetiteName()); if(!r.getName().equals(s.getAppetiteName()))return null; double[] o=r.getSellOffsets(); if(o.length==0)return null; for(double x:o)if(!Double.isFinite(x))return null;
  for(BuyReplacementFillViableCandidate v:s.getViableCandidates())if(!valid(v,s))return null;
  return new BuyReplacementSellPricingStrategyContext(s,new ArrayList<>(s.getEvaluations()),new ArrayList<>(s.getViableCandidates()),o,high,at);
 }
 private static boolean valid(BuyReplacementFillViableCandidate v,BuyReplacementCandidateFillViabilityAssessment s){return v!=null&&v.getOffsetIndex()>=0&&Double.isFinite(v.getOffset())&&v.getBuyPrice()>0&&v.getExactRemainderQuantity()==s.getExactRemainderQuantity()&&v.getTotalCost()==(long)v.getBuyPrice()*s.getExactRemainderQuantity()&&Double.isFinite(v.getProbability())&&v.getProbability()>0&&v.getProbability()<=1&&Double.isFinite(v.getExpectedHours())&&v.getExpectedHours()>=0&&Double.isFinite(v.getUnitsPerHour())&&v.getUnitsPerHour()>=0&&!Double.isNaN(v.getWaitHours())&&v.getWaitHours()>=0;}
}
