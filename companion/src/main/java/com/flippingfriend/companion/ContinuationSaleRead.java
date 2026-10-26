package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only workflow disposition. Never reduces financial/exposure evidence or rebuilds holdings. */
final class ContinuationSaleRead {
    static boolean disposalSource(OfferEvent source) {
        return source!=null && !source.isBuying() && source.isAuthenticatedObservation() && source.getFilledQuantity()>0
            && ("SELLING".equals(source.getEventType()) || "SOLD".equals(source.getEventType()) || "CANCELLED_SELL".equals(source.getEventType()));
    }
    static List<ContinuationDisposition> read(List<OfferEvent> history, Set<String> disposals, Set<String> rejected) {
        return new Index().read(history,disposals,rejected);
    }
    /** The store alone advances this index after validating an exact canonical append. */
    static final class Index {
        private List<OfferEvent> history;
        private final Map<String,OfferEvent> originals=new HashMap<>();
        private final Map<String,String> identities=new HashMap<>();
        private final Map<String,OfferEvent> filled=new LinkedHashMap<>();
        private final Set<String> inconsistent=new HashSet<>();
        private Set<String> priorDisposals,priorRejected;
        private List<ContinuationDisposition> result;
        void reset(){history=null;originals.clear();identities.clear();filled.clear();inconsistent.clear();priorDisposals=null;priorRejected=null;result=null;}
        void appendCanonical(List<OfferEvent> previous,List<OfferEvent> appended) {
            if(history==null || history!=previous || appended==null || appended.size()<previous.size())return;
            for(int index=previous.size();index<appended.size();index++)accept(appended.get(index));
            history=appended;
        }
        private void accept(OfferEvent source) {
            String identity=source.getOfferIdentity();
            if(identity==null || "EMPTY".equals(source.getEventType()))return;
            identities.put(source.getEventId(),identity);
            var origin=originals.putIfAbsent(identity,source);
            if(origin!=null && (origin.getItemId()!=source.getItemId()
                || origin.isBuying()!=source.isBuying() || origin.getSlot()!=source.getSlot()
                || origin.getFirstSeenAt()!=source.getFirstSeenAt())) {
                if(inconsistent.add(identity))result=null;
            }
            if(disposalSource(source))filled.put(source.getEventId(),source);
        }
        List<ContinuationDisposition> read(List<OfferEvent> sources,Set<String> disposals,Set<String> rejected) {
            if(sources==null){reset();return List.of();}
            if(history!=sources){reset();for(var source:sources)accept(source);history=sources;}
            if(result!=null && disposals.equals(priorDisposals) && rejected.equals(priorRejected))return result;
            var excluded=new HashSet<>(inconsistent);
            for(String id:rejected){String identity=identities.get(id);if(identity!=null)excluded.add(identity);}
        Map<Integer,ContinuationDisposition> latest=new TreeMap<>();
        for(var source:filled.values()) {
            var origin=originals.get(source.getOfferIdentity());
            if(!disposals.contains(source.getEventId()) || !disposalSource(source)
                || origin==null || !origin.isAuthenticatedObservation() || excluded.contains(source.getOfferIdentity())
                || origin.getFirstSeenAt()<=0 || origin.getFirstSeenAt()>origin.getObservedAt()
                || source.getFirstSeenAt()>source.getObservedAt())continue;
            var disposition=new ContinuationDisposition(source.getItemId(),source.getEventId(),source.getOfferIdentity(),
                origin.getFirstSeenAt(),source.getObservedAt());
            var previous=latest.get(source.getItemId());
            if(previous==null || previous.getFirstSeenAt()<disposition.getFirstSeenAt())latest.put(source.getItemId(),disposition);
        }
            priorDisposals=Set.copyOf(disposals);priorRejected=Set.copyOf(rejected);result=List.copyOf(latest.values());
            return result;
        }
    }
}
