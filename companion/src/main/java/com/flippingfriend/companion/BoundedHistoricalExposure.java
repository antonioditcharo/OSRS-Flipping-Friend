package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Conservative observed-capital envelope, never a reconstructed position or historical profit. */
final class BoundedHistoricalExposure {
    static DurablePlanningExposure calculate(SqliteStore.PlanningProjections read) {
        var scope=read.tradeScope;
        if(scope==null || scope.hasUnboundedReconciliation || read.history==null)return null;
        Set<Integer> paused=new HashSet<>(scope.unsafeItems);
        Map<Integer,Long> exposure=new HashMap<>();Map<Integer,OfferEvent> prior=new HashMap<>();
        try {
            for(var e:read.history) {
                if(e.getEventType().equals("EMPTY")){prior.remove(e.getSlot());continue;}
                var before=prior.put(e.getSlot(),e);
                if(paused.contains(e.getItemId()) && e.isBuying()) {
                    long funding=Math.multiplyExact(e.getPrice(),(long)e.getTotalQuantity());
                    if(e.getSpent()>funding)return null;
                    if(!sameEpisode(before,e))exposure.merge(e.getItemId(),funding,Math::addExact);
                }
            }
            for(var p:scope.readyPositions())exposure.put(p.getItemId(),p.getTotalCost());
            for(var p:read.positions) {
                if(p==null || p.getItemId()<=0 || p.getTotalCost()<0 || p.getQuantity()<0)return null;
                if(!p.isCostKnown() && p.getTotalCost()==0)continue;
                if(!p.isCostKnown() || p.getQuantity()<=0)return null;
                if(paused.contains(p.getItemId())) {
                    if(p.getConsistencyState()!=PositionConsistencyState.READY
                            && !scope.reconciliationScope.getOrDefault(p.getSourceEventId(),Set.of()).contains(p.getItemId()))return null;
                    exposure.merge(p.getItemId(),p.getTotalCost(),Math::addExact);
                }else {
                    if(p.getConsistencyState()!=PositionConsistencyState.READY)return null;
                    var at=scope.positionsAtEvent.get(p.getSourceEventId());
                    boolean covered=at!=null && at.getItemId()==p.getItemId() && at.getQuantity()==p.getQuantity()
                        && at.getTotalCost()==p.getTotalCost();
                    if(!covered)exposure.merge(p.getItemId(),p.getTotalCost(),Math::addExact);
                }
            }
            long reserved=0;
            for(var p:read.offers) {
                if(p.getState()==OfferLifecycleState.ERROR_RECONCILIATION)return null;
                if(p.getState()!=OfferLifecycleState.BUY_OPEN && p.getState()!=OfferLifecycleState.BUY_PARTIAL)continue;
                long amount=Math.multiplyExact(p.getPrice(),(long)p.getTotalQuantity()-p.getFilledQuantity());
                reserved=Math.addExact(reserved,amount);
                if(paused.contains(p.getItemId())) {
                    // The full offer envelope already includes its unfilled reservation.
                    if(exposure.getOrDefault(p.getItemId(),0L)<amount)return null;
                }else exposure.merge(p.getItemId(),amount,Math::addExact);
            }
            long total=0;for(long value:exposure.values()){if(value<0)return null;total=Math.addExact(total,value);}
            return new DurablePlanningExposure(exposure,reserved,paused);
        }catch(ArithmeticException malformed){return null;}
    }
    private static boolean sameEpisode(OfferEvent a,OfferEvent b) {
        return a!=null && a.isBuying() && Objects.equals(a.getSessionId(),b.getSessionId())
            && Objects.equals(a.getOfferIdentity(),b.getOfferIdentity()) && a.getItemId()==b.getItemId()
            && a.getPrice()==b.getPrice() && a.getTotalQuantity()==b.getTotalQuantity()
            && b.getFilledQuantity()>=a.getFilledQuantity() && b.getSpent()>=a.getSpent();
    }
}
