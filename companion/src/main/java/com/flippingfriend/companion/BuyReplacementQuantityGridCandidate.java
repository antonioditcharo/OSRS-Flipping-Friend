package com.flippingfriend.companion;
/** Immutable production-equivalent quantity-grid evidence. */
final class BuyReplacementQuantityGridCandidate {
 private final BuyReplacementKellyFractionEvaluation source; private final int sizeGridIndex; private final double sizeShare; private final int quantity;
 BuyReplacementQuantityGridCandidate(BuyReplacementKellyFractionEvaluation s,int index,double share,int q){source=s;sizeGridIndex=index;sizeShare=share;quantity=q;}
 int getBuyOffsetIndex(){return source.getBuyOffsetIndex();} int getSellOffsetIndex(){return source.getSellOffsetIndex();} int getBuyPrice(){return source.getBuyPrice();} int getSellPrice(){return source.getSellPrice();} int getFillableQuantity(){return source.getFillableQuantity();} double getKellyFraction(){return source.getKellyFraction();} int getSizeGridIndex(){return sizeGridIndex;} double getSizeShare(){return sizeShare;} int getQuantity(){return quantity;} double getExpectedProfit(){return source.getExpectedProfit();} long getWorstLoss(){return source.getWorstLoss();}
}
