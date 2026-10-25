package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Proves a rejected unchanged frame and its unchanged baseline introduced no execution delta.
 * Original gaps, item entry pauses and full prospective risk reserves remain in force. */
final class UnchangedFrameRecovery {
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
