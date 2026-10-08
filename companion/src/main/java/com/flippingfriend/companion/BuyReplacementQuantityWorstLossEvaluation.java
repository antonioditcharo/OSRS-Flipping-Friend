package com.flippingfriend.companion;
/** Immutable generated-quantity worst-loss evidence; earlier worst loss remains provenance only. */
final class BuyReplacementQuantityWorstLossEvaluation {
    private final BuyReplacementQuantityExpectedProfitEvaluation source; private final double lossCutPct; private final long worstLoss;
    BuyReplacementQuantityWorstLossEvaluation(BuyReplacementQuantityExpectedProfitEvaluation source, double lossCutPct, long worstLoss) { this.source=source; this.lossCutPct=lossCutPct; this.worstLoss=worstLoss; }
    BuyReplacementQuantityExpectedProfitEvaluation getSource() { return source; }
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
    long getUpstreamWorstLoss() { return source.getWorstLoss(); }
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
    long getCompletedSaleNetProfit() { return source.getCompletedSaleNetProfit(); }
    double getCompletedProbability() { return source.getCompletedProbability(); }
    double getStrandedProbability() { return source.getStrandedProbability(); }
    double getQuantityExpectedProfit() { return source.getQuantityExpectedProfit(); }
    double getLossCutPct() { return lossCutPct; }
    long getQuantityWorstLoss() { return worstLoss; }
}
