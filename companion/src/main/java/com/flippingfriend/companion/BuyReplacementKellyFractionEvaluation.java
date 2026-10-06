package com.flippingfriend.companion;
/** Immutable production-equivalent fractional-Kelly evidence. */
final class BuyReplacementKellyFractionEvaluation {
 private final BuyReplacementKellySizingInput source; private final double kellyFraction;
 BuyReplacementKellyFractionEvaluation(BuyReplacementKellySizingInput s,double fraction){source=s;kellyFraction=fraction;}
 int getBuyOffsetIndex(){return source.getBuyOffsetIndex();} int getSellOffsetIndex(){return source.getSellOffsetIndex();} int getBuyPrice(){return source.getBuyPrice();} int getSellPrice(){return source.getSellPrice();} int getFillableQuantity(){return source.getFillableQuantity();} long getFullNetProfit(){return source.getFullNetProfit();} long getFullUnwindLoss(){return source.getFullUnwindLoss();} double getCompletionProbability(){return source.getCompletionProbability();} double getPayoffOdds(){return source.getPayoffOdds();} double getKellyFraction(){return kellyFraction;} double getExpectedProfit(){return source.getExpectedProfit();} long getWorstLoss(){return source.getWorstLoss();}
}
