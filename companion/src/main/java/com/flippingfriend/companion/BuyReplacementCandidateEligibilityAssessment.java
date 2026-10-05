package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable complete affordability evidence plus its ordered affordable-only view. */
final class BuyReplacementCandidateEligibilityAssessment
{
    private final BuyReplacementCandidateAffordabilitySet source;
    private final BuyReplacementCandidateEligibilityOutcome outcome;
    private final List<BuyReplacementCandidateAffordability> assessments;
    private final List<BuyReplacementAffordableCandidate> affordableCandidates;

    BuyReplacementCandidateEligibilityAssessment(BuyReplacementCandidateAffordabilitySet source,
        BuyReplacementCandidateEligibilityOutcome outcome,
        List<BuyReplacementCandidateAffordability> assessments,
        List<BuyReplacementAffordableCandidate> affordableCandidates)
    {
        this.source = source;
        this.outcome = outcome;
        this.assessments = Collections.unmodifiableList(new ArrayList<>(assessments));
        this.affordableCandidates = Collections.unmodifiableList(new ArrayList<>(affordableCandidates));
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
    long getEvaluatedAt() { return source.getEvaluatedAt(); }
    String getAppetiteName() { return source.getAppetiteName(); }
    double getEffectiveHorizonHours() { return source.getEffectiveHorizonHours(); }
    BuyReplacementCandidateEligibilityOutcome getOutcome() { return outcome; }
    List<BuyReplacementCandidateAffordability> getAssessments() { return assessments; }
    List<BuyReplacementAffordableCandidate> getAffordableCandidates() { return affordableCandidates; }
}
