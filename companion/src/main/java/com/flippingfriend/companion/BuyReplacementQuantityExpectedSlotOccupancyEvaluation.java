package com.flippingfriend.companion;

/** Immutable slot-time evidence retaining all raw and calibrated quantity provenance. */
final class BuyReplacementQuantityExpectedSlotOccupancyEvaluation {
    private final BuyReplacementQuantityCalibratedDurationEvaluation source;
    private final double expectedSlotHours;
    BuyReplacementQuantityExpectedSlotOccupancyEvaluation(BuyReplacementQuantityCalibratedDurationEvaluation source, double hours) {
        this.source = source;
        expectedSlotHours = hours;
    }
    BuyReplacementQuantityCalibratedDurationEvaluation getSource() { return source; }
    int getBuyOffsetIndex() { return source.getBuyOffsetIndex(); }
    int getSellOffsetIndex() { return source.getSellOffsetIndex(); }
    int getBuyPrice() { return source.getBuyPrice(); }
    int getSellPrice() { return source.getSellPrice(); }
    int getFillableQuantity() { return source.getFillableQuantity(); }
    double getKellyFraction() { return source.getKellyFraction(); }
    int getSizeGridIndex() { return source.getSizeGridIndex(); }
    double getSizeShare() { return source.getSizeShare(); }
    int getQuantity() { return source.getQuantity(); }
    double getExpectedProfit() { return source.getExpectedProfit(); }
    long getWorstLoss() { return source.getWorstLoss(); }
    double getBuyProbability() { return source.getBuyProbability(); }
    double getBuyExpectedHours() { return source.getBuyExpectedHours(); }
    double getBuyUnitsPerHour() { return source.getBuyUnitsPerHour(); }
    double getBuyWaitHours() { return source.getBuyWaitHours(); }
    double getSellProbability() { return source.getSellProbability(); }
    double getSellExpectedHours() { return source.getSellExpectedHours(); }
    double getSellUnitsPerHour() { return source.getSellUnitsPerHour(); }
    double getSellWaitHours() { return source.getSellWaitHours(); }
    double getCompletionProbability() { return source.getCompletionProbability(); }
    double getCalibratedBuyHours() { return source.getCalibratedBuyHours(); }
    double getCalibratedSellHours() { return source.getCalibratedSellHours(); }
    double getExpectedSlotHours() { return expectedSlotHours; }
}
