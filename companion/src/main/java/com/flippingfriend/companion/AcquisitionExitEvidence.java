package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only qualification against the earliest retained canonical source, never today's item advice. */
final class AcquisitionExitEvidence {
    /** Original full reader remains the semantic reference for prefix preparation. */
    static Map<String,AcquisitionExitContext> read(List<OfferEvent> history) {
        Map<String,AcquisitionExitContext> result=new HashMap<>();
        if(history==null)return result;
        Map<List<Object>,OfferEvent> origins=new HashMap<>();
        Set<List<Object>> invalid=new HashSet<>();
        Map<String,AcceptedBuyPlanProof> usedAdvice=new HashMap<>();
        Set<String> duplicateAdvice=new HashSet<>();
        for(var event:history) {
            var proof=event.getAcceptedBuyPlanProof();
            if(proof==null || !proof.qualifiesOriginal(event))continue;
            var prior=usedAdvice.putIfAbsent(proof.getAcceptedAdviceId(),proof);
            if(prior!=null && !prior.equals(proof))duplicateAdvice.add(proof.getAcceptedAdviceId());
        }
        for(var event:history) {
            if("EMPTY".equals(event.getEventType()))continue;
            if(event.getAccountId()==null || event.getSessionId()==null || event.getOfferIdentity()==null)continue;
            var key=List.<Object>of(event.getAccountId(),event.getSessionId(),event.getSlot(),event.getOfferIdentity());
            OfferEvent origin=origins.putIfAbsent(key,event);
            if(origin==null) {
                origin=event;
                if(!event.isBuying() || !qualifiesOriginal(event)
                    || event.getExitContext()==null || !event.getExitContext().isBound()
                    || !event.getEventId().equals(event.getExitContext().getSourceEventId()))invalid.add(key);
            }
            if(invalid.contains(key))continue;
            var plan=origin.getAcceptedBuyPlanProof();
            if(plan!=null || origin.getAcceptedBuyPlanEvidenceVersion()!=null) {
                if(!"1".equals(origin.getAcceptedBuyPlanEvidenceVersion()) || plan==null
                    || !Objects.equals(origin.getAcceptedBuyPlanEvidenceVersion(),event.getAcceptedBuyPlanEvidenceVersion())
                    || duplicateAdvice.contains(plan.getAcceptedAdviceId()) || !plan.qualifiesOriginal(origin)
                    || !TradeGapEvidence.valid(event) || !plan.matchesEpisode(event)
                    || !Objects.equals(plan,event.getAcceptedBuyPlanProof())
                    || !Objects.equals(event.getExitContext(),plan.getExitContext())) {invalid.add(key);continue;}
                result.put(event.getEventId(),plan.getExitContext());continue;
            }
            if(!TradeGapEvidence.valid(event) || !event.isAuthenticatedObservation()
                || event.getItemId()!=origin.getItemId() || !event.isBuying()
                || event.getPrice()!=origin.getPrice() || event.getTotalQuantity()!=origin.getTotalQuantity()
                || event.getFirstSeenAt()!=origin.getFirstSeenAt() || event.getSequence()<origin.getSequence()
                || event.getObservedAt()<origin.getObservedAt()
                || !origin.hasAcceptedExitProof() && !event.isPredictionComparable()
                || !Objects.equals(event.getAcceptedAdviceProof(),origin.getAcceptedAdviceProof())
                || !Objects.equals(event.getPredictionSourceEventId(),origin.getEventId())
                || !Objects.equals(event.getExitContext(),origin.getExitContext())) {
                invalid.add(key);continue;
            }
            result.put(event.getEventId(),origin.getExitContext());
        }
        return result;
    }
    /** Immutable-prefix preparation only; it grants neither current sale nor entry authority. */
    static final class Index {
        private List<OfferEvent> canonical;
        private int authorizedSize,consumed;
        private long processed;
        private final Map<List<Object>,Episode> episodes=new HashMap<>();
        private final Map<String,AcceptedBuyPlanProof> advice=new HashMap<>();
        private final Set<String> duplicateAdvice=new HashSet<>();
        private final Map<String,Set<String>> adviceEvents=new HashMap<>();
        private final Map<String,Contribution> contributions=new HashMap<>();
        private final Map<String,AcquisitionExitContext> result=new HashMap<>();
        private Map<String,AcquisitionExitContext> snapshot=Map.of();
        void reset() {
            canonical=null;authorizedSize=consumed=0;episodes.clear();advice.clear();duplicateAdvice.clear();
            adviceEvents.clear();contributions.clear();result.clear();snapshot=Map.of();
        }
        void appendCanonical(List<OfferEvent> previous,List<OfferEvent> next) {
            if(previous!=canonical || next==null || next.size()<authorizedSize)reset();
            canonical=next;authorizedSize=next==null?0:next.size();
        }
        Map<String,AcquisitionExitContext> read(List<OfferEvent> history) {
            if(history==null){reset();return Map.of();}
            if(history!=canonical || history.size()!=authorizedSize) {
                reset();canonical=history;authorizedSize=history.size();
            }
            if(consumed==history.size())return snapshot;
            try {
                // The reference first detects conflicting original advice over the whole prefix.
                // Newly discovered conflicts must revoke earlier results before processing the tail.
                for(int i=consumed;i<history.size();i++) {
                    var event=history.get(i);var proof=event.getAcceptedBuyPlanProof();
                    if(proof==null || !proof.qualifiesOriginal(event))continue;
                    var prior=advice.putIfAbsent(proof.getAcceptedAdviceId(),proof);
                    if(prior!=null && !prior.equals(proof) && duplicateAdvice.add(proof.getAcceptedAdviceId()))
                        for(String id:adviceEvents.getOrDefault(proof.getAcceptedAdviceId(),Set.of())) {
                            var head=contributions.get(id);
                            while(head!=null && head.adviceId!=null && duplicateAdvice.contains(head.adviceId))head=head.previous;
                            if(head==null){contributions.remove(id);result.remove(id);}
                            else{contributions.put(id,head);result.put(id,head.context);}
                        }
                }
                for(;consumed<history.size();consumed++){accept(history.get(consumed));processed++;}
                snapshot=Collections.unmodifiableMap(new HashMap<>(result));return snapshot;
            }catch(RuntimeException failure){reset();throw failure;}
        }
        private void accept(OfferEvent event) {
            if("EMPTY".equals(event.getEventType()) || event.getAccountId()==null
                || event.getSessionId()==null || event.getOfferIdentity()==null)return;
            var key=List.<Object>of(event.getAccountId(),event.getSessionId(),event.getSlot(),event.getOfferIdentity());
            var episode=episodes.get(key);
            if(episode==null) {
                episode=new Episode(event);episodes.put(key,episode);
                episode.invalid=!event.isBuying() || !qualifiesOriginal(event) || event.getExitContext()==null
                    || !event.getExitContext().isBound() || !event.getEventId().equals(event.getExitContext().getSourceEventId());
            }
            if(episode.invalid)return;
            var origin=episode.origin;var plan=origin.getAcceptedBuyPlanProof();
            if(plan!=null || origin.getAcceptedBuyPlanEvidenceVersion()!=null) {
                if(!"1".equals(origin.getAcceptedBuyPlanEvidenceVersion()) || plan==null
                    || !Objects.equals(origin.getAcceptedBuyPlanEvidenceVersion(),event.getAcceptedBuyPlanEvidenceVersion())
                    || duplicateAdvice.contains(plan.getAcceptedAdviceId()) || !plan.qualifiesOriginal(origin)
                    || !TradeGapEvidence.valid(event) || !plan.matchesEpisode(event)
                    || !Objects.equals(plan,event.getAcceptedBuyPlanProof())
                    || !Objects.equals(event.getExitContext(),plan.getExitContext())) {episode.invalid=true;return;}
                add(event.getEventId(),plan.getExitContext(),plan.getAcceptedAdviceId());return;
            }
            if(!TradeGapEvidence.valid(event) || !event.isAuthenticatedObservation()
                || event.getItemId()!=origin.getItemId() || !event.isBuying()
                || event.getPrice()!=origin.getPrice() || event.getTotalQuantity()!=origin.getTotalQuantity()
                || event.getFirstSeenAt()!=origin.getFirstSeenAt() || event.getSequence()<origin.getSequence()
                || event.getObservedAt()<origin.getObservedAt()
                || !origin.hasAcceptedExitProof() && !event.isPredictionComparable()
                || !Objects.equals(event.getAcceptedAdviceProof(),origin.getAcceptedAdviceProof())
                || !Objects.equals(event.getPredictionSourceEventId(),origin.getEventId())
                || !Objects.equals(event.getExitContext(),origin.getExitContext())) {episode.invalid=true;return;}
            add(event.getEventId(),origin.getExitContext(),null);
        }
        private void add(String id,AcquisitionExitContext context,String adviceId) {
            contributions.put(id,new Contribution(context,adviceId,contributions.get(id)));result.put(id,context);
            if(adviceId!=null)adviceEvents.computeIfAbsent(adviceId,k->new HashSet<>()).add(id);
        }
        long processedCount(){return processed;}
        private static final class Episode {
            final OfferEvent origin;boolean invalid;
            Episode(OfferEvent origin){this.origin=origin;}
        }
        private static final class Contribution {
            final AcquisitionExitContext context;final String adviceId;final Contribution previous;
            Contribution(AcquisitionExitContext context,String adviceId,Contribution previous) {
                this.context=context;this.adviceId=adviceId;this.previous=previous;
            }
        }
    }
    private static boolean qualifiesOriginal(OfferEvent event) {
        var plan=event.getAcceptedBuyPlanProof();
        if(plan!=null || event.getAcceptedBuyPlanEvidenceVersion()!=null)
            return "1".equals(event.getAcceptedBuyPlanEvidenceVersion()) && plan!=null
                && TradeGapEvidence.valid(event) && plan.qualifiesOriginal(event)
                && Objects.equals(event.getExitContext(),plan.getExitContext());
        if(ExecutionObservationLedger.qualifiesOriginal(event))return true;
        return TradeGapEvidence.valid(event) && event.isAuthenticatedObservation() && event.hasAcceptedExitProof()
            && event.isBuying() && "BUYING".equals(event.getEventType()) && event.getFilledQuantity()==0
            && event.getEventId().equals(event.getPredictionSourceEventId())
            && event.getFirstSeenAt()>0 && event.getFirstSeenAt()==event.getObservedAt()
            && event.getAdviceObservedAt()>0 && event.getAdviceObservedAt()<=event.getFirstSeenAt()
            && event.getFirstSeenAt()-event.getAdviceObservedAt()<=600 && event.wasRecommended()
            && event.getSuggestedPrice()==event.getPrice() && event.getSuggestedQuantity()==event.getTotalQuantity();
    }
}
