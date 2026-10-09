package com.flippingfriend.companion;
import com.flippingfriend.model.TaxCalculator; import java.util.ArrayList; import java.util.List;
/** Pure fail-closed application of PortfolioCandidate expected-GP-per-slot-hour arithmetic. */
final class BuyReplacementQuantityExpectedGpPerSlotHourEvaluator {
    private final TaxCalculator tax;
    BuyReplacementQuantityExpectedGpPerSlotHourEvaluator(TaxCalculator tax) { this.tax=tax; }
    BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet evaluate(BuyReplacementQuantityKellyFractionEvaluationSet source,long at) { if(source==null||tax==null)return null; try{return evaluateValid(source,at);}catch(NullPointerException|IllegalArgumentException|ArithmeticException malformed){return null;} }
    private BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet evaluateValid(BuyReplacementQuantityKellyFractionEvaluationSet s,long at) {
        long previous=s.getEvaluatedAt();
        if(!fresh(previous,at)||s.getEvaluations()==null||s.getEvaluations().isEmpty())return null;
        BuyReplacementQuantityKellyFractionEvaluationSet verified=new BuyReplacementQuantityKellyFractionEvaluator(tax).evaluate(s.getSource(),previous);
        if(verified==null||verified.getEvaluations().size()!=s.getEvaluations().size())return null;
        List<BuyReplacementQuantityExpectedGpPerSlotHourEvaluation> out=new ArrayList<>(s.getEvaluations().size());
        for(int i=0;i<s.getEvaluations().size();i++){
            BuyReplacementQuantityKellyFractionEvaluation e=s.getEvaluations().get(i),v=verified.getEvaluations().get(i);
            if(!valid(e,v,s))return null;
            double rate=expectedGpPerSlotHour(e.getQuantityExpectedProfit(),e.getExpectedSlotHours());
            if(!Double.isFinite(rate))return null;
            out.add(new BuyReplacementQuantityExpectedGpPerSlotHourEvaluation(e,rate));
        }
        return new BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet(s,out,at);
    }
    static double expectedGpPerSlotHour(double expectedProfit,double expectedSlotHours) { return expectedProfit/expectedSlotHours; }
    private static boolean valid(BuyReplacementQuantityKellyFractionEvaluation e,BuyReplacementQuantityKellyFractionEvaluation v,BuyReplacementQuantityKellyFractionEvaluationSet s) {
        return e!=null&&v!=null&&e.getSource()!=null&&v.getSource()!=null
            &&e.getSource().getSource()==v.getSource().getSource()
            &&e.getQuantity()>0&&e.getQuantity()<=s.getExactRemainderQuantity()&&e.getFillableQuantity()==s.getExactRemainderQuantity()
            &&Double.isFinite(e.getQuantityExpectedProfit())&&Double.compare(e.getQuantityExpectedProfit(),v.getQuantityExpectedProfit())==0
            &&Double.isFinite(e.getExpectedSlotHours())&&e.getExpectedSlotHours()>=1.0/60.0&&Double.compare(e.getExpectedSlotHours(),v.getExpectedSlotHours())==0
            &&Double.isFinite(e.getQuantityKellyFraction())&&e.getQuantityKellyFraction()>=s.getMinimumKellyFraction()&&e.getQuantityKellyFraction()<=1
            &&Double.compare(e.getQuantityKellyFraction(),v.getQuantityKellyFraction())==0;
    }
    private static boolean fresh(long observed,long at) { return observed>=0&&at>=observed&&at-observed<=CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS; }
}
