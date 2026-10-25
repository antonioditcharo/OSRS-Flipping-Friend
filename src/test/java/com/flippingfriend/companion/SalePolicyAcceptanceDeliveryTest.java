package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Prospective maintenance acceptance through the actual client/outbox; disposable sources only. */
public class SalePolicyAcceptanceDeliveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long at=java.time.Instant.now().getEpochSecond();
    public static SalePolicyReceipt receipt(String account,String id,OfferEvent source,int price,long now) {
        var json=new com.google.gson.Gson();var settings=new com.google.gson.JsonObject();
        settings.addProperty("accountId",account);settings.addProperty("correlationId","account-for-"+id);
        settings.addProperty("observedAt",now);settings.addProperty("riskAppetite","MODERATE");
        settings.addProperty("checkIntervalMinutes",5);settings.addProperty("targetHoldMinutes",60);settings.addProperty("sellOnly",false);
        var read=new com.google.gson.JsonObject();for(int slot=0;slot<8;slot++)read.addProperty(slot+"",slot==source.getSlot()?source.getEventId():"empty-"+slot);
        settings.add("offerReadSet",read);
        return SalePolicyReceipt.issue(id,1,"a".repeat(64),json.fromJson(settings,AccountSnapshot.class),source,
            source.getFirstSeenAt(),price,source.getTotalQuantity()-source.getFilledQuantity(),false,now,480);
    }
    class Fixture {
        final PluginStorage storage;final SellRelistIntent intents;final OfferTracker tracker=mock(OfferTracker.class);
        final OfferEventOutbox outbox;final CompanionClient client;final TrackedOffer working;
        Fixture(Path root)throws Exception {
            storage=TestStorage.rootedAt(root,"sale-policy");intents=new SellRelistIntent(storage);outbox=new OfferEventOutbox(storage);
            client=new CompanionClient(storage,storage.gson(),new SuggestionLedger(),outbox);
            client.setSalePolicyAcceptanceReader(intents::acceptedPolicies);
            working=new TrackedOffer(0,561,false,200,10,at-3600);working.setState("SELLING");
            when(tracker.getOffer(0)).thenReturn(working);when(tracker.getOffers()).thenReturn(List.of(working));
        }
        OfferEvent publish(TrackedOffer frame,long now)throws Exception {
            client.captureObservedOffer(frame,storage.accountId(),now);
            var events=outbox.pending();assertFalse(events.isEmpty());return events.get(events.size()-1);
        }
        void ack()throws Exception{for(var e:outbox.pending())assertTrue(outbox.acknowledge(e.getEventId(),e.getSessionId(),e.getSequence()));}
        boolean accept(OfferEvent source,String id)throws Exception {return accept(source,id,true);}
        boolean accept(OfferEvent source,String id,boolean stage)throws Exception {
            boolean saved=intents.advise(working,storage.accountId(),true,tracker,source.getEventId(),receipt(storage.accountId(),id,source,150,at),at);
            if(saved && stage)for(var proof:intents.acceptedPolicies(storage.accountId()))outbox.retainSalePolicy(proof);
            return saved;
        }
    }
    @Test public void acceptedReceiptFollowsLaterCallbackLateFillsAckRestartAndExactCollection()throws Exception {
        var path=folder.newFolder().toPath();var f=new Fixture(path);var source=f.publish(f.working,at);f.ack();
        assertTrue(f.accept(source,"receipt-one"));var accepted=f.intents.acceptedPolicies(f.storage.accountId()).get(0);
        assertFalse(accepted.isBound());
        var fresh=f.publish(f.working.copyCurrentObservation(),at+1);
        assertNotNull(fresh.getAcceptedSalePolicyProof());assertTrue(fresh.getAcceptedSalePolicyProof().qualifiesFirst(fresh));f.ack();
        var restart=new Fixture(path);assertEquals(accepted,restart.intents.acceptedPolicies(restart.storage.accountId()).get(0));
        restart.working.setQuantityFilled(2);restart.working.setSpent(400);restart.working.setState("CANCELLED_SELL");
        var cancelled=restart.publish(restart.working.copyCurrentObservation(),at+2);
        assertEquals(fresh.getAcceptedSalePolicyProof(),cancelled.getAcceptedSalePolicyProof());
        restart.working.setCollected(true);when(restart.tracker.getOffer(0)).thenReturn(null);
        when(restart.tracker.getOffers()).thenReturn(List.of());when(restart.tracker.getSettledOffer(0)).thenReturn(restart.working);
        var remaining=restart.intents.collectedRequest(restart.tracker,restart.storage.accountId());
        assertNotNull(remaining);assertEquals(8,remaining.getQuantity());assertEquals(480,remaining.getAcceptedPatienceSeconds());
        assertEquals(accepted,remaining.getAcceptedSalePolicyProof());
        restart.client.captureObservedOfferCleared(0,restart.storage.accountId(),at+3);restart.ack();
        assertNotNull(new OfferEventOutbox(restart.storage).observedSuccessorEpisodes());
    }
    @Test public void alreadyCopiedCallbackCannotClaimLaterAcceptanceButNextCurrentCaptureCan()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());var source=f.publish(f.working,at);var copiedBefore=f.working.copyCurrentObservation();
        assertTrue(f.accept(source,"ordered-receipt"));
        var stale=f.publish(copiedBefore,at);assertNull(stale.getAcceptedSalePolicyProof());
        var fresh=f.publish(f.working.copyCurrentObservation(),at);assertNotNull(fresh.getAcceptedSalePolicyProof());
    }
    @Test public void failedProspectiveReceiptWriteCannotSelfCommitOnLaterCapture()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());var source=f.publish(f.working,at);
        var target=f.storage.accountDir().resolve("sell-relist-intent.json");var blocked=target.resolveSibling(target.getFileName()+".tmp");
        Files.createDirectory(blocked);assertFalse(f.accept(source,"never-accepted"));Files.delete(blocked);
        assertTrue(f.intents.acceptedPolicies(f.storage.accountId()).isEmpty());
        var later=f.publish(f.working.copyCurrentObservation(),at+1);assertNull(later.getAcceptedSalePolicyProof());
        assertFalse(Files.exists(target));
    }
    @Test public void changedOrderAndChangedBackCannotResurrectBoundReceipt()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());var source=f.publish(f.working,at);assertTrue(f.accept(source,"fixed-origin"));
        assertNotNull(f.publish(f.working.copyCurrentObservation(),at+1).getAcceptedSalePolicyProof());
        var changed=new TrackedOffer(0,561,false,201,10,f.working.getFirstSeen());changed.setState("SELLING");
        assertNull(f.publish(changed,at+2).getAcceptedSalePolicyProof());f.ack();
        var restored=new Fixture(f.storage.root());
        assertNull(restored.publish(restored.working.copyCurrentObservation(),at+3).getAcceptedSalePolicyProof());
    }
    @Test public void boundLegacyHintRemainsLegacyWhenLaterPolicyReceiptIsAccepted()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());var source=f.publish(f.working,at);
        // Keep the legacy binding and prospective acceptance on the fixture's frozen clock.
        assertTrue(f.intents.advise(f.working,f.storage.accountId(),true,f.tracker,source.getEventId(),null,at));
        assertTrue(f.accept(source,"prospective-receipt"));
        assertTrue("An earlier bound legacy hint cannot gain retrospective policy authority",f.intents.acceptedPolicies(f.storage.accountId()).isEmpty());
    }
    @Test public void restoredObservationCannotAcquireAFreshOrderingStampThroughAnOrdinaryCopy() {
        var offer=new TrackedOffer(0,561,false,200,10,at-3600);offer.setState("SELLING");
        var original=offer.getFirstCaptureOrder();var copied=offer.copyObservation();
        assertSame(original,copied.getFirstCaptureOrder());assertSame(offer.getObservationCaptureOrder(),copied.getObservationCaptureOrder());
        var json=new com.google.gson.Gson();var restored=json.fromJson(json.toJson(offer),TrackedOffer.class);
        assertNull(restored.getFirstCaptureOrder());assertNull(restored.getObservationCaptureOrder());
        assertNull(restored.copyObservation().getObservationCaptureOrder());
        var current=restored.copyCurrentObservation();assertNotNull(current.getObservationCaptureOrder());
        assertNull("A current read cannot manufacture original placement evidence",current.getFirstCaptureOrder());
        assertNull("The cached object remains unstamped",restored.getObservationCaptureOrder());
    }
    @Test public void foreignReceiptInCopiedContinuationFileRefusesWithoutRewriting()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());var source=f.publish(f.working,at);assertTrue(f.accept(source,"receipt-account"));
        var file=f.storage.accountDir().resolve("sell-relist-intent.json");
        var wire=f.storage.gson().fromJson(Files.readString(file),com.google.gson.JsonObject.class);
        wire.getAsJsonArray("continuations").get(0).getAsJsonObject().getAsJsonObject("acceptedSalePolicyProof")
            .getAsJsonObject("receipt").addProperty("accountId",AccountIdentity.fromProfileKey("another-account"));
        Files.writeString(file,f.storage.gson().toJson(wire));var retained=Files.readAllBytes(file);
        assertNull(new SellRelistIntent(f.storage).acceptedPolicies(f.storage.accountId()));
        assertArrayEquals(retained,Files.readAllBytes(file));
    }
    @Test public void accountSwitchAndSlotReuseCannotBorrowAcceptedPolicy()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());String account=f.storage.accountId();
        var source=f.publish(f.working,at);assertTrue(f.accept(source,"account-bound"));
        var bound=f.publish(f.working.copyCurrentObservation(),at+1);assertNotNull(bound.getAcceptedSalePolicyProof());f.ack();
        var field=PluginStorage.class.getDeclaredField("configManager");field.setAccessible(true);
        var config=(net.runelite.client.config.ConfigManager)field.get(f.storage);when(config.getRSProfileKey()).thenReturn("other-account");
        assertNull(f.intents.acceptedPolicies(account));
        var foreign=f.publish(f.working.copyCurrentObservation(),at+2);
        assertNotEquals(account,foreign.getAccountId());assertNull(foreign.getAcceptedSalePolicyProof());
        when(config.getRSProfileKey()).thenReturn("sale-policy");
        var restored=f.publish(f.working.copyCurrentObservation(),at+3);
        assertEquals(account,restored.getAccountId());assertEquals(bound.getAcceptedSalePolicyProof(),restored.getAcceptedSalePolicyProof());
        f.client.captureObservedOfferCleared(0,account,at+4);f.ack();
        var reused=new TrackedOffer(0,561,false,200,10,at+5);reused.setState("SELLING");
        assertNull(f.publish(reused,at+5).getAcceptedSalePolicyProof());
    }
    @Test public void receiptReadOutagePreservesFirstCancellationAndCannotBindAfterRestart()throws Exception {
        var path=folder.newFolder().toPath();var f=new Fixture(path);var source=f.publish(f.working,at);
        assertEquals("1",source.getSalePolicyEvidenceVersion());assertTrue(f.accept(source,"before-read-outage",false));
        f.client.setSalePolicyAcceptanceReader(account->{throw new IllegalStateException("fixture receipt read outage");});
        f.working.setState("CANCELLED_SELL");f.working.setQuantityFilled(2);f.working.setSpent(400);
        var cancelled=f.publish(f.working.copyCurrentObservation(),at+1);
        assertEquals("1",cancelled.getSalePolicyEvidenceVersion());assertNull(cancelled.getAcceptedSalePolicyProof());
        assertEquals(2,cancelled.getFilledQuantity());assertEquals(400,cancelled.getSpent());f.ack();
        var restarted=new Fixture(path);restarted.working.setState("CANCELLED_SELL");
        restarted.working.setQuantityFilled(2);restarted.working.setSpent(400);
        assertEquals(1,restarted.intents.acceptedPolicies(restarted.storage.accountId()).size());
        var later=restarted.publish(restarted.working.copyCurrentObservation(),at+2);
        assertEquals("1",later.getSalePolicyEvidenceVersion());
        assertNull("A receipt-free first cancellation cannot acquire policy authority later",later.getAcceptedSalePolicyProof());
        assertEquals(400,later.getSpent());assertNotEquals(cancelled.getEventId(),later.getEventId());
    }
    @Test public void unsupportedMarkerCannotNormalizeThroughCachedReceiptAndRefusalSurvivesRestart()throws Exception {
        var path=folder.newFolder().toPath();var f=new Fixture(path);var source=f.publish(f.working,at);assertTrue(f.accept(source,"marker-fence"));
        var bound=f.publish(f.working.copyCurrentObservation(),at+1);assertNotNull(bound.getAcceptedSalePolicyProof());
        var changed=f.outbox.enqueueObservedOffer((id,session,seq)->OfferEvent.builder("future-wire",at+2,"SELLING")
            .accountId(f.storage.accountId()).authenticatedObservation(true).eventIdentity(id,session,source.getOfferIdentity())
            .sequence(seq).slot(0).item(561,"Nature rune").buying(false).price(200).quantities(10,0)
            .firstSeenAt(f.working.getFirstSeen()).salePolicyEvidenceVersion("2").build());
        assertEquals("2",changed.getSalePolicyEvidenceVersion());assertNull(changed.getAcceptedSalePolicyProof());f.ack();
        var restart=new Fixture(path);var current=restart.publish(restart.working.copyCurrentObservation(),at+3);
        assertEquals("1",current.getSalePolicyEvidenceVersion());assertNull(current.getAcceptedSalePolicyProof());
    }
    @Test public void malformedRetainedPolicyMarkerRefusesAppendWithoutRewriting()throws Exception {
        var path=folder.newFolder().toPath();var f=new Fixture(path);var source=f.publish(f.working,at);assertTrue(f.accept(source,"retained-marker"));
        f.publish(f.working.copyCurrentObservation(),at+1);f.ack();
        var file=f.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        var wire=f.storage.gson().fromJson(Files.readString(file),com.google.gson.JsonObject.class);
        var first=wire.getAsJsonObject("salePolicyEpisodes").entrySet().iterator().next().getValue().getAsJsonObject();
        first.addProperty("salePolicyEvidenceVersion","2");Files.writeString(file,f.storage.gson().toJson(wire));var retained=Files.readAllBytes(file);
        var restart=new Fixture(path);restart.client.captureObservedOffer(restart.working.copyCurrentObservation(),restart.storage.accountId(),at+2);
        assertNull(restart.client.continuationSourceEpisodes());assertArrayEquals(retained,Files.readAllBytes(file));
    }
}
