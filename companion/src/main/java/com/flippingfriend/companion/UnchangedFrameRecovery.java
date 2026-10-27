package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Proves a rejected unchanged frame and its unchanged baseline introduced no execution delta.
 * Original gaps, item entry pauses and full prospective risk reserves remain in force. */
final class UnchangedFrameRecovery {
    /** Prefix preparation keeps the original pair/order proof; scope and costs are filtered fresh. */
    static final class Index {
        private List<OfferEvent> canonical;
        private int authorizedSize,consumed;
        private long processed;
        private final Map<Integer,OfferEvent> previous=new HashMap<>();
        private final Map<Integer,OfferEvent[]> pending=new HashMap<>();
        private final Set<String> proven=new HashSet<>(),seenIds=new HashSet<>();
        private final Set<Integer> candidates=new HashSet<>();
        private Map<String,String> priorReasons=Map.of();
        void reset() {
            canonical=null;authorizedSize=consumed=0;previous.clear();pending.clear();proven.clear();
            seenIds.clear();candidates.clear();priorReasons=Map.of();
        }
        void appendCanonical(List<OfferEvent> before,List<OfferEvent> next) {
            if(before!=canonical || next==null || next.size()<authorizedSize)reset();
            canonical=next;authorizedSize=next==null?0:next.size();
        }
        Set<Integer> read(List<OfferEvent> history,AutomaticTradeScope scope,Map<String,String> reasons) {
            if(history==null){reset();return Set.of();}
            boolean changed=false;
            for(var e:reasons.entrySet())if(seenIds.contains(e.getKey()) && !Objects.equals(e.getValue(),priorReasons.get(e.getKey())))changed=true;
            for(var e:priorReasons.entrySet())if(seenIds.contains(e.getKey()) && !Objects.equals(e.getValue(),reasons.get(e.getKey())))changed=true;
            if(changed || history!=canonical || history.size()!=authorizedSize) {
                reset();canonical=history;authorizedSize=history.size();
            }
            if(scope.hasUnboundedReconciliation)return Set.of();
            try {
                for(;consumed<history.size();consumed++) {
                    var e=history.get(consumed);processed++;
                    var before=previous.put(e.getSlot(),e);var pair=pending.remove(e.getSlot());
                    String reason=reasons.get(e.getEventId());
                    if(pair!=null && "authenticated slot baseline; earlier executions remain unresolved".equals(reason)
                            && ordered(pair[1],e) && e.getObservedAt()>=pair[0].getObservedAt()
                            && TradeGapEvidence.unchangedAuthenticatedEpisode(pair[0],e)) {
                        proven.add(pair[1].getEventId());proven.add(e.getEventId());candidates.add(e.getItemId());
                    }
                    if("authenticated observation is not newer in this session".equals(reason) && before!=null
                            && !reasons.containsKey(before.getEventId()) && ordered(before,e)
                            && e.getObservedAt()<before.getObservedAt()
                            && TradeGapEvidence.unchangedAuthenticatedEpisode(before,e))pending.put(e.getSlot(),new OfferEvent[]{before,e});
                    seenIds.add(e.getEventId());
                }
                priorReasons=Collections.unmodifiableMap(new HashMap<>(reasons));
                Set<Integer> result=new HashSet<>(candidates);result.removeAll(scope.unprovenCostItems);
                result.removeIf(item->scope.reconciliationScope.entrySet().stream()
                    .anyMatch(gap->gap.getValue().contains(item) && !proven.contains(gap.getKey())));
                return Collections.unmodifiableSet(result);
            }catch(RuntimeException error){reset();throw error;}
        }
        long processedCount(){return processed;}
    }
    static Set<Integer> items(List<OfferEvent> history,AutomaticTradeScope scope,Map<String,String> reasons) {
        if(history==null || scope.hasUnboundedReconciliation)return Set.of();
        var previous=new HashMap<Integer,OfferEvent>();var pending=new HashMap<Integer,OfferEvent[]>();
        Set<String> proven=new HashSet<>();Set<Integer> candidates=new HashSet<>();
        for(var e:history) {
            var before=previous.put(e.getSlot(),e);var pair=pending.remove(e.getSlot());
            String reason=reasons.get(e.getEventId());
            if(pair!=null && "authenticated slot baseline; earlier executions remain unresolved".equals(reason)
                    && ordered(pair[1],e) && e.getObservedAt()>=pair[0].getObservedAt()
                    && TradeGapEvidence.unchangedAuthenticatedEpisode(pair[0],e)) {
                proven.add(pair[1].getEventId());proven.add(e.getEventId());candidates.add(e.getItemId());
            }
            if("authenticated observation is not newer in this session".equals(reason) && before!=null
                    && !reasons.containsKey(before.getEventId()) && ordered(before,e)
                    && e.getObservedAt()<before.getObservedAt()
                    && TradeGapEvidence.unchangedAuthenticatedEpisode(before,e))pending.put(e.getSlot(),new OfferEvent[]{before,e});
        }
        candidates.removeAll(scope.unprovenCostItems);
        candidates.removeIf(item->scope.reconciliationScope.entrySet().stream()
            .anyMatch(gap->gap.getValue().contains(item) && !proven.contains(gap.getKey())));
        return Collections.unmodifiableSet(candidates);
    }
    private static boolean ordered(OfferEvent a,OfferEvent b) {
        return Objects.equals(a.getAccountId(),b.getAccountId()) && Objects.equals(a.getSessionId(),b.getSessionId())
            && a.getSlot()==b.getSlot() && b.getSequence()>a.getSequence();
    }
    static boolean boundedSell(SqliteStore.PlanningProjections read,OfferLifecycleProjection offer) {
        if(offer.isBuying() || !read.tradeScope.unchangedFrameItems.contains(offer.getItemId()))return false;
        return coveredSell(read,offer);
    }
    static boolean coveredSell(SqliteStore.PlanningProjections read,OfferLifecycleProjection offer) {
        if(offer.isBuying())return false;
        long remaining=0;
        for(var current:read.offers)if(current.getItemId()==offer.getItemId() && current.getState()!=OfferLifecycleState.EMPTY) {
            if(current.isBuying() || current.getState()==OfferLifecycleState.ERROR_RECONCILIATION)return false;
            remaining=Math.addExact(remaining,(long)current.getTotalQuantity()-current.getFilledQuantity());
        }
        var position=read.tradeScope.positions.get(offer.getItemId());
        return remaining>=0 && remaining<=(position==null?0:position.getQuantity());
    }
}
