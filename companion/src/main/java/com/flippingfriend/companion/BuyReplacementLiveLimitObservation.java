package com.flippingfriend.companion;
/** Immutable single-item live buy-limit observation; evidence only, never authorization. */
final class BuyReplacementLiveLimitObservation {
    private final int itemId;
    private final int buyLimit;
    private final int remaining;
    private final long observedAt;
    BuyReplacementLiveLimitObservation(int itemId, int buyLimit, int remaining, long observedAt) {
        this.itemId = itemId;
        this.buyLimit = buyLimit;
        this.remaining = remaining;
        this.observedAt = observedAt;
    }
    int getItemId() { return itemId; }
    int getBuyLimit() { return buyLimit; }
    int getRemaining() { return remaining; }
    long getObservedAt() { return observedAt; }
}
