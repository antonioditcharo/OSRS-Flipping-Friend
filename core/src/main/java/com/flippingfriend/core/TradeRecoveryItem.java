package com.flippingfriend.core;

/** Immutable workflow coverage, never accounting or order authority. Quantities are not additive across kinds. */
public final class TradeRecoveryItem {
    public enum Kind { OFFER, STOCK, CONTINUATION, EVIDENCE, ENTRY_PAUSE }
    public enum State { WORKING, ACTIONABLE, AWAITING_COLLECTION, AWAITING_EVIDENCE, AWAITING_ACCESS, PAUSED, COMPLETE }
    public final String identity, name, reason, nextStep;
    public final Kind kind;
    public final State state;
    public final int itemId, slot, quantity, price;
    public final boolean buying;
    public final long openedAt, reassessAt;
    public TradeRecoveryItem(String identity, Kind kind, State state, int itemId, String name, int slot,
            int quantity, int price, boolean buying, long openedAt, long reassessAt, String reason, String nextStep) {
        if(identity==null || kind==null || state==null || quantity<0 || reassessAt<0)throw new IllegalArgumentException("invalid recovery item");
        this.identity=identity;this.kind=kind;this.state=state;this.itemId=itemId;this.name=name;
        this.slot=slot;this.quantity=quantity;this.price=price;this.buying=buying;this.openedAt=openedAt;
        this.reassessAt=reassessAt;this.reason=reason;this.nextStep=nextStep;
    }
}
