package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.model.TaxCalculator;
/**
 * Pure fail-closed check of one selected replacement against caller-supplied production portfolio
 * constraints, mirroring PortfolioOptimizer's admission test for a single candidate: a free slot,
 * capital within free coins, worst loss within the session loss budget, and committed-plus-new
 * item and group capital within their caps (zero cap means unlimited; an empty group is exempt).
 * Never authorizes, persists, presents, or activates.
 */
final class BuyReplacementQuantityCandidateFeasibilityChecker {
    private final TaxCalculator tax;
    BuyReplacementQuantityCandidateFeasibilityChecker(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityCandidateFeasibilityAssessment check(BuyReplacementQuantityCandidateSelection source,
            PortfolioConstraints constraints, long at) {
        if (source == null || constraints == null || tax == null) return null;
        try {
            return checkValid(source, constraints, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException
                | IndexOutOfBoundsException | ClassCastException malformed) {
            return null;
        }
    }
    private BuyReplacementQuantityCandidateFeasibilityAssessment checkValid(
            BuyReplacementQuantityCandidateSelection source, PortfolioConstraints constraints, long at) {
        long selectedAt = source.getSelectedAt();
        if (selectedAt < 0 || at < selectedAt || at >= source.getExpiresAt()) return null;
        BuyReplacementQuantityCandidateSelection verified = new BuyReplacementQuantityCandidateSelector(tax)
                .select(source.getSource(), source.getMinProfitPerFlip(), selectedAt);
        if (verified == null || verified.getSource() != source.getSource()
                || verified.getOutcome() != source.getOutcome()
                || verified.getSelected() != source.getSelected()
                || verified.getSelectedPosition() != source.getSelectedPosition()
                || verified.getSelectedSourceIndex() != source.getSelectedSourceIndex()
                || Double.compare(verified.getSelectedScore(), source.getSelectedScore()) != 0) return null;
        // The selection's profit floor must be the same setting these constraints carry.
        if (constraints.getMinProfitPerFlip() != source.getMinProfitPerFlip()) return null;
        BuyReplacementQuantityCandidateSet set = source.getSource();
        long maxAge = CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
        long rateAt = set.getSource().getSource().getRateEvaluatedAt();
        if (at - set.getLiveObservedAt() > maxAge || rateAt < 0 || at - rateAt > maxAge) return null;
        for (Long value : constraints.getCommittedByItem().values()) if (value == null || value < 0) return null;
        for (Long value : constraints.getCommittedByGroup().values()) if (value == null || value < 0) return null;
        if (source.getOutcome() != BuyReplacementQuantityCandidateSelectionOutcome.SELECTED) {
            return new BuyReplacementQuantityCandidateFeasibilityAssessment(source, constraints,
                    BuyReplacementQuantityCandidateFeasibilityOutcome.NO_SELECTION,
                    false, false, false, false, false, -1, -1, at);
        }
        PortfolioCandidate c = source.getSelected();
        long capital = c.getCapitalRequired();
        long worstLoss = c.getWorstLoss();
        if (capital <= 0 || worstLoss < 0) return null;
        String group = c.getGroup();
        long item = Math.addExact(constraints.getCommittedByItem().getOrDefault(c.getItemId(), 0L), capital);
        long groupTotal = Math.addExact(constraints.getCommittedByGroup().getOrDefault(group, 0L), capital);
        boolean slot = constraints.getFreeSlots() >= 1;
        boolean coins = capital <= constraints.getFreeCoins();
        boolean loss = worstLoss <= constraints.getSessionLossBudget();
        boolean itemOk = constraints.getPerItemCapitalCap() == 0 || item <= constraints.getPerItemCapitalCap();
        boolean groupOk = group.isEmpty() || constraints.getPerGroupCapitalCap() == 0
                || groupTotal <= constraints.getPerGroupCapitalCap();
        BuyReplacementQuantityCandidateFeasibilityOutcome outcome =
                !slot ? BuyReplacementQuantityCandidateFeasibilityOutcome.NO_FREE_SLOT
                : !coins ? BuyReplacementQuantityCandidateFeasibilityOutcome.INSUFFICIENT_COINS
                : !loss ? BuyReplacementQuantityCandidateFeasibilityOutcome.LOSS_BUDGET_EXCEEDED
                : !itemOk ? BuyReplacementQuantityCandidateFeasibilityOutcome.ITEM_EXPOSURE_EXCEEDED
                : !groupOk ? BuyReplacementQuantityCandidateFeasibilityOutcome.GROUP_EXPOSURE_EXCEEDED
                : BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE;
        return new BuyReplacementQuantityCandidateFeasibilityAssessment(source, constraints, outcome,
                slot, coins, loss, itemOk, groupOk, item, groupTotal, at);
    }
}
