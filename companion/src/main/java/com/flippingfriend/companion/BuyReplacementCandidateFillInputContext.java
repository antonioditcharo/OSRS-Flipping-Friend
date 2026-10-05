package com.flippingfriend.companion;

import com.flippingfriend.model.FillCurve;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable non-authorizing inputs prepared for later affordable-candidate fill evaluation. */
final class BuyReplacementCandidateFillInputContext
{
    private final BuyReplacementCandidateEligibilityAssessment source;
    private final List<BuyReplacementCandidateAffordability> assessments;
    private final List<BuyReplacementAffordableCandidate> affordableCandidates;
    private final FillCurve fillCurve;
    private final long historyObservedAt;
    private final long composedAt;

    BuyReplacementCandidateFillInputContext(BuyReplacementCandidateEligibilityAssessment source,
        List<BuyReplacementCandidateAffordability> assessments,
        List<BuyReplacementAffordableCandidate> affordableCandidates, FillCurve fillCurve,
        long historyObservedAt, long composedAt)
    {
        this.source = source;
        this.assessments = Collections.unmodifiableList(new ArrayList<>(assessments));
        this.affordableCandidates = Collections.unmodifiableList(new ArrayList<>(affordableCandidates));
        this.fillCurve = fillCurve;
        this.historyObservedAt = historyObservedAt;
        this.composedAt = composedAt;
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
    long getEligibilityEvaluatedAt() { return source.getEvaluatedAt(); }
    String getAppetiteName() { return source.getAppetiteName(); }
    double getEffectiveHorizonHours() { return source.getEffectiveHorizonHours(); }
    List<BuyReplacementCandidateAffordability> getAssessments() { return assessments; }
    List<BuyReplacementAffordableCandidate> getAffordableCandidates() { return affordableCandidates; }
    FillCurve getFillCurve() { return fillCurve; }
    long getHistoryObservedAt() { return historyObservedAt; }
    long getComposedAt() { return composedAt; }
}
