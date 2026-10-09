package com.flippingfriend.companion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Immutable live buy-limit coverage evidence; not a candidate, selection, or authority. */
final class BuyReplacementQuantityLiveLimitAssessment {
    private final BuyReplacementQuantityCandidateCapacityInputContext source;
    private final BuyReplacementQuantityLiveLimitOutcome outcome;
    private final int liveBuyLimitRemaining;
    private final int effectiveBuyLimitRemaining;
    private final long liveObservedAt;
    private final List<Integer> coveredIndexes;
    private final List<BuyReplacementQuantityCandidateEconomicInput> coveredInputs;
    private final long evaluatedAt;
    BuyReplacementQuantityLiveLimitAssessment(BuyReplacementQuantityCandidateCapacityInputContext source,
            BuyReplacementQuantityLiveLimitOutcome outcome, int liveBuyLimitRemaining,
            int effectiveBuyLimitRemaining, long liveObservedAt, List<Integer> coveredIndexes,
            List<BuyReplacementQuantityCandidateEconomicInput> coveredInputs, long evaluatedAt) {
        this.source = source;
        this.outcome = outcome;
        this.liveBuyLimitRemaining = liveBuyLimitRemaining;
        this.effectiveBuyLimitRemaining = effectiveBuyLimitRemaining;
        this.liveObservedAt = liveObservedAt;
        this.coveredIndexes = Collections.unmodifiableList(new ArrayList<>(coveredIndexes));
        this.coveredInputs = Collections.unmodifiableList(new ArrayList<>(coveredInputs));
        this.evaluatedAt = evaluatedAt;
    }
    BuyReplacementQuantityCandidateCapacityInputContext getSource() { return source; }
    BuyReplacementQuantityLiveLimitOutcome getOutcome() { return outcome; }
    List<BuyReplacementQuantityCandidateEconomicInput> getInputs() { return source.getInputs(); }
    List<Integer> getCoveredIndexes() { return coveredIndexes; }
    List<BuyReplacementQuantityCandidateEconomicInput> getCoveredInputs() { return coveredInputs; }
    int getRetainedBuyLimitRemaining() { return source.getBuyLimitRemaining(); }
    int getLiveBuyLimitRemaining() { return liveBuyLimitRemaining; }
    int getEffectiveBuyLimitRemaining() { return effectiveBuyLimitRemaining; }
    long getLiveObservedAt() { return liveObservedAt; }
    int getItemId() { return source.getItemId(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    long getExpiresAt() { return source.getExpiresAt(); }
    long getCapacityComposedAt() { return source.getComposedAt(); }
    long getEvaluatedAt() { return evaluatedAt; }
}
