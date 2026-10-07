package com.flippingfriend.companion;

import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayList;
import java.util.List;

/** Pure fail-closed production exit arithmetic; no downstream authority or runtime consumer. */
final class BuyReplacementQuantityUnwindLossEvaluator {
    private final TaxCalculator tax;
    BuyReplacementQuantityUnwindLossEvaluator(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityUnwindLossEvaluationSet evaluate(BuyReplacementQuantityUnwindLossInputContext source, long at) {
        if (source == null || tax == null) return null;
        try { return evaluateValid(source, at); }
        catch (NullPointerException | IllegalArgumentException | ArithmeticException malformedEvidence) { return null; }
    }
    private BuyReplacementQuantityUnwindLossEvaluationSet evaluateValid(BuyReplacementQuantityUnwindLossInputContext s, long at) {
        long slotAt = s.getExpectedSlotOccupancyEvaluatedAt();
        if (!fresh(s.getComposedAt(), at) || !fresh(s.getMarketObservedAt(), at)
                || !fresh(slotAt, s.getComposedAt()) || s.getMarketObservedAt() > slotAt
                || s.getCurrentLowPrice() <= 0 || s.getBucketSeconds() <= 0
                || !Double.isFinite(s.getVolatility()) || s.getVolatility() < 0
                || !Double.isFinite(s.getEffectiveHorizonHours()) || s.getEffectiveHorizonHours() <= 0
                || s.getEvaluations() == null || s.getEvaluations().isEmpty()) return null;
        // Revalidate original slot evidence without refreshing its authority.
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet verified = new BuyReplacementQuantityExpectedSlotOccupancyEvaluator()
                .evaluate(s.getSource().getSource(), slotAt);
        if (verified == null || verified.getEvaluations().size() != s.getEvaluations().size()) return null;
        double buckets = Math.max(1.0, s.getEffectiveHorizonHours() * (3600.0 / Math.max(1, s.getBucketSeconds())));
        if (!Double.isFinite(buckets)) return null;
        double sigma = Math.min(0.2, s.getVolatility()) * Math.sqrt(buckets);
        double drift = Math.max(0.002, 0.25 * sigma);
        if (!Double.isFinite(drift)) return null;
        List<BuyReplacementQuantityUnwindLossEvaluation> out = new ArrayList<>(s.getEvaluations().size());
        for (int i = 0; i < s.getEvaluations().size(); i++) {
            BuyReplacementQuantityExpectedSlotOccupancyEvaluation e = s.getEvaluations().get(i), v = verified.getEvaluations().get(i);
            if (e == null || e.getSource() != v.getSource()
                    || Double.compare(e.getExpectedSlotHours(), v.getExpectedSlotHours()) != 0
                    || e.getQuantity() <= 0 || e.getQuantity() > s.getExactRemainderQuantity()
                    || e.getFillableQuantity() != s.getExactRemainderQuantity()) return null;
            int exit = Math.max(1, (int) Math.round(Math.min(e.getBuyPrice(), s.getCurrentLowPrice()) * (1 - drift)));
            int exitTax = tax.taxPerItem(s.getItemId(), exit);
            if (exitTax < 0 || exitTax > exit) return null;
            long loss = Math.max(0, (long) e.getBuyPrice() - ((long) exit - exitTax));
            long total = Math.multiplyExact(loss, (long) e.getQuantity());
            out.add(new BuyReplacementQuantityUnwindLossEvaluation(e, drift, exit, exitTax, loss, total));
        }
        return new BuyReplacementQuantityUnwindLossEvaluationSet(s, out, at);
    }
    private static boolean fresh(long observed, long at) {
        return observed >= 0 && at >= observed
                && at - observed <= CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
    }
}
