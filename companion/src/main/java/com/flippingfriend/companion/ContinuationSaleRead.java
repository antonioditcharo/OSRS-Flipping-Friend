package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only workflow disposition. Never reduces financial/exposure evidence or rebuilds holdings. */
final class ContinuationSaleRead {
    static List<ContinuationDisposition> read(List<OfferEvent> history, Set<String> disposals, Set<String> rejected) {
        if(history==null)return List.of();
        Map<String,OfferEvent> originals=new HashMap<>();
        Set<String> inconsistent=new HashSet<>();
        for(var source:history) {
            String identity=source.getOfferIdentity();
            if(identity==null || "EMPTY".equals(source.getEventType()))continue;
            var origin=originals.putIfAbsent(identity,source);
            if(rejected.contains(source.getEventId()) || origin!=null && (origin.getItemId()!=source.getItemId()
                || origin.isBuying()!=source.isBuying() || origin.getSlot()!=source.getSlot()
                || origin.getFirstSeenAt()!=source.getFirstSeenAt()))inconsistent.add(identity);
        }
        Map<Integer,ContinuationDisposition> latest=new TreeMap<>();
        for(var source:history) {
            var origin=originals.get(source.getOfferIdentity());
            if(!disposals.contains(source.getEventId()) || source.isBuying() || !source.isAuthenticatedObservation()
                || origin==null || !origin.isAuthenticatedObservation() || inconsistent.contains(source.getOfferIdentity())
                || origin.getFirstSeenAt()<=0 || origin.getFirstSeenAt()>origin.getObservedAt()
                || source.getFirstSeenAt()>source.getObservedAt() || source.getFilledQuantity()<=0
                || !("SELLING".equals(source.getEventType()) || "SOLD".equals(source.getEventType())
                    || "CANCELLED_SELL".equals(source.getEventType())))continue;
            var disposition=new ContinuationDisposition(source.getItemId(),source.getEventId(),source.getOfferIdentity(),
                origin.getFirstSeenAt(),source.getObservedAt());
            var previous=latest.get(source.getItemId());
            if(previous==null || previous.getFirstSeenAt()<disposition.getFirstSeenAt())latest.put(source.getItemId(),disposition);
        }
        return List.copyOf(latest.values());
    }
}
