package com.flippingfriend.companion;

/** Immutable, identity-carrying input for one open-buy maintenance decision. */
final class BuyMaintenancePolicyContext
{
    private final String recommendationId;
    private final String offerIdentity;
    private final int slot;
    private final int itemId;
    private final int remainingQuantity;
    private final int offerPrice;
    private final int currentLowPrice;
    private final int currentHighPrice;
    private final int buyLimitRemaining;
    private final long offerObservedAt;
    private final long marketObservedAt;
    private final long decidedAt;
    private final boolean openBuy;

    BuyMaintenancePolicyContext(String recommendationId, String offerIdentity, int slot, int itemId,
        int remainingQuantity, int offerPrice, int currentLowPrice, int currentHighPrice,
        int buyLimitRemaining, long offerObservedAt, long marketObservedAt, long decidedAt,
        boolean openBuy)
    {
        this.recommendationId = recommendationId;
        this.offerIdentity = offerIdentity;
        this.slot = slot;
        this.itemId = itemId;
        this.remainingQuantity = remainingQuantity;
        this.offerPrice = offerPrice;
        this.currentLowPrice = currentLowPrice;
        this.currentHighPrice = currentHighPrice;
        this.buyLimitRemaining = buyLimitRemaining;
        this.offerObservedAt = offerObservedAt;
        this.marketObservedAt = marketObservedAt;
        this.decidedAt = decidedAt;
        this.openBuy = openBuy;
    }
    String getRecommendationId() { return recommendationId; }
    String getOfferIdentity() { return offerIdentity; }
    int getSlot() { return slot; }
    int getItemId() { return itemId; }
    int getRemainingQuantity() { return remainingQuantity; }
    int getOfferPrice() { return offerPrice; }
    int getCurrentLowPrice() { return currentLowPrice; }
    int getCurrentHighPrice() { return currentHighPrice; }
    int getBuyLimitRemaining() { return buyLimitRemaining; }
    long getOfferObservedAt() { return offerObservedAt; }
    long getMarketObservedAt() { return marketObservedAt; }
    long getDecidedAt() { return decidedAt; }
    boolean isOpenBuy() { return openBuy; }
}
