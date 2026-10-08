package com.flippingfriend.companion;
/** Immutable generated-quantity inputs for later fractional-Kelly arithmetic. */
final class BuyReplacementQuantityKellySizingInput {
    private final BuyReplacementQuantityWorstLossEvaluation source;
    private final long quantityNetProfit, quantityUnwindLoss, quantityWorstLoss;
    private final double completionProbability, payoffOdds;
    BuyReplacementQuantityKellySizingInput(BuyReplacementQuantityWorstLossEvaluation source,long profit,long unwind,long worst,double probability,double odds) {
        this.source=source; quantityNetProfit=profit; quantityUnwindLoss=unwind;
        quantityWorstLoss=worst; completionProbability=probability; payoffOdds=odds;
    }
    BuyReplacementQuantityWorstLossEvaluation getSource() { return source; }
    int getBuyOffsetIndex() { return source.getBuyOffsetIndex(); }
    int getSellOffsetIndex() { return source.getSellOffsetIndex(); }
    int getBuyPrice() { return source.getBuyPrice(); }
    int getSellPrice() { return source.getSellPrice(); }
    int getFillableQuantity() { return source.getFillableQuantity(); }
    double getKellyFraction() { return source.getKellyFraction(); }
    int getSizeGridIndex() { return source.getSizeGridIndex(); }
    double getSizeShare() { return source.getSizeShare(); }
    int getQuantity() { return source.getQuantity(); }
    double getUpstreamExpectedProfit() { return source.getUpstreamExpectedProfit(); }
    long getUpstreamWorstLoss() { return source.getUpstreamWorstLoss(); }
    double getBuyProbability() { return source.getBuyProbability(); }
    double getBuyExpectedHours() { return source.getBuyExpectedHours(); }
    double getBuyUnitsPerHour() { return source.getBuyUnitsPerHour(); }
    double getBuyWaitHours() { return source.getBuyWaitHours(); }
    double getSellProbability() { return source.getSellProbability(); }
    double getSellExpectedHours() { return source.getSellExpectedHours(); }
    double getSellUnitsPerHour() { return source.getSellUnitsPerHour(); }
    double getSellWaitHours() { return source.getSellWaitHours(); }
    double getCalibratedBuyHours() { return source.getCalibratedBuyHours(); }
    double getCalibratedSellHours() { return source.getCalibratedSellHours(); }
    double getExpectedSlotHours() { return source.getExpectedSlotHours(); }
    double getDriftFraction() { return source.getDriftFraction(); }
    int getExitPrice() { return source.getExitPrice(); }
    int getExitTaxPerItem() { return source.getExitTaxPerItem(); }
    long getLossPerItem() { return source.getLossPerItem(); }
    double getCompletedProbability() { return source.getCompletedProbability(); }
    double getStrandedProbability() { return source.getStrandedProbability(); }
    double getQuantityExpectedProfit() { return source.getQuantityExpectedProfit(); }
    double getLossCutPct() { return source.getLossCutPct(); }
    long getQuantityNetProfit() { return quantityNetProfit; }
    long getQuantityUnwindLoss() { return quantityUnwindLoss; }
    long getQuantityWorstLoss() { return quantityWorstLoss; }
    double getCompletionProbability() { return completionProbability; }
    double getPayoffOdds() { return payoffOdds; }
}
