package com.flippingfriend.companion;

/** Immutable viable-only projection retaining complete fill evidence and original provenance. */
final class BuyReplacementFillViableCandidate
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

    BuyReplacementFillViableCandidate(BuyReplacementCandidateFillEvaluation value)
    {
        this.offsetIndex = value.getOffsetIndex();
        this.offset = value.getOffset();
        this.buyPrice = value.getBuyPrice();
        this.exactRemainderQuantity = value.getExactRemainderQuantity();
        this.totalCost = value.getTotalCost();
        this.probability = value.getProbability();
        this.expectedHours = value.getExpectedHours();
        this.unitsPerHour = value.getUnitsPerHour();
        this.waitHours = value.getWaitHours();
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
