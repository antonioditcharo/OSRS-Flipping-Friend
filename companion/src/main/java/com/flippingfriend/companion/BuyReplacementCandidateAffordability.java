package com.flippingfriend.companion;

/** Immutable exact-remainder cost evidence for one price candidate. */
final class BuyReplacementCandidateAffordability
{
    private final int offsetIndex;
    private final double offset;
    private final int buyPrice;
    private final int exactRemainderQuantity;
    private final long totalCost;
    private final BuyReplacementAffordabilityOutcome outcome;

    BuyReplacementCandidateAffordability(int offsetIndex, double offset, int buyPrice,
        int exactRemainderQuantity, long totalCost, BuyReplacementAffordabilityOutcome outcome)
    {
        this.offsetIndex = offsetIndex;
        this.offset = offset;
        this.buyPrice = buyPrice;
        this.exactRemainderQuantity = exactRemainderQuantity;
        this.totalCost = totalCost;
        this.outcome = outcome;
    }

    int getOffsetIndex() { return offsetIndex; }
    double getOffset() { return offset; }
    int getBuyPrice() { return buyPrice; }
    int getExactRemainderQuantity() { return exactRemainderQuantity; }
    long getTotalCost() { return totalCost; }
    BuyReplacementAffordabilityOutcome getOutcome() { return outcome; }
}
