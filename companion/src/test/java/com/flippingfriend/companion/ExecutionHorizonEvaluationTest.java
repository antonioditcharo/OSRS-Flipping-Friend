package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.model.FillEstimate;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExecutionHorizonEvaluationTest {
    static final long AT=1_700_000_000;
    static final String ACCOUNT=AccountIdentity.fromProfileKey("evaluation");
    static ExecutionForecast forecast(boolean buy,double horizon,double probability) {
        return new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,561,buy,100,10,horizon/3600,1,
            3600,12,6,1000,new FillEstimate(probability,.2,100,.1),1,.2);
    }
    static OfferEvent event(String id,long seq,long at,String state,int filled,ExecutionForecast f) {
        return OfferEvent.builder("fixture",AT+at,state).accountId(ACCOUNT).eventIdentity(id,"session","offer")
            .sequence(seq).slot(0).authenticatedObservation(true).item(561,"Nature rune").buying(f.isBuying())
            .price(100).quantities(10,filled).spent(100L*filled).firstSeenAt(AT+100)
            .recommendation("original",100,10,0,f.getExpectedTotalSeconds()/60,f.getCompletionProbability())
            .predictionEvidence("birth",AT+98,true).executionForecast(f).build();
    }
    static List<OfferEvent> history(ExecutionForecast f,long active,long terminal,String state,boolean empty) {
        List<OfferEvent> events=new ArrayList<>();
        if(empty)events.add(OfferEvent.builder("fixture",AT+90,"EMPTY").accountId(ACCOUNT).eventIdentity("empty","session",null)
            .sequence(1).slot(0).authenticatedObservation(true).build());
        events.add(event("birth",2,100,f.isBuying()?"BUYING":"SELLING",0,f));
        if(active>0)events.add(event("active",3,active,f.isBuying()?"BUYING":"SELLING",3,f));
        if(terminal>0)events.add(event("terminal",4,terminal,state,state.equals("BOUGHT")||state.equals("SOLD")?10:3,f));
        return events;
    }
    static ExecutionHorizonEvaluation score(ExecutionForecast f,long active,long terminal,String state,boolean empty) {
        return new ExecutionHorizonEvaluation(ExecutionObservationLedger.compile(ACCOUNT,AT+10000,history(f,active,terminal,state,empty),Set.of()),null);
    }
    @Test public void completedBeforeHorizonHasAnExactBrierContribution() {
        var e=score(forecast(true,300,.8),150,200,"BOUGHT",true);
        assertEquals(1,e.knownCompleted);assertEquals(0,e.ambiguous);
        assertEquals(.04,e.scores.get(0).brierLower,1e-12);assertEquals(.04,e.scores.get(0).brierUpper,1e-12);
        assertEquals(1,e.groups.get(0).completionRateLower,0);assertEquals("HOLD_FOR_VALIDATION",e.promotionStatus);
    }
    @Test public void cancellationBeforeHorizonIsUnknownRatherThanAFailedPrediction() {
        var e=score(forecast(true,300,.8),150,600,"CANCELLED_BUY",true);
        assertEquals(0,e.knownIncomplete);assertEquals(1,e.ambiguous);
        assertEquals(.04,e.scores.get(0).brierLower,1e-12);assertEquals(.64,e.scores.get(0).brierUpper,1e-12);
        assertEquals(0,e.groups.get(0).completionRateLower,0);assertEquals(1,e.groups.get(0).completionRateUpper,0);
    }
    @Test public void aConfirmedActiveReadoutBeyondHorizonSuppliesANegativeLabelEvenIfLaterCanceled() {
        for(String state:List.of("CANCELLED_BUY","BOUGHT","OPEN")) {
            var e=score(forecast(true,300,.8),500,state.equals("OPEN")?0:600,state,true);
            assertEquals(1,e.knownIncomplete);assertEquals(0,e.ambiguous);
            assertEquals(.64,e.scores.get(0).brierLower,1e-12);assertEquals(.64,e.scores.get(0).brierUpper,1e-12);
        }
    }
    @Test public void completedIntervalStraddlingHorizonAndUnknownPlacementRemainAmbiguous() {
        assertEquals(1,score(forecast(true,300,.8),150,400,"BOUGHT",true).ambiguous);
        assertEquals(1,score(forecast(true,300,.8),150,200,"BOUGHT",false).ambiguous);
    }
    @Test public void equalityAtConservativeLowerBoundDoesNotInventFailure() {
        assertEquals(1,score(forecast(true,399,.8),500,600,"CANCELLED_BUY",true).ambiguous);
        assertEquals(1,score(forecast(true,398,.8),500,600,"CANCELLED_BUY",true).knownIncomplete);
        assertEquals(1,score(forecast(true,111,.8),150,200,"BOUGHT",true).knownCompleted);
    }
    @Test public void zeroProbabilityAndSellLegsKeepTheirOwnHorizonAndScore() {
        var e=score(forecast(false,300,0),150,200,"SOLD",true);
        assertEquals(1,e.knownCompleted);assertFalse(e.groups.get(0).buying);
        assertEquals(1,e.scores.get(0).brierLower,0);assertEquals(300,e.groups.get(0).horizonSeconds,0);
    }
    @Test public void missingOriginalForecastAndRecordedGapsAreExcludedWithoutReconstructingContext() {
        var f=forecast(true,300,.8);var history=history(f,150,200,"BOUGHT",true);Gson gson=new Gson();
        var raw=gson.toJsonTree(history.get(1)).getAsJsonObject();raw.remove("executionForecast");history.set(1,gson.fromJson(raw,OfferEvent.class));
        var e=new ExecutionHorizonEvaluation(ExecutionObservationLedger.compile(ACCOUNT,AT+10000,history,Set.of()),null);
        assertEquals(0,e.eligible);assertEquals(1,e.unqualified);
        e=new ExecutionHorizonEvaluation(ExecutionObservationLedger.compile(ACCOUNT,AT+10000,history(f,150,200,"BOUGHT",true),Set.of("offer")),null);
        assertEquals(0,e.eligible);assertEquals(1,e.unqualified);
    }
    @Test public void fullEvaluationDoesNotUseTheTwoHundredRowHttpTail() {
        List<OfferEvent> all=new ArrayList<>();Gson gson=new Gson();long sequence=0;
        for(int i=0;i<205;i++)for(var original:history(forecast(i%2==0,300,.8),150,200,i%2==0?"BOUGHT":"SOLD",true)) {
            var raw=gson.toJsonTree(original).getAsJsonObject();
            String id=original.getEventId()+"-"+i;raw.addProperty("eventId",id);raw.addProperty("sequence",++sequence);
            raw.addProperty("offerIdentity",original.getOfferIdentity()==null?null:"offer-"+i);
            if(original.getOfferIdentity()!=null)raw.addProperty("predictionSourceEventId","birth-"+i);
            // Separate sessions keep the fixture's repeated capture clocks honest.
            raw.addProperty("sessionId","session-"+i);all.add(gson.fromJson(raw,OfferEvent.class));
        }
        var rows=ExecutionObservationLedger.compile(ACCOUNT,AT+10000,all,Set.of());
        var e=new ExecutionHorizonEvaluation(rows,null);assertEquals(205,e.eligible);assertEquals(205,e.knownCompleted);
        assertEquals(200,ExecutionObservationLedger.read(ACCOUNT,AT+10000,sequence,all,Set.of()).getObservations().size());
        assertEquals(2,e.groups.size());
    }
}
