package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Immutable non-activated replacement candidates for live-covered quantities; evidence, not a plan or authority. */
final class BuyReplacementQuantityCandidateSet {
    private final BuyReplacementQuantityLiveLimitAssessment source;
    private final List<PortfolioCandidate> candidates;
    private final List<Integer> sourceIndexes;
    private final long builtAt;
    BuyReplacementQuantityCandidateSet(BuyReplacementQuantityLiveLimitAssessment source,
            List<PortfolioCandidate> candidates, List<Integer> sourceIndexes, long builtAt) {
        this.source = source;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.sourceIndexes = Collections.unmodifiableList(new ArrayList<>(sourceIndexes));
        this.builtAt = builtAt;
    }
    BuyReplacementQuantityLiveLimitAssessment getSource() { return source; }
    List<PortfolioCandidate> getCandidates() { return candidates; }
    List<Integer> getSourceIndexes() { return sourceIndexes; }
    BuyReplacementQuantityLiveLimitOutcome getOutcome() { return source.getOutcome(); }
    int getItemId() { return source.getItemId(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    int getEffectiveBuyLimitRemaining() { return source.getEffectiveBuyLimitRemaining(); }
    long getExpiresAt() { return source.getExpiresAt(); }
    long getLiveObservedAt() { return source.getLiveObservedAt(); }
    long getAssessmentEvaluatedAt() { return source.getEvaluatedAt(); }
    long getBuiltAt() { return builtAt; }
}
