package com.flippingfriend.session;

import com.flippingfriend.core.OfferContinuation;
import com.flippingfriend.data.PluginStorage;
import java.util.ArrayList;
import java.util.Objects;

/** Account-scoped persistence shared by buy replacement and sell relisting. No ledger writes. */
abstract class OfferContinuationStore {
    private final PluginStorage storage;
    private final String file;
    private final boolean buying;
    private String loadedAccount;
    private OfferContinuation intent;
    OfferContinuationStore(PluginStorage storage,String file,boolean buying) {
        this.storage=storage;this.file=file;this.buying=buying;
    }
    protected String account(){return storage.accountId();}
    private boolean load(String expected) {
        if(expected==null || !expected.equals(account())) return false;
        if(!Objects.equals(expected,loadedAccount)) {
            loadedAccount=expected;
            intent=storage.readJson(storage.accountDir(expected).resolve(file),OfferContinuation.class,null);
            if(intent!=null && intent.isBuying()!=buying) intent=null;
        }
        return expected.equals(account());
    }
    private void save() {
        if(loadedAccount!=null && loadedAccount.equals(account()))
            storage.writeJson(storage.accountDir(loadedAccount).resolve(file),intent,OfferContinuation.class);
    }
    protected synchronized void adviseOffer(TrackedOffer original,String expected,boolean managed,OfferTracker tracker,
            long now,long expiresAt) {
        if(!load(expected) || original==null || original.isBuying()!=buying) return;
        var observation=observation(original);
        if(tracker!=null) {
            var current=observation(tracker.getOffer(original.getSlot()));
            if(current==null || !Objects.equals(current.episode,observation.episode) || current.firstSeen!=observation.firstSeen
                || current.item!=observation.item || current.buying!=buying || current.price!=observation.price
                || current.total!=observation.total || !(buying?"BUYING":"SELLING").equals(current.state)) return;
        }
        if(intent!=null && intent.matches(observation) && intent.valid(now)) {
            long revision=intent.getRevision();if(managed)intent.requireManagedCost();
            if(intent.getRevision()!=revision)save();return;
        }
        var created=OfferContinuation.advise(observation,board(tracker),managed,now,expiresAt);
        if(created!=null){intent=created;save();}
    }
    protected synchronized OfferContinuation advance(OfferTracker tracker,String expected,long now) {
        if(!load(expected) || intent==null) return null;
        long revision=intent.getRevision();
        var status=intent.observe(board(tracker),observation(tracker.getSettledOffer(intent.getSlot())),now);
        if(status==OfferContinuation.Status.CLOSED){intent=null;save();return null;}
        if(intent.getRevision()!=revision)save();
        return status==OfferContinuation.Status.COLLECTED?intent:null;
    }
    protected synchronized boolean pending(int item,OfferTracker tracker,String expected,long now) {
        advance(tracker,expected,now);
        return load(expected) && intent!=null && intent.getItemId()==item && intent.valid(now);
    }
    protected synchronized int pendingItem(OfferTracker tracker,String expected,long now) {
        advance(tracker,expected,now);
        return load(expected) && intent!=null && intent.valid(now)?intent.getItemId():0;
    }
    protected synchronized boolean managed(String expected) {
        return !load(expected) || intent!=null && intent.isManaged();
    }
    protected synchronized void abandon(String expected) {
        if(load(expected) && intent!=null){intent=null;save();}
    }
    private static java.util.List<OfferContinuation.Observation> board(OfferTracker tracker) {
        var result=new ArrayList<OfferContinuation.Observation>();
        if(tracker!=null) for(var offer:tracker.getOffers()) result.add(observation(offer));
        return result;
    }
    private static OfferContinuation.Observation observation(TrackedOffer o) {
        return o==null?null:new OfferContinuation.Observation(o.getSlot(),o.getItemId(),o.isBuying(),o.getPrice(),
            o.getTotalQuantity(),o.getQuantityFilled(),o.getFirstSeen(),o.getEpisodeIdentity(),o.getState(),o.isCollected());
    }
}
