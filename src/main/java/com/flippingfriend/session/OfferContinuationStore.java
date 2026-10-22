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
        List<Retirement> retiredContinuations;
        Document(String account,State state){accountId=account;continuations=new ArrayList<>(state.intents);
            retiredContinuations=new ArrayList<>(state.retirements);}
    }
    private static final class Retirement {
        OfferContinuation continuation;
        com.flippingfriend.core.ContinuationDisposition sale;
        Retirement(OfferContinuation original,com.flippingfriend.core.ContinuationDisposition source){continuation=original;sale=source;}
    }
    private final PluginStorage storage;
    private final String file;
    private final boolean buying;
    private final Gson gson=new Gson();
    private static final class State {
        final List<OfferContinuation> intents;
        byte[] committed;
        final Set<String> committedIdentities=new HashSet<>();
        boolean dirty;
        final List<Retirement> retirements=new ArrayList<>();
        State(List<OfferContinuation> intents,byte[] committed){
            this.intents=intents;this.committed=committed;
            for(var intent:intents)committedIdentities.add(intent.getOriginalIdentity());
        }
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
            List<Retirement> retirements=new ArrayList<>();
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
                        if(document.retiredContinuations!=null)retirements.addAll(document.retiredContinuations);
                    }else restored.add(gson.fromJson(object,OfferContinuation.class));
                }
            }
            if(!validEvidence(restored,retirements))return false;
            if(!expected.equals(account()))return false;
            state=new State(restored,image);accounts.put(expected,state);
            state.retirements.addAll(retirements);
            return true;
        }catch(Exception unreadable){return false;}
    }
    private boolean validEvidence(List<OfferContinuation> intents,List<Retirement> retirements) {
        Set<String> identities=new HashSet<>();
        for(var intent:intents)if(intent==null || !intent.isWellFormed() || intent.isBuying()!=buying
            || !identities.add(intent.getOriginalIdentity()))return false;
        for(var retired:retirements)if(retired==null || retired.continuation==null || !retired.continuation.isWellFormed()
            || !buying || !retired.continuation.supersededBy(retired.sale)
            || !identities.add(retired.continuation.getOriginalIdentity()))return false;
        return true;
    }
    private boolean commit(String expected) {
        if(expected==null || !expected.equals(account()) || state!=accounts.get(expected))return false;
        try {
            Path path=storage.accountDir(expected).resolve(file);
            if(!Arrays.equals(contents(path),state.committed))return false;
            if(!validEvidence(state.intents,state.retirements))return false;
            if(!state.dirty)return true;
            byte[] image=gson.toJson(new Document(expected,state)).getBytes(StandardCharsets.UTF_8);
            if(!storage.writeDurably(path,image))return false;
            state.committed=image;state.dirty=false;
            state.committedIdentities.clear();
            for(var intent:state.intents)state.committedIdentities.add(intent.getOriginalIdentity());
            return expected.equals(account());
        }catch(Exception unavailable){return false;}
    }
    /** Presentation changes are prospective promises, not callback facts eligible for background retry. */
    private boolean commitAdvice(String expected,List<OfferContinuation> proposed) {
        if(expected==null || !expected.equals(account()) || state!=accounts.get(expected))return false;
        try {
            Path path=storage.accountDir(expected).resolve(file);
            if(!Arrays.equals(contents(path),state.committed) || !validEvidence(proposed,state.retirements))return false;
            State candidate=new State(proposed,state.committed);candidate.retirements.addAll(state.retirements);
            byte[] image=gson.toJson(new Document(expected,candidate)).getBytes(StandardCharsets.UTF_8);
            if(!storage.writeDurably(path,image))return false;
            // Publish only the successfully committed candidate. A failed initial hint/source upgrade
            // must never ride along with a later collection or an unrelated account's observation.
            state.intents.clear();state.intents.addAll(proposed);
            state.committed=image;state.dirty=false;state.committedIdentities.clear();
            for(var intent:proposed)state.committedIdentities.add(intent.getOriginalIdentity());
            return expected.equals(account());
        }catch(Exception unavailable){return false;}
    }
    protected synchronized boolean adviseOffer(TrackedOffer original,String expected,boolean managed,OfferTracker tracker,
            long now,long expiresAt) {
        return adviseOffer(original,expected,managed,tracker,now,expiresAt,null);
    }
    protected synchronized boolean adviseOffer(TrackedOffer original,String expected,boolean managed,OfferTracker tracker,
            long now,long expiresAt,String saleRepriceSource) {
        if(!load(expected) || original==null || original.isBuying()!=buying)return false;
        var evidence=tracker==null?null:tracker.getContinuationEvidence();
        if(evidence!=null && (!evidence.isAvailable() || !evidence.isForAccount(expected)))return false;
        if(saleRepriceSource!=null && (buying || saleRepriceSource.isBlank()
            || original.getLastFillObservedAt()<original.getFirstSeen() || original.getLastFillObservedAt()>now))return false;
        var observation=observation(original);
        for(var retired:state.retirements)if(retired.continuation.matches(observation))return false;
        if(tracker!=null) {
            var current=observation(evidence==null?tracker.getOffer(original.getSlot()):evidence.getOffer(original.getSlot()));
            if(current==null || !Objects.equals(current.episode,observation.episode) || current.firstSeen!=observation.firstSeen
                || current.item!=observation.item || current.buying!=buying || current.price!=observation.price
                || current.total!=observation.total || !(buying?"BUYING":"SELLING").equals(current.state))return false;
        }
        for(int index=0;index<state.intents.size();index++)if(state.intents.get(index).matches(observation)) {
            var originalIntent=state.intents.get(index);
            // Clone only the acceptance candidate. Existing dirty progress/removals remain in state
            // and keep their normal retry behavior if this prospective write is refused.
            var intent=gson.fromJson(gson.toJson(originalIntent),OfferContinuation.class);
            if(intent.hasFutureEvidence(now))return false;
            intent.retainForFreshRevalidation();
            if(!intent.valid(now))return false;
            if(managed)intent.requireManagedCost();
            if(saleRepriceSource!=null && intent.getSaleRepriceSource()==null)
                intent.bindSaleRepriceSource(saleRepriceSource,original.getLastFillObservedAt(),now);
            if(intent.getRevision()==0)return commit(expected);
            var proposed=new ArrayList<>(state.intents);proposed.set(index,intent);
            return commitAdvice(expected,proposed);
        }
        var created=OfferContinuation.advise(observation,board(tracker,evidence),managed,now,expiresAt);
        if(created==null)return false;
        if(saleRepriceSource!=null)created.bindSaleRepriceSource(saleRepriceSource,original.getLastFillObservedAt(),now);
        var proposed=new ArrayList<>(state.intents);proposed.add(created);
        return commitAdvice(expected,proposed);
    }
    protected synchronized List<OfferContinuation> collectedAll(OfferTracker tracker,String expected,long now) {
        if(!load(expected))return Collections.emptyList();
        var evidence=tracker==null?null:tracker.getContinuationEvidence();
        if(evidence!=null && (!evidence.isAvailable() || !evidence.isForAccount(expected)))return Collections.emptyList();
        var board=board(tracker,evidence);var collected=new ArrayList<OfferContinuation>();boolean changed=false;
        var iterator=state.intents.iterator();
        while(iterator.hasNext()) {
            var intent=iterator.next();long revision=intent.getRevision();
            // A clock rollback is not an expiry or a disposal. Preserve even legacy expiry
            // metadata until the original evidence can be evaluated at its recorded time.
            if(!intent.hasFutureEvidence(now))intent.retainForFreshRevalidation();
            var status=intent.observe(board,tracker==null?null:observation(evidence==null?
                tracker.getSettledOffer(intent.getSlot()):evidence.getSettledOffer(intent.getSlot())),now);
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
        return retained!=null && retained.intents.stream().anyMatch(intent->intent.getItemId()==item
            && (intent.valid(now) || intent.hasFutureEvidence(now)));
    }
    protected synchronized boolean acknowledgedCancellation(TrackedOffer offer,String expected,long now) {
        if(!load(expected))return false;
        var observation=observation(offer);
        return state.intents.stream().anyMatch(intent->state.committedIdentities.contains(intent.getOriginalIdentity())
            && intent.acknowledgesCancellation(observation,now));
    }
    protected synchronized int pendingItem(OfferTracker tracker,String expected,long now) {
        var collected=collectedAll(tracker,expected,now);
        if(!collected.isEmpty())return collected.get(0).getItemId();
        State retained=expected!=null && expected.equals(account())?accounts.get(expected):null;
        return retained==null?0:retained.intents.stream().filter(intent->intent.valid(now) || intent.hasFutureEvidence(now))
            .mapToInt(OfferContinuation::getItemId).findFirst().orElse(0);
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
    protected synchronized boolean observeSales(com.flippingfriend.core.AccountLedgerSnapshot ledger,String expected,long now) {
        if(!buying || ledger==null || !ledger.isValid() || !Objects.equals(expected,ledger.getAccountId())
            || ledger.getObservedAt()>now || now-ledger.getObservedAt()>150 || ledger.getContinuationDispositions()==null
            || !load(expected))return false;
        var iterator=state.intents.iterator();
        while(iterator.hasNext()) {
            var intent=iterator.next();
            if(intent.hasFutureEvidence(now))continue;
            for(var sale:ledger.getContinuationDispositions())if(intent.supersededBy(sale)) {
                state.retirements.add(new Retirement(intent,sale));iterator.remove();state.dirty=true;break;
            }
        }
        return commit(expected);
    }
    private List<OfferContinuation.Observation> board(OfferTracker tracker,OfferTracker.ContinuationEvidence evidence) {
        var result=new ArrayList<OfferContinuation.Observation>();
        if(tracker!=null) {
            for(var offer:evidence==null?tracker.getOffers():evidence.getOffers())result.add(observation(offer));
            // A sale can complete/collect between worker passes. Preserve that exit decision too.
            if(buying)for(int slot=0;slot<8;slot++) {
                var settled=evidence==null?tracker.getSettledOffer(slot):evidence.getSettledOffer(slot);
                if(settled!=null && !settled.isBuying())result.add(observation(settled));
            }
        }
        return result;
    }
    private static OfferContinuation.Observation observation(TrackedOffer offer) {
        return offer==null?null:new OfferContinuation.Observation(offer.getSlot(),offer.getItemId(),offer.isBuying(),offer.getPrice(),
            offer.getTotalQuantity(),offer.getQuantityFilled(),offer.getFirstSeen(),offer.getEpisodeIdentity(),offer.getState(),offer.isCollected());
    }
}
