package com.flippingfriend.session;

import com.flippingfriend.core.OfferContinuation;
import com.flippingfriend.data.PluginStorage;
import com.google.gson.*;
import java.nio.file.*;
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
    private String loadedAccount;
    private List<OfferContinuation> intents=new ArrayList<>();
    OfferContinuationStore(PluginStorage storage,String file,boolean buying) {
        this.storage=storage;this.file=file;this.buying=buying;
    }
    protected String account(){return storage.accountId();}
    private boolean load(String expected) {
        if(expected==null || !expected.equals(account()))return false;
        if(!Objects.equals(expected,loadedAccount)) {
            var path=storage.accountDir(expected).resolve(file);List<OfferContinuation> restored=new ArrayList<>();
            try {
                if(Files.isRegularFile(path)) {
                    JsonElement tree=gson.fromJson(Files.readString(path),JsonElement.class);
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
            }catch(Exception unreadable){return false;} // Never overwrite unreadable or foreign evidence as empty.
            if(!expected.equals(account()))return false;
            loadedAccount=expected;intents=restored;
            boolean retained=false;for(var intent:intents)retained|=intent.retainForFreshRevalidation();
            if(retained)save();
        }
        return expected.equals(account());
    }
    private void save() {
        if(loadedAccount!=null && loadedAccount.equals(account()))
            storage.writeJson(storage.accountDir(loadedAccount).resolve(file),new Document(loadedAccount,intents),Document.class);
    }
    protected synchronized void adviseOffer(TrackedOffer original,String expected,boolean managed,OfferTracker tracker,
            long now,long expiresAt) {
        if(!load(expected) || original==null || original.isBuying()!=buying)return;
        var observation=observation(original);
        if(tracker!=null) {
            var current=observation(tracker.getOffer(original.getSlot()));
            if(current==null || !Objects.equals(current.episode,observation.episode) || current.firstSeen!=observation.firstSeen
                || current.item!=observation.item || current.buying!=buying || current.price!=observation.price
                || current.total!=observation.total || !(buying?"BUYING":"SELLING").equals(current.state))return;
        }
        for(var intent:intents)if(intent.matches(observation) && intent.valid(now)) {
            long revision=intent.getRevision();if(managed)intent.requireManagedCost();
            if(intent.getRevision()!=revision)save();return;
        }
        var created=OfferContinuation.advise(observation,board(tracker),managed,now,expiresAt);
        if(created!=null){intents.add(created);save();}
    }
    protected synchronized List<OfferContinuation> collectedAll(OfferTracker tracker,String expected,long now) {
        if(!load(expected))return Collections.emptyList();
        var board=board(tracker);var collected=new ArrayList<OfferContinuation>();boolean changed=false;
        var iterator=intents.iterator();
        while(iterator.hasNext()) {
            var intent=iterator.next();long revision=intent.getRevision();
            var status=intent.observe(board,tracker==null?null:observation(tracker.getSettledOffer(intent.getSlot())),now);
            changed|=intent.getRevision()!=revision;
            if(status==OfferContinuation.Status.CLOSED){iterator.remove();changed=true;}
            else if(status==OfferContinuation.Status.COLLECTED)collected.add(intent);
        }
        if(changed)save();return Collections.unmodifiableList(collected);
    }
    protected synchronized OfferContinuation advance(OfferTracker tracker,String expected,long now) {
        var collected=collectedAll(tracker,expected,now);return collected.isEmpty()?null:collected.get(0);
    }
    protected synchronized boolean pending(int item,OfferTracker tracker,String expected,long now) {
        collectedAll(tracker,expected,now);
        return load(expected) && intents.stream().anyMatch(intent->intent.getItemId()==item && intent.valid(now));
    }
    protected synchronized int pendingItem(OfferTracker tracker,String expected,long now) {
        var collected=collectedAll(tracker,expected,now);
        if(!collected.isEmpty())return collected.get(0).getItemId();
        return load(expected)?intents.stream().filter(intent->intent.valid(now)).mapToInt(OfferContinuation::getItemId).findFirst().orElse(0):0;
    }
    protected synchronized boolean managed(String expected) {
        return !load(expected) || intents.stream().anyMatch(OfferContinuation::isManaged);
    }
    protected synchronized void abandon(String expected) {
        if(load(expected) && !intents.isEmpty()){intents.clear();save();}
    }
    protected synchronized void abandonItem(String expected,int item) {
        if(load(expected) && intents.removeIf(intent->intent.getItemId()==item))save();
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
