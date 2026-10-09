package com.flippingfriend.companion;
/** Immutable generated-quantity expected GP-per-slot-hour evidence. */
final class BuyReplacementQuantityExpectedGpPerSlotHourEvaluation {
    private final BuyReplacementQuantityKellyFractionEvaluation source; private final double expectedGpPerSlotHour;
    BuyReplacementQuantityExpectedGpPerSlotHourEvaluation(BuyReplacementQuantityKellyFractionEvaluation source,double rate) { this.source=source; expectedGpPerSlotHour=rate; }
    BuyReplacementQuantityKellyFractionEvaluation getSource() { return source; }
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
    long getQuantityNetProfit() { return source.getQuantityNetProfit(); }
    long getQuantityUnwindLoss() { return source.getQuantityUnwindLoss(); }
    long getQuantityWorstLoss() { return source.getQuantityWorstLoss(); }
    double getCompletionProbability() { return source.getCompletionProbability(); }
    double getPayoffOdds() { return source.getPayoffOdds(); }
    double getQuantityKellyFraction() { return source.getQuantityKellyFraction(); }
    double getQuantityExpectedGpPerSlotHour() { return expectedGpPerSlotHour; }
}
