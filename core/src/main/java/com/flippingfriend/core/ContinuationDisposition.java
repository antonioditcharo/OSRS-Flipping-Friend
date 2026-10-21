package com.flippingfriend.core;

/** Exact recorded sale source used only to retire an older local rebuy promise. */
public final class ContinuationDisposition {
    private final int itemId;
    private final String sourceEventId, offerIdentity;
    private final long firstSeenAt, observedAt;

    public ContinuationDisposition(int item, String source, String offer, long firstSeen, long observed) {
        itemId=item; sourceEventId=source; offerIdentity=offer; firstSeenAt=firstSeen; observedAt=observed;
        if(!isValid())throw new IllegalArgumentException("Invalid continuation disposition");
    }
    public boolean isValid() {
        return itemId>0 && sourceEventId!=null && !sourceEventId.isBlank()
            && offerIdentity!=null && !offerIdentity.isBlank() && firstSeenAt>0 && observedAt>=firstSeenAt;
    }
    public int getItemId(){return itemId;}
    public String getSourceEventId(){return sourceEventId;}
    public String getOfferIdentity(){return offerIdentity;}
    public long getFirstSeenAt(){return firstSeenAt;}
    public long getObservedAt(){return observedAt;}
}
