package com.flippingfriend.core;

/** Immutable current-state view produced by {@link OfferLifecycleReducer}. */
public final class OfferLifecycleProjection
{
    private final OfferLifecycleState state;
    private final int slot;
    private final String offerIdentity;
    private final String sessionId;
    private final long lastSequence;
    private final long lastObservedAt;
    private final int itemId;
    private final String itemName;
    private final boolean buying;
    private final int price;
    private final int totalQuantity;
    private final int filledQuantity;
    private final long spent;
    private final String recommendationId;

    OfferLifecycleProjection(OfferLifecycleState state, int slot, String offerIdentity,
        String sessionId, long lastSequence, long lastObservedAt, int itemId, String itemName,
        boolean buying, int price, int totalQuantity, int filledQuantity, long spent,
        String recommendationId)
    {
        this.state = state;
        this.slot = slot;
        this.offerIdentity = offerIdentity;
        this.sessionId = sessionId;
        this.lastSequence = lastSequence;
        this.lastObservedAt = lastObservedAt;
        this.itemId = itemId;
        this.itemName = itemName;
        this.buying = buying;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.filledQuantity = filledQuantity;
        this.spent = spent;
        this.recommendationId = recommendationId;
    }

    public OfferLifecycleState getState() { return state; }
    public int getSlot() { return slot; }
    public String getOfferIdentity() { return offerIdentity; }
    public String getSessionId() { return sessionId; }
    public long getLastSequence() { return lastSequence; }
    public long getLastObservedAt() { return lastObservedAt; }
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public boolean isBuying() { return buying; }
    public int getPrice() { return price; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getFilledQuantity() { return filledQuantity; }
    public long getSpent() { return spent; }
    public String getRecommendationId() { return recommendationId; }
}
