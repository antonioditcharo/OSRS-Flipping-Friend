package com.flippingfriend.companion;
/** Immutable positive-net-margin projection preserving complete evaluation provenance. */
final class BuyReplacementPositiveNetMarginCandidate {
 private final BuyReplacementNetMarginEvaluation source;
 BuyReplacementPositiveNetMarginCandidate(BuyReplacementNetMarginEvaluation s){source=s;}
 int getBuyOffsetIndex(){return source.getBuyOffsetIndex();} double getBuyOffset(){return source.getBuyOffset();} int getBuyPrice(){return source.getBuyPrice();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} long getTotalCost(){return source.getTotalCost();} double getBuyProbability(){return source.getBuyProbability();} double getBuyExpectedHours(){return source.getBuyExpectedHours();} double getBuyUnitsPerHour(){return source.getBuyUnitsPerHour();} double getBuyWaitHours(){return source.getBuyWaitHours();} int getSellOffsetIndex(){return source.getSellOffsetIndex();} double getSellOffset(){return source.getSellOffset();} int getSellPrice(){return source.getSellPrice();} long getRawSpreadPerItem(){return source.getRawSpreadPerItem();} int getTaxPerItem(){return source.getTaxPerItem();} long getNetMarginPerItem(){return source.getNetMarginPerItem();} long getTotalTax(){return source.getTotalTax();} long getTotalNetMargin(){return source.getTotalNetMargin();}
}
