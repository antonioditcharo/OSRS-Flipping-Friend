package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioConstraints;
/** Immutable account-constraint feasibility evidence for one selected replacement; not a plan or authority. */
final class BuyReplacementQuantityCandidateFeasibilityAssessment {
    private final BuyReplacementQuantityCandidateSelection source;
    private final PortfolioConstraints constraints;
    private final BuyReplacementQuantityCandidateFeasibilityOutcome outcome;
    private final boolean slotAvailable;
    private final boolean coinsAllowed;
    private final boolean lossAllowed;
    private final boolean itemAllowed;
    private final boolean groupAllowed;
    private final long itemCapitalAfter;
    private final long groupCapitalAfter;
    private final long assessedAt;
    BuyReplacementQuantityCandidateFeasibilityAssessment(BuyReplacementQuantityCandidateSelection source,
            PortfolioConstraints constraints, BuyReplacementQuantityCandidateFeasibilityOutcome outcome,
            boolean slotAvailable, boolean coinsAllowed, boolean lossAllowed, boolean itemAllowed,
            boolean groupAllowed, long itemCapitalAfter, long groupCapitalAfter, long assessedAt) {
        this.source = source;
        this.constraints = constraints;
        this.outcome = outcome;
        this.slotAvailable = slotAvailable;
        this.coinsAllowed = coinsAllowed;
        this.lossAllowed = lossAllowed;
        this.itemAllowed = itemAllowed;
        this.groupAllowed = groupAllowed;
        this.itemCapitalAfter = itemCapitalAfter;
        this.groupCapitalAfter = groupCapitalAfter;
        this.assessedAt = assessedAt;
    }
    BuyReplacementQuantityCandidateSelection getSource() { return source; }
    PortfolioConstraints getConstraints() { return constraints; }
    BuyReplacementQuantityCandidateFeasibilityOutcome getOutcome() { return outcome; }
    boolean isFeasible() { return outcome == BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE; }
    boolean isSlotAvailable() { return slotAvailable; }
    boolean isCoinsAllowed() { return coinsAllowed; }
    boolean isLossAllowed() { return lossAllowed; }
    boolean isItemAllowed() { return itemAllowed; }
    boolean isGroupAllowed() { return groupAllowed; }
    /** Already-committed item capital plus the selected candidate's capital, or -1 without a selection. */
    long getItemCapitalAfter() { return itemCapitalAfter; }
    /** Already-committed group capital plus the selected candidate's capital, or -1 without a selection. */
    long getGroupCapitalAfter() { return groupCapitalAfter; }
    long getExpiresAt() { return source.getExpiresAt(); }
    long getAssessedAt() { return assessedAt; }
}
