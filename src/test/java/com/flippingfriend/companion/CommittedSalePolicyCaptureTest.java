package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.session.*;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real callback/checkpoint/outbox ordering; isolated files, no HTTP or game input. */
public class CommittedSalePolicyCaptureTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    class Fixture {
        final PluginStorage storage;final SellRelistIntent intents;final OfferTracker tracker;
        final OfferEventOutbox outbox;final CompanionClient client;
        Fixture(Path root,boolean seed)throws Exception {
            storage=TestStorage.rootedAt(root,"committed-sale-proof");
            if(seed) {
                var saved=new TrackedOffer(0,561,false,200,10,now()-3600);saved.setState("SELLING");
                Files.writeString(storage.accountDir().resolve("offers.json"),storage.gson().toJson(List.of(saved)));
            }
            intents=new SellRelistIntent(storage);outbox=new OfferEventOutbox(storage);
            client=new CompanionClient(storage,storage.gson(),new SuggestionLedger(),outbox);
            client.setSalePolicyAcceptanceReader(intents::acceptedPolicies);
            tracker=new OfferTracker(storage,new PositionBook(storage),mock(BuyLimitTracker.class),mock(TradeJournal.class),
                new TaxCalculator(),new TradePlans(storage));
            var field=OfferTracker.class.getDeclaredField("sellRelist");field.setAccessible(true);field.set(tracker,intents);
            tracker.load();tracker.setContinuationSourceReader(client::continuationSourceEpisodes);
            tracker.setChangeListener(()->{
                var current=tracker.getOffer(0);
                if(current==null)client.captureObservedOfferCleared(0,storage.accountId(),now());
                else client.captureObservedOffer(current.copyObservation(),storage.accountId(),now());
                tracker.reconcileContinuationSources();
            });
        }
        void callback(GrandExchangeOfferState state,int filled){tracker.onOfferChanged(0,raw(state,filled));}
        OfferEvent latest()throws Exception{var all=outbox.pending();return all.get(all.size()-1);}
        void ack()throws Exception{for(var e:outbox.pending())assertTrue(outbox.acknowledge(e.getEventId(),e.getSessionId(),e.getSequence()));}
        Path intentFile(){return storage.accountDir().resolve("sell-relist-intent.json");}
        Path blocked(){return intentFile().resolveSibling(intentFile().getFileName()+".tmp");}
        AcceptedSalePolicyProof accept(OfferEvent source)throws Exception {
            var receipt=SalePolicyAcceptanceDeliveryTest.receipt(storage.accountId(),"committed-before-progress",source,150,now());
            assertTrue(intents.advise(tracker.getOffer(0),storage.accountId(),true,tracker,source.getEventId(),receipt,now()));
            var proof=intents.acceptedPolicies(storage.accountId()).get(0);outbox.retainSalePolicy(proof);return proof;
        }
    }
    @Test public void committedAcceptanceSurvivesFirstCancellationCounterSaveFailureThenRetryAckRestartAndCollection()throws Exception {
        var path=folder.newFolder().toPath();var f=new Fixture(path,true);f.callback(GrandExchangeOfferState.SELLING,0);
        var source=f.latest();var accepted=f.accept(source);f.ack();var before=Files.readAllBytes(f.intentFile());
        Files.createDirectory(f.blocked());f.callback(GrandExchangeOfferState.CANCELLED_SELL,2);
        assertArrayEquals("Failed counter checkpoint retains the exact committed acceptance",before,Files.readAllBytes(f.intentFile()));
        var cancelled=f.latest();assertEquals("1",cancelled.getSalePolicyEvidenceVersion());
        assertEquals(2,cancelled.getFilledQuantity());assertEquals(400,cancelled.getSpent());
        assertNotNull("A failed later counter save must not suppress a previously committed acceptance",cancelled.getAcceptedSalePolicyProof());
        assertTrue(accepted.hasSameAcceptance(cancelled.getAcceptedSalePolicyProof()));
        assertEquals(source.getEventId(),cancelled.getAcceptedSalePolicyProof().getReceipt().getSourceEventId());
        assertEquals(10,cancelled.getAcceptedSalePolicyProof().getReceipt().getQuantity());
        assertEquals(accepted,f.intents.acceptedPolicies(f.storage.accountId()).get(0));
        Files.delete(f.blocked());f.tracker.reconcileContinuationSources();f.tracker.save();f.ack();
        var restart=new Fixture(path,false);restart.callback(GrandExchangeOfferState.CANCELLED_SELL,2);
        assertEquals(cancelled.getAcceptedSalePolicyProof(),restart.latest().getAcceptedSalePolicyProof());
        restart.callback(GrandExchangeOfferState.EMPTY,0);
        var remainder=restart.intents.collectedRequest(restart.tracker,restart.storage.accountId());
        assertNotNull(remainder);assertEquals(8,remainder.getQuantity());assertEquals(accepted,remainder.getAcceptedSalePolicyProof());
        assertEquals(source.getEventId(),remainder.getSaleRepriceSource());assertEquals(480,remainder.getAcceptedPatienceSeconds());
    }
    @Test public void changedCommittedFileCannotExportCachedAcceptanceOrRewriteExternalBytes()throws Exception {
        var f=new Fixture(folder.newFolder().toPath(),true);f.callback(GrandExchangeOfferState.SELLING,0);f.accept(f.latest());
        var external=Files.readString(f.intentFile())+"\n";Files.writeString(f.intentFile(),external);
        assertNull(f.intents.acceptedPolicies(f.storage.accountId()));
        assertEquals(external,Files.readString(f.intentFile()));
    }
    @Test public void unreadableCommittedPathWithholdsAcceptanceInCachedAndFreshStoresWithoutWriting()throws Exception {
        var f=new Fixture(folder.newFolder().toPath(),true);f.callback(GrandExchangeOfferState.SELLING,0);f.accept(f.latest());
        var saved=f.intentFile().resolveSibling("preserved-intent.json");Files.move(f.intentFile(),saved);Files.createDirectory(f.intentFile());
        var before=Files.readAllBytes(saved);
        assertNull(f.intents.acceptedPolicies(f.storage.accountId()));
        assertNull(new SellRelistIntent(f.storage).acceptedPolicies(f.storage.accountId()));
        assertTrue(Files.isDirectory(f.intentFile()));assertArrayEquals(before,Files.readAllBytes(saved));
    }
    @Test public void foreignDocumentOrNestedReceiptCannotLoadAcceptanceInFreshStore()throws Exception {
        for(boolean nested:new boolean[]{false,true}) {
            var f=new Fixture(folder.newFolder().toPath(),true);f.callback(GrandExchangeOfferState.SELLING,0);f.accept(f.latest());
            var document=f.storage.gson().fromJson(Files.readString(f.intentFile()),com.google.gson.JsonObject.class);
            if(nested)document.getAsJsonArray("continuations").get(0).getAsJsonObject()
                .getAsJsonObject("acceptedSalePolicyProof").getAsJsonObject("receipt").addProperty("accountId","foreign-account");
            else document.addProperty("accountId","foreign-account");
            var external=f.storage.gson().toJson(document);Files.writeString(f.intentFile(),external);
            assertNull(f.intents.acceptedPolicies(f.storage.accountId()));
            assertNull(new SellRelistIntent(f.storage).acceptedPolicies(f.storage.accountId()));
            assertNull(new SellRelistIntent(f.storage).acceptedPolicies("foreign-account"));
            assertEquals(external,Files.readString(f.intentFile()));
        }
    }
    private static long now(){return java.time.Instant.now().getEpochSecond();}
    private static GrandExchangeOffer raw(GrandExchangeOfferState state,int filled) {
        var offer=mock(GrandExchangeOffer.class);when(offer.getState()).thenReturn(state);when(offer.getItemId()).thenReturn(561);
        when(offer.getPrice()).thenReturn(200L);when(offer.getTotalQuantity()).thenReturn(10);
        when(offer.getQuantitySold()).thenReturn(filled);when(offer.getSpent()).thenReturn(200L*filled);return offer;
    }
}
