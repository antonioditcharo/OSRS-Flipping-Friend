package com.flippingfriend.companion;

/** Immutable, non-authorizing inputs for a later buy-replacement pricing policy. */
final class BuyReplacementPricingContext
{
    private final String readinessAssessmentId;
    private final String readinessPolicyVersion;
    private final String intentId;
    private final String originalOfferIdentity;
    private final String recommendationId;
    private final int slot;
    private final int itemId;
    private final String itemName;
    private final int exactRemainderQuantity;
    private final int historicalOriginalPrice;
    private final int currentLowPrice;
    private final int currentHighPrice;
    private final int buyLimitRemaining;
    private final long spendableCoins;
    private final boolean members;
    private final long intentCreatedAt;
    private final long intentExpiresAt;
    private final long accountObservedAt;
    private final long marketObservedAt;
    private final long inputObservedAt;
    private final long evaluatedAt;

    BuyReplacementPricingContext(String readinessAssessmentId, String readinessPolicyVersion,
        String intentId, String originalOfferIdentity, String recommendationId, int slot,
        int itemId, String itemName, int exactRemainderQuantity, int historicalOriginalPrice,
        int currentLowPrice, int currentHighPrice, int buyLimitRemaining, long spendableCoins,
        boolean members, long intentCreatedAt, long intentExpiresAt, long accountObservedAt,
        long marketObservedAt, long inputObservedAt, long evaluatedAt)
    {
        this.readinessAssessmentId = readinessAssessmentId;
        this.readinessPolicyVersion = readinessPolicyVersion;
        this.intentId = intentId;
        this.originalOfferIdentity = originalOfferIdentity;
        this.recommendationId = recommendationId;
        this.slot = slot;
        this.itemId = itemId;
        this.itemName = itemName;
        this.exactRemainderQuantity = exactRemainderQuantity;
        this.historicalOriginalPrice = historicalOriginalPrice;
        this.currentLowPrice = currentLowPrice;
        this.currentHighPrice = currentHighPrice;
        this.buyLimitRemaining = buyLimitRemaining;
        this.spendableCoins = spendableCoins;
        this.members = members;
        this.intentCreatedAt = intentCreatedAt;
        this.intentExpiresAt = intentExpiresAt;
        this.accountObservedAt = accountObservedAt;
        this.marketObservedAt = marketObservedAt;
        this.inputObservedAt = inputObservedAt;
        this.evaluatedAt = evaluatedAt;
    }

    String getReadinessAssessmentId() { return readinessAssessmentId; }
    String getReadinessPolicyVersion() { return readinessPolicyVersion; }
    String getIntentId() { return intentId; }
    String getOriginalOfferIdentity() { return originalOfferIdentity; }
    String getRecommendationId() { return recommendationId; }
    int getSlot() { return slot; }
    int getItemId() { return itemId; }
    String getItemName() { return itemName; }
    int getExactRemainderQuantity() { return exactRemainderQuantity; }
    int getHistoricalOriginalPrice() { return historicalOriginalPrice; }
    int getCurrentLowPrice() { return currentLowPrice; }
    int getCurrentHighPrice() { return currentHighPrice; }
    int getBuyLimitRemaining() { return buyLimitRemaining; }
    long getSpendableCoins() { return spendableCoins; }
    boolean isMembers() { return members; }
    long getIntentCreatedAt() { return intentCreatedAt; }
    long getIntentExpiresAt() { return intentExpiresAt; }
    long getAccountObservedAt() { return accountObservedAt; }
    long getMarketObservedAt() { return marketObservedAt; }
    long getInputObservedAt() { return inputObservedAt; }
    long getEvaluatedAt() { return evaluatedAt; }
}
