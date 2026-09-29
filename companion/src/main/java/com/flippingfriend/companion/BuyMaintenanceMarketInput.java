package com.flippingfriend.companion;

/** Minimal immutable market input used to compose a buy-maintenance policy context. */
final class BuyMaintenanceMarketInput
{
    private final int itemId;
    private final int lowPrice;
    private final int highPrice;
    private final long observedAt;
    BuyMaintenanceMarketInput(int itemId, int lowPrice, int highPrice, long observedAt)
    {
        this.itemId = itemId;
        this.lowPrice = lowPrice;
        this.highPrice = highPrice;
        this.observedAt = observedAt;
    }
    int getItemId() { return itemId; }
    int getLowPrice() { return lowPrice; }
    int getHighPrice() { return highPrice; }
    long getObservedAt() { return observedAt; }
}
