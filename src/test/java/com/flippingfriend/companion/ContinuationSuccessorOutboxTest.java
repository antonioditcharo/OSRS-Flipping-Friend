package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual forced account outbox receipts, independent of delivery ACKs and learning attribution. */
public class ContinuationSuccessorOutboxTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    static final long AT=1800000000;
    PluginStorage storage;
    OfferEventOutbox box;
    @Before public void setup()throws Exception {
        storage=TestStorage.rootedAt(folder.newFolder().toPath(),"successors");box=new OfferEventOutbox(storage);
    }
    ContinuationSuccessorProof advice(String origin,String id,int quantity,boolean buying) {
        return ContinuationSuccessorProof.accepted(storage.accountId(),origin,buying?null:"accepted-source-"+origin,
            id,561,buying,150,quantity,AT,CaptureOrder.next());
    }
    OfferEventOutbox.EventFactory frame(String episode,int slot,long start,long at,int quantity,int filled,boolean buying,
            ContinuationSuccessorProof proof) {
        return (id,session,sequence)->OfferEvent.builder("fixture",at,buying?"BUYING":"SELLING")
            .eventIdentity(id,session,episode).sequence(sequence).authenticatedObservation(true).slot(slot).item(561,"Nature rune")
            .buying(buying).price(150).quantities(quantity,filled).spent(150L*filled).firstSeenAt(start)
            .continuationSuccessorProof(proof).build();
    }
    OfferEvent first(String origin,String episode,int slot,int quantity,boolean buying)throws Exception {
        var accepted=advice(origin,"advice-"+episode,quantity,buying).captured(CaptureOrder.next());
        return box.enqueueObservedOffer(frame(episode,slot,AT,AT,quantity,0,buying,accepted));
    }
    void ackAll()throws Exception {for(var e:box.pending())assertTrue(box.acknowledge(e.getEventId(),e.getSessionId(),e.getSequence()));}
    @Test public void exactSameSecondSuccessorSurvivesDifferentAdviceFillsAckEmptyAndRestart()throws Exception {
        var first=first("origin-A","child-A",0,80,false);var proof=first.getContinuationSuccessorProof();
        assertNotNull(proof);assertTrue(proof.qualifiesOriginal(first));assertFalse(first.isPredictionComparable());
        var other=advice("origin-B","later",80,false).captured(CaptureOrder.next());
        var progress=box.enqueueObservedOffer(frame("child-A",0,AT,AT+10,80,30,false,other));
        assertEquals(proof,progress.getContinuationSuccessorProof());ackAll();
        box.enqueueObservedOffer((id,s,q)->OfferEvent.builder("empty",AT+20,"EMPTY").eventIdentity(id,s,null)
            .sequence(q).slot(0).authenticatedObservation(true).build());ackAll();
        box=new OfferEventOutbox(storage);assertTrue(box.pending().isEmpty());
        assertEquals(1,box.observedSuccessorEpisodes().size());assertEquals(1,box.successorBindings().size());
        assertEquals(first.getEventId(),box.successorBindings().get(0).getEventId());
        assertEquals(proof,box.successorBindings().get(0).getContinuationSuccessorProof());
        assertEquals("accepted-source-origin-A",proof.getOriginRepriceSource());
    }
    @Test public void firstUnboundOrAlreadyFilledObservationCannotBorrowLaterAdvice()throws Exception {
        for(boolean filled:new boolean[]{false,true}) {
            int slot=filled?1:0;String episode="unbound-"+slot;
            var prospective=advice("origin-A","initial-"+slot,80,false).captured(CaptureOrder.next());
            var first=box.enqueueObservedOffer(frame(episode,slot,AT,AT,80,filled?1:0,false,filled?prospective:null));
            assertNull(first.getContinuationSuccessorProof());
            var later=advice("origin-A","late-"+slot,80,false).captured(CaptureOrder.next());
            var next=box.enqueueObservedOffer(frame(episode,slot,AT,AT+10,80,20,false,later));
            assertNull(next.getContinuationSuccessorProof());
        }
        assertEquals(2,box.observedSuccessorEpisodes().size());assertTrue(box.successorBindings().isEmpty());
    }
    @Test public void firstCallbackPublicationAcrossASecondBoundaryKeepsItsProspectiveOrderWithoutBackfilling()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            int slot=buying?0:1;String episode="delayed-"+slot;
            var accepted=advice("origin-A","display-"+slot,80,buying);
            var first=box.enqueueObservedOffer(frame(episode,slot,AT,AT+1,80,0,buying,accepted.captured(CaptureOrder.next())));
            var proof=first.getContinuationSuccessorProof();assertNotNull(proof);assertTrue(proof.qualifiesOriginal(first));
            assertEquals(AT,proof.getFirstSeenAt());assertEquals(AT+1,proof.getCapturedAt());
            assertFalse(proof.qualifiesOriginal(frame(episode,slot,AT,AT,80,0,buying,null)
                .build(first.getEventId(),first.getSessionId(),first.getSequence()).withAccountId(storage.accountId())));
        }
        var stale=advice("origin-A","stale",80,false).captured(CaptureOrder.next());
        assertNull(box.enqueueObservedOffer(frame("expired-publication",2,AT,AT+601,80,0,false,stale)).getContinuationSuccessorProof());
    }
    @Test public void changedExactOrderAndCrossAccountProofAreUnboundEvenWithMatchingItem()throws Exception {
        var supplied=advice("origin-A","accepted",80,false).captured(CaptureOrder.next());
        assertNull(box.enqueueObservedOffer(frame("wrong-quantity",0,AT,AT,79,0,false,supplied)).getContinuationSuccessorProof());
        var foreign=ContinuationSuccessorProof.accepted(AccountIdentity.fromProfileKey("other"),"origin-A","foreign",561,false,
            150,80,AT,CaptureOrder.next()).captured(CaptureOrder.next());
        assertNull(box.enqueueObservedOffer(frame("other-account",1,AT,AT,80,0,false,foreign)).getContinuationSuccessorProof());
        var valid=first("origin-B","valid",2,70,false);
        assertNull(box.enqueueObservedOffer(frame("valid",2,AT,AT+1,69,0,false,supplied)).getContinuationSuccessorProof());
        assertEquals(valid.getContinuationSuccessorProof(),box.successorBindings().get(0).getContinuationSuccessorProof());
    }
    @Test public void aSingleAcceptanceCannotBindTwoEpisodesOrBeReboundAfterEmpty()throws Exception {
        var accepted=advice("origin-A","once",80,true).captured(CaptureOrder.next());
        var first=box.enqueueObservedOffer(frame("first",0,AT,AT,80,0,true,accepted));assertNotNull(first.getContinuationSuccessorProof());
        assertNull(box.enqueueObservedOffer(frame("second",1,AT,AT,80,0,true,accepted)).getContinuationSuccessorProof());
        ackAll();box=new OfferEventOutbox(storage);
        assertNull(box.enqueueObservedOffer(frame("third",2,AT,AT,80,0,true,accepted)).getContinuationSuccessorProof());
        assertEquals(1,box.successorBindings().size());assertEquals(3,box.observedSuccessorEpisodes().size());
    }
    @Test public void laterOrOtherProcessAcceptanceCannotQualifyAndLegacyIdentityNormalizes()throws Exception {
        CaptureOrder capture=CaptureOrder.next();var late=advice("origin-A","late",80,false).captured(capture);
        assertNull(box.enqueueObservedOffer(frame("late",0,AT,AT,80,0,false,late)).getContinuationSuccessorProof());
        var json=new Gson();var foreign=json.toJsonTree(advice("origin-B","other-process",70,false)).getAsJsonObject();
        foreign.getAsJsonObject("acceptedOrder").addProperty("process","another-process");
        var supplied=json.fromJson(foreign,ContinuationSuccessorProof.class).captured(CaptureOrder.next());
        assertNull(box.enqueueObservedOffer(frame("foreign",1,AT,AT,70,0,false,supplied)).getContinuationSuccessorProof());
        var legacy=first("origin-C","2@"+AT+":561",2,50,false);
        assertEquals(OfferContinuation.identity(2,561,false,150,50,AT,null),legacy.getContinuationSuccessorProof().getSuccessorIdentity());
        assertEquals(legacy.getContinuationSuccessorProof().getSuccessorIdentity(),ContinuationSuccessorProof.workflowIdentity(legacy));
    }
    @Test public void failedAppendCannotExposeReceiptAndRetryPreservesOriginalIdsThroughAccountSwitch()throws Exception {
        var fail=new AtomicBoolean(true);box=new OfferEventOutbox(storage,(f,t)->{if(fail.get())throw new java.io.IOException("fixture");});
        var accepted=advice("origin-A","accepted",80,false).captured(CaptureOrder.next());
        try{box.enqueueObservedOffer(frame("first",0,AT,AT,80,0,false,accepted));fail();}catch(java.io.IOException expected){}
        try{box.successorBindings();fail("Uncommitted relationship must remain unavailable");}catch(java.io.IOException expected){}
        var other=TestStorage.rootedAt(folder.newFolder().toPath(),"other");assertTrue(new OfferEventOutbox(other).successorBindings().isEmpty());
        fail.set(false);box.retryUnsaved();var original=box.pending().get(0);ackAll();
        var restored=new OfferEventOutbox(storage).successorBindings().get(0);
        assertEquals(original.getEventId(),restored.getEventId());assertEquals(original.getSequence(),restored.getSequence());
        assertEquals(original.getContinuationSuccessorProof(),restored.getContinuationSuccessorProof());
    }
    @Test public void corruptRelationshipReceiptsCannotBecomeClassificationAuthority()throws Exception {
        first("origin-A","first",0,80,false);ackAll();Path file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        var json=new Gson();var state=json.fromJson(Files.readString(file),com.google.gson.JsonObject.class);
        var receipts=state.getAsJsonObject("successorEpisodes");var event=receipts.entrySet().iterator().next().getValue().getAsJsonObject();
        event.getAsJsonObject("continuationSuccessorProof").addProperty("quantity",81);
        Files.writeString(file,json.toJson(state));
        try{new OfferEventOutbox(storage).successorBindings();fail();}catch(IllegalStateException expected){}
    }
    @Test public void fullObservationMatchingRejectsChangedFieldsEvenWithSameExplicitEpisode()throws Exception {
        var proof=first("origin-A","explicit",0,80,false).getContinuationSuccessorProof();
        assertTrue(proof.matches(new OfferContinuation.Observation(0,561,false,150,80,0,AT,"explicit","SELLING",false)));
        assertFalse(proof.matches(new OfferContinuation.Observation(0,561,false,149,80,0,AT,"explicit","SELLING",false)));
        assertFalse(proof.matches(new OfferContinuation.Observation(1,561,false,150,80,0,AT,"explicit","SELLING",false)));
        assertFalse(proof.matches(new OfferContinuation.Observation(0,561,false,150,79,0,AT,"explicit","SELLING",false)));
    }
    @Test public void immutableAcceptanceMatchesAcrossCaptureAndBindingButNotChangedAuthority()throws Exception {
        var accepted=advice("origin-A","same",80,false);
        var captured=accepted.captured(CaptureOrder.next());
        var bound=box.enqueueObservedOffer(frame("first",0,AT,AT,80,0,false,captured)).getContinuationSuccessorProof();
        assertTrue(accepted.hasSameAcceptance(captured));assertTrue(accepted.hasSameAcceptance(bound));
        assertTrue(bound.hasSameAcceptance(accepted));assertFalse(accepted.hasSameAcceptance(null));
        var gson=new Gson();
        for(String field:new String[]{"accountId","originIdentity","originSaleRepriceSource","acceptedAdviceId","itemId",
                "buying","price","quantity","acceptedAt","acceptedOrder"}) {
            var changed=gson.toJsonTree(accepted).getAsJsonObject();
            switch(field) {
                case "accountId": changed.addProperty(field,AccountIdentity.fromProfileKey("other"));break;
                case "itemId": changed.addProperty(field,562);break;
                case "buying": changed.addProperty(field,true);changed.remove("originSaleRepriceSource");break;
                case "price": changed.addProperty(field,151);break;
                case "quantity": changed.addProperty(field,79);break;
                case "acceptedAt": changed.addProperty(field,AT-1);break;
                case "acceptedOrder": changed.getAsJsonObject(field).addProperty("order",accepted.getAcceptedOrder().getOrder()+1);break;
                default: changed.addProperty(field,"different");
            }
            assertFalse(field,accepted.hasSameAcceptance(gson.fromJson(changed,ContinuationSuccessorProof.class)));
        }
    }
    @Test public void realClientCapturesRenewedSuccessorWhenStableLearningAdviceHasExpired()throws Exception {
        var seconds=new java.util.concurrent.atomic.AtomicLong(AT-601);
        java.time.Clock clock=new java.time.Clock() {
            public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
            public java.time.Clock withZone(java.time.ZoneId zone){return this;}
            public java.time.Instant instant(){return java.time.Instant.ofEpochSecond(seconds.get());}
        };
        var ledger=new SuggestionLedger(clock);
        ledger.recorded("original-learning",561,false,150,80,0,0,0);
        seconds.set(AT);var accepted=advice("origin-A","renewed-workflow",80,false);
        ledger.recorded("unchanged-learning",561,false,150,80,0,0,0,null,null,accepted);
        var client=new CompanionClient(storage,storage.gson(),ledger,box);
        var offer=new com.flippingfriend.session.TrackedOffer(0,561,false,150,80,AT);offer.setState("SELLING");
        client.captureObservedOffer(offer,storage.accountId(),AT);
        var event=box.pending().get(0);
        assertNull("An expired learning association is still expired",event.getRecommendationId());
        assertNotNull("Fresh workflow acceptance is independent from the learning display clock",event.getContinuationSuccessorProof());
        assertTrue(accepted.hasSameAcceptance(event.getContinuationSuccessorProof()));
        assertEquals("renewed-workflow",event.getContinuationSuccessorProof().getAcceptedAdviceId());
        assertFalse(event.isPredictionComparable());
    }
    @Test public void corruptedScopeAndDuplicateFirstSourceSequencesWithholdReceiptsWithoutRewritingSources()throws Exception {
        first("origin-A","first",0,80,false);first("origin-B","second",1,70,false);ackAll();
        Path file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);String original=Files.readString(file);
        for(String corruption:new String[]{"account","session","sequence"}) {
            var json=new Gson();var state=json.fromJson(original,com.google.gson.JsonObject.class);
            var entries=new ArrayList<>(state.getAsJsonObject("successorEpisodes").entrySet());
            var first=entries.get(0).getValue().getAsJsonObject();var second=entries.get(1).getValue().getAsJsonObject();
            if("account".equals(corruption))first.addProperty("accountId",AccountIdentity.fromProfileKey("other"));
            else if("session".equals(corruption))first.addProperty("sessionId","other-session");
            else second.addProperty("sequence",first.get("sequence").getAsLong());
            String corrupt=json.toJson(state);Files.writeString(file,corrupt);
            try{new OfferEventOutbox(storage).observedSuccessorEpisodes();fail(corruption);}catch(IllegalStateException expected){}
            assertEquals(corrupt,Files.readString(file));
        }
    }
    @Test public void visibleWaitAndFailedSaveWithdrawOnlySuccessorAndSameObjectCanBeAcceptedAgain()throws Exception {
        long now=java.time.Instant.now().getEpochSecond();
        var ledger=new SuggestionLedger();var client=new CompanionClient(storage,storage.gson(),ledger,box);
        var proof=ContinuationSuccessorProof.accepted(storage.accountId(),"origin-A","display",561,false,150,80,now,CaptureOrder.next());
        var suggestion=com.flippingfriend.model.Suggestion.builder(com.flippingfriend.model.SuggestionType.SELL)
            .item(561,"Nature rune").price(150).quantity(80).continuationOrigin("origin-A").build();
        suggestion.bindContinuationSuccessorProof(proof);
        client.recordPresentedAdvice(suggestion,storage.accountId());
        assertEquals(proof,ledger.attributeSuccessor(561,false,now));
        var originalLearning=ledger.attribute(561,false,now);assertNotNull(originalLearning);
        client.recordPresentedAdvice(com.flippingfriend.model.Suggestion.waiting("Checking","Waiting"),storage.accountId());
        assertNull(ledger.attributeSuccessor(561,false,now));
        assertSame("WAIT must not erase historical learning association",originalLearning,ledger.attribute(561,false,now));
        client.recordPresentedAdvice(suggestion,storage.accountId());
        assertEquals("Returning the same object must re-establish display evidence",proof,ledger.attributeSuccessor(561,false,now));
        client.withdrawPresentedSuccessor();
        assertNull(ledger.attributeSuccessor(561,false,now));assertNotNull(ledger.attribute(561,false,now));
        client.recordPresentedAdvice(suggestion,storage.accountId());assertEquals(proof,ledger.attributeSuccessor(561,false,now));
    }
}
