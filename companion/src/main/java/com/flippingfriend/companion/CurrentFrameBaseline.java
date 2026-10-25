package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Acknowledged cash/eight-slot proof can restore current state, never omitted executions. */
final class CurrentFrameBaseline {
    static List<OfferLifecycleProjection> qualify(SqliteStore.PlanningProjections read,AccountSnapshot account,long now) {
        if(account==null || !account.isAutomaticTradeScope() || read==null)return List.of();
        var candidates=new ArrayList<OfferLifecycleProjection>();var changed=new ArrayList<OfferLifecycleProjection>();
        for(var prior:read.offers) {
            if(prior.getState()!=OfferLifecycleState.ERROR_RECONCILIATION){candidates.add(prior);continue;}
            var e=read.currentEvents.get(prior.getSlot());
            if(e==null || !Objects.equals(e.getSessionId(),prior.getSessionId()) || e.getSequence()!=prior.getLastSequence()
                    || e.getObservedAt()!=prior.getLastObservedAt())return List.of();
            var state=OfferLifecycleClassifier.classify(e);
            if(state==OfferLifecycleState.ERROR_RECONCILIATION)return List.of();
            var next=OfferLifecycleProjection.restore(state,e.getSlot(),e.getOfferIdentity(),e.getSessionId(),e.getSequence(),e.getObservedAt(),
                e.getItemId(),e.getItemName(),e.isBuying(),e.getPrice(),e.getTotalQuantity(),e.getFilledQuantity(),e.getSpent(),e.getRecommendationId());
            candidates.add(next);changed.add(next);
        }
        var proof=new SqliteStore.PlanningProjections(read.positions,candidates);proof.currentEvents=read.currentEvents;
        return CurrentOfferReadSet.valid(proof,account,now)?List.copyOf(changed):List.of();
    }
}
