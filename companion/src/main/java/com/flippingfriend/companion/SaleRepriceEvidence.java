package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only proof of one exact working sale, its late fills, cancellation and collection. */
final class SaleRepriceEvidence {
    final OfferEvent original, cancelled;
    final long lastProgressAt;
    final long collectedAt;
    final int remaining;
    private SaleRepriceEvidence(OfferEvent original,OfferEvent cancelled,long progress,long collected,int remaining) {
        this.original=original;this.cancelled=cancelled;lastProgressAt=progress;collectedAt=collected;this.remaining=remaining;
    }
    static SaleRepriceEvidence prove(List<OfferEvent> history,String source,String account,int item,int quantity,long now) {
        return prove(history,source,account,item,quantity,now,0);
    }
    static SaleRepriceEvidence prove(List<OfferEvent> history,String source,String account,int item,int quantity,long now,long parentPatience) {
        var qualified=qualifiedSuccessors(history,account,item,now,parentPatience);
        return qualified==null?null:proveChain(history,source,account,item,quantity,now,qualified);
    }
    /** Qualify first sources in durable order; a child cannot bootstrap a later or cyclic origin. */
    static List<ContinuationSuccessorProof> qualifiedSuccessors(List<OfferEvent> history,String account,int item,long now) {
        return qualifiedSuccessors(history,account,item,now,0);
    }
    static List<ContinuationSuccessorProof> qualifiedSuccessors(List<OfferEvent> history,String account,int item,long now,long parentPatience) {
        if(history==null || account==null || parentPatience<0)return null;
        var qualified=new ArrayList<ContinuationSuccessorProof>();
        var sources=new HashMap<String,OfferEvent>();var first=new HashMap<String,OfferEvent>();
        var acceptanceOrders=new HashSet<String>();var adviceIds=new HashSet<String>();
        for(int index=0;index<history.size();index++) {
            var event=history.get(index);
            if(event==null || !account.equals(event.getAccountId()) || sources.put(event.getEventId(),event)!=null)return null;
            if("EMPTY".equals(event.getEventType()))continue;
            if(first.putIfAbsent(event.getOfferIdentity(),event)!=null)continue;
            var proof=event.getContinuationSuccessorProof();
            if(proof==null || proof.isBuying() || proof.getItemId()!=item || !proof.qualifiesOriginal(event)
                || !validSuccessorEpisode(history,index,proof,now))continue;
            var parent=sources.get(proof.getOriginRepriceSource());
            if(parent==null || parent==event || !proof.getOriginIdentity().equals(ContinuationSuccessorProof.workflowIdentity(parent))
                || proof.getOriginIdentity().equals(proof.getSuccessorIdentity()))continue;
            // An explicitly linked ancestor must itself have qualified prospectively; a malformed
            // association cannot be laundered through a child maintenance instruction.
            var parentFirst=first.get(parent.getOfferIdentity());
            if(parentFirst!=null && parentFirst.getContinuationSuccessorProof()!=null
                && !qualified.contains(parentFirst.getContinuationSuccessorProof()))continue;
            var prefix=history.subList(0,index);
            var origin=proveChain(prefix,proof.getOriginRepriceSource(),account,item,proof.getQuantity(),event.getObservedAt(),qualified);
            if(origin==null || proof.getAcceptedAt()<origin.collectedAt)continue;
            // A later successor acceptance cannot mature an originally too-young cancellation
            // source. Historical urgent exceptions need their own proven policy receipt.
            if(origin.original.getObservedAt()-origin.original.getFirstSeenAt()<parentPatience
                || origin.original.getObservedAt()-origin.lastProgressAt<parentPatience)continue;
            String order=proof.getAcceptedOrder().getProcess()+":"+proof.getAcceptedOrder().getOrder();
            if(!acceptanceOrders.add(order) || !adviceIds.add(proof.getAcceptedAdviceId()))return null;
            qualified.add(proof);
        }
        return Collections.unmodifiableList(qualified);
    }
    private static boolean validSuccessorEpisode(List<OfferEvent> history,int start,ContinuationSuccessorProof proof,long now) {
        OfferLifecycleProjection projection=null;boolean cleared=false;
        for(int index=start;index<history.size();index++) {
            var event=history.get(index);if(event==null)return false;
            if(Objects.equals(proof.getOfferIdentity(),event.getOfferIdentity())) {
                if(cleared || event.getObservedAt()>now || !proof.matchesEpisode(event)
                    || !proof.equals(event.getContinuationSuccessorProof()))return false;
                var transition=OfferLifecycleReducer.apply(projection,event);
                if(!transition.isAccepted())return false;projection=transition.getProjection();
            } else if(!cleared && event.getSlot()==proof.getSlot()) {
                var transition=OfferLifecycleReducer.apply(projection,event);
                if(!event.isAuthenticatedObservation() || !"EMPTY".equals(event.getEventType()) || !transition.isAccepted())return false;
                cleared=true;
            }
        }
        return projection!=null;
    }
    private static SaleRepriceEvidence proveChain(List<OfferEvent> history,String source,String account,int item,int quantity,long now,
            Collection<ContinuationSuccessorProof> successors) {
        if(history==null || source==null || source.isBlank() || quantity<=0)return null;
        OfferEvent origin=null;
        Map<Integer,OfferEvent> beforeAcceptance=new HashMap<>(),existingSales=new HashMap<>();
        for(var e:history) {
            if(e!=null && source.equals(e.getEventId())) {
                if(origin!=null)return null;origin=e;
                for(var previous:beforeAcceptance.values())if(retainedSale(previous,account,item)
                    && !Objects.equals(previous.getOfferIdentity(),origin.getOfferIdentity()))
                    existingSales.put(previous.getSlot(),previous);
            } else if(origin==null && e!=null) {
                // Keep the latest observation even when it is EMPTY/invalid/unauthenticated.
                // Skipping it could resurrect an older sale as accepted-board evidence.
                beforeAcceptance.put(e.getSlot(),e);
            }
        }
        if(origin==null || !origin.isAuthenticatedObservation() || !account.equals(origin.getAccountId())
            || origin.isBuying() || origin.getItemId()!=item || !"SELLING".equals(origin.getEventType())
            || origin.getFirstSeenAt()<=0 || origin.getFirstSeenAt()>origin.getObservedAt() || origin.getObservedAt()>now
            || origin.getFilledQuantity()>=origin.getTotalQuantity())return null;
        Map<Integer,OfferLifecycleProjection> peerProgress=new HashMap<>();
        for(var iterator=existingSales.values().iterator();iterator.hasNext();) {
            var e=iterator.next();var transition=OfferLifecycleReducer.apply(null,e);
            if(!transition.isAccepted())iterator.remove();
            else peerProgress.put(e.getSlot(),transition.getProjection());
        }
        OfferLifecycleProjection projection=null;OfferEvent cancelled=null;
        boolean seen=false,collected=false;long progress=origin.getFirstSeenAt(),acceptedProgress=Long.MAX_VALUE,collectedAt=0;int filled=0;
        for(var e:history) {
            if(e==null || !account.equals(e.getAccountId()))return null;
            boolean exact=Objects.equals(origin.getOfferIdentity(),e.getOfferIdentity());
            if(!seen && !exact)continue;
            if(e.getObservedAt()>now)return null;
            if(exact) {
                if(collected || !e.isAuthenticatedObservation() || e.isBuying() || e.getItemId()!=item
                    || e.getSlot()!=origin.getSlot() || e.getPrice()!=origin.getPrice()
                    || e.getTotalQuantity()!=origin.getTotalQuantity() || e.getFirstSeenAt()!=origin.getFirstSeenAt()
                    || !Objects.equals(e.getSessionId(),origin.getSessionId()))return null;
                var transition=OfferLifecycleReducer.apply(projection,e);
                if(!transition.isAccepted())return null;
                projection=transition.getProjection();
                if(e.getFilledQuantity()>filled){filled=e.getFilledQuantity();progress=e.getObservedAt();}
                if(source.equals(e.getEventId())){seen=true;acceptedProgress=progress;}
                if("CANCELLED_SELL".equals(e.getEventType()) && e.getFilledQuantity()<e.getTotalQuantity())cancelled=e;
                if("SOLD".equals(e.getEventType()) || e.getFilledQuantity()>=e.getTotalQuantity())return null;
            } else if(seen && e.getSlot()==origin.getSlot() && !collected) {
                var transition=OfferLifecycleReducer.apply(projection,e);
                if(!transition.isAccepted() || !e.isAuthenticatedObservation() || cancelled==null
                    || !"EMPTY".equals(e.getEventType()))return null;
                projection=transition.getProjection();collected=true;collectedAt=e.getObservedAt();
            }
            if(seen && !exact) {
                var peer=existingSales.get(e.getSlot());
                if(peer!=null) {
                    if(!sameRetainedSale(peer,e,account,item)) {
                        // An EMPTY, changed order or reused slot permanently revokes the exemption.
                        existingSales.remove(e.getSlot());peerProgress.remove(e.getSlot());
                    } else {
                        var transition=OfferLifecycleReducer.apply(peerProgress.get(e.getSlot()),e);
                        if(!transition.isAccepted())return null;
                        peerProgress.put(e.getSlot(),transition.getProjection());
                    }
                }
                // Existing parallel sales were on the accepted board; their heartbeats/fills
                // do not replace this remainder. Any new sale still supersedes the promise,
                // including one started between acceptance and collection.
                boolean linked=false;
                for(var successor:successors)if(successor.matchesEpisode(e)){linked=true;break;}
                if(e.getItemId()==item && (!e.isBuying() && !existingSales.containsKey(e.getSlot()) && !linked
                    || e.isBuying() && e.getFirstSeenAt()>origin.getFirstSeenAt()))return null;
            }
        }
        if(!seen || !collected || cancelled==null)return null;
        var original=observation(origin,false);
        var budget=OfferContinuation.advise(original,List.of(original),true,origin.getObservedAt(),0);
        if(budget==null)return null;
        // Collection first, then apply claims. The shared reducer never consumes a root twice
        // for grandchildren and never releases executed units when a child is collected.
        var settled=observation(cancelled,true);
        budget.observe(List.of(),settled,now);
        var status=budget.observe(List.of(),settled,now,successors,List.of());
        return status==OfferContinuation.Status.COLLECTED && quantity<=budget.getCollectedRemaining()
            ?new SaleRepriceEvidence(origin,cancelled,acceptedProgress,collectedAt,budget.getCollectedRemaining()):null;
    }
    private static OfferContinuation.Observation observation(OfferEvent e,boolean collected) {
        String legacy=e.getSlot()+"@"+e.getFirstSeenAt()+":"+e.getItemId();
        return new OfferContinuation.Observation(e.getSlot(),e.getItemId(),e.isBuying(),e.getPrice(),e.getTotalQuantity(),
            e.getFilledQuantity(),e.getFirstSeenAt(),legacy.equals(e.getOfferIdentity())?null:e.getOfferIdentity(),e.getEventType(),collected);
    }
    private static boolean retainedSale(OfferEvent e,String account,int item) {
        return e!=null && e.isAuthenticatedObservation() && TradeGapEvidence.valid(e)
            && account.equals(e.getAccountId()) && !e.isBuying() && e.getItemId()==item
            && e.getFirstSeenAt()>0 && e.getFirstSeenAt()<=e.getObservedAt();
    }
    private static boolean sameRetainedSale(OfferEvent a,OfferEvent b,String account,int item) {
        return retainedSale(b,account,item) && a.getSlot()==b.getSlot() && a.getPrice()==b.getPrice()
            && a.getTotalQuantity()==b.getTotalQuantity() && a.getFirstSeenAt()==b.getFirstSeenAt()
            && Objects.equals(a.getOfferIdentity(),b.getOfferIdentity()) && Objects.equals(a.getSessionId(),b.getSessionId());
    }
}
