package com.flippingfriend.companion;

import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayList;
import java.util.List;

/** Pure fail-closed generated-quantity application of PortfolioCandidate expected-profit arithmetic. */
final class BuyReplacementQuantityExpectedProfitEvaluator {
    private final TaxCalculator tax;
    BuyReplacementQuantityExpectedProfitEvaluator(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityExpectedProfitEvaluationSet evaluate(BuyReplacementQuantityUnwindLossEvaluationSet source, long at) {
        if (source == null || tax == null) return null;
        try { return evaluateValid(source, at); }
        catch (NullPointerException | IllegalArgumentException | ArithmeticException malformed) { return null; }
    }
    private BuyReplacementQuantityExpectedProfitEvaluationSet evaluateValid(BuyReplacementQuantityUnwindLossEvaluationSet s, long at) {
        long previous = s.getEvaluatedAt();
        if (!fresh(previous, at) || s.getEvaluations() == null || s.getEvaluations().isEmpty()) return null;
        // Revalidate Package 3.67 at its original timestamp without refreshing authority.
        BuyReplacementQuantityUnwindLossEvaluationSet verified = new BuyReplacementQuantityUnwindLossEvaluator(tax).evaluate(s.getSource(), previous);
        if (verified == null || verified.getEvaluations().size() != s.getEvaluations().size()) return null;
        List<BuyReplacementQuantityExpectedProfitEvaluation> out = new ArrayList<>(s.getEvaluations().size());
        for (int i = 0; i < s.getEvaluations().size(); i++) {
            BuyReplacementQuantityUnwindLossEvaluation e = s.getEvaluations().get(i), v = verified.getEvaluations().get(i);
            if (!valid(e, v, s)) return null;
            long netProfit = Math.multiplyExact(tax.netMarginPerItem(s.getItemId(), e.getBuyPrice(), e.getSellPrice()), (long)e.getQuantity());
            // Require injected tax behavior to remain internally coherent, including exemptions.
            if (netProfit != tax.netProfit(s.getItemId(), e.getBuyPrice(), e.getSellPrice(), e.getQuantity())) return null;
            double completed = e.getCompletionProbability();
            double stranded = e.getBuyProbability() * (1 - e.getSellProbability());
            double expected = completed * netProfit - stranded * e.getTotalUnwindLoss();
            if (!finiteProbability(completed) || !finiteProbability(stranded) || !Double.isFinite(expected)) return null;
            out.add(new BuyReplacementQuantityExpectedProfitEvaluation(e, netProfit, completed, stranded, expected));
        }
        return new BuyReplacementQuantityExpectedProfitEvaluationSet(s, out, at);
    }
    private static boolean valid(BuyReplacementQuantityUnwindLossEvaluation e, BuyReplacementQuantityUnwindLossEvaluation v, BuyReplacementQuantityUnwindLossEvaluationSet s) {
        return e != null && v != null && e.getSource() == v.getSource()
                && e.getQuantity() > 0 && e.getQuantity() <= s.getExactRemainderQuantity()
                && e.getFillableQuantity() == s.getExactRemainderQuantity()
                && e.getBuyPrice() > 0 && e.getSellPrice() > 0
                && finiteProbability(e.getBuyProbability()) && finiteProbability(e.getSellProbability())
                && Double.compare(e.getCompletionProbability(), e.getBuyProbability() * e.getSellProbability()) == 0
                && Double.compare(e.getDriftFraction(), v.getDriftFraction()) == 0
                && e.getExitPrice() == v.getExitPrice() && e.getExitTaxPerItem() == v.getExitTaxPerItem()
                && e.getLossPerItem() == v.getLossPerItem() && e.getTotalUnwindLoss() == v.getTotalUnwindLoss()
                && e.getTotalUnwindLoss() >= 0;
    }
    private static boolean finiteProbability(double p) { return Double.isFinite(p) && p >= 0 && p <= 1; }
    private static boolean fresh(long observed, long at) {
        return observed >= 0 && at >= observed
                && at - observed <= CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
    }
}
