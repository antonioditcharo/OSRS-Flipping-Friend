package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Pure readout of recorded source events. Never posts accounting, claims, samples or repairs. */
final class ExecutionObservationLedger {
    private ExecutionObservationLedger(){ }

    static boolean qualifiesOriginal(OfferEvent origin) {
        return TradeGapEvidence.valid(origin) && origin.isAuthenticatedObservation()
            && origin.getFilledQuantity()==0 && (origin.isBuying()?"BUYING":"SELLING").equals(origin.getEventType())
            && "1".equals(origin.getPredictionEvidenceVersion()) && origin.isPredictionComparable()
            && origin.getEventId().equals(origin.getPredictionSourceEventId())
            && origin.getFirstSeenAt()>0 && origin.getFirstSeenAt()==origin.getObservedAt()
            && origin.getAdviceObservedAt()>0 && origin.getAdviceObservedAt()<origin.getFirstSeenAt()
            && origin.getFirstSeenAt()-origin.getAdviceObservedAt()<=600
            && origin.wasRecommended() && origin.getSuggestedPrice()==origin.getPrice()
            && origin.getSuggestedQuantity()==origin.getTotalQuantity()
            && Double.isFinite(origin.getPredictedMinutes()) && origin.getPredictedMinutes()>0
            && Double.isFinite(origin.getPredictedCompletion()) && origin.getPredictedCompletion()>=0
            && origin.getPredictedCompletion()<=1;
    }
    private static boolean active(OfferEvent e) {
        return ("BUYING".equals(e.getEventType()) || "SELLING".equals(e.getEventType())) && !e.isComplete();
    }
    private static boolean terminal(OfferEvent e) {
        return Set.of("BOUGHT","SOLD","CANCELLED_BUY","CANCELLED_SELL").contains(e.getEventType());
    }
    static ExecutionLearningReport read(String account,long now,long cursor,List<OfferEvent> history,Set<String> gaps) {
        if(account==null || history==null || gaps==null || gaps.stream().anyMatch(Objects::isNull))
            return ExecutionLearningReport.unavailable(account,now,"Recorded source scope is incomplete.");
        Map<List<Object>,Episode> episodes=new LinkedHashMap<>();
        Map<Integer,Episode> latest=new HashMap<>();Map<Integer,OfferEvent> empty=new HashMap<>();
        Map<List<Object>,OfferEvent> ordered=new HashMap<>();Set<String> ids=new HashSet<>();
        for(OfferEvent e:history) {
            if(!TradeGapEvidence.valid(e) || !account.equals(e.getAccountId()) || !ids.add(e.getEventId()))
                return ExecutionLearningReport.unavailable(account,now,"Recorded source identity is inconsistent.");
            if(e.getPredictionEvidenceVersion()!=null && e.getObservedAt()>now)
                return ExecutionLearningReport.unavailable(account,now,"A prospective observation is in the future.");
            List<Object> scope=List.of(e.getSessionId(),e.getSlot());OfferEvent prior=ordered.get(scope);
            boolean order=prior==null || (e.getSequence()>prior.getSequence() && e.getObservedAt()>=prior.getObservedAt());
            if(order)ordered.put(scope,e);
            Episode previous=latest.get(e.getSlot());
            if("EMPTY".equals(e.getEventType())) {
                if(e.isAuthenticatedObservation()) {
                    if(previous!=null && previous.terminal==null)previous.ended=true;
                    if(order)empty.put(e.getSlot(),e);else empty.remove(e.getSlot());
                }
                continue;
            }
            List<Object> key=List.of(e.getSessionId(),e.getSlot(),e.getOfferIdentity());Episode episode=episodes.get(key);
            if(episode==null) {
                episode=new Episode(e);episodes.put(key,episode);
                if(previous!=null && previous!=episode && previous.terminal==null)previous.ended=true;
            }
            latest.put(e.getSlot(),episode);
            if(episode.terminal!=null)continue; // First terminal observation stays fixed on later heartbeat/reconnect.
            episode.prospective|=e.getPredictionEvidenceVersion()!=null;
            episode.invalid|=!order || OfferLifecycleClassifier.classify(e)==OfferLifecycleState.ERROR_RECONCILIATION
                || e.getItemId()!=episode.original.getItemId() || e.isBuying()!=episode.original.isBuying()
                || e.getPrice()!=episode.original.getPrice() || e.getTotalQuantity()!=episode.original.getTotalQuantity()
                || e.getFilledQuantity()<episode.lastFilled;
            episode.accountingGap|=gaps.contains(e.getOfferIdentity());
            episode.lastFilled=e.getFilledQuantity();
            episode.comparable&="1".equals(e.getPredictionEvidenceVersion()) && e.isPredictionComparable()
                && episode.original.getEventId().equals(e.getPredictionSourceEventId())
                && e.getFirstSeenAt()==episode.original.getFirstSeenAt();
            if(!e.isAuthenticatedObservation() || !"1".equals(e.getPredictionEvidenceVersion()))continue;
            if(active(e)) {
                if(episode.firstActive==null) {
                    episode.firstActive=e;OfferEvent before=empty.get(e.getSlot());
                    if(before!=null && before.getSessionId().equals(e.getSessionId())
                        && before.getSequence()<e.getSequence() && before.getObservedAt()<=e.getObservedAt())episode.emptyBefore=before;
                }
                episode.lastActive=e;
            }
            if(episode.firstPositive==null) {
                if(e.getFilledQuantity()==0 && active(e))episode.lastZero=e;
                if(e.getFilledQuantity()>0)episode.firstPositive=e;
            }
            if(terminal(e))episode.terminal=e;
        }
        List<ExecutionTimingObservation> observations=new ArrayList<>();
        try {
            for(Episode e:episodes.values())if(e.prospective) {
                boolean qualified=e.comparable && !e.invalid && !e.accountingGap;
                String reason=e.invalid?"Recorded contradiction; timing bounds are unknown."
                    :e.accountingGap?"Recorded accounting gap; prediction is excluded."
                    :e.firstActive==null?"No prospective authenticated open observation."
                    :!qualified?"Original forecast is unknown or incomparable."
                    :"Observed intervals only; forecast decomposition and chronological validation are required.";
                observations.add(new ExecutionTimingObservation(e.original,e.firstActive,e.emptyBefore,e.lastActive,
                    e.lastZero,e.firstPositive,e.terminal,e.ended,!e.invalid,qualified,reason));
            }
        }catch(ArithmeticException overflow) {
            return ExecutionLearningReport.unavailable(account,now,"Observation time cannot be bounded safely.");
        }
        return new ExecutionLearningReport(account,now,cursor,"OBSERVING",
            "Public-price baseline active. Timing intervals are diagnostic; no challenger has passed promotion gates.",observations);
    }
    private static final class Episode {
        final OfferEvent original;
        OfferEvent firstActive,lastActive,emptyBefore,lastZero,firstPositive,terminal;
        boolean prospective,invalid,ended,comparable,accountingGap;
        int lastFilled;
        Episode(OfferEvent origin){original=origin;comparable=qualifiesOriginal(origin);}
    }
}
