package com.flippingfriend.core;

/** Immutable, versioned identity link across cancel, collect, and possible replacement. */
public final class BuyReplacementIntent
{
    public static final String SCHEMA_VERSION = "1";
    private final String intentId;
    private final String originalOfferIdentity;
    private final String recommendationId;
    private final int slot;
    private final int itemId;
    private final String itemName;
    private final int originalPrice;
    private final int originalQuantity;
    private final int filledQuantity;
    private final int remainderQuantity;
    private final long createdAt;
    private final long expiresAt;
    private final BuyReplacementIntentState state;
    private final String reasonCode;

    private BuyReplacementIntent(String intentId, String offerIdentity, String recommendationId,
        int slot, int itemId, String itemName, int originalPrice, int originalQuantity,
        int filledQuantity, long createdAt, long expiresAt, BuyReplacementIntentState state,
        String reasonCode)
    {
        if (blank(intentId) || blank(offerIdentity) || blank(recommendationId) || slot < 0
            || itemId <= 0 || blank(itemName) || originalPrice <= 0 || originalQuantity <= 0
            || filledQuantity < 0 || filledQuantity >= originalQuantity || createdAt < 0
            || expiresAt <= createdAt || state == null || blank(reasonCode))
            throw new IllegalArgumentException("invalid buy replacement intent");
        this.intentId = intentId;
        this.originalOfferIdentity = offerIdentity;
        this.recommendationId = recommendationId;
        this.slot = slot;
        this.itemId = itemId;
        this.itemName = itemName;
        this.originalPrice = originalPrice;
        this.originalQuantity = originalQuantity;
        this.filledQuantity = filledQuantity;
        this.remainderQuantity = originalQuantity - filledQuantity;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.state = state;
        this.reasonCode = reasonCode;
    }

    public static BuyReplacementIntent cancelAuthorized(String intentId, String offerIdentity,
        String recommendationId, int slot, int itemId, String itemName, int originalPrice,
        int originalQuantity, int filledQuantity, long createdAt, long expiresAt)
    {
        return new BuyReplacementIntent(intentId, offerIdentity, recommendationId, slot, itemId,
            itemName, originalPrice, originalQuantity, filledQuantity, createdAt, expiresAt,
            BuyReplacementIntentState.CANCEL_AUTHORIZED, "CANCEL_AUTHORIZED");
    }

    BuyReplacementIntent advance(BuyReplacementIntentState next, String reason)
    {
        return new BuyReplacementIntent(intentId, originalOfferIdentity, recommendationId, slot,
            itemId, itemName, originalPrice, originalQuantity, filledQuantity, createdAt,
            expiresAt, next, reason);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    public String getSchemaVersion() { return SCHEMA_VERSION; }
    public String getIntentId() { return intentId; }
    public String getOriginalOfferIdentity() { return originalOfferIdentity; }
    public String getRecommendationId() { return recommendationId; }
    public int getSlot() { return slot; }
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getOriginalPrice() { return originalPrice; }
    public int getOriginalQuantity() { return originalQuantity; }
    public int getFilledQuantity() { return filledQuantity; }
    public int getRemainderQuantity() { return remainderQuantity; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public BuyReplacementIntentState getState() { return state; }
    public String getReasonCode() { return reasonCode; }
}
