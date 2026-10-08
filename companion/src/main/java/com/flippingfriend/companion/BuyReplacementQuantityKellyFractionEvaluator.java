package com.flippingfriend.companion;
import com.flippingfriend.model.TaxCalculator; import java.util.ArrayList; import java.util.List;
/** Pure fail-closed generated-quantity application of CandidateFactory Kelly arithmetic. */
final class BuyReplacementQuantityKellyFractionEvaluator {
    private final TaxCalculator tax;
    BuyReplacementQuantityKellyFractionEvaluator(TaxCalculator tax) { this.tax=tax; }
    BuyReplacementQuantityKellyFractionEvaluationSet evaluate(BuyReplacementQuantityKellySizingInputContext source,long at) { if(source==null||tax==null)return null; try{return evaluateValid(source,at);}catch(NullPointerException|IllegalArgumentException|ArithmeticException malformed){return null;} }
    private BuyReplacementQuantityKellyFractionEvaluationSet evaluateValid(BuyReplacementQuantityKellySizingInputContext s,long at) {
        long previous=s.getComposedAt();
        if(!fresh(previous,at)||s.getInputs()==null||s.getInputs().isEmpty()||!constants(s))return null;
        BuyReplacementQuantityKellySizingInputContext verified=new BuyReplacementQuantityKellySizingInputContextComposer(tax).compose(s.getSource(),previous);
        if(verified==null||verified.getInputs().size()!=s.getInputs().size())return null;
        List<BuyReplacementQuantityKellyFractionEvaluation> out=new ArrayList<>(s.getInputs().size());
        for(int index=0;index<s.getInputs().size();index++){
            BuyReplacementQuantityKellySizingInput input=s.getInputs().get(index),check=verified.getInputs().get(index);
            if(!valid(input,check,s))return null;
            double fraction=kellyFraction(input.getCompletionProbability(),input.getPayoffOdds(),s.getKellyShare(),s.getMinimumKellyFraction());
            if(!Double.isFinite(fraction)||fraction<s.getMinimumKellyFraction()||fraction>1)return null;
            out.add(new BuyReplacementQuantityKellyFractionEvaluation(input,fraction));
        }
        return new BuyReplacementQuantityKellyFractionEvaluationSet(s,out,at);
    }
    static double kellyFraction(double p,double b,double share,double minimum) {
        if(b<=0||p<=0)return minimum;
        return Math.max(minimum,Math.min(1.0,share*((p*b-(1.0-p))/b)));
    }
    private static boolean constants(BuyReplacementQuantityKellySizingInputContext s) {
        if(!Double.isFinite(s.getKellyShare())||s.getKellyShare()<=0||!Double.isFinite(s.getMinimumKellyFraction())||s.getMinimumKellyFraction()<=0||s.getMinimumKellyFraction()>1)return false;
        double[] grid=s.getSizeGrid(); double[] expected={0.5,1.0,2.0,4.0,8.0};
        if(grid==null||grid.length!=expected.length)return false;
        for(int i=0;i<grid.length;i++)if(Double.compare(grid[i],expected[i])!=0)return false;
        return Double.compare(s.getKellyShare(),0.35)==0&&Double.compare(s.getMinimumKellyFraction(),0.1)==0;
    }
    private static boolean valid(BuyReplacementQuantityKellySizingInput e,BuyReplacementQuantityKellySizingInput v,BuyReplacementQuantityKellySizingInputContext s) {
        return e!=null&&v!=null&&e.getSource()!=null&&v.getSource()!=null
            &&e.getSource().getSource()==v.getSource().getSource()
            &&e.getQuantity()>0&&e.getQuantity()<=s.getExactRemainderQuantity()&&e.getFillableQuantity()==s.getExactRemainderQuantity()
            &&e.getQuantityNetProfit()==v.getQuantityNetProfit()&&e.getQuantityUnwindLoss()==v.getQuantityUnwindLoss()
            &&e.getQuantityWorstLoss()==v.getQuantityWorstLoss()&&e.getQuantityWorstLoss()>=1
            &&Double.isFinite(e.getCompletionProbability())&&e.getCompletionProbability()>0&&e.getCompletionProbability()<=1
            &&Double.compare(e.getCompletionProbability(),v.getCompletionProbability())==0
            &&Double.isFinite(e.getPayoffOdds())&&Double.compare(e.getPayoffOdds(),v.getPayoffOdds())==0;
    }
    private static boolean fresh(long observed,long at) { return observed>=0&&at>=observed&&at-observed<=CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS; }
}
