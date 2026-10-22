package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only qualification against the earliest retained canonical source, never today's item advice. */
final class AcquisitionExitEvidence {
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
