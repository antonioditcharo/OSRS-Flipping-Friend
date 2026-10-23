package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.*;

/** Incremental, non-financial completion evidence over one immutable canonical source prefix. */
final class PerformanceCompletionIndex {
    private String account;
    private long generation,positionCursor;
    private int count;
    private OfferEvent boundary;
    private boolean initialized;
    private long processed;
    // No history/list retention: only episodes with recorded disposal evidence occupy this index.
    private final Map<Episode,Long> disposalAt=new HashMap<>();
    private final Map<String,Long> completionAt=new HashMap<>();

    synchronized Set<String> completed(String account,long generation,long positionCursor,List<OfferEvent> history,
            Set<String> disposalSources,Set<String> gappedOffers,long now) {
        if(account==null || history==null || disposalSources==null || gappedOffers==null) {
            invalidate();return Set.of();
        }
        // Generation identifies the entire immutable prefix; boundary identity additionally catches
        // callers replacing a same-generation list instead of appending its existing event objects.
        boolean rebuild=!initialized || !Objects.equals(this.account,account) || this.generation!=generation
            || this.positionCursor!=positionCursor || history.size()<count
            || count>0 && history.get(count-1)!=boundary;
        if(rebuild) {
            invalidate();this.account=account;this.generation=generation;this.positionCursor=positionCursor;initialized=true;
        }
        for(int index=count;index<history.size();index++) {
            var event=history.get(index);processed++;
            if(event==null) {invalidate();return Set.of();}
            if(event.isBuying())continue;
            var episode=new Episode(event.getOfferIdentity(),event.getItemId());
            if(disposalSources.contains(event.getEventId()))disposalAt.merge(episode,event.getObservedAt(),Math::min);
            var disposed=disposalAt.get(episode);
            if(disposed!=null && "SOLD".equals(event.getEventType()) && event.getTotalQuantity()>0
                    && event.getFilledQuantity()==event.getTotalQuantity())
                completionAt.merge(event.getOfferIdentity(),Math.max(disposed,event.getObservedAt()),Math::min);
        }
        count=history.size();boundary=count==0?null:history.get(count-1);
        var result=new HashSet<String>();
        for(var entry:completionAt.entrySet())if(entry.getValue()<=now && !gappedOffers.contains(entry.getKey()))result.add(entry.getKey());
        return Collections.unmodifiableSet(result);
    }
    private void invalidate() {
        initialized=false;account=null;count=0;boundary=null;disposalAt.clear();completionAt.clear();
    }
    /** Cumulative decoded input events; diagnostics/tests prove unchanged reports never rescan. */
    synchronized long processedCount(){return processed;}
    synchronized int retainedEpisodeCount(){return disposalAt.size();}
    private static final class Episode {
        final String offer;final int item;
        Episode(String offer,int item){this.offer=offer;this.item=item;}
        @Override public boolean equals(Object value) {
            if(!(value instanceof Episode))return false;var other=(Episode)value;
            return item==other.item && Objects.equals(offer,other.offer);
        }
        @Override public int hashCode(){return Objects.hash(offer,item);}
    }
}
