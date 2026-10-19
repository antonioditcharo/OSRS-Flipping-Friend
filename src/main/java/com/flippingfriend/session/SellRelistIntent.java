package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Durable guidance intent, not ownership: the exact sell offer the player was asked to reprice. */
@Singleton
public final class SellRelistIntent extends OfferContinuationStore {
    public static final class CollectedRemainder {
        private final int item,quantity;private final boolean managed;
        private CollectedRemainder(int item,int quantity,boolean managed){this.item=item;this.quantity=quantity;this.managed=managed;}
        public int getItemId(){return item;}
        public int getQuantity(){return quantity;}
        public boolean requiresManagedCost(){return managed;}
    }
    @Inject public SellRelistIntent(PluginStorage storage){super(storage,"sell-relist-intent.json",false);}
    public synchronized void advise(TrackedOffer offer){advise(offer,account());}
    public synchronized void advise(TrackedOffer offer,String expected){advise(offer,expected,false);}
    public synchronized void advise(TrackedOffer offer,String expected,boolean managed){advise(offer,expected,managed,null);}
    public synchronized void advise(TrackedOffer offer,String expected,boolean managed,OfferTracker tracker) {
        adviseOffer(offer,expected,managed,tracker,java.time.Instant.now().getEpochSecond(),0);
    }
    public synchronized void observe(OfferTracker tracker,String expected){advance(tracker,expected,java.time.Instant.now().getEpochSecond());}
    public synchronized boolean requiresManagedCost(String expected){return managed(expected);}
    public synchronized CollectedRemainder collectedRequest(OfferTracker tracker,String expected) {
        var intent=advance(tracker,expected,java.time.Instant.now().getEpochSecond());
        return intent==null?null:new CollectedRemainder(intent.getItemId(),intent.getCollectedRemaining(),intent.isManaged());
    }
    public synchronized int[] collectedRemainder(OfferTracker tracker){return collectedRemainder(tracker,account());}
    public synchronized int[] collectedRemainder(OfferTracker tracker,String expected) {
        var request=collectedRequest(tracker,expected);
        return request==null?null:new int[]{request.item,request.quantity};
    }
}
