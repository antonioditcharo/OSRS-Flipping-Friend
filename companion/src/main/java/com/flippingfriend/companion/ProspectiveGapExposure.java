package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Possible new loss capital, distinct from the retained lifetime historical exposure guard. */
final class ProspectiveGapExposure {
    static Map<Integer,Long> calculate(SqliteStore.PlanningProjections read) {
        return read.prospectiveGapMemo==null?calculateUncached(read):read.prospectiveGapMemo.calculate(read);
    }
    /** One immutable source prefix, one original risk window and exact opening/scope inputs only.
     * Never caches current holdings, cash, market marks, losses or final entry authorization. */
    static final class Memo {
        private List<OfferEvent> history;
        private List<Object> window;
        private Set<Integer> items;
        private Map<Integer,Long> opening,result;
        private FundingState state;
        synchronized void reset(){history=null;window=null;items=null;opening=null;result=null;state=null;}
        /** Only a completely validated SqliteStore append carries a previous prefix forward. */
        synchronized void appendCanonical(List<OfferEvent> previous,List<OfferEvent> appended) {
            if(history==null || history!=previous || state==null || appended==null || appended.size()<previous.size())return;
            for(int index=previous.size();index<appended.size();index++)state.append(appended.get(index));
            history=appended;result=state.snapshot();
        }
        synchronized Map<Integer,Long> calculate(SqliteStore.PlanningProjections read) {
            var evidence=read.riskEvidence;
            if(evidence==null || evidence.window==null || evidence.unbounded || read.history==null)
                return calculateUncached(read);
            var w=evidence.window;var key=new ArrayList<Object>();
            Collections.addAll(key,w.accountId,w.windowId,w.sessionId,w.startedAt,w.positionCursor,w.startingCapital);
            for(int slot=0;slot<8;slot++)key.add(w.sourceForSlot(slot));
            if(history==read.history && key.equals(window) && evidence.newReconciliationItems.equals(items)
                    && evidence.openingCosts.equals(opening))return result;
            state=FundingState.build(read);var computed=state.snapshot();
            history=read.history;window=key;items=Set.copyOf(evidence.newReconciliationItems);
            opening=Map.copyOf(evidence.openingCosts);result=computed;return computed;
        }
    }
    private static Map<Integer,Long> calculateUncached(SqliteStore.PlanningProjections read) {
        var evidence=read.riskEvidence;
        if(evidence==null || evidence.window==null || evidence.unbounded)return null;
        if(evidence.newReconciliationItems.isEmpty())return Map.of();
        if(read.history==null)return null;
        return FundingState.build(read).snapshot();
    }
    private static final class FundingState {
        final Map<Integer,OfferEvent> prior=new HashMap<>();
        final Set<String> seen=new HashSet<>();
        final Map<Integer,Long> amounts=new HashMap<>();
        String account;boolean invalid;
        static FundingState build(SqliteStore.PlanningProjections read) {
            var state=new FundingState();var evidence=read.riskEvidence;var window=evidence.window;
            state.account=window.accountId;
            if(evidence.newReconciliationItems.isEmpty())return state;
        try {
            Map<String,Integer> anchorSlots=new HashMap<>();
            for(int slot=0;slot<8;slot++)anchorSlots.put(window.sourceForSlot(slot),slot);
            Map<Integer,OfferEvent> anchor=new HashMap<>();int boundary=-1;
            for(int index=0;index<read.history.size();index++) {
                var e=read.history.get(index);
                if(!state.validate(e)){state.invalid=true;return state;}
                Integer slot=anchorSlots.get(e.getEventId());
                if(slot!=null) {
                    if(slot!=e.getSlot() || !e.isAuthenticatedObservation() || !window.sessionId.equals(e.getSessionId())
                        || e.getObservedAt()!=window.startedAt || anchor.put(slot,e)!=null){state.invalid=true;return state;}
                    boundary=Math.max(boundary,index);
                }
            }
            if(anchor.size()!=8 || boundary<0){state.invalid=true;return state;}
            for(int item:evidence.newReconciliationItems)state.amounts.put(item,evidence.openingCosts.getOrDefault(item,0L));
            // The authenticated opening board proves the maximum future funding of an active buy.
            // Use the full envelope; already filled opening cost may be counted twice, never less.
            for(var e:anchor.values())if(e.isBuying() && ("BUYING".equals(e.getEventType()))
                && e.getFilledQuantity()<e.getTotalQuantity() && state.amounts.containsKey(e.getItemId()))add(state.amounts,e);
            for(int index=0;index<read.history.size();index++) {
                state.funding(read.history.get(index),index>boundary);
            }
        }catch(ArithmeticException|IllegalArgumentException malformed){state.invalid=true;}
            return state;
        }
        private boolean validate(OfferEvent e) {
            return TradeGapEvidence.valid(e) && account.equals(e.getAccountId()) && seen.add(e.getEventId());
        }
        private void funding(OfferEvent e,boolean afterBoundary) {
            if("EMPTY".equals(e.getEventType())){prior.remove(e.getSlot());return;}
            var before=prior.put(e.getSlot(),e);
            if(afterBoundary && e.isBuying() && amounts.containsKey(e.getItemId())
                && !BoundedHistoricalExposure.sameEpisode(before,e))add(amounts,e);
        }
        void append(OfferEvent e) {
            if(invalid || amounts.isEmpty())return;
            try{if(!validate(e)){invalid=true;return;}funding(e,true);}
            catch(ArithmeticException|IllegalArgumentException malformed){invalid=true;}
        }
        Map<Integer,Long> snapshot() {
            if(invalid)return null;
            try{long total=0;for(long value:amounts.values())total=Math.addExact(total,value);return Map.copyOf(amounts);}
            catch(ArithmeticException overflow){invalid=true;return null;}
        }
    }
    private static void add(Map<Integer,Long> amounts,OfferEvent e) {
        long funding=Math.multiplyExact(e.getPrice(),(long)e.getTotalQuantity());
        if(e.getSpent()>funding)throw new IllegalArgumentException("Contradictory source funding");
        amounts.merge(e.getItemId(),funding,Math::addExact);
    }
}
