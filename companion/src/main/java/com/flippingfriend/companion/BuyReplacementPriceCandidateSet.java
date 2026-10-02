package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable ordered prices permitted for later exact-remainder evaluation. */
final class BuyReplacementPriceCandidateSet
{
    private final BuyReplacementPricingStrategyContext strategy;
    private final List<BuyReplacementPriceCandidate> candidates;

    BuyReplacementPriceCandidateSet(BuyReplacementPricingStrategyContext strategy,
        List<BuyReplacementPriceCandidate> candidates)
    {
        this.strategy = strategy;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    String getReadinessAssessmentId() { return strategy.getReadinessAssessmentId(); }
    String getReadinessPolicyVersion() { return strategy.getReadinessPolicyVersion(); }
    String getIntentId() { return strategy.getIntentId(); }
    String getOriginalOfferIdentity() { return strategy.getOriginalOfferIdentity(); }
    String getRecommendationId() { return strategy.getRecommendationId(); }
    int getSlot() { return strategy.getSlot(); }
    int getItemId() { return strategy.getItemId(); }
    String getItemName() { return strategy.getItemName(); }
    int getExactRemainderQuantity() { return strategy.getExactRemainderQuantity(); }
    int getHistoricalOriginalPrice() { return strategy.getHistoricalOriginalPrice(); }
    int getCurrentLowPrice() { return strategy.getCurrentLowPrice(); }
    int getCurrentHighPrice() { return strategy.getCurrentHighPrice(); }
    int getBuyLimitRemaining() { return strategy.getBuyLimitRemaining(); }
    long getSpendableCoins() { return strategy.getSpendableCoins(); }
    boolean isMembers() { return strategy.isMembers(); }
    long getIntentCreatedAt() { return strategy.getIntentCreatedAt(); }
    long getIntentExpiresAt() { return strategy.getIntentExpiresAt(); }
    long getAccountObservedAt() { return strategy.getAccountObservedAt(); }
    long getMarketObservedAt() { return strategy.getMarketObservedAt(); }
    long getInputObservedAt() { return strategy.getInputObservedAt(); }
    long getEvaluatedAt() { return strategy.getEvaluatedAt(); }
    String getAppetiteName() { return strategy.getAppetiteName(); }
    double getEffectiveHorizonHours() { return strategy.getEffectiveHorizonHours(); }
    List<BuyReplacementPriceCandidate> getCandidates() { return candidates; }
}
