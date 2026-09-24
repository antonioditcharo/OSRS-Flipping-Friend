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

    /** Restores one normalized durable projection row without replaying a transition. */
    public static OfferLifecycleProjection restore(OfferLifecycleState state, int slot,
        String offerIdentity, String sessionId, long lastSequence, long lastObservedAt,
        int itemId, String itemName, boolean buying, int price, int totalQuantity,
        int filledQuantity, long spent, String recommendationId)
    {
        if (state == null || slot < 0 || lastSequence < 0 || lastObservedAt < 0
            || itemId < 0 || price < 0 || totalQuantity < 0 || filledQuantity < 0
            || filledQuantity > totalQuantity || spent < 0)
        {
            throw new IllegalArgumentException("invalid durable offer projection");
        }
        return new OfferLifecycleProjection(state, slot, offerIdentity, sessionId, lastSequence,
            lastObservedAt, itemId, itemName, buying, price, totalQuantity, filledQuantity,
            spent, recommendationId);
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
