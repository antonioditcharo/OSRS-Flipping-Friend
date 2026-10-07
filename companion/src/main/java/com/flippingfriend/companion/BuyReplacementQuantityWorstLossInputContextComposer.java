package com.flippingfriend.companion;

import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.model.TaxCalculator;

/** Pure fail-closed binding to CandidateFactory's production risk-appetite loss-cut input. */
final class BuyReplacementQuantityWorstLossInputContextComposer {
    private final TaxCalculator tax;
    BuyReplacementQuantityWorstLossInputContextComposer(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityWorstLossInputContext compose(BuyReplacementQuantityExpectedProfitEvaluationSet source, long at) {
        if (source == null || tax == null) return null;
        try { return composeValid(source, at); }
        catch (NullPointerException | IllegalArgumentException | ArithmeticException malformed) { return null; }
    }
    private BuyReplacementQuantityWorstLossInputContext composeValid(BuyReplacementQuantityExpectedProfitEvaluationSet s, long at) {
        long previous = s.getEvaluatedAt();
        if (!fresh(previous, at) || s.getEvaluations() == null || s.getEvaluations().isEmpty()) return null;
        RiskAppetite appetite = RiskAppetite.forName(s.getAppetiteName());
        double loss = appetite.getLossCutPct();
        if (s.getAppetiteName() == null || !appetite.getName().equalsIgnoreCase(s.getAppetiteName())
                || !Double.isFinite(loss) || loss <= 0) return null;
        // Revalidate Package 3.68 at its original timestamp without refreshing authority.
        BuyReplacementQuantityExpectedProfitEvaluationSet verified = new BuyReplacementQuantityExpectedProfitEvaluator(tax)
                .evaluate(s.getSource(), previous);
        if (verified == null || verified.getEvaluations().size() != s.getEvaluations().size()) return null;
        for (int i = 0; i < s.getEvaluations().size(); i++) {
            BuyReplacementQuantityExpectedProfitEvaluation e = s.getEvaluations().get(i), v = verified.getEvaluations().get(i);
            if (!valid(e, v, s)) return null;
        }
        return new BuyReplacementQuantityWorstLossInputContext(s, loss, at);
    }
    private static boolean valid(BuyReplacementQuantityExpectedProfitEvaluation e, BuyReplacementQuantityExpectedProfitEvaluation v, BuyReplacementQuantityExpectedProfitEvaluationSet s) {
        return e != null && v != null && e.getSource() == v.getSource()
                && e.getQuantity() > 0 && e.getQuantity() <= s.getExactRemainderQuantity()
                && e.getFillableQuantity() == s.getExactRemainderQuantity()
                && e.getBuyPrice() > 0 && e.getSellPrice() > 0
                && e.getCompletedSaleNetProfit() == v.getCompletedSaleNetProfit()
                && Double.compare(e.getCompletedProbability(), v.getCompletedProbability()) == 0
                && Double.compare(e.getStrandedProbability(), v.getStrandedProbability()) == 0
                && Double.compare(e.getQuantityExpectedProfit(), v.getQuantityExpectedProfit()) == 0
                && Double.isFinite(e.getQuantityExpectedProfit());
    }
    private static boolean fresh(long observed, long at) {
        return observed >= 0 && at >= observed
                && at - observed <= CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
    }
}
