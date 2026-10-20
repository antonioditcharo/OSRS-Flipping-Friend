package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Storage-failure boundaries use disposable files and intercepted HTTP; no account/game writes. */
public class OfferPersistenceRecoveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final AtomicBoolean blocked=new AtomicBoolean();
    OfferEventOutbox.EventFactory observed(String state,long at,int filled,int target,AtomicReference<OfferEvent> captured) {
        return (id,session,sequence)->{
            var event=OfferEvent.builder("fixture",at,state).eventIdentity(id,session,"episode").sequence(sequence)
                .slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,filled).spent(100L*filled)
                .firstSeenAt(100).authenticatedObservation(true).recommendation("plan",100,10,0,12,.75)
                .predictionEvidence(null,90,false).exitContext(new AcquisitionExitContext(target,90,480)).build();
            if(captured!=null)captured.set(event);return event;
        };
    }
    OfferEventOutbox queue(PluginStorage storage) {
        return new OfferEventOutbox(storage,(file,temp)->{if(blocked.get())throw new IOException("fixture unavailable storage");});
    }
    @Test public void aFailedFillIsRetriedUnderItsOriginalIdentityBeforeALaterClear()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"failed-fill");var outbox=queue(storage);
        var start=outbox.enqueueObservedOffer(observed("BUYING",100,0,150,null));
        var failed=new AtomicReference<OfferEvent>();blocked.set(true);
        try{outbox.enqueueObservedOffer(observed("BOUGHT",120,10,200,failed));fail();}catch(IOException expected){}
        blocked.set(false);
        var clear=outbox.enqueueObservedOffer((id,s,q)->OfferReplayTailTest.event(id,s,q,0));
        var restored=new OfferEventOutbox(storage).pending();assertEquals(3,restored.size());
        assertEquals(start.getEventId(),restored.get(0).getEventId());assertEquals(failed.get().getEventId(),restored.get(1).getEventId());
        assertEquals(start.getSessionId(),restored.get(1).getSessionId());assertEquals(2,restored.get(1).getSequence());
        assertEquals(10,restored.get(1).getFilledQuantity());assertEquals(1000,restored.get(1).getSpent());
        assertEquals(start.getEventId(),restored.get(1).getPredictionSourceEventId());assertEquals(start.getExitContext(),restored.get(1).getExitContext());
        assertEquals(3,clear.getSequence());assertEquals("EMPTY",restored.get(2).getEventType());
    }
    @Test public void theFirstUnsavedObservationKeepsItsSessionAndOriginalPlanDuringLaterProgress()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"first-save");var outbox=queue(storage);
        var failed=new AtomicReference<OfferEvent>();blocked.set(true);
        try{outbox.enqueueObservedOffer(observed("BUYING",100,0,150,failed));fail();}catch(IOException expected){}
        assertFalse(Files.exists(storage.accountDir().resolve(OfferEventOutbox.FILE_NAME)));blocked.set(false);
        outbox.enqueueObservedOffer(observed("BOUGHT",120,10,200,null));
        var restored=new OfferEventOutbox(storage).pending();assertEquals(2,restored.size());
        assertEquals(failed.get().getEventId(),restored.get(0).getEventId());assertEquals(failed.get().getSessionId(),restored.get(1).getSessionId());
        assertEquals(failed.get().getEventId(),restored.get(1).getPredictionSourceEventId());
        assertEquals(150,restored.get(1).getExitContext().getTargetSellPrice());
    }
    @Test public void aDirtyObservationCannotExposeOnlyTheOldDurablePrefixForDelivery()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"pending-fence");var outbox=queue(storage);
        outbox.enqueueObservedOffer(observed("BUYING",100,0,150,null));blocked.set(true);
        try{outbox.enqueueObservedOffer(observed("BOUGHT",120,10,200,null));fail();}catch(IOException expected){}
        try{outbox.pending();fail("No delivery read may hide the unsaved tail");}catch(IOException expected){}
        assertEquals(1,new OfferEventOutbox(storage).pending().size());blocked.set(false);outbox.retryUnsaved();assertEquals(2,outbox.pending().size());
    }
    @Test public void aChangedDestinationIsNotOverwrittenAtTheReplacementBoundary()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"changed-destination");
        new OfferEventOutbox(storage).enqueueObservedOffer(observed("BUYING",100,0,150,null));
        Path file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);byte[] before=Files.readAllBytes(file);
        byte[] changed=(new String(before,java.nio.charset.StandardCharsets.UTF_8)+" ").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var mutate=new AtomicBoolean(true);var outbox=new OfferEventOutbox(storage,(f,t)->{if(mutate.getAndSet(false))Files.write(f,changed);});
        try{outbox.enqueueObservedOffer(observed("BOUGHT",120,10,200,null));fail("An external replacement must not be overwritten");}catch(IOException expected){}
        assertArrayEquals(changed,Files.readAllBytes(file));
        try{outbox.pending();fail();}catch(IOException expected){}
        assertArrayEquals(changed,Files.readAllBytes(file));Files.write(file,before);outbox.retryUnsaved();assertEquals(2,outbox.pending().size());
    }
    @Test public void unsavedEvidenceStaysWithItsAccountAcrossProfileSwitches()throws Exception {
        var root=folder.newFolder().toPath();var one=TestStorage.rootedAt(root,"one");var two=TestStorage.rootedAt(root,"two");
        String oneId=one.accountId(),twoId=two.accountId();Path oneDir=one.accountDir(),twoDir=two.accountDir();
        var current=new AtomicReference<>(oneId);var storage=mock(PluginStorage.class);
        when(storage.hasAccount()).thenReturn(true);when(storage.gson()).thenReturn(new Gson());when(storage.accountId()).thenAnswer(i->current.get());
        when(storage.accountDir(oneId)).thenReturn(oneDir);when(storage.accountDir(twoId)).thenReturn(twoDir);
        var outbox=queue(storage);var failed=new AtomicReference<OfferEvent>();blocked.set(true);
        try{outbox.enqueueObservedOffer(observed("BUYING",100,0,150,failed));fail();}catch(IOException expected){}
        current.set(two.accountId());blocked.set(false);assertTrue(outbox.pending().isEmpty());
        outbox.enqueueObservedOffer(observed("BUYING",100,0,180,null));var other=Files.readAllBytes(two.accountDir().resolve(OfferEventOutbox.FILE_NAME));
        current.set(one.accountId());outbox.retryUnsaved();var pending=outbox.pending();assertEquals(1,pending.size());assertEquals(failed.get().getEventId(),pending.get(0).getEventId());
        assertEquals(one.accountId(),pending.get(0).getAccountId());assertArrayEquals(other,Files.readAllBytes(two.accountDir().resolve(OfferEventOutbox.FILE_NAME)));
    }
    @Test public void anInvalidLaterFrameDoesNotDiscardTheRetainedValidObservation()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"invalid-tail");var outbox=queue(storage);blocked.set(true);
        var failed=new AtomicReference<OfferEvent>();try{outbox.enqueueObservedOffer(observed("BUYING",100,0,150,failed));fail();}catch(IOException expected){}
        try{outbox.enqueueObservedOffers(List.of(observed("BOUGHT",120,10,200,null),(id,s,q)->{throw new IllegalArgumentException("fixture malformed frame");}));fail();}
        catch(IllegalArgumentException expected){}
        blocked.set(false);outbox.retryUnsaved();var restored=outbox.pending();assertEquals(1,restored.size());assertEquals(failed.get().getEventId(),restored.get(0).getEventId());
        assertEquals(2,outbox.enqueueObservedOffer(observed("BOUGHT",120,10,200,null)).getSequence());
    }
    @Test public void aLocalPersistenceFailureRearmsAnOtherwiseDrainedLifecycleWithoutAnotherOffer()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"local-retry");var outbox=queue(storage);
        var clock=new AtomicLong(100);var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,clock::get);
        client.resumeOfferReplay();assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));assertEquals(Long.MAX_VALUE,client.nextRetryAtMillis());
        blocked.set(true);client.publishObservedOfferCleared(0,storage.accountId(),100);
        assertEquals("A save failure must not wait indefinitely for a new game event",2100,client.nextRetryAtMillis());
        assertFalse(client.requestPendingOfferReplay(r->{throw new AssertionError("Respect retry spacing");}));
        assertFalse(client.replayPendingOffers(e->{throw new AssertionError("No source may be sent before its local commit");}));
        assertTrue(client.lastError().contains("storage"));
        blocked.set(false);var sent=new ArrayList<OfferEvent>();assertTrue(client.replayPendingOffers(e->{sent.add(e);return OfferReplayTailTest.ack(e);}));
        assertEquals(1,sent.size());assertEquals(1,sent.get(0).getSequence());assertTrue(new OfferEventOutbox(storage).pending().isEmpty());
    }
    @Test public void readersWithholdAuthorityWithoutRetryingForcedStorageWrites()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"read-only-fence");var writes=new AtomicInteger();
        var outbox=new OfferEventOutbox(storage,(f,t)->{writes.incrementAndGet();if(blocked.get())throw new IOException("fixture storage");});
        blocked.set(true);try{outbox.enqueueObservedOffer(observed("BUYING",100,0,150,null));fail();}catch(IOException expected){}
        blocked.set(false);
        for(int i=0;i<3;i++)try{outbox.pending();fail();}catch(IOException expected){}
        assertEquals("Rendering/eligibility reads cannot retry fsync on their calling threads",1,writes.get());
        outbox.retryUnsaved();assertEquals(2,writes.get());assertEquals(1,outbox.pending().size());
    }
    @Test public void aLegacyScopeUpgradeCannotDiscardTheCallbackWhenItsCombinedCommitFails()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"legacy-retry");
        var original=new OfferEventOutbox(storage).enqueue((id,s,q)->OfferReplayTailTest.event(id,s,q,1));
        var file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        var legacy=storage.gson().fromJson(Files.readString(file),com.google.gson.JsonObject.class);legacy.remove("accountId");
        legacy.getAsJsonArray("pending").get(0).getAsJsonObject().remove("accountId");Files.writeString(file,storage.gson().toJson(legacy));
        byte[] before=Files.readAllBytes(file);var failed=new AtomicReference<OfferEvent>();var outbox=queue(storage);blocked.set(true);
        try{outbox.enqueueObservedOffer(observed("BUYING",100,0,150,failed));fail();}catch(IOException expected){}
        assertNotNull("The new callback must be retained with the normalized scoped prefix",failed.get());assertArrayEquals(before,Files.readAllBytes(file));
        blocked.set(false);outbox.retryUnsaved();var restored=new OfferEventOutbox(storage).pending();assertEquals(2,restored.size());
        assertEquals(original.getEventId(),restored.get(0).getEventId());assertEquals(failed.get().getEventId(),restored.get(1).getEventId());
        assertEquals(original.getSessionId(),restored.get(1).getSessionId());assertEquals(storage.accountId(),restored.get(0).getAccountId());
    }
    @Test public void pendingOrNewlyAppendedObservationsWithdrawCachedCompanionCollection()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"collection-fence");
        Files.createDirectories(storage.root().resolve("companion"));Files.writeString(storage.root().resolve("companion/companion.properties"),"token=fixture-only\n");
        var outbox=queue(storage);var during=new AtomicReference<Runnable>(()->{});var reads=new AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/accounts/",exchange->{try {
            reads.incrementAndGet();during.get().run();
            var body=new Gson().toJson(CompanionAction.collect(561,"Fixture",0,10,"Collect","Fixture")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(AccountIdentity.HEADER,storage.accountId());exchange.getResponseHeaders().set(AccountIdentity.PROTOCOL_HEADER,AccountIdentity.PROTOCOL);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
        }finally{exchange.close();}});server.start();
        try {
            var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,System::currentTimeMillis,
                "http://127.0.0.1:"+server.getAddress().getPort()+"/v1/");client.resumeOfferReplay();
            assertNotNull(client.nextAction());
            outbox.enqueueObservedOffer(observed("BUYING",100,0,150,null));
            assertNull("An old collection must not outrank fresh local offer evidence",client.nextAction());assertEquals(1,reads.get());
            assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));
            during.set(()->{try{outbox.enqueueObservedOffer(observed("BOUGHT",120,10,200,null));}catch(Exception e){throw new AssertionError(e);}});
            assertNull("Recheck pending delivery after receiving the action",client.nextAction());assertEquals(2,reads.get());
        }finally{server.stop(0);}
    }
}
