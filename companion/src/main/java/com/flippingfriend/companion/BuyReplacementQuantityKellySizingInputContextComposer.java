package com.flippingfriend.companion;
import com.flippingfriend.model.TaxCalculator; import java.util.ArrayList; import java.util.List;
/** Pure fail-closed binding of Package 3.70 evidence to production Kelly inputs. */
final class BuyReplacementQuantityKellySizingInputContextComposer {
    private static final double KELLY_SHARE=0.35, MIN_KELLY_FRACTION=0.1;
    private static final double[] SIZE_GRID={0.5,1.0,2.0,4.0,8.0};
    private final TaxCalculator tax;
    BuyReplacementQuantityKellySizingInputContextComposer(TaxCalculator tax) { this.tax=tax; }
    BuyReplacementQuantityKellySizingInputContext compose(BuyReplacementQuantityWorstLossEvaluationSet source,long at) {
        if(source==null||tax==null)return null;
        try{return composeValid(source,at);}catch(NullPointerException|IllegalArgumentException|ArithmeticException malformed){return null;}
    }
    private BuyReplacementQuantityKellySizingInputContext composeValid(BuyReplacementQuantityWorstLossEvaluationSet s,long at) {
        long previous=s.getEvaluatedAt();
        if(!fresh(previous,at)||s.getEvaluations()==null||s.getEvaluations().isEmpty())return null;
        // Revalidate Package 3.70 at its original timestamp without refreshing authority.
        BuyReplacementQuantityWorstLossEvaluationSet verified=new BuyReplacementQuantityWorstLossEvaluator(tax).evaluate(s.getSource(),previous);
        if(verified==null||verified.getEvaluations().size()!=s.getEvaluations().size())return null;
        List<BuyReplacementQuantityKellySizingInput> out=new ArrayList<>(s.getEvaluations().size());
        for(int i=0;i<s.getEvaluations().size();i++){
            BuyReplacementQuantityWorstLossEvaluation e=s.getEvaluations().get(i),v=verified.getEvaluations().get(i);
            if(!valid(e,v,s))return null;
            long profit=e.getCompletedSaleNetProfit(), unwind=e.getTotalUnwindLoss(), worst=e.getQuantityWorstLoss();
            double p=e.getCompletionProbability(); double odds=unwind>0?(double)profit/unwind:profit;
            if(!Double.isFinite(odds))return null;
            out.add(new BuyReplacementQuantityKellySizingInput(e,profit,unwind,worst,p,odds));
        }
        return new BuyReplacementQuantityKellySizingInputContext(s,out,KELLY_SHARE,MIN_KELLY_FRACTION,SIZE_GRID,at);
    }
    private static boolean valid(BuyReplacementQuantityWorstLossEvaluation e,BuyReplacementQuantityWorstLossEvaluation v,BuyReplacementQuantityWorstLossEvaluationSet s) {
        return e!=null&&v!=null&&e.getSource()==v.getSource()&&e.getQuantity()>0&&e.getQuantity()<=s.getExactRemainderQuantity()
            &&e.getFillableQuantity()==s.getExactRemainderQuantity()&&e.getBuyPrice()>0&&e.getSellPrice()>0
            &&e.getTotalUnwindLoss()>=0&&e.getQuantityWorstLoss()>=1
            &&Double.isFinite(e.getCompletionProbability())&&e.getCompletionProbability()>0&&e.getCompletionProbability()<=1
            &&e.getCompletedSaleNetProfit()==v.getCompletedSaleNetProfit()
            &&e.getTotalUnwindLoss()==v.getTotalUnwindLoss()&&e.getQuantityWorstLoss()==v.getQuantityWorstLoss()
            &&Double.compare(e.getCompletionProbability(),v.getCompletionProbability())==0
            &&Double.compare(e.getQuantityExpectedProfit(),v.getQuantityExpectedProfit())==0;
    }
    private static boolean fresh(long observed,long at){return observed>=0&&at>=observed&&at-observed<=CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;}
}
