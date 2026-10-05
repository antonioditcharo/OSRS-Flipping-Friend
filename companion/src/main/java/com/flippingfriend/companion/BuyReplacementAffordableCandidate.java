package com.flippingfriend.companion;

/** Immutable affordable projection preserving original candidate provenance. */
final class BuyReplacementAffordableCandidate
{
    private final int offsetIndex;
    private final double offset;
    private final int buyPrice;
    private final int exactRemainderQuantity;
    private final long totalCost;

    BuyReplacementAffordableCandidate(int offsetIndex, double offset, int buyPrice,
        int exactRemainderQuantity, long totalCost)
    {
        this.offsetIndex = offsetIndex;
        this.offset = offset;
        this.buyPrice = buyPrice;
        this.exactRemainderQuantity = exactRemainderQuantity;
        this.totalCost = totalCost;
    }

    int getOffsetIndex() { return offsetIndex; }
    double getOffset() { return offset; }
    int getBuyPrice() { return buyPrice; }
    int getExactRemainderQuantity() { return exactRemainderQuantity; }
    long getTotalCost() { return totalCost; }
}
