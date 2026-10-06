package com.flippingfriend.companion;
/** Immutable production-equivalent worst-loss evidence. */
final class BuyReplacementWorstLossEvaluation {
 private final BuyReplacementExpectedProfitEvaluation source; private final double lossCutPct; private final long worstLoss;
 BuyReplacementWorstLossEvaluation(BuyReplacementExpectedProfitEvaluation s,double loss,long worst){source=s;lossCutPct=loss;worstLoss=worst;}
 int getBuyOffsetIndex(){return source.getBuyOffsetIndex();} double getBuyOffset(){return source.getBuyOffset();} int getBuyPrice(){return source.getBuyPrice();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} long getTotalCost(){return source.getTotalCost();} int getSellOffsetIndex(){return source.getSellOffsetIndex();} double getSellOffset(){return source.getSellOffset();} int getSellPrice(){return source.getSellPrice();} long getTotalNetMargin(){return source.getTotalNetMargin();} long getTotalUnwindLoss(){return source.getTotalUnwindLoss();} double getCompletionProbability(){return source.getCompletionProbability();} double getExpectedSlotHours(){return source.getExpectedSlotHours();} double getExpectedProfit(){return source.getExpectedProfit();} double getLossCutPct(){return lossCutPct;} long getWorstLoss(){return worstLoss;}
}
