package com.flippingfriend.companion;
import com.flippingfriend.model.PriceOffset; import com.flippingfriend.model.RiskAppetite; import java.util.ArrayList; import java.util.List;
/** Pure fail-closed positive raw-spread interpretation without tax or economics. */
final class BuyReplacementSpreadEligibilityEvaluator {
 BuyReplacementSpreadEligibilityAssessment evaluate(BuyReplacementSellPriceCandidateSet s,long at){
  if(s==null||at<s.getStrategyComposedAt()||s.getCandidates()==null||s.getCandidates().isEmpty()||s.getViableCandidates()==null||s.getViableCandidates().isEmpty())return null;
  RiskAppetite r=RiskAppetite.forName(s.getAppetiteName()); if(!r.getName().equals(s.getAppetiteName()))return null; double[] o=r.getSellOffsets(); int expected=s.getViableCandidates().size()*o.length; if(o.length==0||s.getCandidates().size()!=expected)return null;
  List<BuyReplacementSellPriceCandidate> complete=new ArrayList<>(expected); List<BuyReplacementPositiveSpreadCandidate> positive=new ArrayList<>();
  for(int i=0;i<expected;i++){BuyReplacementSellPriceCandidate c=s.getCandidates().get(i);BuyReplacementFillViableCandidate b=s.getViableCandidates().get(i/o.length);int si=i%o.length;if(!valid(c,b,s,si,o[si]))return null;complete.add(c);if(c.getSellPrice()>c.getBuyPrice())positive.add(new BuyReplacementPositiveSpreadCandidate(c));}
  return new BuyReplacementSpreadEligibilityAssessment(s,positive.isEmpty()?BuyReplacementSpreadEligibilityOutcome.INELIGIBLE:BuyReplacementSpreadEligibilityOutcome.ELIGIBLE,complete,positive,at);
 }
 private static boolean valid(BuyReplacementSellPriceCandidate c,BuyReplacementFillViableCandidate b,BuyReplacementSellPriceCandidateSet s,int si,double so){return c!=null&&b!=null&&Double.isFinite(so)&&c.getBuyOffsetIndex()==b.getOffsetIndex()&&Double.compare(c.getBuyOffset(),b.getOffset())==0&&c.getBuyPrice()==b.getBuyPrice()&&c.getExactRemainderQuantity()==s.getExactRemainderQuantity()&&c.getExactRemainderQuantity()==b.getExactRemainderQuantity()&&c.getTotalCost()==b.getTotalCost()&&c.getTotalCost()==(long)c.getBuyPrice()*s.getExactRemainderQuantity()&&Double.compare(c.getBuyProbability(),b.getProbability())==0&&Double.compare(c.getBuyExpectedHours(),b.getExpectedHours())==0&&Double.compare(c.getBuyUnitsPerHour(),b.getUnitsPerHour())==0&&Double.compare(c.getBuyWaitHours(),b.getWaitHours())==0&&c.getSellOffsetIndex()==si&&Double.compare(c.getSellOffset(),so)==0&&c.getSellPrice()==PriceOffset.apply(s.getCurrentHighPrice(),so);}
}
