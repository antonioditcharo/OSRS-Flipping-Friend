package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable complete fill evidence for every preserved affordable replacement candidate. */
final class BuyReplacementCandidateFillEvaluationSet
{
    private final BuyReplacementCandidateFillInputContext source;
    private final List<BuyReplacementCandidateAffordability> assessments;
    private final List<BuyReplacementAffordableCandidate> affordableCandidates;
    private final List<BuyReplacementCandidateFillEvaluation> evaluations;
    private final long evaluatedAt;

    BuyReplacementCandidateFillEvaluationSet(BuyReplacementCandidateFillInputContext source,
        List<BuyReplacementCandidateAffordability> assessments,
        List<BuyReplacementAffordableCandidate> affordableCandidates,
        List<BuyReplacementCandidateFillEvaluation> evaluations, long evaluatedAt)
    {
        this.source = source;
        this.assessments = Collections.unmodifiableList(new ArrayList<>(assessments));
        this.affordableCandidates = Collections.unmodifiableList(new ArrayList<>(affordableCandidates));
        this.evaluations = Collections.unmodifiableList(new ArrayList<>(evaluations));
        this.evaluatedAt = evaluatedAt;
    }

    String getReadinessAssessmentId() { return source.getReadinessAssessmentId(); }
    String getReadinessPolicyVersion() { return source.getReadinessPolicyVersion(); }
    String getIntentId() { return source.getIntentId(); }
    String getOriginalOfferIdentity() { return source.getOriginalOfferIdentity(); }
    String getRecommendationId() { return source.getRecommendationId(); }
    int getSlot() { return source.getSlot(); }
    int getItemId() { return source.getItemId(); }
    String getItemName() { return source.getItemName(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    int getHistoricalOriginalPrice() { return source.getHistoricalOriginalPrice(); }
    int getCurrentLowPrice() { return source.getCurrentLowPrice(); }
    int getCurrentHighPrice() { return source.getCurrentHighPrice(); }
    int getBuyLimitRemaining() { return source.getBuyLimitRemaining(); }
    long getSpendableCoins() { return source.getSpendableCoins(); }
    boolean isMembers() { return source.isMembers(); }
    long getIntentCreatedAt() { return source.getIntentCreatedAt(); }
    long getIntentExpiresAt() { return source.getIntentExpiresAt(); }
    long getAccountObservedAt() { return source.getAccountObservedAt(); }
    long getMarketObservedAt() { return source.getMarketObservedAt(); }
    long getInputObservedAt() { return source.getInputObservedAt(); }
    long getEligibilityEvaluatedAt() { return source.getEligibilityEvaluatedAt(); }
    long getHistoryObservedAt() { return source.getHistoryObservedAt(); }
    long getFillInputComposedAt() { return source.getComposedAt(); }
    String getAppetiteName() { return source.getAppetiteName(); }
    double getEffectiveHorizonHours() { return source.getEffectiveHorizonHours(); }
    List<BuyReplacementCandidateAffordability> getAssessments() { return assessments; }
    List<BuyReplacementAffordableCandidate> getAffordableCandidates() { return affordableCandidates; }
    List<BuyReplacementCandidateFillEvaluation> getEvaluations() { return evaluations; }
    long getEvaluatedAt() { return evaluatedAt; }
}
