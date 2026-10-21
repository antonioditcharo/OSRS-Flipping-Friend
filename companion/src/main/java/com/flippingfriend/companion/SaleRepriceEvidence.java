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
        for(var e:history)if(e!=null && source.equals(e.getEventId())) {
            if(origin!=null)return null;origin=e;
        }
        if(origin==null || !origin.isAuthenticatedObservation() || !account.equals(origin.getAccountId())
            || origin.isBuying() || origin.getItemId()!=item || !"SELLING".equals(origin.getEventType())
            || origin.getFirstSeenAt()<=0 || origin.getFirstSeenAt()>origin.getObservedAt() || origin.getObservedAt()>now
            || origin.getFilledQuantity()>=origin.getTotalQuantity())return null;
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
            // A subsequent accumulation or newly started sale supersedes this returned remainder.
            if(seen && !exact && e.getItemId()==item && e.getFirstSeenAt()>origin.getFirstSeenAt()
                && (e.isBuying() || collected && !e.isBuying()))return null;
        }
        return seen && collected && cancelled!=null && quantity<=cancelled.getTotalQuantity()-cancelled.getFilledQuantity()
            ?new SaleRepriceEvidence(origin,cancelled,acceptedProgress):null;
    }
}
