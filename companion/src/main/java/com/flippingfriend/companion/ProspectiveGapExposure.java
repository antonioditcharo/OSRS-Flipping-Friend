package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Possible new loss capital, distinct from the retained lifetime historical exposure guard. */
final class ProspectiveGapExposure {
    static Map<Integer,Long> calculate(SqliteStore.PlanningProjections read) {
        var evidence=read.riskEvidence;
        if(evidence==null || evidence.window==null || evidence.unbounded)return null;
        if(evidence.newReconciliationItems.isEmpty())return Map.of();
        if(read.history==null)return null;
        var window=evidence.window;
        try {
            Map<String,Integer> anchorSlots=new HashMap<>();
            for(int slot=0;slot<8;slot++)anchorSlots.put(window.sourceForSlot(slot),slot);
            Map<Integer,OfferEvent> prior=new HashMap<>(),anchor=new HashMap<>();
            Set<String> seen=new HashSet<>();int boundary=-1;
            for(int index=0;index<read.history.size();index++) {
                var e=read.history.get(index);
                if(!TradeGapEvidence.valid(e) || !window.accountId.equals(e.getAccountId()) || !seen.add(e.getEventId()))return null;
                Integer slot=anchorSlots.get(e.getEventId());
                if(slot!=null) {
                    if(slot!=e.getSlot() || !e.isAuthenticatedObservation() || !window.sessionId.equals(e.getSessionId())
                        || e.getObservedAt()!=window.startedAt || anchor.put(slot,e)!=null)return null;
                    boundary=Math.max(boundary,index);
                }
            }
            if(anchor.size()!=8 || boundary<0)return null;
            Map<Integer,Long> amounts=new HashMap<>();
            for(int item:evidence.newReconciliationItems)amounts.put(item,evidence.openingCosts.getOrDefault(item,0L));
            // The authenticated opening board proves the maximum future funding of an active buy.
            // Use the full envelope; already filled opening cost may be counted twice, never less.
            for(var e:anchor.values())if(e.isBuying() && ("BUYING".equals(e.getEventType()))
                && e.getFilledQuantity()<e.getTotalQuantity() && amounts.containsKey(e.getItemId()))add(amounts,e);
            for(int index=0;index<read.history.size();index++) {
                var e=read.history.get(index);
                if("EMPTY".equals(e.getEventType())) {prior.remove(e.getSlot());continue;}
                var before=prior.put(e.getSlot(),e);
                if(index>boundary && e.isBuying() && amounts.containsKey(e.getItemId())
                    && !BoundedHistoricalExposure.sameEpisode(before,e))add(amounts,e);
            }
            long total=0;for(long value:amounts.values())total=Math.addExact(total,value);
            return Collections.unmodifiableMap(amounts);
        }catch(ArithmeticException|IllegalArgumentException malformed){return null;}
    }
    private static void add(Map<Integer,Long> amounts,OfferEvent e) {
        long funding=Math.multiplyExact(e.getPrice(),(long)e.getTotalQuantity());
        if(e.getSpent()>funding)throw new IllegalArgumentException("Contradictory source funding");
        amounts.merge(e.getItemId(),funding,Math::addExact);
    }
}
