package com.flippingfriend.companion;
/** Immutable positive raw-spread projection preserving complete pairing provenance. */
final class BuyReplacementPositiveSpreadCandidate {
 private final BuyReplacementSellPriceCandidate source; private final long rawSpreadPerItem;
 BuyReplacementPositiveSpreadCandidate(BuyReplacementSellPriceCandidate s){source=s;rawSpreadPerItem=(long)s.getSellPrice()-s.getBuyPrice();}
 int getBuyOffsetIndex(){return source.getBuyOffsetIndex();} double getBuyOffset(){return source.getBuyOffset();} int getBuyPrice(){return source.getBuyPrice();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} long getTotalCost(){return source.getTotalCost();} double getBuyProbability(){return source.getBuyProbability();} double getBuyExpectedHours(){return source.getBuyExpectedHours();} double getBuyUnitsPerHour(){return source.getBuyUnitsPerHour();} double getBuyWaitHours(){return source.getBuyWaitHours();} int getSellOffsetIndex(){return source.getSellOffsetIndex();} double getSellOffset(){return source.getSellOffset();} int getSellPrice(){return source.getSellPrice();} long getRawSpreadPerItem(){return rawSpreadPerItem;}
}
