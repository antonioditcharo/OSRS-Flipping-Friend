package com.flippingfriend.session;

import com.flippingfriend.core.OfferContinuation;
import com.flippingfriend.core.ContinuationSuccessorProof;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.CaptureOrder;
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
        List<OfferContinuation> consumedContinuations;
        List<ContinuationSuccessorProof> successorAcceptances;
        List<OfferContinuation.Observation> unresolvedSuccessors;
        Document(String account,State state){accountId=account;continuations=new ArrayList<>(state.intents);
            retiredContinuations=new ArrayList<>(state.retirements);consumedContinuations=new ArrayList<>(state.consumed);
            successorAcceptances=new ArrayList<>(state.acceptances);unresolvedSuccessors=new ArrayList<>(state.unresolved);}
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
        final List<OfferContinuation> consumed=new ArrayList<>();
        final List<ContinuationSuccessorProof> acceptances=new ArrayList<>();
        final List<OfferContinuation.Observation> unresolved=new ArrayList<>();
        final Set<Integer> blockedItems=new HashSet<>();
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
            List<OfferContinuation> consumed=new ArrayList<>();
            List<ContinuationSuccessorProof> acceptances=new ArrayList<>();
            List<OfferContinuation.Observation> unresolved=new ArrayList<>();
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
                        if(document.consumedContinuations!=null)consumed.addAll(document.consumedContinuations);
                        if(document.successorAcceptances!=null)acceptances.addAll(document.successorAcceptances);
                        if(document.unresolvedSuccessors!=null)unresolved.addAll(document.unresolvedSuccessors);
                    }else restored.add(gson.fromJson(object,OfferContinuation.class));
                }
            }
            if(!validEvidence(restored,retirements))return false;
            if(!expected.equals(account()))return false;
            state=new State(restored,image);accounts.put(expected,state);
            state.retirements.addAll(retirements);
            state.consumed.addAll(consumed);state.acceptances.addAll(acceptances);state.unresolved.addAll(unresolved);
            if(!validState(state,expected)){accounts.remove(expected);state=null;return false;}
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
    private List<OfferContinuation> origins(State current){var all=new ArrayList<>(current.intents);all.addAll(current.consumed);return all;}
    private boolean validState(State current,String expected) {
        if(!validEvidence(current.intents,current.retirements))return false;
        var identities=new HashSet<String>();for(var hint:current.intents)identities.add(hint.getOriginalIdentity());
        for(var hint:current.consumed)if(hint==null || !hint.isWellFormed() || hint.isBuying()!=buying
            || hint.getClaimedQuantity()<=0 || !identities.add(hint.getOriginalIdentity()))return false;
        var acceptedIds=new HashSet<String>();
        for(var proof:current.acceptances)if(proof==null || !proof.isWellFormed() || proof.isBound()
            || !expected.equals(proof.getAccountId()) || proof.isBuying()!=buying
            || !acceptedIds.add(proof.getAcceptedAdviceId()))return false;
        for(var hint:origins(current))for(var proof:hint.getSuccessorClaims())
            if(!expected.equals(proof.getAccountId()))return false;
        var pendingIds=new HashSet<String>();
        for(var observation:current.unresolved)if(observation==null || observation.item<=0 || observation.slot<0 || observation.slot>7
            || observation.price<=0 || observation.total<=0 || observation.firstSeen<=0
            || !pendingIds.add(OfferContinuation.identity(observation.slot,observation.item,observation.buying,
                observation.price,observation.total,observation.firstSeen,observation.episode)))return false;
        return true;
    }
    private void copyContext(State target,State source){
        target.retirements.addAll(source.retirements);target.consumed.addAll(source.consumed);
        target.acceptances.addAll(source.acceptances);target.unresolved.addAll(source.unresolved);
    }
    private boolean commit(String expected) {
        if(expected==null || !expected.equals(account()) || state!=accounts.get(expected))return false;
        try {
            Path path=storage.accountDir(expected).resolve(file);
            if(!Arrays.equals(contents(path),state.committed))return false;
            if(!validState(state,expected))return false;
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
            State candidate=new State(proposed,state.committed);copyContext(candidate,state);
            if(!validState(candidate,expected))return false;
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
    /** Commit the accepted exact successor before any callback may claim an origin. */
    public synchronized ContinuationSuccessorProof acceptSuccessor(String origin,int item,int price,int quantity,
            OfferTracker tracker,String expected,long now) {
        if(origin==null || price<=0 || quantity<=0)return null;
        OfferContinuation chosen=null;
        for(var hint:collectedAll(tracker,expected,now))if(origin.equals(hint.getOriginalIdentity()) && hint.getItemId()==item
            && quantity<=hint.getCollectedRemaining())chosen=hint;
        if(chosen==null || !expected.equals(account()) || state!=accounts.get(expected))return null;
        var order=CaptureOrder.next();
        for(var old:state.acceptances)if(old.getOriginIdentity().equals(origin) && old.getItemId()==item
            && old.getPrice()==price && old.getQuantity()==quantity && now>=old.getAcceptedAt() && now-old.getAcceptedAt()<=600
            && old.getAcceptedOrder().getProcess().equals(order.getProcess())) {
            boolean claimed=false;for(var hint:origins(state))for(var proof:hint.getSuccessorClaims())claimed|=proof.hasSameAcceptance(old);
            if(!claimed)return old;
        }
        var accepted=ContinuationSuccessorProof.accepted(expected,origin,chosen.getSaleRepriceSource(),UUID.randomUUID().toString(),
            item,buying,price,quantity,now,order);
        try {
            var path=storage.accountDir(expected).resolve(file);
            if(!Arrays.equals(contents(path),state.committed))return null;
            var candidate=new State(new ArrayList<>(state.intents),state.committed);copyContext(candidate,state);candidate.acceptances.add(accepted);
            if(!validState(candidate,expected))return null;
            byte[] image=gson.toJson(new Document(expected,candidate)).getBytes(StandardCharsets.UTF_8);
            if(!storage.writeDurably(path,image))return null;
            state.acceptances.add(accepted);state.committed=image;state.dirty=false;
            state.committedIdentities.clear();for(var hint:state.intents)state.committedIdentities.add(hint.getOriginalIdentity());
            return expected.equals(account())?accepted:null;
        }catch(Exception unavailable){return null;}
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
        if(evidence!=null && (!evidence.hasObservationEvidence() || !evidence.isForAccount(expected)))return Collections.emptyList();
        // A failed relationship-source read withdraws guidance, not already processed client
        // counter/collection facts. Preserve those facts before settled observations can expire.
        boolean sourcesAvailable=evidence==null || evidence.hasSourceEvidence();
        var board=board(tracker,evidence);var collected=new ArrayList<OfferContinuation>();boolean changed=false;
        var proofs=qualifiedSuccessors(evidence,expected);
        var unresolved=new HashSet<String>();
        if(evidence!=null && evidence.requiresSources()) {
            var candidates=new ArrayList<>(board);
            for(int slot=0;slot<8;slot++) {
                var settled=evidence.getSettledOffer(slot);if(settled!=null)candidates.add(observation(settled));
            }
            // Cached episodes may establish a pending barrier, never collection/counter facts.
            for(var cached:tracker.getOffers())candidates.add(observation(cached));
            var retainedIds=new HashSet<String>();for(var old:state.unresolved)retainedIds.add(identity(old));
            for(var candidate:candidates)if(candidate!=null && candidate.item>0 && candidate.price>0 && candidate.total>0
                && candidate.firstSeen>0 && needsClassification(candidate)) {
                if(classified(candidate,evidence,proofs,expected))board.add(candidate);
                else if(retainedIds.add(identity(candidate))){state.unresolved.add(candidate);changed=true;}
            }
            var waiting=state.unresolved.iterator();
            while(waiting.hasNext()) {
                var candidate=waiting.next();
                if(classified(candidate,evidence,proofs,expected)) {
                    // Retain the classified episode for this reducer pass even if its slot was
                    // cleared while capture retried. Absence cannot erase a pending supersession.
                    board.add(candidate);waiting.remove();changed=true;
                }else {unresolved.add(identity(candidate));board.add(candidate);}
            }
        }else for(var pending:state.unresolved){unresolved.add(identity(pending));board.add(pending);}
        state.blockedItems.clear();for(var pending:state.unresolved)state.blockedItems.add(pending.item);
        for(var proof:proofs)if(proof.getAcceptedAt()>now || proof.getFirstSeenAt()>now || proof.getCapturedAt()>now)state.blockedItems.add(proof.getItemId());
        for(var consumed:state.consumed) {
            long revision=consumed.getRevision();
            var status=consumed.observeConsumedBudget(withoutCachedOriginal(board,consumed,evidence),settled(tracker,evidence,consumed),proofs);
            changed|=revision!=consumed.getRevision();
            if(status==OfferContinuation.Status.UNKNOWN || consumed.hasFutureEvidence(now))state.blockedItems.add(consumed.getItemId());
        }
        var iterator=state.intents.iterator();
        while(iterator.hasNext()) {
            var intent=iterator.next();long revision=intent.getRevision();
            // A clock rollback is not an expiry or a disposal. Preserve even legacy expiry
            // metadata until the original evidence can be evaluated at its recorded time.
            if(!intent.hasFutureEvidence(now))intent.retainForFreshRevalidation();
            var status=intent.observe(withoutCachedOriginal(board,intent,evidence),settled(tracker,evidence,intent),now,proofs,unresolved);
            changed|=intent.getRevision()!=revision;
            if(status==OfferContinuation.Status.UNKNOWN || intent.getClaimedQuantity()>0 && intent.hasFutureEvidence(now))state.blockedItems.add(intent.getItemId());
            if(status==OfferContinuation.Status.CLOSED){
                if(intent.getClaimedQuantity()>0)state.consumed.add(intent);
                iterator.remove();changed=true;
            }
            else if(status==OfferContinuation.Status.COLLECTED)collected.add(intent);
        }
        state.dirty|=changed;
        collected.removeIf(hint->state.blockedItems.contains(hint.getItemId()));
        // Retry without a new revision: temporary settled evidence may already be pruned.
        return commit(expected) && sourcesAvailable?Collections.unmodifiableList(collected):Collections.emptyList();
    }
    private static String identity(OfferContinuation.Observation offer){return OfferContinuation.identity(offer.slot,offer.item,
        offer.buying,offer.price,offer.total,offer.firstSeen,offer.episode);}
    private boolean needsClassification(OfferContinuation.Observation offer){
        // Retained original episodes are already known workflow evidence, not new successors
        // of a child accepted later. Their counters enter only their own current observation.
        for(var hint:origins(state))if(hint.matches(offer))return false;
        for(var hint:origins(state))if(hint.getItemId()==offer.item && !hint.recognizes(offer))return true;
        return false;
    }
    private OfferContinuation.Observation settled(OfferTracker tracker,OfferTracker.ContinuationEvidence evidence,OfferContinuation hint){
        return tracker==null?null:observation(evidence==null?tracker.getSettledOffer(hint.getSlot()):evidence.getSettledOffer(hint.getSlot()));
    }
    private List<OfferContinuation.Observation> withoutCachedOriginal(List<OfferContinuation.Observation> board,
            OfferContinuation hint,OfferTracker.ContinuationEvidence evidence) {
        if(evidence==null)return board;
        var result=new ArrayList<OfferContinuation.Observation>();
        var current=evidence.getOffer(hint.getSlot());
        for(var offer:board)if(!hint.matches(offer) || current!=null && hint.matches(observation(current)))result.add(offer);
        return result;
    }
    private boolean classified(OfferContinuation.Observation offer,OfferTracker.ContinuationEvidence evidence,
            List<ContinuationSuccessorProof> proofs,String expected){
        for(var proof:proofs)if(proof.matches(offer))return true;
        for(var source:evidence.getFirstSources())if(source!=null && source.isAuthenticatedObservation()
            && expected.equals(source.getAccountId()) && Objects.equals(identity(offer),ContinuationSuccessorProof.workflowIdentity(source))
            && source.getSlot()==offer.slot && source.getItemId()==offer.item && source.isBuying()==offer.buying
            && source.getPrice()==offer.price && source.getTotalQuantity()==offer.total && source.getFirstSeenAt()==offer.firstSeen)return true;
        return false;
    }
    private List<ContinuationSuccessorProof> qualifiedSuccessors(OfferTracker.ContinuationEvidence evidence,String expected){
        var proofs=new LinkedHashMap<String,ContinuationSuccessorProof>();
        for(var hint:origins(state))for(var proof:hint.getSuccessorClaims())proofs.put(proof.getSuccessorIdentity(),proof);
        if(evidence!=null)for(var source:evidence.getFirstSources()) {
            if(source==null || !expected.equals(source.getAccountId()))continue;
            var proof=source.getContinuationSuccessorProof();
            if(proof==null || !proof.qualifiesOriginal(source) || !expected.equals(proof.getAccountId()) || proof.isBuying()!=buying)continue;
            OfferContinuation origin=null;for(var hint:origins(state))if(hint.getOriginalIdentity().equals(proof.getOriginIdentity()))origin=hint;
            if(origin==null || origin.getItemId()!=proof.getItemId() || origin.isBuying()!=proof.isBuying()
                || !Objects.equals(origin.getSaleRepriceSource(),proof.getOriginSaleRepriceSource()))continue;
            boolean accepted=false;for(var saved:state.acceptances)accepted|=saved.hasSameAcceptance(proof);
            for(var saved:origin.getSuccessorClaims())accepted|=saved.equals(proof);
            if(!accepted)continue;
            var previous=proofs.get(proof.getSuccessorIdentity());
            if(previous==null || previous.equals(proof))proofs.put(proof.getSuccessorIdentity(),proof);
        }
        return new ArrayList<>(proofs.values());
    }
    protected synchronized OfferContinuation advance(OfferTracker tracker,String expected,long now) {
        var collected=collectedAll(tracker,expected,now);return collected.isEmpty()?null:collected.get(0);
    }
    protected synchronized boolean pending(int item,OfferTracker tracker,String expected,long now) {
        collectedAll(tracker,expected,now);
        State retained=expected!=null && expected.equals(account())?accounts.get(expected):null;
        return retained!=null && (retained.blockedItems.contains(item) || retained.intents.stream().anyMatch(intent->intent.getItemId()==item
            && (intent.valid(now) || intent.hasFutureEvidence(now))));
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
        if(!state.intents.isEmpty()){
            for(var hint:state.intents)retainConsumed(hint);
            state.intents.clear();state.dirty=true;
        }return commit(expected);
    }
    protected synchronized boolean abandonItem(String expected,int item) {
        if(!load(expected))return false;
        for(var hint:state.intents)if(hint.getItemId()==item)retainConsumed(hint);
        state.dirty|=state.intents.removeIf(intent->intent.getItemId()==item);return commit(expected);
    }
    private void retainConsumed(OfferContinuation hint){
        if(hint.getClaimedQuantity()<=0)return;
        for(var old:state.consumed)if(old.getOriginalIdentity().equals(hint.getOriginalIdentity()))return;
        state.consumed.add(hint);
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
                retainConsumed(intent);state.retirements.add(new Retirement(intent,sale));iterator.remove();state.dirty=true;break;
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
