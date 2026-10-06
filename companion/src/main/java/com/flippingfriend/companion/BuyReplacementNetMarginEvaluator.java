package com.flippingfriend.companion;
import com.flippingfriend.model.TaxCalculator; import java.util.ArrayList; import java.util.List;
/** Pure ordered use of the authoritative tax contract without filtering or selection. */
final class BuyReplacementNetMarginEvaluator {
 BuyReplacementNetMarginEvaluationSet evaluate(BuyReplacementSpreadEligibilityAssessment s,TaxCalculator tax,long at){
  if(s==null||tax==null||s.getOutcome()!=BuyReplacementSpreadEligibilityOutcome.ELIGIBLE||at<s.getEvaluatedAt()||s.getPositiveSpreadCandidates()==null||s.getPositiveSpreadCandidates().isEmpty())return null;
  List<BuyReplacementNetMarginEvaluation> out=new ArrayList<>(s.getPositiveSpreadCandidates().size());
  for(BuyReplacementPositiveSpreadCandidate c:s.getPositiveSpreadCandidates()){
   if(!valid(c,s))return null; int t=tax.taxPerItem(s.getItemId(),c.getSellPrice()); long m=tax.netMarginPerItem(s.getItemId(),c.getBuyPrice(),c.getSellPrice()); long tt=tax.taxFor(s.getItemId(),c.getSellPrice(),c.getExactRemainderQuantity()); long tm=tax.netProfit(s.getItemId(),c.getBuyPrice(),c.getSellPrice(),c.getExactRemainderQuantity());
   if(tt!=(long)t*c.getExactRemainderQuantity()||tm!=m*c.getExactRemainderQuantity())return null; out.add(new BuyReplacementNetMarginEvaluation(c,t,m,tt,tm));
  }
  return new BuyReplacementNetMarginEvaluationSet(s,out,at);
 }
 private static boolean valid(BuyReplacementPositiveSpreadCandidate c,BuyReplacementSpreadEligibilityAssessment s){return c!=null&&c.getBuyOffsetIndex()>=0&&Double.isFinite(c.getBuyOffset())&&c.getBuyPrice()>0&&c.getSellOffsetIndex()>=0&&Double.isFinite(c.getSellOffset())&&c.getSellPrice()>c.getBuyPrice()&&c.getRawSpreadPerItem()==(long)c.getSellPrice()-c.getBuyPrice()&&c.getExactRemainderQuantity()==s.getExactRemainderQuantity()&&c.getTotalCost()==(long)c.getBuyPrice()*s.getExactRemainderQuantity()&&Double.isFinite(c.getBuyProbability())&&c.getBuyProbability()>0&&c.getBuyProbability()<=1&&Double.isFinite(c.getBuyExpectedHours())&&c.getBuyExpectedHours()>=0&&Double.isFinite(c.getBuyUnitsPerHour())&&c.getBuyUnitsPerHour()>=0&&!Double.isNaN(c.getBuyWaitHours())&&c.getBuyWaitHours()>=0;}
}
