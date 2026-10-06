package com.flippingfriend.companion;
/** Immutable generated-quantity inputs for later two-leg fill evaluation. */
final class BuyReplacementQuantityFillInput {
 private final BuyReplacementQuantityGridCandidate source;
 BuyReplacementQuantityFillInput(BuyReplacementQuantityGridCandidate s){source=s;}
 int getBuyOffsetIndex(){return source.getBuyOffsetIndex();} int getSellOffsetIndex(){return source.getSellOffsetIndex();} int getBuyPrice(){return source.getBuyPrice();} int getSellPrice(){return source.getSellPrice();} int getFillableQuantity(){return source.getFillableQuantity();} double getKellyFraction(){return source.getKellyFraction();} int getSizeGridIndex(){return source.getSizeGridIndex();} double getSizeShare(){return source.getSizeShare();} int getQuantity(){return source.getQuantity();} double getExpectedProfit(){return source.getExpectedProfit();} long getWorstLoss(){return source.getWorstLoss();}
}
