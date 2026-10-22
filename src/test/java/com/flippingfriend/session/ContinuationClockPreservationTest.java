package com.flippingfriend.session;

import com.flippingfriend.core.OfferContinuation;
import com.flippingfriend.data.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Simulated clock rollback with disposable account files; never changes the operating-system clock. */
public class ContinuationClockPreservationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    OfferContinuation hint(boolean buy,int slot,int item,long first,long created,boolean collected) {
        var original=new OfferContinuation.Observation(slot,item,buy,100,100,20,first,"episode-"+slot,
            buy?"BUYING":"SELLING",false);
        var hint=OfferContinuation.advise(original,List.of(original),!buy,created,0);
        if(collected)assertEquals(OfferContinuation.Status.COLLECTED,hint.observe(List.of(),
            new OfferContinuation.Observation(slot,item,buy,100,100,40,first,"episode-"+slot,
                buy?"CANCELLED_BUY":"CANCELLED_SELL",true),created));
        return hint;
    }
    class Fixture {
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"clock");
        final OfferTracker tracker=mock(OfferTracker.class);
        final boolean buying;
        final Path path;
        OfferContinuationStore book;
        Fixture(boolean buy,OfferContinuation... hints)throws Exception {
            buying=buy;path=storage.accountDir().resolve(buy?"buy-reprice-intent.json":"sell-relist-intent.json");
            when(tracker.getOffers()).thenReturn(List.of());
            var document=new JsonObject();document.addProperty("formatVersion",2);document.addProperty("accountId",storage.accountId());
            document.add("continuations",gson.toJsonTree(List.of(hints)));document.add("retiredContinuations",new JsonArray());
            Files.writeString(path,document.toString());restart();
        }
        void restart(){book=new OfferContinuationStore(storage,path.getFileName().toString(),buying){};}
        List<OfferContinuation> requests(long at){return book.collectedAll(tracker,storage.accountId(),at);}
        JsonObject document()throws Exception{return gson.fromJson(Files.readString(path),JsonObject.class);}
        void replace(JsonObject document)throws Exception{Files.writeString(path,document.toString());restart();}
    }
    @Test public void futureCreationWithholdsRequestsAndAcknowledgementWithoutDeletingPartialStockProtection()throws Exception {
        var f=new Fixture(true,hint(true,0,561,1000,1100,true));String before=Files.readString(f.path);
        assertTrue(f.requests(1099).isEmpty());assertEquals(before,Files.readString(f.path));
        assertTrue("The acquired partial must not escape its retained workflow",f.book.pending(561,f.tracker,f.storage.accountId(),1099));
        assertEquals(561,f.book.pendingItem(f.tracker,f.storage.accountId(),1099));
        var cancelled=new TrackedOffer(0,561,true,100,100,1000);cancelled.setState("CANCELLED_BUY");cancelled.setQuantityFilled(40);
        var raw=gson.toJsonTree(cancelled).getAsJsonObject();raw.addProperty("episodeIdentity","episode-0");cancelled=gson.fromJson(raw,TrackedOffer.class);
        assertFalse(f.book.acknowledgedCancellation(cancelled,f.storage.accountId(),1099));
        f.restart();assertTrue(f.requests(1099).isEmpty());assertEquals(before,Files.readString(f.path));
        assertEquals(60,f.requests(1100).get(0).getCollectedRemaining());assertEquals(before,Files.readString(f.path));
    }
    @Test public void laterSaleAcceptanceStaysImmutableAndResumesOnlyAtItsOriginalTime()throws Exception {
        var sale=hint(false,0,561,1000,1100,true);sale.bindSaleRepriceSource("accepted-source",1200,1300);
        var f=new Fixture(false,sale);String before=Files.readString(f.path);
        assertTrue(f.requests(1299).isEmpty());assertEquals(before,Files.readString(f.path));
        f.restart();assertTrue(f.requests(1299).isEmpty());
        var ready=f.requests(1300).get(0);assertEquals(60,ready.getCollectedRemaining());
        assertEquals("accepted-source",ready.getSaleRepriceSource());assertEquals(1200,ready.getSaleRepriceProgressAt());
        assertEquals(1300,ready.getSaleRepriceAcceptedAt());assertEquals(before,Files.readString(f.path));
    }
    @Test public void legacyMissingCreationCannotMakeAFutureOriginalOfferActionable()throws Exception {
        var f=new Fixture(false,hint(false,0,561,1000,1100,true));var document=f.document();
        document.getAsJsonArray("continuations").get(0).getAsJsonObject().remove("createdAt");f.replace(document);
        String before=Files.readString(f.path);assertTrue(f.requests(999).isEmpty());assertEquals(before,Files.readString(f.path));
        assertEquals(60,f.requests(1000).get(0).getCollectedRemaining());assertEquals(before,Files.readString(f.path));
    }
    @Test public void futureLegacyAdviceExpiryIsNotRewrittenUntilTheOriginalEvidenceIsCurrent()throws Exception {
        var f=new Fixture(true,hint(true,0,561,1000,1100,true));var document=f.document();
        document.getAsJsonArray("continuations").get(0).getAsJsonObject().addProperty("expiresAt",1500);f.replace(document);
        String before=Files.readString(f.path);assertTrue(f.requests(1099).isEmpty());assertEquals(before,Files.readString(f.path));
        assertEquals(60,f.requests(1100).get(0).getCollectedRemaining());
        var retained=f.document().getAsJsonArray("continuations").get(0).getAsJsonObject();
        assertEquals(0,retained.get("expiresAt").getAsLong());assertEquals(1100,retained.get("createdAt").getAsLong());
    }
    @Test public void aFutureSiblingDoesNotHideAnIndependentCurrentRemainderOrChangeItsEvidence()throws Exception {
        var f=new Fixture(true,hint(true,0,561,1000,1100,true),hint(true,1,562,800,900,true));
        String before=Files.readString(f.path);var ready=f.requests(1000);assertEquals(1,ready.size());
        assertEquals(562,ready.get(0).getItemId());assertEquals(before,Files.readString(f.path));
        f.restart();assertEquals(1,f.requests(1000).size());assertTrue(f.book.pending(561,f.tracker,f.storage.accountId(),1000));
        assertEquals(2,f.requests(1100).size());
    }
    @Test public void dirtyOtherProgressRetriesWithoutMutatingOrDeletingTheFutureSibling()throws Exception {
        var f=new Fixture(true,hint(true,0,561,1000,1100,true),hint(true,1,562,800,900,false));
        var current=new TrackedOffer(1,562,true,100,100,800);current.setState("CANCELLED_BUY");current.setQuantityFilled(50);current.setCollected(true);
        var raw=gson.toJsonTree(current).getAsJsonObject();raw.addProperty("episodeIdentity","episode-1");current=gson.fromJson(raw,TrackedOffer.class);
        when(f.tracker.getSettledOffer(1)).thenReturn(current);
        String before=Files.readString(f.path);var future=f.document().getAsJsonArray("continuations").get(0).deepCopy();
        Path blocked=f.path.resolveSibling(f.path.getFileName()+".tmp");Files.createDirectory(blocked);
        assertTrue(f.requests(1000).isEmpty());assertEquals(before,Files.readString(f.path));
        when(f.tracker.getSettledOffer(1)).thenReturn(null);Files.delete(blocked);
        assertEquals(50,f.requests(1000).get(0).getCollectedRemaining());
        assertEquals(future,f.document().getAsJsonArray("continuations").get(0));
        f.restart();assertEquals(2,f.requests(1100).size());
    }
    @Test public void profileSwitchAndRestartPreserveTheOriginalFutureDocumentUntilClockRecovery()throws Exception {
        var f=new Fixture(true,hint(true,0,561,1000,1100,true));String before=Files.readString(f.path);String original=f.storage.accountId();
        assertTrue(f.requests(1099).isEmpty());
        var field=PluginStorage.class.getDeclaredField("configManager");field.setAccessible(true);
        var config=(net.runelite.client.config.ConfigManager)field.get(f.storage);when(config.getRSProfileKey()).thenReturn("other");
        assertTrue(f.book.collectedAll(f.tracker,original,1099).isEmpty());assertTrue(f.requests(1099).isEmpty());
        when(config.getRSProfileKey()).thenReturn("clock");assertEquals(before,Files.readString(f.path));
        f.restart();assertEquals(60,f.requests(1100).get(0).getCollectedRemaining());
    }
    @Test public void currentCounterRegressionStillWithdrawsTheReceiptAfterTheClockCatchesUp()throws Exception {
        var f=new Fixture(true,hint(true,0,561,1000,1100,true));assertTrue(f.requests(1099).isEmpty());
        var current=new TrackedOffer(0,561,true,100,100,1000);current.setState("CANCELLED_BUY");current.setQuantityFilled(30);current.setCollected(true);
        var raw=gson.toJsonTree(current).getAsJsonObject();raw.addProperty("episodeIdentity","episode-0");current=gson.fromJson(raw,TrackedOffer.class);
        when(f.tracker.getSettledOffer(0)).thenReturn(current);
        assertTrue(f.requests(1100).isEmpty());assertFalse(f.book.pending(561,f.tracker,f.storage.accountId(),1100));
        assertEquals(0,f.document().getAsJsonArray("continuations").size());
    }
    TrackedOffer corrected(boolean buy,int filled,String state,String identity) {
        var offer=new TrackedOffer(0,561,buy,100,100,1000);offer.setState(state);offer.setQuantityFilled(filled);offer.setSpent(100L*filled);
        var raw=gson.toJsonTree(offer).getAsJsonObject();raw.addProperty("episodeIdentity",identity);
        return gson.fromJson(raw,TrackedOffer.class);
    }
    @Test public void correctedProgressAfterSavedCollectionNarrowsTheRemainderThroughFailedWritesAndRestart()throws Exception {
        for(boolean buy:new boolean[]{true,false})for(boolean future:new boolean[]{true,false}) {
            var f=new Fixture(buy,hint(buy,0,561,1000,1100,true));long at=future?1099:1100;
            var updated=corrected(buy,50,buy?"BUYING":"SELLING","episode-0");
            when(f.tracker.getOffers()).thenReturn(List.of(updated));
            Path blocked=f.path.resolveSibling(f.path.getFileName()+".tmp");Files.createDirectory(blocked);String before=Files.readString(f.path);
            assertTrue(f.requests(at).isEmpty());assertEquals(before,Files.readString(f.path));
            Files.delete(blocked);assertTrue("The original offer is visible again, so wait for collection",f.requests(at).isEmpty());
            var retained=f.document().getAsJsonArray("continuations").get(0).getAsJsonObject();
            assertEquals(50,retained.get("collectedRemaining").getAsInt());assertEquals(50,retained.get("maximumFilled").getAsInt());
            when(f.tracker.getOffers()).thenReturn(List.of());f.restart();
            assertEquals(50,f.requests(1100).get(0).getCollectedRemaining());
        }
    }
    @Test public void currentCollectionDuringSuspensionIsRetainedWithoutBecomingGuidanceEarly()throws Exception {
        for(boolean buy:new boolean[]{true,false}) {
            var f=new Fixture(buy,hint(buy,0,561,1000,1100,false));
            var terminal=corrected(buy,50,buy?"CANCELLED_BUY":"CANCELLED_SELL","episode-0");terminal.setCollected(true);
            when(f.tracker.getSettledOffer(0)).thenReturn(terminal);assertTrue(f.requests(1099).isEmpty());
            when(f.tracker.getSettledOffer(0)).thenReturn(null);f.restart();assertTrue(f.requests(1099).isEmpty());
            assertEquals(50,f.requests(1100).get(0).getCollectedRemaining());
        }
    }
    @Test public void aSmallerRetainedCollectionCapCannotGrowWhenAnotherCollectionCallbackArrives()throws Exception {
        for(boolean buy:new boolean[]{true,false}) {
            var f=new Fixture(buy,hint(buy,0,561,1000,1100,true));var document=f.document();
            document.getAsJsonArray("continuations").get(0).getAsJsonObject().addProperty("collectedRemaining",30);f.replace(document);
            var terminal=corrected(buy,50,buy?"CANCELLED_BUY":"CANCELLED_SELL","episode-0");terminal.setCollected(true);
            when(f.tracker.getSettledOffer(0)).thenReturn(terminal);
            assertTrue(f.requests(1099).isEmpty());when(f.tracker.getSettledOffer(0)).thenReturn(null);f.restart();
            assertEquals("Existing evidence may conservatively cap fewer units; a refresh must not add units",30,f.requests(1100).get(0).getCollectedRemaining());
        }
    }
    @Test public void missingLegacyTotalUsesCurrentObservedRemainderWithoutChangingTheOriginalIdentity()throws Exception {
        for(boolean buy:new boolean[]{true,false})for(int total:new int[]{90,100}) {
            var f=new Fixture(buy,hint(buy,0,561,1000,1100,true));var document=f.document();
            document.getAsJsonArray("continuations").get(0).getAsJsonObject().remove("total");f.replace(document);
            var observed=corrected(buy,total==100?50:40,buy?"BUYING":"SELLING","episode-0");
            var raw=gson.toJsonTree(observed).getAsJsonObject();raw.addProperty("totalQuantity",total);observed=gson.fromJson(raw,TrackedOffer.class);
            when(f.tracker.getOffers()).thenReturn(List.of(observed));assertTrue(f.requests(1099).isEmpty());
            var retained=f.document().getAsJsonArray("continuations").get(0).getAsJsonObject();
            assertEquals(0,retained.get("total").getAsInt());assertEquals("episode-0",retained.get("episode").getAsString());
            assertEquals(50,retained.get("collectedRemaining").getAsInt());
            when(f.tracker.getOffers()).thenReturn(List.of());f.restart();assertEquals(50,f.requests(1100).get(0).getCollectedRemaining());
        }
    }
    @Test public void currentTerminalFullCounterAndReplacementFactsCannotResurrectAfterClockRecovery()throws Exception {
        for(boolean buy:new boolean[]{true,false})for(String change:List.of("terminal","full-counter","replacement")) {
            var f=new Fixture(buy,hint(buy,0,561,1000,1100,true));
            var updated=corrected(buy,change.equals("replacement")?0:100,
                change.equals("terminal")?(buy?"BOUGHT":"SOLD"):(buy?"BUYING":"SELLING"),
                change.equals("replacement")?"new-episode":"episode-0");
            when(f.tracker.getOffers()).thenReturn(List.of(updated));assertTrue(f.requests(1099).isEmpty());
            assertEquals(0,f.document().getAsJsonArray("continuations").size());
            when(f.tracker.getOffers()).thenReturn(List.of());f.restart();assertTrue(f.requests(1100).isEmpty());
        }
    }
    @Test public void actualLateCallbacksAfterCollectionPreserveExactCostsAndTheCorrectedCapAcrossStaleSnapshotRestart()throws Exception {
        for(boolean buy:new boolean[]{true,false}) {
            var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"actual-clock");
            long now=java.time.Instant.now().getEpochSecond(),first=now-600,created=now+300;
            var original=new TrackedOffer(0,561,buy,100,100,first);original.setQuantityFilled(40);original.setRecordedQuantity(40);
            original.setSpent(4000);original.setRecordedSpent(4000);original.setCollected(true);original.setJournalled(true);original.setLastChanged(now);
            original.setState(buy?"CANCELLED_BUY":"CANCELLED_SELL");
            var raw=gson.toJsonTree(original).getAsJsonObject();raw.addProperty("episodeIdentity","actual-episode");original=gson.fromJson(raw,TrackedOffer.class);
            storage.writeJson(storage.accountDir().resolve("offers.json"),List.of(original),new com.google.gson.reflect.TypeToken<List<TrackedOffer>>(){}.getType());
            var start=new OfferContinuation.Observation(0,561,buy,100,100,20,first,"actual-episode",buy?"BUYING":"SELLING",false);
            var retained=OfferContinuation.advise(start,List.of(start),!buy,created,0);
            retained.observe(List.of(),new OfferContinuation.Observation(0,561,buy,100,100,40,first,"actual-episode",
                buy?"CANCELLED_BUY":"CANCELLED_SELL",true),created);
            String file=buy?"buy-reprice-intent.json":"sell-relist-intent.json";var path=storage.accountDir().resolve(file);
            var document=new JsonObject();document.addProperty("formatVersion",2);document.addProperty("accountId",storage.accountId());
            document.add("continuations",gson.toJsonTree(List.of(retained)));document.add("retiredContinuations",new JsonArray());Files.writeString(path,document.toString());
            var positions=new PositionBook(storage);positions.recordBuy(561,"Nature rune",buy?40:60,buy?4000:6000,java.time.Instant.ofEpochSecond(first));
            var tracker=new OfferTracker(storage,positions,mock(BuyLimitTracker.class),mock(TradeJournal.class),new com.flippingfriend.model.TaxCalculator(),new TradePlans(storage));
            var field=OfferTracker.class.getDeclaredField(buy?"buyReprice":"sellRelist");field.setAccessible(true);
            field.set(tracker,buy?new BuyRepriceIntent(storage):new SellRelistIntent(storage));tracker.load();
            var callback=mock(net.runelite.api.GrandExchangeOffer.class);
            when(callback.getItemId()).thenReturn(561);when(callback.getPrice()).thenReturn(100L);when(callback.getTotalQuantity()).thenReturn(100);
            when(callback.getQuantitySold()).thenReturn(50);when(callback.getSpent()).thenReturn(5000L);
            when(callback.getState()).thenReturn(buy?net.runelite.api.GrandExchangeOfferState.BUYING:net.runelite.api.GrandExchangeOfferState.SELLING);
            tracker.onOfferChanged(0,callback);
            assertEquals(50,positions.get(561).getQuantity());assertEquals(5000,positions.get(561).getTotalCost());
            var updated=gson.fromJson(Files.readString(path),JsonObject.class).getAsJsonArray("continuations").get(0).getAsJsonObject();
            assertEquals(50,updated.get("collectedRemaining").getAsInt());assertEquals(created,updated.get("createdAt").getAsLong());
            // Crash before offers.json saves again; the newly committed workflow still owns its cap.
            var restarted=new OfferTracker(storage,positions,mock(BuyLimitTracker.class),mock(TradeJournal.class),new com.flippingfriend.model.TaxCalculator(),new TradePlans(storage));restarted.load();
            var book=new OfferContinuationStore(storage,file,buy){};
            assertTrue(book.collectedAll(restarted,storage.accountId(),now).isEmpty());
            assertEquals(50,book.collectedAll(restarted,storage.accountId(),created).get(0).getCollectedRemaining());
            assertEquals(50,positions.get(561).getQuantity());assertEquals(5000,positions.get(561).getTotalCost());
        }
    }
}
