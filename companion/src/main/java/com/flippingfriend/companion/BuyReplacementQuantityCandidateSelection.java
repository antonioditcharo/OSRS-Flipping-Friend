package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
/** Immutable single-slot replacement preference evidence; not a plan, decision, or authority. */
final class BuyReplacementQuantityCandidateSelection {
    private final BuyReplacementQuantityCandidateSet source;
    private final BuyReplacementQuantityCandidateSelectionOutcome outcome;
    private final PortfolioCandidate selected;
    private final int selectedPosition;
    private final int selectedSourceIndex;
    private final double selectedScore;
    private final long minProfitPerFlip;
    private final int belowMinimum;
    private final int noCapital;
    private final int cannotComplete;
    private final int notWorthDoing;
    private final long selectedAt;
    BuyReplacementQuantityCandidateSelection(BuyReplacementQuantityCandidateSet source,
            BuyReplacementQuantityCandidateSelectionOutcome outcome, PortfolioCandidate selected,
            int selectedPosition, int selectedSourceIndex, double selectedScore, long minProfitPerFlip,
            int belowMinimum, int noCapital, int cannotComplete, int notWorthDoing, long selectedAt) {
        this.source = source;
        this.outcome = outcome;
        this.selected = selected;
        this.selectedPosition = selectedPosition;
        this.selectedSourceIndex = selectedSourceIndex;
        this.selectedScore = selectedScore;
        this.minProfitPerFlip = minProfitPerFlip;
        this.belowMinimum = belowMinimum;
        this.noCapital = noCapital;
        this.cannotComplete = cannotComplete;
        this.notWorthDoing = notWorthDoing;
        this.selectedAt = selectedAt;
    }
    BuyReplacementQuantityCandidateSet getSource() { return source; }
    BuyReplacementQuantityCandidateSelectionOutcome getOutcome() { return outcome; }
    /** Null unless the outcome is SELECTED. */
    PortfolioCandidate getSelected() { return selected; }
    /** Position in the candidate set, or -1. */
    int getSelectedPosition() { return selectedPosition; }
    /** Original Package 3.78 input index, or -1. */
    int getSelectedSourceIndex() { return selectedSourceIndex; }
    /** Expected GP per slot-hour of the selected candidate, or NaN. */
    double getSelectedScore() { return selectedScore; }
    long getMinProfitPerFlip() { return minProfitPerFlip; }
    int getBelowMinimumCount() { return belowMinimum; }
    int getNoCapitalCount() { return noCapital; }
    int getCannotCompleteCount() { return cannotComplete; }
    int getNotWorthDoingCount() { return notWorthDoing; }
    int getCandidateCount() { return source.getCandidates().size(); }
    long getExpiresAt() { return source.getExpiresAt(); }
    long getSelectedAt() { return selectedAt; }
}
