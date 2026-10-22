package com.flippingfriend.session;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Durable operational claims and write retries; all account files are disposable. */
public class ContinuationSuccessorPersistenceTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    class Fixture {
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"successor-persistence");
        final OfferTracker tracker=mock(OfferTracker.class);
        final boolean buying;
        final Path path;
        OfferContinuationStore book;
        Fixture(boolean buy,boolean fullyClaimed)throws Exception {
            buying=buy;path=storage.accountDir().resolve(buy?"buy-reprice-intent.json":"sell-relist-intent.json");
            when(tracker.getOffers()).thenReturn(List.of());
            var a=observation(0,20,"A",false);var b=observation(1,30,"B",false);
            var first=OfferContinuation.advise(a,List.of(a,b),!buy,1100,0);
            var second=OfferContinuation.advise(b,List.of(a,b),!buy,1100,0);
            first.observe(List.of(),observation(0,20,"A",true),1101);
            second.observe(List.of(),observation(1,30,"B",true),1101);
            int quantity=fullyClaimed?80:50;
            var acceptance=ContinuationSuccessorProof.accepted(storage.accountId(),"A","accepted-child",561,buy,100,quantity,1199,CaptureOrder.next());
            var event=OfferEvent.builder("fixture",1200,buy?"BUYING":"SELLING").accountId(storage.accountId())
                .eventIdentity("source-child","session","child").authenticatedObservation(true).slot(2).item(561,"Rune")
                .buying(buy).price(100).quantities(quantity,0).spent(0).sequence(10).firstSeenAt(1200).build();
            var proof=acceptance.captured(CaptureOrder.next()).bindTo(event);assertNotNull(proof);
            first.observe(List.of(),null,1200,List.of(proof),Set.of());
            var document=new JsonObject();document.addProperty("formatVersion",2);document.addProperty("accountId",storage.accountId());
            document.add("continuations",gson.toJsonTree(fullyClaimed?List.of(second):List.of(first,second)));
            document.add("consumedContinuations",gson.toJsonTree(fullyClaimed?List.of(first):List.of()));
            document.add("successorAcceptances",gson.toJsonTree(List.of(acceptance)));document.add("retiredContinuations",new JsonArray());
            Files.writeString(path,document.toString());restart();
        }
        OfferContinuation.Observation observation(int slot,int filled,String id,boolean collected){return new OfferContinuation.Observation(
            slot,561,buying,100,100,filled,1000+slot,id,collected?(buying?"CANCELLED_BUY":"CANCELLED_SELL"):(buying?"BUYING":"SELLING"),collected);}
        TrackedOffer current(int slot,int filled,String id){
            var value=new TrackedOffer(slot,561,buying,100,100,1000+slot);value.setQuantityFilled(filled);value.setState(buying?"BUYING":"SELLING");
            var json=gson.toJsonTree(value).getAsJsonObject();json.addProperty("episodeIdentity",id);return gson.fromJson(json,TrackedOffer.class);
        }
        void restart(){book=new OfferContinuationStore(storage,path.getFileName().toString(),buying){};}
        List<OfferContinuation> requests(long at){return book.collectedAll(tracker,storage.accountId(),at);}
        JsonObject document()throws Exception{return gson.fromJson(Files.readString(path),JsonObject.class);}
    }
    @Test public void consumedFutureClaimWithholdsSiblingButStillRetainsCurrentOriginalCorrections()throws Exception {
        for(boolean buy:new boolean[]{true,false}) {
            var f=new Fixture(buy,true);String before=Files.readString(f.path);
            assertTrue(f.requests(1199).isEmpty());assertEquals(before,Files.readString(f.path));
            assertEquals(70,f.requests(1200).get(0).getCollectedRemaining());
            when(f.tracker.getOffers()).thenReturn(List.of(f.current(0,40,"A")));
            assertTrue(f.requests(1199).isEmpty());
            var consumed=f.document().getAsJsonArray("consumedContinuations").get(0).getAsJsonObject();
            assertEquals(60,consumed.get("collectedRemaining").getAsInt());
            when(f.tracker.getOffers()).thenReturn(List.of());f.restart();assertTrue(f.requests(1300).isEmpty());
            assertTrue(f.book.pending(561,f.tracker,f.storage.accountId(),1300));
        }
    }
    @Test public void consumedBudgetContradictionRetriesAfterFailedWriteAndAccountSwitch()throws Exception {
        for(boolean buy:new boolean[]{true,false}) {
            var f=new Fixture(buy,true);String original=f.storage.accountId();String before=Files.readString(f.path);
            var blocked=f.path.resolveSibling(f.path.getFileName()+".tmp");Files.createDirectory(blocked);
            when(f.tracker.getOffers()).thenReturn(List.of(f.current(0,30,"A")));
            assertTrue(f.requests(1300).isEmpty());assertEquals(before,Files.readString(f.path));
            when(f.tracker.getOffers()).thenReturn(List.of());
            var field=PluginStorage.class.getDeclaredField("configManager");field.setAccessible(true);
            var config=(net.runelite.client.config.ConfigManager)field.get(f.storage);when(config.getRSProfileKey()).thenReturn("foreign");
            assertTrue(f.book.collectedAll(f.tracker,original,1300).isEmpty());
            when(config.getRSProfileKey()).thenReturn("successor-persistence");Files.delete(blocked);
            assertTrue(f.requests(1300).isEmpty());f.restart();assertTrue(f.requests(1300).isEmpty());
            assertEquals(70,f.document().getAsJsonArray("consumedContinuations").get(0).getAsJsonObject().get("collectedRemaining").getAsInt());
        }
    }
    @Test public void explicitAbandonRetainsClaimedOriginWithoutRestoringItsEntryPromise()throws Exception {
        for(boolean buy:new boolean[]{true,false})for(boolean whole:new boolean[]{true,false}) {
            var f=new Fixture(buy,false);assertEquals(2,f.requests(1300).size());
            assertTrue(whole?f.book.abandon(f.storage.accountId()):f.book.abandonItem(f.storage.accountId(),561));
            assertEquals(0,f.document().getAsJsonArray("continuations").size());
            assertEquals(1,f.document().getAsJsonArray("consumedContinuations").size());f.restart();assertTrue(f.requests(1300).isEmpty());
            when(f.tracker.getOffers()).thenReturn(List.of(f.current(0,60,"A")));assertTrue(f.requests(1300).isEmpty());
            assertTrue("Allocation contradiction survives explicit promise abandonment",f.book.pending(561,f.tracker,f.storage.accountId(),1300));
        }
    }
    @Test public void canonicalSaleRetirementKeepsTheClaimedOriginBudgetMonitor()throws Exception {
        var f=new Fixture(true,false);String account=f.storage.accountId();
        var sale=new ContinuationDisposition(561,"recorded-sale","later-sale",1250,1300);
        var ledger=new AccountLedgerSnapshot(account,1300,0,0,0,0,0,0,0,0,List.of()).withContinuationDispositions(List.of(sale));
        assertTrue(f.book.observeSales(ledger,account,1300));
        assertEquals(0,f.document().getAsJsonArray("continuations").size());
        assertEquals(2,f.document().getAsJsonArray("retiredContinuations").size());
        assertEquals(1,f.document().getAsJsonArray("consumedContinuations").size());
        f.restart();when(f.tracker.getOffers()).thenReturn(List.of(f.current(0,60,"A")));assertTrue(f.requests(1300).isEmpty());
        assertTrue(f.book.pending(561,f.tracker,account,1300));
    }
}
