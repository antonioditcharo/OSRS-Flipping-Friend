package com.flippingfriend.companion;
import com.flippingfriend.model.RiskAppetite; import com.flippingfriend.model.TaxCalculator; import java.util.ArrayList; import java.util.List;
/** Pure fail-closed generated-quantity application of CandidateFactory worst-loss arithmetic. */
final class BuyReplacementQuantityWorstLossEvaluator {
    private final TaxCalculator tax;
    BuyReplacementQuantityWorstLossEvaluator(TaxCalculator tax) { this.tax=tax; }
    BuyReplacementQuantityWorstLossEvaluationSet evaluate(BuyReplacementQuantityWorstLossInputContext source,long at) { if(source==null||tax==null)return null; try{return evaluateValid(source,at);}catch(NullPointerException|IllegalArgumentException|ArithmeticException malformed){return null;} }
    private BuyReplacementQuantityWorstLossEvaluationSet evaluateValid(BuyReplacementQuantityWorstLossInputContext s,long at) {
        long previous=s.getComposedAt(); if(!fresh(previous,at)||s.getEvaluations()==null||s.getEvaluations().isEmpty())return null;
        RiskAppetite appetite=RiskAppetite.forName(s.getAppetiteName()); double loss=appetite.getLossCutPct();
        if(s.getAppetiteName()==null||!appetite.getName().equalsIgnoreCase(s.getAppetiteName())||!Double.isFinite(loss)||loss<=0||Double.compare(loss,s.getLossCutPct())!=0)return null;
        // Revalidate Package 3.69 at its original timestamp without refreshing authority.
        BuyReplacementQuantityWorstLossInputContext verified=new BuyReplacementQuantityWorstLossInputContextComposer(tax).compose(s.getSource(),previous);
        if(verified==null||verified.getEvaluations().size()!=s.getEvaluations().size())return null;
        List<BuyReplacementQuantityWorstLossEvaluation> out=new ArrayList<>(s.getEvaluations().size());
        for(int i=0;i<s.getEvaluations().size();i++){ BuyReplacementQuantityExpectedProfitEvaluation e=s.getEvaluations().get(i),v=verified.getEvaluations().get(i); if(!valid(e,v,s))return null;
            double raw=e.getBuyPrice()*loss*e.getQuantity(); if(!Double.isFinite(raw)||raw<0||raw>Long.MAX_VALUE)return null;
            long worst=Math.max(1,(long)raw); out.add(new BuyReplacementQuantityWorstLossEvaluation(e,loss,worst)); }
        return new BuyReplacementQuantityWorstLossEvaluationSet(s,out,at);
    }
    private static boolean valid(BuyReplacementQuantityExpectedProfitEvaluation e,BuyReplacementQuantityExpectedProfitEvaluation v,BuyReplacementQuantityWorstLossInputContext s) { return e!=null&&v!=null&&e==v&&e.getQuantity()>0&&e.getQuantity()<=s.getExactRemainderQuantity()&&e.getFillableQuantity()==s.getExactRemainderQuantity()&&e.getBuyPrice()>0&&e.getSellPrice()>0&&e.getCompletedSaleNetProfit()==v.getCompletedSaleNetProfit()&&Double.compare(e.getCompletedProbability(),v.getCompletedProbability())==0&&Double.compare(e.getStrandedProbability(),v.getStrandedProbability())==0&&Double.compare(e.getQuantityExpectedProfit(),v.getQuantityExpectedProfit())==0&&Double.isFinite(e.getQuantityExpectedProfit()); }
    private static boolean fresh(long observed,long at){return observed>=0&&at>=observed&&at-observed<=CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;}
}
