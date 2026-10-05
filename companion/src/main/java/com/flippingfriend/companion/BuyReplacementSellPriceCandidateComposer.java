package com.flippingfriend.companion;
import com.flippingfriend.model.PriceOffset; import com.flippingfriend.model.RiskAppetite; import java.util.ArrayList; import java.util.List;
/** Pure ordered generation of raw non-selected sell-price pairings. */
final class BuyReplacementSellPriceCandidateComposer {
 BuyReplacementSellPriceCandidateSet compose(BuyReplacementSellPricingStrategyContext s){
  if(s==null||s.getCurrentHighPrice()<=0||s.getExactRemainderQuantity()<=0||s.getViableCandidates()==null||s.getViableCandidates().isEmpty())return null;
  double[] o=s.getSellOffsets(); RiskAppetite r=RiskAppetite.forName(s.getAppetiteName()); if(!r.getName().equals(s.getAppetiteName())||o.length==0||!java.util.Arrays.equals(o,r.getSellOffsets()))return null;
  List<BuyReplacementSellPriceCandidate> out=new ArrayList<>(s.getViableCandidates().size()*o.length);
  for(BuyReplacementFillViableCandidate b:s.getViableCandidates()){
   if(!valid(b,s))return null;
   for(int i=0;i<o.length;i++){if(!Double.isFinite(o[i]))return null;int price=PriceOffset.apply(s.getCurrentHighPrice(),o[i]);if(price<=0)return null;out.add(new BuyReplacementSellPriceCandidate(b,i,o[i],price));}
  }
  return out.isEmpty()?null:new BuyReplacementSellPriceCandidateSet(s,out);
 }
 private static boolean valid(BuyReplacementFillViableCandidate b,BuyReplacementSellPricingStrategyContext s){return b!=null&&b.getOffsetIndex()>=0&&Double.isFinite(b.getOffset())&&b.getBuyPrice()>0&&b.getExactRemainderQuantity()==s.getExactRemainderQuantity()&&b.getTotalCost()==(long)b.getBuyPrice()*s.getExactRemainderQuantity()&&Double.isFinite(b.getProbability())&&b.getProbability()>0&&b.getProbability()<=1&&Double.isFinite(b.getExpectedHours())&&b.getExpectedHours()>=0&&Double.isFinite(b.getUnitsPerHour())&&b.getUnitsPerHour()>=0&&!Double.isNaN(b.getWaitHours())&&b.getWaitHours()>=0;}
}
