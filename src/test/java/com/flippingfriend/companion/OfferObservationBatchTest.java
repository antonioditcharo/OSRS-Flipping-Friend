package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OfferObservationBatchTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    OfferEventOutbox.EventFactory observation(int slot,long at,int filled,int target) {
        return (id,s,q)->OfferEvent.builder("fixture",at,"BUYING").eventIdentity(id,s,"episode-"+slot).sequence(q)
            .slot(slot).item(561+slot,"Fixture").buying(true).price(100).quantities(10,filled).spent(100L*filled)
            .firstSeenAt(100).authenticatedObservation(true).recommendation("plan",100,10,0,12,.75)
            .predictionEvidence(null,90,false).exitContext(new AcquisitionExitContext(target,90,480)).build();
    }
    List<OfferEventOutbox.EventFactory> frame(long at,int filled,int target) {
        var result=new ArrayList<OfferEventOutbox.EventFactory>();for(int slot=0;slot<8;slot++)result.add(observation(slot,at,filled,target));return result;
    }
    @Test public void eightIdentitiesSequencesAndOriginalBindingsCommitTogetherOnceAndSurviveRestart()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"batch");var writes=new AtomicInteger();
        var outbox=new OfferEventOutbox(storage,(f,t)->writes.incrementAndGet());
        var first=outbox.enqueueObservedOffers(frame(100,0,150));assertEquals(1,writes.get());
        assertEquals(8,new HashSet<>(first.stream().map(OfferEvent::getEventId).collect(java.util.stream.Collectors.toList())).size());
        var restored=new OfferEventOutbox(storage).pending();assertEquals(8,restored.size());
        for(int i=0;i<8;i++) {
            assertEquals(i+1,restored.get(i).getSequence());assertEquals(first.get(i).getEventId(),restored.get(i).getEventId());
            assertTrue(restored.get(i).isPredictionComparable());assertEquals(first.get(i).getEventId(),restored.get(i).getPredictionSourceEventId());
        }
        var later=new OfferEventOutbox(storage).enqueueObservedOffers(frame(120,3,200));
        for(int i=0;i<8;i++) {
            assertEquals(first.get(i).getEventId(),later.get(i).getPredictionSourceEventId());
            assertEquals(first.get(i).getExitContext(),later.get(i).getExitContext());
        }
    }
    @Test public void aLateInvalidFactoryOrDuplicateSlotCannotPersistHalfAFrameOrConsumeSequences()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"invalid");var outbox=new OfferEventOutbox(storage);
        outbox.enqueueObservedOffers(frame(100,0,150));Path file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);byte[] before=Files.readAllBytes(file);
        for(boolean duplicate:new boolean[]{false,true}) {
            var factories=frame(120,3,200);
            factories.set(7,duplicate?observation(0,120,3,200):(id,s,q)->{throw new IllegalArgumentException("fixture");});
            try{outbox.enqueueObservedOffers(factories);fail();}catch(IllegalArgumentException expected){}
            assertArrayEquals(before,Files.readAllBytes(file));
        }
        assertEquals(9,outbox.enqueueObservedOffers(List.of(observation(0,120,3,200))).get(0).getSequence());
    }
    @Test public void replacementFailureRetainsEveryEventBindingAndSequenceForBothEnqueueAndAck()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"failed-write");var fail=new AtomicBoolean();
        var outbox=new OfferEventOutbox(storage,(f,t)->{if(fail.get())throw new java.io.IOException("fixture replacement failure");});
        var first=outbox.enqueueObservedOffers(frame(100,0,150));Path file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);byte[] before=Files.readAllBytes(file);fail.set(true);
        try{outbox.enqueueObservedOffers(frame(120,3,200));fail();}catch(java.io.IOException expected){}
        assertArrayEquals(before,Files.readAllBytes(file));
        try{outbox.acknowledgePrefix(first,storage.accountId());fail();}catch(java.io.IOException expected){}
        assertArrayEquals(before,Files.readAllBytes(file));assertFalse(Files.exists(file.resolveSibling(file.getFileName()+".tmp")));
        fail.set(false);assertEquals(17,outbox.enqueueObservedOffers(List.of(observation(0,120,3,200))).get(0).getSequence());
        assertEquals("The eight unsaved observations must precede the later callback",17,new OfferEventOutbox(storage).pending().size());
    }
    @Test public void prefixAcknowledgementRejectsHolesForeignScopeAndDuplicateKeysWithoutAnyRemoval()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"bad-prefix");var outbox=new OfferEventOutbox(storage);
        var first=outbox.enqueueObservedOffers(frame(100,0,150));Path file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);byte[] before=Files.readAllBytes(file);
        assertFalse(outbox.acknowledgePrefix(List.of(first.get(1)),storage.accountId()));
        assertFalse(outbox.acknowledgePrefix(List.of(first.get(0),first.get(0)),storage.accountId()));
        assertFalse(outbox.acknowledgePrefix(List.of(first.get(0).withAccountId(AccountIdentity.fromProfileKey("other"))),storage.accountId()));
        assertFalse(outbox.acknowledgePrefix(first,AccountIdentity.fromProfileKey("other")));
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test public void oneMatchedPrefixReplacementPreservesConcurrentAppendsAndTheirOriginalBindings()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"prefix");var writes=new AtomicInteger();
        var outbox=new OfferEventOutbox(storage,(f,t)->writes.incrementAndGet());var first=outbox.enqueueObservedOffers(frame(100,0,150));
        var tail=outbox.enqueueObservedOffers(List.of(observation(0,120,3,200)));writes.set(0);
        assertTrue(outbox.acknowledgePrefix(first,storage.accountId()));assertEquals(1,writes.get());
        var restored=new OfferEventOutbox(storage).pending();assertEquals(1,restored.size());assertEquals(tail.get(0).getEventId(),restored.get(0).getEventId());
        assertEquals(first.get(0).getEventId(),restored.get(0).getPredictionSourceEventId());assertEquals(9,restored.get(0).getSequence());
    }
    @Test public void switchingProfilesInTheLastFactoryCannotWriteEitherAccountsState()throws Exception {
        var root=folder.newFolder().toPath();var one=TestStorage.rootedAt(root,"one");var two=TestStorage.rootedAt(root,"two");
        new OfferEventOutbox(one).enqueueObservedOffers(frame(100,0,150));new OfferEventOutbox(two).enqueueObservedOffers(frame(100,0,150));
        Path file=one.accountDir().resolve(OfferEventOutbox.FILE_NAME),other=two.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        byte[] before=Files.readAllBytes(file),otherBefore=Files.readAllBytes(other);var current=new AtomicReference<>(one.accountId());
        String firstAccount=one.accountId();Path firstDirectory=one.accountDir();var firstGson=one.gson();
        var storage=org.mockito.Mockito.mock(PluginStorage.class);
        org.mockito.Mockito.when(storage.accountId()).thenAnswer(i->current.get());org.mockito.Mockito.when(storage.hasAccount()).thenReturn(true);
        org.mockito.Mockito.when(storage.gson()).thenReturn(firstGson);org.mockito.Mockito.when(storage.accountDir(firstAccount)).thenReturn(firstDirectory);
        var factories=frame(120,3,200);var last=factories.get(7);factories.set(7,(id,s,q)->{current.set(two.accountId());return last.build(id,s,q);});
        try{new OfferEventOutbox(storage).enqueueObservedOffers(factories);fail();}catch(IllegalStateException expected){}
        assertArrayEquals(before,Files.readAllBytes(file));assertArrayEquals(otherBefore,Files.readAllBytes(other));
    }
    @Test public void theSameEightObservationsAndAcknowledgementsNeedTwoReplacementsInsteadOfSixteen()throws Exception {
        for(boolean batch:new boolean[]{false,true}) {
            var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"work-budget");var writes=new AtomicInteger();
            var outbox=new OfferEventOutbox(storage,(f,t)->writes.incrementAndGet());
            var client=new CompanionClient(storage,new com.google.gson.Gson(),new SuggestionLedger(),outbox);client.resumeOfferReplay();
            var factories=frame(100,0,150);List<OfferEvent> events;
            if(batch)events=outbox.enqueueObservedOffers(factories);
            else {events=new ArrayList<>();for(var factory:factories)events.add(outbox.enqueueObservedOffer(factory));}
            if(batch)assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));
            else for(var event:events)assertTrue(client.consumeAcknowledgement(event,OfferReplayTailTest.ack(event)));
            assertEquals(batch?2:16,writes.get());assertTrue(new OfferEventOutbox(storage).pending().isEmpty());
            assertEquals(8,events.size());for(int i=0;i<8;i++)assertEquals(i+1,events.get(i).getSequence());
        }
    }
}
