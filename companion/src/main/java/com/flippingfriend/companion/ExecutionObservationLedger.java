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
            && origin.getPredictedCompletion()<=1
            && (origin.getExecutionForecast()==null || origin.getExecutionForecast().matches(origin));
    }
    private static boolean active(OfferEvent e) {
        return ("BUYING".equals(e.getEventType()) || "SELLING".equals(e.getEventType())) && !e.isComplete();
    }
    private static boolean terminal(OfferEvent e) {
        return Set.of("BOUGHT","SOLD","CANCELLED_BUY","CANCELLED_SELL").contains(e.getEventType());
    }
    static ExecutionLearningReport read(String account,long now,long cursor,List<OfferEvent> history,Set<String> gaps) {
        try {
            return new ExecutionLearningReport(account,now,cursor,"OBSERVING",
                "Public-price baseline active. Timing intervals are diagnostic; no challenger has passed promotion gates.",
                compile(account,now,history,gaps));
        } catch(IllegalArgumentException | ArithmeticException invalid) {
            return ExecutionLearningReport.unavailable(account,now,invalid.getMessage());
        }
    }
    /** Complete observations for offline evaluation; HTTP readouts remain bounded separately. */
    static List<ExecutionTimingObservation> compile(String account,long now,List<OfferEvent> history,Set<String> gaps) {
        if(account==null || history==null || gaps==null || gaps.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Recorded source scope is incomplete.");
        List<ExecutionTimingObservation> result=new ArrayList<>();
        compile(account,now,history,gaps,new LinkedHashMap<>(),new HashSet<>(),new HashMap<>(),new HashSet<>(),true,result::add);
        return Collections.unmodifiableList(result);
    }
    static List<ExecutionTimingObservation> compileDisk(String account,long now,Iterable<OfferEvent> history,Set<String> gaps,OfflineScratch disk) {
        List<ExecutionTimingObservation> result=new ArrayList<>();
        compile(account,now,history,gaps,disk.map("timing-episodes",Episode.class),disk.set("timing-known"),
            disk.map("timing-order",OfferEvent.class),disk.set("timing-ids"),false,result::add);
        return Collections.unmodifiableList(result);
    }
    private static void compile(String account,long now,Iterable<OfferEvent> history,Set<String> gaps,
            Map<List<Object>,Episode> episodes,Set<String> knownOffers,Map<List<Object>,OfferEvent> ordered,
            Set<String> ids,boolean checkGapMembership,java.util.function.Consumer<ExecutionTimingObservation> output) {
        Map<Integer,Episode> latest=new HashMap<>();Map<Integer,OfferEvent> empty=new HashMap<>();
        for(OfferEvent e:history) {
            if(!TradeGapEvidence.valid(e) || !account.equals(e.getAccountId()) || !ids.add(e.getEventId()))
                throw new IllegalArgumentException("Recorded source identity is inconsistent.");
            if(e.getPredictionEvidenceVersion()!=null && e.getObservedAt()>now)
                throw new IllegalArgumentException("A prospective observation is in the future.");
            List<Object> scope=List.of(e.getSessionId(),e.getSlot());OfferEvent prior=ordered.get(scope);
            boolean order=prior==null || (e.getSequence()>prior.getSequence() && e.getObservedAt()>=prior.getObservedAt());
            if(order)ordered.put(scope,e);
            Episode previous=latest.get(e.getSlot());
            if("EMPTY".equals(e.getEventType())) {
                if(e.isAuthenticatedObservation()) {
                    if(previous!=null && previous.terminal==null){previous.ended=true;episodes.put(key(previous.original),previous);}
                    if(order)empty.put(e.getSlot(),e);else empty.remove(e.getSlot());
                }
                continue;
            }
            knownOffers.add(e.getOfferIdentity());
            List<Object> key=List.of(e.getSessionId(),e.getSlot(),e.getOfferIdentity());Episode episode=previous!=null && key(previous.original).equals(key)?previous:episodes.get(key);
            if(episode==null) {
                episode=new Episode(e);episodes.put(key,episode);
                if(previous!=null && previous!=episode && previous.terminal==null){previous.ended=true;episodes.put(key(previous.original),previous);}
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
            if(!e.isAuthenticatedObservation() || !"1".equals(e.getPredictionEvidenceVersion())){episodes.put(key,episode);continue;}
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
            episodes.put(key,episode);
        }
        if(checkGapMembership && !knownOffers.containsAll(gaps))throw new IllegalArgumentException("Recorded reconciliation scope has no matching offer source.");
        try {
            for(Episode e:episodes.values())if(e.prospective) {
                boolean qualified=e.comparable && !e.invalid && !e.accountingGap;
                String reason=e.invalid?"Recorded contradiction; timing bounds are unknown."
                    :e.accountingGap?"Recorded accounting gap; prediction is excluded."
                    :e.firstActive==null?"No prospective authenticated open observation."
                    :!qualified?"Original forecast is unknown or incomparable."
                    :"Observed intervals only; forecast decomposition and chronological validation are required.";
                output.accept(new ExecutionTimingObservation(e.original,e.firstActive,e.emptyBefore,e.lastActive,
                    e.lastZero,e.firstPositive,e.terminal,e.ended,!e.invalid,qualified,reason));
            }
        }catch(ArithmeticException overflow) {
            throw new IllegalArgumentException("Observation time cannot be bounded safely.",overflow);
        }
    }
    private static List<Object> key(OfferEvent e){return List.of(e.getSessionId(),e.getSlot(),e.getOfferIdentity());}
    private static final class Episode {
        final OfferEvent original;
        OfferEvent firstActive,lastActive,emptyBefore,lastZero,firstPositive,terminal;
        boolean prospective,invalid,ended,comparable,accountingGap;
        int lastFilled;
        Episode(OfferEvent origin){original=origin;comparable=qualifiesOriginal(origin);}
    }
}
