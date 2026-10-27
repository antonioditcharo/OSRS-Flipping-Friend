package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.*;

/** Narrows an already validated source prefix; the caller keeps its original qualification rules. */
final class ExecutionPredictionIndex {
    private List<OfferEvent> canonical;
    private int authorizedSize,consumed;
    private long processed;
    private final Map<List<Object>,List<Row>> episodes=new HashMap<>();
    private final Map<String,Row> firstEvents=new HashMap<>();
    void reset(){canonical=null;authorizedSize=consumed=0;episodes.clear();firstEvents.clear();}
    void appendCanonical(List<OfferEvent> previous,List<OfferEvent> next) {
        if(previous!=canonical || next==null || next.size()<authorizedSize)reset();
        canonical=next;authorizedSize=next==null?0:next.size();
    }
    private static List<Object> key(OfferEvent event) {
        return Arrays.asList(event.getAccountId(),event.getSessionId(),event.getSlot(),event.getOfferIdentity());
    }
    List<OfferEvent> throughFirstTarget(List<OfferEvent> history,OfferEvent target) {
        if(history==null || target==null){reset();return List.of();}
        if(history!=canonical || history.size()!=authorizedSize){reset();canonical=history;authorizedSize=history.size();}
        try {
            for(;consumed<history.size();consumed++) {
                var event=history.get(consumed);var row=new Row(consumed,event);processed++;
                firstEvents.putIfAbsent(event.getEventId(),row);episodes.computeIfAbsent(key(event),k->new ArrayList<>()).add(row);
            }
            var end=firstEvents.get(target.getEventId());if(end==null)return List.of();
            var result=new ArrayList<OfferEvent>();
            for(var row:episodes.getOrDefault(key(target),List.of())) {
                if(row.order>end.order)break;result.add(row.event);
            }
            // The reference stops at the first matching ID even when that row belongs to another
            // episode. Preserve this global ordering boundary rather than searching beyond it.
            if(result.isEmpty() || result.get(result.size()-1)!=end.event)result.add(end.event);
            return Collections.unmodifiableList(result);
        }catch(RuntimeException failure){reset();throw failure;}
    }
    long processedCount(){return processed;}
    private static final class Row {
        final int order;final OfferEvent event;
        Row(int order,OfferEvent event){this.order=order;this.event=event;}
    }
}
