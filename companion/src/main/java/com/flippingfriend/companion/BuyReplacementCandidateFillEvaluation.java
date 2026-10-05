package com.flippingfriend.companion;

/** Immutable measured buy-fill evidence preserving original affordable-candidate provenance. */
final class BuyReplacementCandidateFillEvaluation
{
    private final int offsetIndex;
    private final double offset;
    private final int buyPrice;
    private final int exactRemainderQuantity;
    private final long totalCost;
    private final double probability;
    private final double expectedHours;
    private final double unitsPerHour;
    private final double waitHours;

    BuyReplacementCandidateFillEvaluation(int offsetIndex, double offset, int buyPrice,
        int exactRemainderQuantity, long totalCost, double probability, double expectedHours,
        double unitsPerHour, double waitHours)
    {
        this.offsetIndex = offsetIndex;
        this.offset = offset;
        this.buyPrice = buyPrice;
        this.exactRemainderQuantity = exactRemainderQuantity;
        this.totalCost = totalCost;
        this.probability = probability;
        this.expectedHours = expectedHours;
        this.unitsPerHour = unitsPerHour;
        this.waitHours = waitHours;
    }

    int getOffsetIndex() { return offsetIndex; }
    double getOffset() { return offset; }
    int getBuyPrice() { return buyPrice; }
    int getExactRemainderQuantity() { return exactRemainderQuantity; }
    long getTotalCost() { return totalCost; }
    double getProbability() { return probability; }
    double getExpectedHours() { return expectedHours; }
    double getUnitsPerHour() { return unitsPerHour; }
    double getWaitHours() { return waitHours; }
}
