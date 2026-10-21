package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ClientCapturePersistenceTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    TrackedOffer offer(int filled) {
        var o=new TrackedOffer(0,561,true,100,3,100);o.setItemName("Nature rune");
        o.setState(filled==3?"BOUGHT":"BUYING");o.setQuantityFilled(filled);o.setSpent(100L*filled);return o;
    }
    @Test public void captureFreezesOriginalPlanBeforeLogoutWithoutAnyNetworkRequest()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"original");
        Files.createDirectories(storage.root().resolve("companion"));Files.writeString(storage.root().resolve("companion/companion.properties"),"token=fixture-only\n");
        var network=new AtomicInteger();var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{network.incrementAndGet();e.sendResponseHeaders(500,-1);e.close();});server.start();
        try {
            var ledger=new SuggestionLedger(Clock.fixed(Instant.ofEpochSecond(90),ZoneOffset.UTC));
            var context=new AcquisitionExitContext(150,80,480);
            ledger.recorded("original",561,true,100,3,0,12,.75,null,context);
            var outbox=new OfferEventOutbox(storage);
            var client=new CompanionClient(storage,new Gson(),ledger,outbox,()->100L,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1/");
            client.resumeOfferReplay();var copied=offer(0);client.captureObservedOffer(copied,storage.accountId(),100);
            copied.setQuantityFilled(3);copied.setSpent(999);ledger.clear();client.pauseOfferReplay();
            client.captureObservedOffer(offer(3),storage.accountId(),110);client.captureObservedOfferCleared(0,storage.accountId(),111);
            var pending=new OfferEventOutbox(storage).pending();assertEquals(3,pending.size());assertEquals(0,network.get());
            assertEquals(0,pending.get(0).getFilledQuantity());assertEquals(300,pending.get(1).getSpent());
            assertEquals("original",pending.get(1).getRecommendationId());assertEquals(context.bind(pending.get(0).getEventId()),pending.get(1).getExitContext());
            assertEquals(pending.get(0).getEventId(),pending.get(1).getPredictionSourceEventId());
        }finally{server.stop(0);}
    }
    @Test public void newCaptureWakesADrainedQueueButCannotBypassFailedTransportBackoff()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"wake");var outbox=new OfferEventOutbox(storage);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,()->100L);client.resumeOfferReplay();
        assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));assertEquals(Long.MAX_VALUE,client.nextRetryAtMillis());
        client.captureObservedOffer(offer(0),storage.accountId(),100);assertEquals(100,client.nextRetryAtMillis());
        assertFalse(client.replayPendingOffers(e->{throw new java.io.IOException("fixture transport");}));assertEquals(2100,client.nextRetryAtMillis());
        client.captureObservedOffer(offer(3),storage.accountId(),110);assertEquals(2100,client.nextRetryAtMillis());
        assertFalse(client.requestPendingOfferReplay(r->{throw new AssertionError("Backoff was bypassed");}));
    }
    @Test public void failedCaptureRetainsOriginalIdsAndOrderUntilStorageRecovers()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"storage");var blocked=new AtomicBoolean(true);
        var outbox=new OfferEventOutbox(storage,(f,t)->{if(blocked.get())throw new java.io.IOException("fixture storage");});
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox);
        client.captureObservedOffer(offer(0),storage.accountId(),100);client.captureObservedOffer(offer(3),storage.accountId(),110);
        client.captureObservedOfferCleared(0,storage.accountId(),111);
        try{outbox.pending();fail("Uncommitted evidence cannot authorize delivery");}catch(java.io.IOException expected){}
        blocked.set(false);outbox.retryUnsaved();var saved=outbox.pending();assertEquals(3,saved.size());
        var restored=new OfferEventOutbox(storage).pending();for(int i=0;i<3;i++){assertEquals(saved.get(i).getEventId(),restored.get(i).getEventId());assertEquals(i+1,restored.get(i).getSequence());}
        assertEquals("BOUGHT",restored.get(1).getEventType());assertEquals("EMPTY",restored.get(2).getEventType());
    }
    @Test public void capturedSourcesStayInTheirOwnAccountAcrossProfileSwitches()throws Exception {
        var one=TestStorage.rootedAt(folder.newFolder().toPath(),"one");var two=TestStorage.rootedAt(folder.newFolder().toPath(),"two");
        String first=one.accountId(),second=two.accountId();Path firstDir=one.accountDir(),secondDir=two.accountDir();
        var active=new AtomicReference<>(first);var storage=mock(PluginStorage.class);when(storage.hasAccount()).thenReturn(true);
        when(storage.gson()).thenReturn(new Gson());when(storage.accountId()).thenAnswer(i->active.get());
        when(storage.accountDir(first)).thenReturn(firstDir);when(storage.accountDir(second)).thenReturn(secondDir);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),new OfferEventOutbox(storage));
        client.captureObservedOffer(offer(3),first,110);active.set(second);
        client.captureObservedOfferCleared(0,first,111);assertTrue(new OfferEventOutbox(two).pending().isEmpty());
        var saved=new OfferEventOutbox(one).pending();assertEquals(1,saved.size());assertEquals(first,saved.get(0).getAccountId());
    }
    @Test public void aCaptureDuringReplayCannotBeAcknowledgedOrForgottenWithTheOldPrefix()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"during-replay");var outbox=new OfferEventOutbox(storage);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox,()->100L);client.resumeOfferReplay();
        client.captureObservedOffer(offer(0),storage.accountId(),100);
        assertFalse(client.replayPendingOffers(e->{client.captureObservedOffer(offer(3),storage.accountId(),110);return OfferReplayTailTest.ack(e);}));
        var pending=new OfferEventOutbox(storage).pending();assertEquals(1,pending.size());assertEquals(3,pending.get(0).getFilledQuantity());
        assertEquals(2,pending.get(0).getSequence());assertTrue(client.nextRetryAtMillis()<=100);
        assertTrue(client.replayPendingOffers(OfferReplayTailTest::ack));assertTrue(outbox.pending().isEmpty());
    }
}
