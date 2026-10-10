package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Immutable best-fitting single-slot replacement evidence; not a plan, decision, or authority. */
final class BuyReplacementQuantityConstrainedSelection {
    private final BuyReplacementQuantityCandidateSet source;
    private final PortfolioConstraints constraints;
    private final BuyReplacementQuantityConstrainedSelectionOutcome outcome;
    private final PortfolioCandidate selected;
    private final int selectedPosition;
    private final int selectedSourceIndex;
    private final double selectedScore;
    private final List<BuyReplacementQuantityCandidateConstraintStatus> statuses;
    private final long selectedAt;
    BuyReplacementQuantityConstrainedSelection(BuyReplacementQuantityCandidateSet source,
            PortfolioConstraints constraints, BuyReplacementQuantityConstrainedSelectionOutcome outcome,
            PortfolioCandidate selected, int selectedPosition, int selectedSourceIndex, double selectedScore,
            List<BuyReplacementQuantityCandidateConstraintStatus> statuses, long selectedAt) {
        this.source = source;
        this.constraints = constraints;
        this.outcome = outcome;
        this.selected = selected;
        this.selectedPosition = selectedPosition;
        this.selectedSourceIndex = selectedSourceIndex;
        this.selectedScore = selectedScore;
        this.statuses = Collections.unmodifiableList(new ArrayList<>(statuses));
        this.selectedAt = selectedAt;
    }
    BuyReplacementQuantityCandidateSet getSource() { return source; }
    PortfolioConstraints getConstraints() { return constraints; }
    BuyReplacementQuantityConstrainedSelectionOutcome getOutcome() { return outcome; }
    /** Null unless the outcome is SELECTED. */
    PortfolioCandidate getSelected() { return selected; }
    /** Position in the candidate set, or -1. */
    int getSelectedPosition() { return selectedPosition; }
    /** Original Package 3.78 input index, or -1. */
    int getSelectedSourceIndex() { return selectedSourceIndex; }
    /** Expected GP per slot-hour of the selected candidate, or NaN. */
    double getSelectedScore() { return selectedScore; }
    /** One status per candidate, in candidate-set order. */
    List<BuyReplacementQuantityCandidateConstraintStatus> getStatuses() { return statuses; }
    long getExpiresAt() { return source.getExpiresAt(); }
    long getSelectedAt() { return selectedAt; }
}
