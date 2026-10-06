package com.flippingfriend.companion;
/** Immutable production Kelly inputs without calculating a fraction or quantity. */
final class BuyReplacementKellySizingInput {
 private final BuyReplacementWorstLossEvaluation source; private final long fullNetProfit; private final long fullUnwindLoss; private final double completionProbability; private final double payoffOdds;
 BuyReplacementKellySizingInput(BuyReplacementWorstLossEvaluation s,long profit,long unwind,double p,double odds){source=s;fullNetProfit=profit;fullUnwindLoss=unwind;completionProbability=p;payoffOdds=odds;}
 int getBuyOffsetIndex(){return source.getBuyOffsetIndex();} int getSellOffsetIndex(){return source.getSellOffsetIndex();} int getBuyPrice(){return source.getBuyPrice();} int getSellPrice(){return source.getSellPrice();} int getFillableQuantity(){return source.getExactRemainderQuantity();} long getFullNetProfit(){return fullNetProfit;} long getFullUnwindLoss(){return fullUnwindLoss;} double getCompletionProbability(){return completionProbability;} double getPayoffOdds(){return payoffOdds;} double getExpectedProfit(){return source.getExpectedProfit();} long getWorstLoss(){return source.getWorstLoss();}
}
