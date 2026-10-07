package com.flippingfriend.companion;

/** Immutable generated-quantity expected-profit evidence; earlier economics remain provenance only. */
final class BuyReplacementQuantityExpectedProfitEvaluation {
    private final BuyReplacementQuantityUnwindLossEvaluation source;
    private final long completedSaleNetProfit;
    private final double completedProbability, strandedProbability, expectedProfit;
    BuyReplacementQuantityExpectedProfitEvaluation(BuyReplacementQuantityUnwindLossEvaluation source, long netProfit,
            double completed, double stranded, double expectedProfit) {
        this.source = source; completedSaleNetProfit = netProfit;
        completedProbability = completed; strandedProbability = stranded;
        this.expectedProfit = expectedProfit;
    }
    BuyReplacementQuantityUnwindLossEvaluation getSource() { return source; }
    int getBuyOffsetIndex() { return source.getBuyOffsetIndex(); }
    int getSellOffsetIndex() { return source.getSellOffsetIndex(); }
    int getBuyPrice() { return source.getBuyPrice(); }
    int getSellPrice() { return source.getSellPrice(); }
    int getFillableQuantity() { return source.getFillableQuantity(); }
    double getKellyFraction() { return source.getKellyFraction(); }
    int getSizeGridIndex() { return source.getSizeGridIndex(); }
    double getSizeShare() { return source.getSizeShare(); }
    int getQuantity() { return source.getQuantity(); }
    double getUpstreamExpectedProfit() { return source.getExpectedProfit(); }
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
    double getDriftFraction() { return source.getDriftFraction(); }
    int getExitPrice() { return source.getExitPrice(); }
    int getExitTaxPerItem() { return source.getExitTaxPerItem(); }
    long getLossPerItem() { return source.getLossPerItem(); }
    long getTotalUnwindLoss() { return source.getTotalUnwindLoss(); }
    long getCompletedSaleNetProfit() { return completedSaleNetProfit; }
    double getCompletedProbability() { return completedProbability; }
    double getStrandedProbability() { return strandedProbability; }
    double getQuantityExpectedProfit() { return expectedProfit; }
}
