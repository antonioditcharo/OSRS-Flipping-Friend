package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.TestStorage;
import com.google.gson.Gson;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OfferReplayTailTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    static OfferEvent event(String id,String session,long sequence,int slot) {
        return OfferEvent.builder("fixture",100,"EMPTY").eventIdentity(id,session,null)
            .sequence(sequence).slot(slot).authenticatedObservation(true).build();
    }
    static String ack(OfferEvent event){return new Gson().toJson(Map.of("accepted",true,"eventId",event.getEventId(),"sessionId",event.getSessionId(),"sequence",event.getSequence()));}
    @Test public void negativeMonotonicOriginResumesImmediatelyAndRetainsBoundedFailureBackoff()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"negative-clock");var outbox=new OfferEventOutbox(storage);
        var clock=new java.util.concurrent.atomic.AtomicLong(-10_000);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,clock::get);
        var original=outbox.enqueue((id,s,q)->event(id,s,q,0));client.resumeOfferReplay();
        var queue=new ArrayList<Runnable>();assertTrue(client.requestPendingOfferReplay(queue::add));
        client.pauseOfferReplay();queue.remove(0).run();assertEquals(original.getEventId(),outbox.pending().get(0).getEventId());
        client.resumeOfferReplay();assertFalse(client.replayPendingOffers(e->{throw new java.io.IOException("fixture offline");}));
        assertEquals(-8_000,client.nextRetryAtMillis());clock.set(-8_001);assertFalse(client.requestPendingOfferReplay(queue::add));
        clock.set(-8_000);assertTrue(client.requestPendingOfferReplay(queue::add));client.pauseOfferReplay();queue.remove(0).run();
        assertEquals(original.getObservedAt(),outbox.pending().get(0).getObservedAt());
        client.resumeOfferReplay();assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));assertTrue(outbox.pending().isEmpty());
    }
    @Test public void negativeOriginResumeDoesNotLetAnOldQueuedLifecycleAcknowledgeTheNewOne()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"negative-lifecycle");var outbox=new OfferEventOutbox(storage);
        var clock=new java.util.concurrent.atomic.AtomicLong(-100_000);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,clock::get);
        var original=outbox.enqueue((id,s,q)->event(id,s,q,0));client.resumeOfferReplay();var queue=new ArrayList<Runnable>();
        assertTrue(client.requestPendingOfferReplay(queue::add));client.pauseOfferReplay();clock.addAndGet(1);client.resumeOfferReplay();
        assertTrue(client.requestPendingOfferReplay(queue::add));queue.remove(0).run();
        assertEquals(original.getEventId(),outbox.pending().get(0).getEventId());
        assertFalse("The stale task cannot release the current lifecycle's queued request",client.requestPendingOfferReplay(queue::add));
        client.pauseOfferReplay();queue.remove(0).run();assertEquals(original.getSequence(),outbox.pending().get(0).getSequence());
    }
    @Test public void appendedEventsRemainScheduledAfterTheOriginalReplaySnapshotIsAcknowledged()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"tail");var outbox=new OfferEventOutbox(storage);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,()->100L);
        outbox.enqueue((id,s,q)->event(id,s,q,0));client.resumeOfferReplay();
        var appended=new ArrayList<OfferEvent>();
        assertFalse("A newly appended tail is not a fully drained queue",client.replayPendingOffers(e->{
            appended.add(outbox.enqueue((id,s,q)->event(id,s,q,1)));return ack(e);
        }));
        assertEquals(1,outbox.pending().size());assertEquals(appended.get(0).getEventId(),outbox.pending().get(0).getEventId());
        var queued=new ArrayList<Runnable>();assertTrue("The tail must remain eligible for the lifecycle retry pump",client.requestPendingOfferReplay(queued::add));
        assertEquals(1,queued.size());client.pauseOfferReplay();queued.get(0).run();
        assertEquals(1,outbox.pending().size());client.resumeOfferReplay();
        assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));assertTrue(outbox.pending().isEmpty());
    }
    @Test public void aReplayRequestedAtCompletionCannotLoseItsRetryWakeup()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"completion");var outbox=new OfferEventOutbox(storage);
        var reference=new java.util.concurrent.atomic.AtomicReference<CompanionClient>();
        var append=new java.util.concurrent.atomic.AtomicBoolean(false);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,()->{
            if(append.compareAndSet(true,false))try {
                outbox.enqueue((id,s,q)->event(id,s,q,1));
                assertFalse(reference.get().replayPendingOffers(e->{throw new AssertionError("Replay must remain single flight");}));
            }catch(Exception failure){throw new IllegalStateException(failure);}
            return 100L;
        });reference.set(client);
        outbox.enqueue((id,s,q)->event(id,s,q,0));client.resumeOfferReplay();append.set(true);
        client.replayPendingOffers(OfferReplayTailTest::ack);
        assertEquals(1,outbox.pending().size());
        assertTrue("A request arriving after the empty check must retain its wakeup",client.nextRetryAtMillis()<=100);
        assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));assertTrue(outbox.pending().isEmpty());
    }
    @Test public void invalidOrLostThirdAcknowledgementCommitsOnlyTheFirstTwoAndRetriesTheSameRemainder()throws Exception {
        for(boolean disconnected:new boolean[]{false,true}) {
            var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"partial");var writes=new java.util.concurrent.atomic.AtomicInteger();
            var outbox=new OfferEventOutbox(storage,(f,t)->writes.incrementAndGet());
            var factories=new ArrayList<OfferEventOutbox.EventFactory>();for(int i=0;i<8;i++){final int slot=i;factories.add((id,s,q)->event(id,s,q,slot));}
            var original=outbox.enqueueObservedOffers(factories);writes.set(0);
            var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,()->100L);client.resumeOfferReplay();
            assertFalse(client.replayPendingOffers(e->{if(e.getSequence()==3){if(disconnected)throw new java.io.IOException("fixture");return "{}";}return ack(e);}));
            assertEquals(1,writes.get());var restored=new OfferEventOutbox(storage).pending();assertEquals(6,restored.size());
            assertEquals(original.get(2).getEventId(),restored.get(0).getEventId());assertEquals(2100,client.nextRetryAtMillis());
            assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));assertTrue(outbox.pending().isEmpty());
        }
    }
    @Test public void failedAcknowledgementReplacementRetriesIdenticalSourcesAfterRestart()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"ack-failure");var fail=new java.util.concurrent.atomic.AtomicBoolean();
        var outbox=new OfferEventOutbox(storage,(f,t)->{if(fail.get())throw new java.io.IOException("fixture");});
        var original=outbox.enqueueObservedOffers(List.of((id,s,q)->event(id,s,q,0),(id,s,q)->event(id,s,q,1)));
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox);client.resumeOfferReplay();fail.set(true);
        var received=new ArrayList<String>();assertFalse(client.replayPendingOffers(e->{received.add(e.getEventId());return ack(e);}));
        assertEquals(2,outbox.pending().size());fail.set(false);
        var restarted=new CompanionClient(storage,new Gson(),new SuggestionLedger(),new OfferEventOutbox(storage));restarted.resumeOfferReplay();
        assertTrue(restarted.replayPendingOffers(e->{received.add(e.getEventId());return ack(e);}));
        assertEquals(List.of(original.get(0).getEventId(),original.get(1).getEventId(),original.get(0).getEventId(),original.get(1).getEventId()),received);
        assertTrue(new OfferEventOutbox(storage).pending().isEmpty());
    }
    @Test public void aBusyReplayRequestCannotBypassBackoffAfterATransportFailure()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"backoff");var outbox=new OfferEventOutbox(storage);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,()->100L);client.resumeOfferReplay();
        outbox.enqueue((id,s,q)->event(id,s,q,0));
        assertFalse(client.replayPendingOffers(e->{
            assertFalse(client.replayPendingOffers(ignored->{throw new AssertionError("Single flight");}));
            throw new java.io.IOException("fixture offline");
        }));
        assertEquals(2100,client.nextRetryAtMillis());assertEquals(1,outbox.pending().size());
        assertFalse(client.requestPendingOfferReplay(r->{throw new AssertionError("Backoff must hold");}));
    }
}
