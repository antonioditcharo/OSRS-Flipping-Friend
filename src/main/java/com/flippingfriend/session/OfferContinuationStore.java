package com.flippingfriend.session;

import com.flippingfriend.core.OfferContinuation;
import com.flippingfriend.data.PluginStorage;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Account-scoped continuation queue. Its hints never book executions or authorize orders. */
abstract class OfferContinuationStore {
    private static final class Document {
        int formatVersion=2;
        String accountId;
        List<OfferContinuation> continuations;
        Document(String account,List<OfferContinuation> values){accountId=account;continuations=new ArrayList<>(values);}
    }
    private final PluginStorage storage;
    private final String file;
    private final boolean buying;
    private final Gson gson=new Gson();
    private static final class State {
        final List<OfferContinuation> intents;
        byte[] committed;
        boolean dirty;
        State(List<OfferContinuation> intents,byte[] committed){this.intents=intents;this.committed=committed;}
    }
    // Keep unsaved callback evidence across profile switches without cross-account writes.
    private final Map<String,State> accounts=new HashMap<>();
    private State state;
    OfferContinuationStore(PluginStorage storage,String file,boolean buying) {
        this.storage=storage;this.file=file;this.buying=buying;
    }
    protected String account(){return storage.accountId();}
    private byte[] contents(Path path)throws java.io.IOException {
        return Files.exists(path)?Files.readAllBytes(path):null;
    }
    private boolean load(String expected) {
        if(expected==null || !expected.equals(account()))return false;
        try {
            byte[] image=contents(storage.accountDir(expected).resolve(file));
            State cached=accounts.get(expected);
            if(cached!=null) {
                state=cached;
                if(Arrays.equals(image,cached.committed))return expected.equals(account());
                // An external replacement/rollback cannot silently delete an in-memory promise.
                return false;
            }
            List<OfferContinuation> restored=new ArrayList<>();
            if(image!=null) {
                JsonElement tree=gson.fromJson(new String(image,StandardCharsets.UTF_8),JsonElement.class);
                if(tree==null)return false;
                if(!tree.isJsonNull()) {
                    var object=tree.getAsJsonObject();
                    if(object.has("formatVersion")) {
                        var version=object.get("formatVersion");
                        if(!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber() || version.getAsBigDecimal().intValueExact()!=2)return false;
                        var document=gson.fromJson(object,Document.class);
                        if(document.formatVersion!=2 || !expected.equals(document.accountId) || document.continuations==null)return false;
                        restored.addAll(document.continuations);
                    }else restored.add(gson.fromJson(object,OfferContinuation.class));
                }
            }
            Set<String> identities=new HashSet<>();
            for(var intent:restored)if(intent==null || !intent.isWellFormed() || intent.isBuying()!=buying
                || !identities.add(intent.getOriginalIdentity()))return false;
            if(!expected.equals(account()))return false;
            state=new State(restored,image);accounts.put(expected,state);
            for(var intent:restored)state.dirty|=intent.retainForFreshRevalidation();
            return true;
        }catch(Exception unreadable){return false;}
    }
    private boolean commit(String expected) {
        if(expected==null || !expected.equals(account()) || state!=accounts.get(expected))return false;
        try {
            Path path=storage.accountDir(expected).resolve(file);
            if(!Arrays.equals(contents(path),state.committed))return false;
            if(!state.dirty)return true;
            byte[] image=gson.toJson(new Document(expected,state.intents)).getBytes(StandardCharsets.UTF_8);
            if(!storage.writeDurably(path,image))return false;
            state.committed=image;state.dirty=false;
            return expected.equals(account());
        }catch(Exception unavailable){return false;}
    }
    protected synchronized boolean adviseOffer(TrackedOffer original,String expected,boolean managed,OfferTracker tracker,
            long now,long expiresAt) {
        if(!load(expected) || original==null || original.isBuying()!=buying)return false;
        var observation=observation(original);
        if(tracker!=null) {
            var current=observation(tracker.getOffer(original.getSlot()));
            if(current==null || !Objects.equals(current.episode,observation.episode) || current.firstSeen!=observation.firstSeen
                || current.item!=observation.item || current.buying!=buying || current.price!=observation.price
                || current.total!=observation.total || !(buying?"BUYING":"SELLING").equals(current.state))return false;
        }
        for(var intent:state.intents)if(intent.matches(observation) && intent.valid(now)) {
            long revision=intent.getRevision();if(managed)intent.requireManagedCost();
            state.dirty|=intent.getRevision()!=revision;return commit(expected);
        }
        var created=OfferContinuation.advise(observation,board(tracker),managed,now,expiresAt);
        if(created==null)return false;
        state.intents.add(created);state.dirty=true;return commit(expected);
    }
    protected synchronized List<OfferContinuation> collectedAll(OfferTracker tracker,String expected,long now) {
        if(!load(expected))return Collections.emptyList();
        var board=board(tracker);var collected=new ArrayList<OfferContinuation>();boolean changed=false;
        var iterator=state.intents.iterator();
        while(iterator.hasNext()) {
            var intent=iterator.next();long revision=intent.getRevision();
            var status=intent.observe(board,tracker==null?null:observation(tracker.getSettledOffer(intent.getSlot())),now);
            changed|=intent.getRevision()!=revision;
            if(status==OfferContinuation.Status.CLOSED){iterator.remove();changed=true;}
            else if(status==OfferContinuation.Status.COLLECTED)collected.add(intent);
        }
        state.dirty|=changed;
        // Retry without a new revision: temporary settled evidence may already be pruned.
        return commit(expected)?Collections.unmodifiableList(collected):Collections.emptyList();
    }
    protected synchronized OfferContinuation advance(OfferTracker tracker,String expected,long now) {
        var collected=collectedAll(tracker,expected,now);return collected.isEmpty()?null:collected.get(0);
    }
    protected synchronized boolean pending(int item,OfferTracker tracker,String expected,long now) {
        collectedAll(tracker,expected,now);
        State retained=expected!=null && expected.equals(account())?accounts.get(expected):null;
        return retained!=null && retained.intents.stream().anyMatch(intent->intent.getItemId()==item && intent.valid(now));
    }
    protected synchronized int pendingItem(OfferTracker tracker,String expected,long now) {
        var collected=collectedAll(tracker,expected,now);
        if(!collected.isEmpty())return collected.get(0).getItemId();
        State retained=expected!=null && expected.equals(account())?accounts.get(expected):null;
        return retained==null?0:retained.intents.stream().filter(intent->intent.valid(now)).mapToInt(OfferContinuation::getItemId).findFirst().orElse(0);
    }
    protected synchronized boolean managed(String expected) {
        return !load(expected) || state.intents.stream().anyMatch(OfferContinuation::isManaged);
    }
    protected synchronized boolean abandon(String expected) {
        if(!load(expected))return false;
        if(!state.intents.isEmpty()){state.intents.clear();state.dirty=true;}return commit(expected);
    }
    protected synchronized boolean abandonItem(String expected,int item) {
        if(!load(expected))return false;
        state.dirty|=state.intents.removeIf(intent->intent.getItemId()==item);return commit(expected);
    }
    private static List<OfferContinuation.Observation> board(OfferTracker tracker) {
        var result=new ArrayList<OfferContinuation.Observation>();
        if(tracker!=null)for(var offer:tracker.getOffers())result.add(observation(offer));return result;
    }
    private static OfferContinuation.Observation observation(TrackedOffer offer) {
        return offer==null?null:new OfferContinuation.Observation(offer.getSlot(),offer.getItemId(),offer.isBuying(),offer.getPrice(),
            offer.getTotalQuantity(),offer.getQuantityFilled(),offer.getFirstSeen(),offer.getEpisodeIdentity(),offer.getState(),offer.isCollected());
    }
}
