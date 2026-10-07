package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.List;
/** Pure fail-closed application of production wait-only arithmetic to both quantity legs. */
final class BuyReplacementQuantityCalibratedDurationEvaluator {
 BuyReplacementQuantityCalibratedDurationEvaluationSet evaluate(BuyReplacementQuantityDurationCalibrationInputContext s,long at){
  if(s==null)return null;
  try{return evaluateValid(s,at);}catch(NullPointerException | IllegalArgumentException malformedEvidence){return null;}
 }
 private BuyReplacementQuantityCalibratedDurationEvaluationSet evaluateValid(BuyReplacementQuantityDurationCalibrationInputContext s,long at){
  if(s.getComposedAt()<0||at<s.getComposedAt()||at-s.getComposedAt()>CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS
   ||!positive(s.getWaitMultiplier())||(s.isLearningDisabled()&&Double.compare(s.getWaitMultiplier(),1.0)!=0)
   ||s.getItemId()<=0||s.getExactRemainderQuantity()<=0||!text(s.getIntentId())||!text(s.getOriginalOfferIdentity())||!text(s.getRecommendationId())||!text(s.getAppetiteName())
   ||!positive(s.getEffectiveHorizonHours())||!positive(s.getSeasonalMultiplier())
   ||s.getHistoryObservedAt()<0||s.getMarketContextObservedAt()<0||s.getHistoryObservedAt()>s.getFillInputComposedAt()||s.getMarketContextObservedAt()>s.getFillInputComposedAt()
   ||s.getFillInputComposedAt()>s.getFillEvaluatedAt()||s.getFillEvaluatedAt()>s.getFillViabilityAssessedAt()||s.getFillViabilityAssessedAt()>s.getRoundTripCompletionEvaluatedAt()||s.getRoundTripCompletionEvaluatedAt()>s.getComposedAt()
   ||s.getEvaluations()==null||s.getEvaluations().isEmpty()||s.getViableCandidates()==null||s.getViableCandidates().size()!=s.getEvaluations().size()||s.getCompleteFillEvaluations()==null||s.getCompleteFillEvaluations().isEmpty())return null;
  List<BuyReplacementQuantityCalibratedDurationEvaluation> out=new ArrayList<>(s.getEvaluations().size()); int cursor=0;
  for(int i=0;i<s.getEvaluations().size();i++){
   BuyReplacementQuantityRoundTripCompletionEvaluation e=s.getEvaluations().get(i);
   if(!valid(e,s.getViableCandidates().get(i),s))return null;
   while(cursor<s.getCompleteFillEvaluations().size()&&!valid(e,new BuyReplacementQuantityFillViableCandidate(s.getCompleteFillEvaluations().get(cursor)),s))cursor++;
   if(cursor==s.getCompleteFillEvaluations().size())return null; cursor++;
   double b=correct(e.getBuyExpectedHours(),e.getBuyWaitHours(),s.getWaitMultiplier()); double x=correct(e.getSellExpectedHours(),e.getSellWaitHours(),s.getWaitMultiplier());
   if(!Double.isFinite(b)||b<0||!Double.isFinite(x)||x<0)return null;
   out.add(new BuyReplacementQuantityCalibratedDurationEvaluation(e,b,x));
  }
  return new BuyReplacementQuantityCalibratedDurationEvaluationSet(s,out,at);
 }
 // Arithmetic fallback does not relax upstream admissibility of wait measurements.
 static double correct(double expected,double wait,double multiplier){if(wait<=0||Double.isNaN(wait)||Double.isInfinite(wait))return expected;double working=Math.max(0,expected-wait);return wait*multiplier+working;}
 private static boolean positive(double x){return Double.isFinite(x)&&x>0;} private static boolean text(String x){return x!=null&&!x.trim().isEmpty();}
 private static boolean valid(BuyReplacementQuantityRoundTripCompletionEvaluation e,BuyReplacementQuantityFillViableCandidate v,BuyReplacementQuantityDurationCalibrationInputContext s){return e!=null&&v!=null&&e.getBuyOffsetIndex()>=0&&e.getSellOffsetIndex()>=0&&e.getBuyPrice()>0&&e.getSellPrice()>e.getBuyPrice()&&e.getSizeGridIndex()>=0&&Double.isFinite(e.getSizeShare())&&e.getSizeShare()>0&&Double.isFinite(e.getKellyFraction())&&e.getKellyFraction()>0&&e.getKellyFraction()<=1&&Double.isFinite(e.getExpectedProfit())&&e.getWorstLoss()>0&&Double.isFinite(e.getCompletionProbability())&&e.getCompletionProbability()>0&&e.getFillableQuantity()==v.getFillableQuantity()&&e.getFillableQuantity()==s.getExactRemainderQuantity()&&e.getQuantity()==v.getQuantity()&&e.getQuantity()>0&&e.getQuantity()<=e.getFillableQuantity()&&e.getBuyOffsetIndex()==v.getBuyOffsetIndex()&&e.getSellOffsetIndex()==v.getSellOffsetIndex()&&e.getBuyPrice()==v.getBuyPrice()&&e.getSellPrice()==v.getSellPrice()&&e.getSizeGridIndex()==v.getSizeGridIndex()&&Double.compare(e.getSizeShare(),v.getSizeShare())==0&&Double.compare(e.getKellyFraction(),v.getKellyFraction())==0&&Double.compare(e.getExpectedProfit(),v.getExpectedProfit())==0&&e.getWorstLoss()==v.getWorstLoss()&&Double.compare(e.getBuyProbability(),v.getBuyProbability())==0&&Double.compare(e.getSellProbability(),v.getSellProbability())==0&&Double.compare(e.getBuyExpectedHours(),v.getBuyExpectedHours())==0&&Double.compare(e.getBuyUnitsPerHour(),v.getBuyUnitsPerHour())==0&&Double.compare(e.getBuyWaitHours(),v.getBuyWaitHours())==0&&Double.compare(e.getSellExpectedHours(),v.getSellExpectedHours())==0&&Double.compare(e.getSellUnitsPerHour(),v.getSellUnitsPerHour())==0&&Double.compare(e.getSellWaitHours(),v.getSellWaitHours())==0&&Double.isFinite(e.getBuyProbability())&&e.getBuyProbability()>0&&e.getBuyProbability()<=1&&Double.isFinite(e.getSellProbability())&&e.getSellProbability()>0&&e.getSellProbability()<=1&&Double.isFinite(e.getBuyExpectedHours())&&e.getBuyExpectedHours()>=0&&Double.isFinite(e.getSellExpectedHours())&&e.getSellExpectedHours()>=0&&Double.isFinite(e.getBuyUnitsPerHour())&&e.getBuyUnitsPerHour()>=0&&Double.isFinite(e.getSellUnitsPerHour())&&e.getSellUnitsPerHour()>=0&&!Double.isNaN(e.getBuyWaitHours())&&e.getBuyWaitHours()>=0&&!Double.isNaN(e.getSellWaitHours())&&e.getSellWaitHours()>=0&&Double.compare(e.getCompletionProbability(),e.getBuyProbability()*e.getSellProbability())==0;}
}
