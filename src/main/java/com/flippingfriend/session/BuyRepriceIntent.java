package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Durable exact-offer continuation; a fresh companion plan must authorize any replacement. */
@Singleton
public final class BuyRepriceIntent extends OfferContinuationStore {
    public static final class Request {
        public final int itemId,quantity;
        public final String originIdentity;
        private Request(int itemId,int quantity,String origin){this.itemId=itemId;this.quantity=quantity;originIdentity=origin;}
    }
    @Inject public BuyRepriceIntent(PluginStorage storage){super(storage,"buy-reprice-intent.json",true);}
    public synchronized boolean advise(TrackedOffer offer,String expected,OfferTracker tracker,long now) {
        return adviseOffer(offer,expected,false,tracker,now,0);
    }
    public synchronized void observe(OfferTracker tracker,String expected,long now){advance(tracker,expected,now);}
    public synchronized boolean observeRecordedSales(com.flippingfriend.core.AccountLedgerSnapshot ledger,String expected,long now) {
        return observeSales(ledger,expected,now);
    }
    public synchronized boolean isGuidedCancellation(TrackedOffer offer,String expected,long now) {
        return acknowledgedCancellation(offer,expected,now);
    }
    public synchronized Request collectedRequest(OfferTracker tracker,String expected,long now) {
        var intent=advance(tracker,expected,now);
        return intent==null?null:new Request(intent.getItemId(),intent.getCollectedRemaining(),intent.getOriginalIdentity());
    }
    public synchronized java.util.List<Request> collectedRequests(OfferTracker tracker,String expected,long now) {
        var requests=new java.util.ArrayList<Request>();
        for(var intent:collectedAll(tracker,expected,now))requests.add(new Request(intent.getItemId(),intent.getCollectedRemaining(),intent.getOriginalIdentity()));
        return java.util.Collections.unmodifiableList(requests);
    }
    public synchronized boolean isPending(int item,OfferTracker tracker,String expected,long now){return pending(item,tracker,expected,now);}
    public synchronized int pendingItemId(OfferTracker tracker,String expected,long now){return pendingItem(tracker,expected,now);}
    public synchronized boolean clear(String expected){return abandon(expected);}
    public synchronized boolean clearItem(String expected,int item){return abandonItem(expected,item);}
}
