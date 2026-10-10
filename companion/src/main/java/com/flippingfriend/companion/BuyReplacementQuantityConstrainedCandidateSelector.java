package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Pure fail-closed choice of the best replacement candidate that fits the supplied production
 * constraints for the single slot being replaced. Applies the production optimizer's per-candidate
 * gates and its admission checks to every candidate, then keeps the highest expected GP per
 * slot-hour (earliest on ties), matching what the production optimizer selects for one free slot.
 * Never authorizes, persists, presents, or activates.
 */
final class BuyReplacementQuantityConstrainedCandidateSelector {
    private final TaxCalculator tax;
    BuyReplacementQuantityConstrainedCandidateSelector(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityConstrainedSelection select(BuyReplacementQuantityCandidateSet source,
            PortfolioConstraints constraints, long at) {
        if (source == null || constraints == null || tax == null) return null;
        try {
            return selectValid(source, constraints, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException
                | IndexOutOfBoundsException | ClassCastException malformed) {
            return null;
        }
    }
    private BuyReplacementQuantityConstrainedSelection selectValid(BuyReplacementQuantityCandidateSet source,
            PortfolioConstraints constraints, long at) {
        Map<Integer, Long> byItem = constraints.getCommittedByItem();
        Map<String, Long> byGroup = constraints.getCommittedByGroup();
        for (Long value : byItem.values()) if (value == null || value < 0) return null;
        for (Long value : byGroup.values()) if (value == null || value < 0) return null;
        // Reuses the Package 3.82 selector as the revalidation, timing, and freshness gate.
        BuyReplacementQuantityCandidateSelection unconstrained = new BuyReplacementQuantityCandidateSelector(tax)
                .select(source, constraints.getMinProfitPerFlip(), at);
        if (unconstrained == null) return null;
        boolean slot = constraints.getFreeSlots() >= 1;
        long floor = Math.max(1, constraints.getMinProfitPerFlip());
        List<PortfolioCandidate> candidates = source.getCandidates();
        List<BuyReplacementQuantityCandidateConstraintStatus> statuses = new ArrayList<>();
        int survivors = 0, notWorthDoing = 0, best = -1;
        double bestScore = Double.NaN;
        for (int k = 0; k < candidates.size(); k++) {
            PortfolioCandidate c = candidates.get(k);
            BuyReplacementQuantityCandidateConstraintStatus status;
            if (c.getNetProfit() < floor) status = BuyReplacementQuantityCandidateConstraintStatus.BELOW_MINIMUM;
            else if (c.getCapitalRequired() <= 0) status = BuyReplacementQuantityCandidateConstraintStatus.NO_CAPITAL;
            else if (c.getCompletionProbability() <= 0) status = BuyReplacementQuantityCandidateConstraintStatus.CANNOT_COMPLETE;
            else {
                survivors++;
                double score = c.expectedGpPerSlotHour();
                if (!Double.isFinite(score)) return null;
                if (score <= 0) {
                    notWorthDoing++;
                    status = BuyReplacementQuantityCandidateConstraintStatus.NOT_WORTH_DOING;
                } else {
                    status = admission(c, constraints, byItem, byGroup);
                    if (slot && status == BuyReplacementQuantityCandidateConstraintStatus.ADMISSIBLE
                            && (best < 0 || score > bestScore)) {
                        best = k;
                        bestScore = score;
                    }
                }
            }
            statuses.add(status);
        }
        BuyReplacementQuantityConstrainedSelectionOutcome outcome =
                !slot ? BuyReplacementQuantityConstrainedSelectionOutcome.NO_FREE_SLOT
                : best >= 0 ? BuyReplacementQuantityConstrainedSelectionOutcome.SELECTED
                : survivors == 0 || notWorthDoing == survivors
                        ? BuyReplacementQuantityConstrainedSelectionOutcome.NO_ELIGIBLE_CANDIDATE
                        : BuyReplacementQuantityConstrainedSelectionOutcome.PORTFOLIO_CONSTRAINT;
        if (outcome != BuyReplacementQuantityConstrainedSelectionOutcome.SELECTED) {
            return new BuyReplacementQuantityConstrainedSelection(source, constraints, outcome, null, -1, -1,
                    Double.NaN, statuses, at);
        }
        // Consistency with the unconstrained preference: never better than it, and identical when it fits.
        if (unconstrained.getOutcome() != BuyReplacementQuantityCandidateSelectionOutcome.SELECTED
                || bestScore > unconstrained.getSelectedScore()) return null;
        int top = unconstrained.getSelectedPosition();
        if (statuses.get(top) == BuyReplacementQuantityCandidateConstraintStatus.ADMISSIBLE && top != best) return null;
        return new BuyReplacementQuantityConstrainedSelection(source, constraints, outcome, candidates.get(best),
                best, source.getSourceIndexes().get(best), bestScore, statuses, at);
    }
    private static BuyReplacementQuantityCandidateConstraintStatus admission(PortfolioCandidate c,
            PortfolioConstraints limits, Map<Integer, Long> byItem, Map<String, Long> byGroup) {
        long capital = c.getCapitalRequired();
        String group = c.getGroup();
        long item = Math.addExact(byItem.getOrDefault(c.getItemId(), 0L), capital);
        long groupTotal = Math.addExact(byGroup.getOrDefault(group, 0L), capital);
        if (capital > limits.getFreeCoins()) return BuyReplacementQuantityCandidateConstraintStatus.INSUFFICIENT_COINS;
        if (c.getWorstLoss() > limits.getSessionLossBudget())
            return BuyReplacementQuantityCandidateConstraintStatus.LOSS_BUDGET_EXCEEDED;
        if (limits.getPerItemCapitalCap() != 0 && item > limits.getPerItemCapitalCap())
            return BuyReplacementQuantityCandidateConstraintStatus.ITEM_EXPOSURE_EXCEEDED;
        if (!group.isEmpty() && limits.getPerGroupCapitalCap() != 0 && groupTotal > limits.getPerGroupCapitalCap())
            return BuyReplacementQuantityCandidateConstraintStatus.GROUP_EXPOSURE_EXCEEDED;
        return BuyReplacementQuantityCandidateConstraintStatus.ADMISSIBLE;
    }
}
