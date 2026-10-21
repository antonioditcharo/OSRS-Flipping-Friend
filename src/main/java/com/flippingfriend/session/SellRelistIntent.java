package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Durable guidance intent, not ownership: the exact sell offer the player was asked to reprice. */
@Singleton
public final class SellRelistIntent extends OfferContinuationStore {
    public static final class CollectedRemainder {
        private final int item,quantity;private final boolean managed;private final String repriceSource;
        private final int originalPrice;private final long firstSeen,progressAt;
        private CollectedRemainder(com.flippingfriend.core.OfferContinuation intent){
            item=intent.getItemId();quantity=intent.getCollectedRemaining();managed=intent.isManaged();
            repriceSource=intent.getSaleRepriceSource();originalPrice=intent.getOriginalPrice();
            firstSeen=intent.getFirstSeenAt();progressAt=intent.getSaleRepriceProgressAt();
        }
        public int getItemId(){return item;}
        public int getQuantity(){return quantity;}
        public boolean requiresManagedCost(){return managed;}
        public String getSaleRepriceSource(){return repriceSource;}
        public int getOriginalPrice(){return originalPrice;}
        public long getFirstSeenAt(){return firstSeen;}
        public long getProgressAt(){return progressAt;}
    }
    @Inject public SellRelistIntent(PluginStorage storage){super(storage,"sell-relist-intent.json",false);}
    public synchronized void advise(TrackedOffer offer){advise(offer,account());}
    public synchronized void advise(TrackedOffer offer,String expected){advise(offer,expected,false);}
    public synchronized void advise(TrackedOffer offer,String expected,boolean managed){advise(offer,expected,managed,null);}
    public synchronized boolean advise(TrackedOffer offer,String expected,boolean managed,OfferTracker tracker) {
        return adviseOffer(offer,expected,managed,tracker,java.time.Instant.now().getEpochSecond(),0);
    }
    public synchronized boolean advise(TrackedOffer offer,String expected,boolean managed,OfferTracker tracker,String source) {
        return adviseOffer(offer,expected,managed,tracker,java.time.Instant.now().getEpochSecond(),0,source);
    }
    public synchronized void observe(OfferTracker tracker,String expected){advance(tracker,expected,java.time.Instant.now().getEpochSecond());}
    public synchronized boolean requiresManagedCost(String expected){return managed(expected);}
    public synchronized CollectedRemainder collectedRequest(OfferTracker tracker,String expected) {
        var intent=advance(tracker,expected,java.time.Instant.now().getEpochSecond());
        return intent==null?null:new CollectedRemainder(intent);
    }
    public synchronized java.util.List<CollectedRemainder> collectedRequests(OfferTracker tracker,String expected) {
        var requests=new java.util.ArrayList<CollectedRemainder>();
        for(var intent:collectedAll(tracker,expected,java.time.Instant.now().getEpochSecond()))
            requests.add(new CollectedRemainder(intent));
        return java.util.Collections.unmodifiableList(requests);
    }
    public synchronized int[] collectedRemainder(OfferTracker tracker){return collectedRemainder(tracker,account());}
    public synchronized int[] collectedRemainder(OfferTracker tracker,String expected) {
        var request=collectedRequest(tracker,expected);
        return request==null?null:new int[]{request.item,request.quantity};
    }
}
