package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.TrackedOffer;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual client capture after learning attribution expires; disposable files, no HTTP/game input. */
public class AcceptedBuyPlanDeliveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    static final long AT=1800000000;
    @Test public void freshAcceptedUnchangedBuyRetainsItsOriginalPlanWithoutRenewingLearning()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"accepted-buy");
        var seconds=new AtomicLong(AT-601);
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}
            public Instant instant(){return Instant.ofEpochSecond(seconds.get());}};
        var ledger=new SuggestionLedger(clock);
        ledger.recorded("old-learning",561,true,100,10,0,10,.8,null,new AcquisitionExitContext(150,80,45));
        seconds.set(AT);ledger.recorded("fresh-display",561,true,100,10,0,10,.8,null,new AcquisitionExitContext(170,90,90));
        var outbox=new OfferEventOutbox(storage);var client=new CompanionClient(storage,storage.gson(),ledger,outbox);
        var offer=new TrackedOffer(0,561,true,100,10,AT);offer.setState("BUYING");
        client.captureObservedOffer(offer,storage.accountId(),AT);
        var first=outbox.pending().get(0);
        assertNull("Expired learning attribution must remain expired",first.getRecommendationId());
        assertFalse(first.isPredictionComparable());
        assertNotNull("Fresh accepted acquisition context cannot expire with the learning clock",first.getExitContext());
        assertEquals(170,first.getExitContext().getTargetSellPrice());assertEquals(90,first.getExitContext().getStopPrice());
        assertEquals(first.getEventId(),first.getExitContext().getSourceEventId());
    }
    static final class Fixture {
        final PluginStorage storage;final AtomicLong seconds;final SuggestionLedger ledger;
        final OfferEventOutbox outbox;final CompanionClient client;
        Fixture(java.nio.file.Path path){this(TestStorage.rootedAt(path,"accepted-buy"),new AtomicLong(AT),null);}
        Fixture(PluginStorage storage,AtomicLong seconds,OfferEventOutbox existing) {
            this.storage=storage;this.seconds=seconds;
            ledger=new SuggestionLedger(new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}
                public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochSecond(seconds.get());}});
            outbox=existing==null?new OfferEventOutbox(storage):existing;
            client=new CompanionClient(storage,storage.gson(),ledger,outbox);
        }
        void advice(String id,int target){ledger.recorded(id,561,true,100,10,0,0,0,null,new AcquisitionExitContext(target,90,480));}
        TrackedOffer offer(int slot,long first,int price,int filled,String state) {
            var offer=new TrackedOffer(slot,561,true,price,10,first);offer.setState(state);offer.setQuantityFilled(filled);
            offer.setSpent((long)price*filled);return offer;
        }
        OfferEvent publish(TrackedOffer offer,long at)throws Exception {
            client.captureObservedOffer(offer,storage.accountId(),at);
            var pending=outbox.pending();assertFalse(pending.isEmpty());return pending.get(pending.size()-1);
        }
        void ackAll()throws Exception{for(var e:outbox.pending())assertTrue(outbox.acknowledge(e.getEventId(),e.getSessionId(),e.getSequence()));}
    }
    @Test public void acknowledgedPlanWithoutForecastSurvivesPartialFillsRestartAndChangedAdvice()throws Exception {
        var path=folder.newFolder().toPath();var before=new Fixture(path);before.advice("accepted",170);
        var first=before.publish(before.offer(0,AT,100,0,"BUYING"),AT+1);
        assertNotNull(first.getAcceptedBuyPlanProof());assertNull(first.getAcceptedBuyPlanProof().getForecast());
        assertFalse(first.isPredictionComparable());assertEquals(170,first.getExitContext().getTargetSellPrice());
        var partial=before.publish(before.offer(0,AT,100,3,"BUYING"),AT+60);
        assertEquals(first.getAcceptedBuyPlanProof(),partial.getAcceptedBuyPlanProof());before.ackAll();
        var after=new Fixture(path);after.seconds.set(AT+8*3600);after.advice("different",999);
        var terminal=after.publish(after.offer(0,AT,100,10,"BOUGHT"),AT+8*3600);
        assertEquals(first.getAcceptedBuyPlanProof(),terminal.getAcceptedBuyPlanProof());
        assertEquals(first.getExitContext(),terminal.getExitContext());assertEquals(1000,terminal.getSpent());
        assertEquals(10,terminal.getFilledQuantity());assertTrue(Double.isNaN(after.client.originalBuyExpectedMinutes(0)));
    }
    @Test public void changedOrderThenChangedBackStaysUnknownAcrossRestart()throws Exception {
        var path=folder.newFolder().toPath();var before=new Fixture(path);before.advice("accepted",170);
        var first=before.publish(before.offer(0,AT,100,0,"BUYING"),AT);assertNotNull(first.getExitContext());
        var changed=before.publish(before.offer(0,AT,101,0,"BUYING"),AT+1);
        assertNull(changed.getAcceptedBuyPlanProof());assertNull(changed.getExitContext());before.ackAll();
        var after=new Fixture(path);after.seconds.set(AT+2);after.advice("new-claim",190);
        var changedBack=after.publish(after.offer(0,AT,100,2,"BUYING"),AT+2);
        assertNull(changedBack.getAcceptedBuyPlanProof());assertNull(changedBack.getExitContext());
        assertEquals("1",changedBack.getAcceptedBuyPlanEvidenceVersion());assertEquals(200,changedBack.getSpent());
    }
    @Test public void oneAcceptedDisplayCannotLabelTwoEpisodesOrBorrowLegacyContext()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());f.advice("one-display",170);
        var first=f.publish(f.offer(0,AT,100,0,"BUYING"),AT);assertNotNull(first.getAcceptedBuyPlanProof());
        var other=f.publish(f.offer(1,AT,100,0,"BUYING"),AT+1);
        assertNull(other.getAcceptedBuyPlanProof());assertNull(other.getExitContext());
        assertNull(other.getAcceptedAdviceProof());assertEquals("1",other.getAcceptedBuyPlanEvidenceVersion());
        assertEquals(170,first.getExitContext().getTargetSellPrice());
        assertEquals(first.getEventId(),f.outbox.observedSuccessorEpisodes().stream()
            .filter(e->e.getSlot()==0).findFirst().get().getEventId());
    }
    @Test public void unknownFirstSourceCannotAcquireALaterPlanOrUseLegacyAcceptedProof()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());
        var unknown=f.publish(f.offer(0,AT,100,0,"BUYING"),AT);
        assertEquals("1",unknown.getAcceptedBuyPlanEvidenceVersion());assertNull(unknown.getExitContext());
        f.seconds.set(AT+1);f.advice("late",170);
        var later=f.publish(f.offer(0,AT,100,0,"BUYING"),AT+1);
        assertNull(later.getAcceptedBuyPlanProof());assertNull(later.getAcceptedAdviceProof());assertNull(later.getExitContext());
        f.ackAll();var restart=new Fixture(f.storage.root());restart.advice("still-later",190);
        var filled=restart.publish(restart.offer(0,AT,100,10,"BOUGHT"),AT+10);
        assertNull(filled.getAcceptedBuyPlanProof());assertNull(filled.getExitContext());assertEquals(1000,filled.getSpent());
    }
    @Test public void failedForcedWriteRetainsTheExactOriginalPlanAndIdentityOnRetry()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"accepted-buy");
        var fail=new java.util.concurrent.atomic.AtomicBoolean(true);
        var outbox=new OfferEventOutbox(storage,(file,temp)->{if(fail.get())throw new java.io.IOException("fixture write failure");});
        var f=new Fixture(storage,new AtomicLong(AT),outbox);f.advice("original",170);
        f.client.captureObservedOffer(f.offer(0,AT,100,0,"BUYING"),storage.accountId(),AT+1);
        assertNull("Uncommitted sources cannot be used for workflow authority",f.client.continuationSourceEpisodes());
        fail.set(false);outbox.retryUnsaved();var durable=outbox.pending().get(0);
        assertNotNull(durable.getAcceptedBuyPlanProof());assertEquals(170,durable.getExitContext().getTargetSellPrice());
        var originalId=durable.getEventId();f.seconds.set(AT+2);f.advice("later",999);
        var progress=f.publish(f.offer(0,AT,100,2,"BUYING"),AT+2);
        assertEquals(durable.getAcceptedBuyPlanProof(),progress.getAcceptedBuyPlanProof());
        assertEquals(originalId,progress.getExitContext().getSourceEventId());
        assertEquals(2,outbox.pending().size());
    }
    @Test public void upgradedLegacyEpisodeKeepsOnlyItsOriginalContextAfterAckAndRestart()throws Exception {
        var path=folder.newFolder().toPath();var f=new Fixture(path);
        var advice=CaptureOrder.next();var captured=CaptureOrder.next();
        var original=f.outbox.enqueueObservedOffer((id,session,seq)->OfferEvent.builder("legacy",AT,"BUYING")
            .accountId(f.storage.accountId()).authenticatedObservation(true).eventIdentity(id,session,"0@"+AT+":561")
            .sequence(seq).slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,0).firstSeenAt(AT)
            .recommendation("legacy-plan",100,10,0,0,0).predictionEvidence(null,AT,false)
            .acceptedAdviceProof(new AcceptedAdviceProof(advice,captured)).exitContext(new AcquisitionExitContext(140,80,90)).build());
        assertNotNull(original.getExitContext());f.ackAll();
        var file=f.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        var wire=f.storage.gson().fromJson(java.nio.file.Files.readString(file),com.google.gson.JsonObject.class);
        wire.remove("successorEpisodes");java.nio.file.Files.writeString(file,f.storage.gson().toJson(wire));
        var upgraded=new Fixture(path);upgraded.advice("new-display",999);
        var partial=upgraded.publish(upgraded.offer(0,AT,100,3,"BUYING"),AT+1);
        assertNull(partial.getAcceptedBuyPlanEvidenceVersion());assertNull(partial.getAcceptedBuyPlanProof());
        assertEquals(original.getExitContext(),partial.getExitContext());assertEquals(300,partial.getSpent());
    }
    @Test public void accountScopedQueuesNeverBorrowAnotherAccountsAcceptedPlan()throws Exception {
        var path=folder.newFolder().toPath();var a=new Fixture(path);a.advice("account-a",170);
        var original=a.publish(a.offer(0,AT,100,0,"BUYING"),AT);
        var bStorage=TestStorage.rootedAt(path,"other-account");var b=new Fixture(bStorage,new AtomicLong(AT),null);
        var unknown=b.publish(b.offer(0,AT,100,0,"BUYING"),AT);
        assertNotEquals(original.getAccountId(),unknown.getAccountId());assertNull(unknown.getAcceptedBuyPlanProof());
        assertNull(unknown.getExitContext());
        a.client.captureObservedOffer(a.offer(1,AT+1,100,0,"BUYING"),bStorage.accountId(),AT+1);
        assertEquals(1,a.outbox.pending().size());assertEquals(1,b.outbox.pending().size());
        a.ackAll();var restarted=new Fixture(path);
        var filled=restarted.publish(restarted.offer(0,AT,100,10,"BOUGHT"),AT+60);
        assertEquals(original.getAcceptedBuyPlanProof(),filled.getAcceptedBuyPlanProof());
    }
    @Test public void malformedRetainedPlanWrapperRefusesRestartAppendWithoutRewritingEvidence()throws Exception {
        for(boolean badVersion:new boolean[]{true,false}) {
            var path=folder.newFolder().toPath();var before=new Fixture(path);before.advice("accepted",170);
            var original=before.publish(before.offer(0,AT,100,0,"BUYING"),AT);
            assertNotNull(original.getAcceptedBuyPlanProof());before.ackAll();
            var file=before.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
            var wire=before.storage.gson().fromJson(java.nio.file.Files.readString(file),com.google.gson.JsonObject.class);
            var receipts=wire.getAsJsonObject("successorEpisodes");assertEquals(1,receipts.size());
            var wrapper=receipts.entrySet().iterator().next().getValue().getAsJsonObject();
            if(badVersion)wrapper.addProperty("acceptedBuyPlanEvidenceVersion","2");
            else wrapper.getAsJsonObject("exitContext").addProperty("targetSellPrice",999);
            java.nio.file.Files.writeString(file,before.storage.gson().toJson(wire));
            var retained=java.nio.file.Files.readAllBytes(file);var restart=new Fixture(path);
            restart.client.captureObservedOffer(restart.offer(0,AT,100,3,"BUYING"),restart.storage.accountId(),AT+60);
            assertNull("Malformed retained evidence cannot authorize workflow reads",restart.client.continuationSourceEpisodes());
            try {restart.outbox.pending();fail("Malformed retained plan accepted");}
            catch(IllegalStateException expected) { }
            assertArrayEquals("Refused append cannot rewrite preserved source evidence",retained,java.nio.file.Files.readAllBytes(file));
        }
    }
}
