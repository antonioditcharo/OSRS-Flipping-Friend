package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.List;

/** Pure fail-closed three-outcome slot-time arithmetic, with no runtime consumer. */
final class BuyReplacementQuantityExpectedSlotOccupancyEvaluator {
    private static final double MIN_SLOT_HOURS = 1.0 / 60.0;

    BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet evaluate(BuyReplacementQuantityCalibratedDurationEvaluationSet source, long at) {
        if (source == null) return null;
        try {
            return evaluateValid(source, at);
        } catch (NullPointerException | IllegalArgumentException malformedEvidence) {
            return null;
        }
    }

    private BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet evaluateValid(BuyReplacementQuantityCalibratedDurationEvaluationSet source, long at) {
        long previous = source.getEvaluatedAt();
        if (previous < 0 || at < previous
                || at - previous > CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS
                || source.getEvaluations() == null || source.getEvaluations().isEmpty()) return null;
        // Revalidate at the original timestamp, never refresh upstream evidence or calibration choice.
        BuyReplacementQuantityCalibratedDurationEvaluationSet verified = new BuyReplacementQuantityCalibratedDurationEvaluator()
                .evaluate(source.getSource(), previous);
        if (verified == null || verified.getEvaluations().size() != source.getEvaluations().size()) return null;
        double horizon = source.getEffectiveHorizonHours();
        if (!Double.isFinite(horizon) || horizon <= 0) return null;
        List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> out = new ArrayList<>(source.getEvaluations().size());
        for (int i = 0; i < source.getEvaluations().size(); i++) {
            BuyReplacementQuantityCalibratedDurationEvaluation e = source.getEvaluations().get(i);
            BuyReplacementQuantityCalibratedDurationEvaluation v = verified.getEvaluations().get(i);
            if (e == null || e.getSource() != v.getSource()
                    || !nonnegative(e.getCalibratedBuyHours()) || !nonnegative(e.getCalibratedSellHours())
                    || Double.compare(e.getCalibratedBuyHours(), v.getCalibratedBuyHours()) != 0
                    || Double.compare(e.getCalibratedSellHours(), v.getCalibratedSellHours()) != 0) return null;
            double hours = expectedHours(e.getBuyProbability(), e.getSellProbability(),
                    e.getCalibratedBuyHours(), e.getCalibratedSellHours(), horizon);
            if (!Double.isFinite(hours) || hours < MIN_SLOT_HOURS) return null;
            out.add(new BuyReplacementQuantityExpectedSlotOccupancyEvaluation(e, hours));
        }
        return new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(source, out, at);
    }

    // Match both the production constructor horizon floor and the occupancy result floor.
    static double expectedHours(double buyProbability, double sellProbability,
            double buyHours, double sellHours, double horizonHours) {
        double horizon = Math.max(MIN_SLOT_HOURS, horizonHours);
        double completed = buyProbability * sellProbability;
        double stranded = buyProbability * (1 - sellProbability);
        double neverBought = 1 - buyProbability;
        double hours = neverBought * horizon
                + completed * (buyHours + sellHours)
                + stranded * (buyHours + horizon);
        return Math.max(MIN_SLOT_HOURS, hours);
    }
    private static boolean nonnegative(double value) { return Double.isFinite(value) && value >= 0; }
}
