package com.flippingfriend.companion;

/** One ordered, non-selected buy-price candidate with its exact strategy provenance. */
final class BuyReplacementPriceCandidate
{
    private final int offsetIndex;
    private final double offset;
    private final int buyPrice;

    BuyReplacementPriceCandidate(int offsetIndex, double offset, int buyPrice)
    {
        this.offsetIndex = offsetIndex;
        this.offset = offset;
        this.buyPrice = buyPrice;
    }

    int getOffsetIndex() { return offsetIndex; }
    double getOffset() { return offset; }
    int getBuyPrice() { return buyPrice; }
}
