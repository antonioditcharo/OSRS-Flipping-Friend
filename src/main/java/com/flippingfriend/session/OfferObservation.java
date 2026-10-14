package com.flippingfriend.session;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/** Plain immutable copy made on the client thread before an observation is buffered. */
public final class OfferObservation implements GrandExchangeOffer
{
    private final GrandExchangeOfferState state;
    private final int itemId, totalQuantity, filledQuantity;
    private final long price, spent;

    private OfferObservation(GrandExchangeOffer offer)
    {
        state = offer.getState(); itemId = offer.getItemId(); price = offer.getPrice();
        totalQuantity = offer.getTotalQuantity(); filledQuantity = offer.getQuantitySold();
        spent = offer.getSpent();
    }
    public static OfferObservation copyOf(GrandExchangeOffer offer) { return new OfferObservation(offer); }
    public GrandExchangeOfferState getState() { return state; }
    public int getItemId() { return itemId; }
    public long getPrice() { return price; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getQuantitySold() { return filledQuantity; }
    public long getSpent() { return spent; }
}
