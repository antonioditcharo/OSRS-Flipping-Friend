package com.flippingfriend.companion;

/** Immutable quantity-specific exit-loss evidence; upstream economics remain provenance only. */
final class BuyReplacementQuantityUnwindLossEvaluation {
    private final BuyReplacementQuantityExpectedSlotOccupancyEvaluation source;
    private final double driftFraction;
    private final int exitPrice, exitTaxPerItem;
    private final long lossPerItem, totalUnwindLoss;
    BuyReplacementQuantityUnwindLossEvaluation(BuyReplacementQuantityExpectedSlotOccupancyEvaluation source, double drift, int price, int tax, long loss, long total) {
        this.source = source; driftFraction = drift; exitPrice = price;
        exitTaxPerItem = tax; lossPerItem = loss; totalUnwindLoss = total;
    }
    BuyReplacementQuantityExpectedSlotOccupancyEvaluation getSource() { return source; }
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
    double getExpectedSlotHours() { return source.getExpectedSlotHours(); }
    double getDriftFraction() { return driftFraction; }
    int getExitPrice() { return exitPrice; }
    int getExitTaxPerItem() { return exitTaxPerItem; }
    long getLossPerItem() { return lossPerItem; }
    long getTotalUnwindLoss() { return totalUnwindLoss; }
}
