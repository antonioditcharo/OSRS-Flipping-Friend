package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only proof of one exact working sale, its late fills, cancellation and collection. */
final class SaleRepriceEvidence {
    final OfferEvent original, cancelled;
    final long lastProgressAt;
    private SaleRepriceEvidence(OfferEvent original,OfferEvent cancelled,long progress) {
        this.original=original;this.cancelled=cancelled;lastProgressAt=progress;
    }
    static SaleRepriceEvidence prove(List<OfferEvent> history,String source,String account,int item,int quantity,long now) {
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
        boolean seen=false,collected=false;long progress=origin.getFirstSeenAt(),acceptedProgress=Long.MAX_VALUE;int filled=0;
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
                projection=transition.getProjection();collected=true;
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
                if(e.getItemId()==item && (!e.isBuying() && !existingSales.containsKey(e.getSlot())
                    || e.isBuying() && e.getFirstSeenAt()>origin.getFirstSeenAt()))return null;
            }
        }
        return seen && collected && cancelled!=null && quantity<=cancelled.getTotalQuantity()-cancelled.getFilledQuantity()
            ?new SaleRepriceEvidence(origin,cancelled,acceptedProgress):null;
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
