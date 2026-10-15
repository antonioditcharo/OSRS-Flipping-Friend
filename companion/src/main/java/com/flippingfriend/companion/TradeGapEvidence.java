package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only causal scope of recorded gaps. It makes no execution, ownership or disposal inference. */
final class TradeGapEvidence {
    private static final Set<String> NONEMPTY = Set.of("BUYING","BOUGHT","CANCELLED_BUY",
        "SELLING","SOLD","CANCELLED_SELL");
    private TradeGapEvidence() { }

    static Map<String,Set<Integer>> unknown(Map<String,Integer> gaps) {
        Map<String,Set<Integer>> result=new HashMap<>();
        gaps.keySet().forEach(id->result.put(id,Collections.emptySet()));
        return result;
    }
    static Map<String,Set<Integer>> scope(List<OfferEvent> history,Map<String,Integer> gaps) {
        Map<String,Set<Integer>> result=unknown(gaps);
        Map<Integer,Slot> slots=new HashMap<>();
        Set<String> eventIds=new HashSet<>();
        String account=null;
        for (OfferEvent e:history) {
            if (!valid(e) || !eventIds.add(e.getEventId())) return unknown(gaps);
            if (account==null) account=e.getAccountId();
            if (!account.equals(e.getAccountId())) return unknown(gaps);
            Slot prior=slots.get(e.getSlot());
            boolean ordered=prior==null || (prior.trusted && prior.session.equals(e.getSessionId())
                && e.getSequence()>prior.sequence && e.getObservedAt()>=prior.observedAt);
            if (gaps.containsKey(e.getEventId()) && gaps.get(e.getEventId())==e.getItemId() && ordered) {
                Set<Integer> possible=new HashSet<>();
                if (prior!=null) possible.addAll(prior.items);
                if (e.getItemId()>0) possible.add(e.getItemId());
                if (!possible.isEmpty()) result.put(e.getEventId(),Collections.unmodifiableSet(possible));
            }
            Slot next=prior==null?new Slot(e.getSessionId()):prior;
            if (!ordered) next.trusted=false;
            // A witnessed terminal followed by EMPTY separates later offer episodes. Past gap
            // mappings have already been retained; clearing this context does not dispose stock.
            if ("EMPTY".equals(e.getEventType())) {
                if (ordered && next.terminal) next.items.clear();
                next.terminal=false;
            } else {
                next.items.add(e.getItemId());
                OfferLifecycleState state=OfferLifecycleClassifier.classify(e);
                next.terminal=state==OfferLifecycleState.BUY_FILLED_UNCOLLECTED
                    || state==OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
                    || state==OfferLifecycleState.SELL_FILLED_UNCOLLECTED
                    || state==OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED;
            }
            next.sequence=Math.max(next.sequence,e.getSequence());
            next.observedAt=Math.max(next.observedAt,e.getObservedAt());
            slots.put(e.getSlot(),next);
        }
        return result;
    }
    private static boolean valid(OfferEvent e) {
        if (e==null || !"1".equals(e.getSchemaVersion()) || blank(e.getEventId()) || blank(e.getSessionId())
                || e.getSequence()<=0 || e.getObservedAt()<0 || e.getSlot()<0 || e.getSlot()>=8) return false;
        try { AccountIdentity.require(e.getAccountId()); } catch (IllegalArgumentException invalid) { return false; }
        if ("EMPTY".equals(e.getEventType())) return e.getItemId()==0 && !e.isBuying()
            && e.getPrice()==0 && e.getTotalQuantity()==0 && e.getFilledQuantity()==0 && e.getSpent()==0
            && blank(e.getOfferIdentity());
        if (!NONEMPTY.contains(e.getEventType()) || e.getItemId()<=0 || blank(e.getOfferIdentity())
                || e.getPrice()<=0 || e.getTotalQuantity()<=0 || e.getFilledQuantity()<0
                || e.getFilledQuantity()>e.getTotalQuantity() || e.getSpent()<0) return false;
        boolean buy=e.getEventType().equals("BUYING") || e.getEventType().equals("BOUGHT")
            || e.getEventType().equals("CANCELLED_BUY");
        return buy==e.isBuying() && (e.getFilledQuantity()==0 ? e.getSpent()==0 : e.getSpent()>0);
    }
    private static boolean blank(String value) { return value==null || value.isBlank(); }
    private static final class Slot {
        final String session;
        final Set<Integer> items=new HashSet<>();
        long sequence,observedAt;
        boolean trusted=true,terminal;
        Slot(String session) { this.session=session; }
    }
}
