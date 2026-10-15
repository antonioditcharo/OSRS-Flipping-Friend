package com.flippingfriend.session;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/** Session-local lower bound from a real bank read, debited by possible later buy funding. */
final class BankCashEvidence {
    private final OfferObservation[] slots=new OfferObservation[8];
    private long available;
    private boolean known;

    void reset() { available=0; known=false; java.util.Arrays.fill(slots,null); }
    long available() { return known?available:0; }
    void rebase(long coins,GrandExchangeOffer[] current) {
        reset();
        if (coins<0 || current==null || current.length!=8) return;
        for (int i=0;i<8;i++) {
            if (!valid(current[i])) { reset(); return; }
            slots[i]=OfferObservation.copyOf(current[i]);
        }
        available=coins; known=true;
    }
    void observeAll(GrandExchangeOffer[] current) {
        if (current==null || current.length!=8) { reset(); return; }
        for (int i=0;i<8;i++) observe(i,current[i]);
    }
    void observe(int slot,GrandExchangeOffer value) {
        if (!known) return;
        if (slot<0 || slot>=8 || !valid(value)) { reset(); return; }
        OfferObservation next=OfferObservation.copyOf(value), previous=slots[slot];
        if (buying(next) && !sameBuy(previous,next)) {
            try { available=Math.max(0,Math.subtractExact(available,
                Math.multiplyExact(next.getPrice(),(long)next.getTotalQuantity()))); }
            catch (ArithmeticException invalid) { reset(); return; }
        }
        slots[slot]=next;
    }
    private static boolean sameBuy(OfferObservation previous,OfferObservation next) {
        return previous!=null && buying(previous) && previous.getItemId()==next.getItemId()
            && previous.getPrice()==next.getPrice() && previous.getTotalQuantity()==next.getTotalQuantity()
            && next.getQuantitySold()>=previous.getQuantitySold() && next.getSpent()>=previous.getSpent();
    }
    private static boolean buying(GrandExchangeOffer offer) {
        return offer.getState()==GrandExchangeOfferState.BUYING || offer.getState()==GrandExchangeOfferState.BOUGHT
            || offer.getState()==GrandExchangeOfferState.CANCELLED_BUY;
    }
    private static boolean valid(GrandExchangeOffer e) {
        if (e==null || e.getState()==null) return false;
        if (e.getState()==GrandExchangeOfferState.EMPTY) return true;
        return e.getItemId()>0 && e.getPrice()>0 && e.getTotalQuantity()>0
            && e.getQuantitySold()>=0 && e.getQuantitySold()<=e.getTotalQuantity() && e.getSpent()>=0;
    }
}
