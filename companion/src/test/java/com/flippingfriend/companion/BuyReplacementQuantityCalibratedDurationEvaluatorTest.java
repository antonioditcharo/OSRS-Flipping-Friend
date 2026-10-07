package com.flippingfriend.companion;
import java.lang.reflect.Method; import java.lang.reflect.Modifier;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
import org.junit.Test; import static org.junit.Assert.*;
public class BuyReplacementQuantityCalibratedDurationEvaluatorTest {
 private final BuyReplacementQuantityCalibratedDurationEvaluator evaluator=new BuyReplacementQuantityCalibratedDurationEvaluator();
 @Test public void independentlyCorrectsBothLegsAndPreservesEveryGetter()throws Exception{
  BuyReplacementQuantityDurationCalibrationInputContext s=context(0.2,0.3,0.75); BuyReplacementQuantityCalibratedDurationEvaluationSet r=evaluator.evaluate(s,1100);assertNotNull(r);assertSame(s,r.getSource());
  assertSame(s.getEvaluations(),r.getCompletionEvaluations());assertSame(s.getCompleteFillEvaluations(),r.getCompleteFillEvaluations());assertSame(s.getViableCandidates(),r.getViableCandidates());assertEquals(1100,r.getEvaluatedAt());assertEquals(s.getComposedAt(),r.getCalibrationInputComposedAt());
  for(Method a:s.getClass().getDeclaredMethods()){if(a.getParameterCount()!=0||a.getName().equals("getEvaluations")||a.getName().equals("getComposedAt"))continue;assertEquals(a.invoke(s),r.getClass().getDeclaredMethod(a.getName()).invoke(r));}
  assertEquals(s.getEvaluations().size(),r.getEvaluations().size());
  for(int i=0;i<r.getEvaluations().size();i++){BuyReplacementQuantityRoundTripCompletionEvaluation a=s.getEvaluations().get(i);BuyReplacementQuantityCalibratedDurationEvaluation e=r.getEvaluations().get(i);assertSame(a,e.getSource());
   for(Method g:a.getClass().getDeclaredMethods()){if(g.getParameterCount()==0)assertEquals(g.invoke(a),e.getClass().getDeclaredMethod(g.getName()).invoke(e));}
   assertEquals(0.2*0.75+Math.max(0,1.2-0.2),e.getCalibratedBuyHours(),0);assertEquals(0.3*0.75+Math.max(0,1.4-0.3),e.getCalibratedSellHours(),0);
  }
  try{r.getEvaluations().clear();fail("immutable");}catch(UnsupportedOperationException expected){}
  assertEquals(r.getEvaluations().get(0).getCalibratedBuyHours(),evaluator.evaluate(s,1100).getEvaluations().get(0).getCalibratedBuyHours(),0);
 }
 @Test public void arithmeticMatchesProductionIncludingExceptionalWaits()throws Exception{
  Method production=CandidateFactory.class.getDeclaredMethod("correctedHours",com.flippingfriend.model.FillEstimate.class,double.class);production.setAccessible(true);
  for(double w:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0.2,2})for(double m:new double[]{0.5,1,2}){
   double expected=(Double)production.invoke(null,new com.flippingfriend.model.FillEstimate(0.6,1.2,100,w),m);assertEquals(expected,BuyReplacementQuantityCalibratedDurationEvaluator.correct(1.2,w,m),0);
  }
 }
 @Test public void validZeroAndInfiniteWaitsRemainUnchanged(){for(double w:new double[]{0,Double.POSITIVE_INFINITY}){BuyReplacementQuantityCalibratedDurationEvaluationSet r=evaluator.evaluate(context(w,w,0.75),1100);assertNotNull(r);assertEquals(1.2,r.getEvaluations().get(0).getCalibratedBuyHours(),0);assertEquals(1.4,r.getEvaluations().get(0).getCalibratedSellHours(),0);}}
 @Test public void inclusiveFreshnessAndFutureTimeFailClosed(){BuyReplacementQuantityDurationCalibrationInputContext s=context(0.2,0.3,1);assertNotNull(evaluator.evaluate(s,1220));assertNull(evaluator.evaluate(s,1221));assertNull(evaluator.evaluate(s,1099));assertNull(evaluator.evaluate(null,1100));}
 @Test public void invalidMultipliersAndOverflowFailClosed(){for(double m:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertNull(evaluator.evaluate(context(0.2,0.3,m),1100));assertNull(evaluator.evaluate(context(2,2,Double.MAX_VALUE),1100));}
 @Test public void upstreamInvalidWaitsAreNotAdmitted(){assertNull(evaluator.evaluate(context(-1,0.3,1),1100));assertNull(evaluator.evaluate(context(Double.NaN,0.3,1),1100));}
 @Test public void malformedNullSourceFailsClosed(){assertNull(evaluator.evaluate(new BuyReplacementQuantityDurationCalibrationInputContext(null,false,1,1100),1100));}
 @Test public void reorderedAndInconsistentCompletionFailClosed(){
  BuyReplacementQuantityFillViabilityAssessment a=BuyReplacementQuantityFillViabilityEvaluatorTest.quantityFillViabilityAssessmentForCompanionTests();BuyReplacementQuantityRoundTripCompletionEvaluationSet b=new BuyReplacementQuantityRoundTripCompletionEvaluator().evaluate(a,1100);
  List<BuyReplacementQuantityRoundTripCompletionEvaluation> list=new ArrayList<>(b.getEvaluations());assertTrue(list.size()>1);Collections.reverse(list);assertNull(evaluator.evaluate(new BuyReplacementQuantityDurationCalibrationInputContext(new BuyReplacementQuantityRoundTripCompletionEvaluationSet(a,list,1100),false,1,1100),1100));
  list=new ArrayList<>(b.getEvaluations());list.set(0,new BuyReplacementQuantityRoundTripCompletionEvaluation(a.getViableCandidates().get(0),0.99));assertNull(evaluator.evaluate(new BuyReplacementQuantityDurationCalibrationInputContext(new BuyReplacementQuantityRoundTripCompletionEvaluationSet(a,list,1100),false,1,1100),1100));
 }
 @Test public void contractsStayPackagePrivateAndOutsideDownstreamAuthority()throws Exception{for(Class<?> c:new Class<?>[]{BuyReplacementQuantityCalibratedDurationEvaluation.class,BuyReplacementQuantityCalibratedDurationEvaluationSet.class,BuyReplacementQuantityCalibratedDurationEvaluator.class}){assertFalse(Modifier.isPublic(c.getModifiers()));String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/"+c.getSimpleName()+".java"));for(String x:new String[]{"expectedSlotHours","expectedGpPerSlotHour","PortfolioCandidate","PolicyDecision","OfferLifecycleAction","REPLACEMENT_AUTHORIZED","estimateBuy","estimateSell","SqliteStore","schedule","selected"})assertFalse(x,source.contains(x));}}
 static BuyReplacementQuantityCalibratedDurationEvaluationSet evaluationSetForCompanionTests(){return new BuyReplacementQuantityCalibratedDurationEvaluator().evaluate(BuyReplacementQuantityDurationCalibrationInputContextComposerTest.contextForCompanionTests(),1100);}
 private static BuyReplacementQuantityDurationCalibrationInputContext context(double bw,double sw,double multiplier){
  BuyReplacementQuantityFillInputContext input=BuyReplacementQuantityFillInputContextComposerTest.quantityFillContextForCompanionTests();List<BuyReplacementQuantityFillEvaluation> fills=new ArrayList<>();for(BuyReplacementQuantityFillInput x:input.getInputs())fills.add(new BuyReplacementQuantityFillEvaluation(x,0.6,1.2,100,bw,0.5,1.4,90,sw));
  // Direct immutable fixtures also exercise malformed evidence that upstream composers refuse.
  BuyReplacementQuantityFillEvaluationSet fs=new BuyReplacementQuantityFillEvaluationSet(input,fills,1100);List<BuyReplacementQuantityFillViableCandidate> viable=new ArrayList<>();List<BuyReplacementQuantityRoundTripCompletionEvaluation> complete=new ArrayList<>();for(BuyReplacementQuantityFillEvaluation e:fills){BuyReplacementQuantityFillViableCandidate v=new BuyReplacementQuantityFillViableCandidate(e);viable.add(v);complete.add(new BuyReplacementQuantityRoundTripCompletionEvaluation(v,0.3));}
  BuyReplacementQuantityFillViabilityAssessment a=new BuyReplacementQuantityFillViabilityAssessment(fs,BuyReplacementQuantityFillViabilityOutcome.VIABLE,fills,viable,1100);return new BuyReplacementQuantityDurationCalibrationInputContext(new BuyReplacementQuantityRoundTripCompletionEvaluationSet(a,complete,1100),false,multiplier,1100);
 }
}
