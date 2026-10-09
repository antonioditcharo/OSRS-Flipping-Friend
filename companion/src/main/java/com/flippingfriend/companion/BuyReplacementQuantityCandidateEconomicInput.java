package com.flippingfriend.companion;

/** Immutable economic constructor inputs, not a constructed or authorized candidate. */
final class BuyReplacementQuantityCandidateEconomicInput {
    private final BuyReplacementQuantityExpectedGpPerSlotHourEvaluation source;
    private final double horizonHours;

    BuyReplacementQuantityCandidateEconomicInput(
            BuyReplacementQuantityExpectedGpPerSlotHourEvaluation source, double horizonHours) {
        this.source = source;
        this.horizonHours = horizonHours;
    }

    BuyReplacementQuantityExpectedGpPerSlotHourEvaluation getSource() { return source; }
    int getTargetBuyPrice() { return source.getBuyPrice(); }
    int getEquilibriumBuyPrice() { return source.getBuyPrice(); }
    int getExitBuyPrice() { return source.getBuyPrice(); }
    int getTargetSellPrice() { return source.getSellPrice(); }
    int getEquilibriumSellPrice() { return source.getSellPrice(); }
    int getExitSellPrice() { return source.getSellPrice(); }
    int getQuantity() { return source.getQuantity(); }
    int getExactRemainderQuantity() { return source.getFillableQuantity(); }
    long getNetProfit() { return source.getQuantityNetProfit(); }
    long getWorstLoss() { return source.getQuantityWorstLoss(); }
    long getUnwindLoss() { return source.getQuantityUnwindLoss(); }
    double getBuyFillProbability() { return source.getBuyProbability(); }
    double getSellFillProbability() { return source.getSellProbability(); }
    double getBuyHours() { return source.getCalibratedBuyHours(); }
    double getSellHours() { return source.getCalibratedSellHours(); }
    double getHorizonHours() { return horizonHours; }
    double getExpectedProfit() { return source.getQuantityExpectedProfit(); }
    double getExpectedSlotHours() { return source.getExpectedSlotHours(); }
    double getExpectedGpPerSlotHour() { return source.getQuantityExpectedGpPerSlotHour(); }
}
