package com.flippingfriend.session;

import com.flippingfriend.data.*;
import java.nio.file.*;
import java.util.*;
import net.runelite.client.config.ConfigManager;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real file failures between a displayed cancellation and its observed collection. */
public class ContinuationWriteRecoveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=java.time.Instant.now().getEpochSecond();
    class Fixture {
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"first");
        final OfferTracker tracker=mock(OfferTracker.class);
        final BuyRepriceIntent book=new BuyRepriceIntent(storage);
        final TrackedOffer offer=new TrackedOffer(0,561,true,100,100,now-600);
        final Path path=storage.accountDir().resolve("buy-reprice-intent.json"),blocked=path.resolveSibling(path.getFileName()+".tmp");
        Fixture()throws Exception {
            offer.startEpisodeIdentity();offer.setState("BUYING");offer.setQuantityFilled(20);
            when(tracker.getOffers()).thenReturn(List.of(offer));when(tracker.getOffer(0)).thenReturn(offer);
            book.advise(offer,storage.accountId(),tracker,now);
        }
        void collectWithWriteFailure()throws Exception {
            Files.createDirectory(blocked);
            offer.setState("CANCELLED_BUY");offer.setQuantityFilled(40);offer.setCollected(true);
            when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);
            when(tracker.getSettledOffer(0)).thenReturn(offer);book.observe(tracker,storage.accountId(),now);
            when(tracker.getSettledOffer(0)).thenReturn(null);
        }
        void profile(String name)throws Exception {
            var field=PluginStorage.class.getDeclaredField("configManager");field.setAccessible(true);
            when(((ConfigManager)field.get(storage)).getRSProfileKey()).thenReturn(name);
        }
    }
    @Test public void aFailedCollectionSaveRetriesAfterSettledEvidenceIsPrunedAndSurvivesRestart()throws Exception {
        var f=new Fixture();f.collectWithWriteFailure();Files.delete(f.blocked);
        f.book.observe(f.tracker,f.storage.accountId(),now+1);
        var request=new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now+86400);
        assertNotNull("Unchanged later observations must retry the exact retained remainder",request);assertEquals(60,request.quantity);
    }
    @Test public void unsavedCollectionCannotBeOfferedAsADurableReplacement()throws Exception {
        var f=new Fixture();f.collectWithWriteFailure();
        assertNull("Keep the hint in memory, but wait for its durable commit",f.book.collectedRequest(f.tracker,f.storage.accountId(),now));
        assertTrue("The partial holding stays protected while its continuation retries",f.book.isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void anAccountSwitchCannotDropTheOtherAccountsDirtyContinuation()throws Exception {
        var f=new Fixture();f.collectWithWriteFailure();String original=f.storage.accountId();
        f.profile("second");assertNull(f.book.collectedRequest(f.tracker,f.storage.accountId(),now));
        f.profile("first");Files.delete(f.blocked);f.book.observe(f.tracker,original,now+1);
        var request=new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,original,now+86400);
        assertNotNull("Switching profiles must retain unsaved observed collection evidence",request);assertEquals(60,request.quantity);
    }
    @Test public void anExternallyChangedUnreadableFileIsPreservedEvenAfterTheBookWasLoaded()throws Exception {
        var f=new Fixture();String broken="{unreadable retained evidence";Files.writeString(f.path,broken);
        f.offer.setState("CANCELLED_BUY");f.offer.setQuantityFilled(40);f.offer.setCollected(true);
        when(f.tracker.getOffers()).thenReturn(List.of());when(f.tracker.getSettledOffer(0)).thenReturn(f.offer);
        f.book.observe(f.tracker,f.storage.accountId(),now);
        assertEquals("A cached book cannot overwrite changed source evidence",broken,Files.readString(f.path));
    }
    @Test public void failedClearDoesNotReturnAfterAnUnchangedObservationAndRestart()throws Exception {
        var f=new Fixture();Files.createDirectory(f.blocked);assertFalse(f.book.clearItem(f.storage.accountId(),561));
        Files.delete(f.blocked);f.book.observe(f.tracker,f.storage.accountId(),now+1);
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void anInterruptedTemporaryDocumentNeverReplacesCommittedEvidence()throws Exception {
        var f=new Fixture();String committed=Files.readString(f.path);Files.writeString(f.blocked,"{incomplete replacement");
        assertTrue(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
        assertEquals(committed,Files.readString(f.path));assertEquals("{incomplete replacement",Files.readString(f.blocked));
    }
    @Test public void aPersonalSaleKeepsItsUnknownCostWhileFailedRelistWritesRetry()throws Exception {
        var f=new Fixture();var sale=new TrackedOffer(1,562,false,150,100,now-600);sale.startEpisodeIdentity();sale.setState("SELLING");sale.setQuantityFilled(20);
        when(f.tracker.getOffers()).thenReturn(List.of(sale));when(f.tracker.getOffer(1)).thenReturn(sale);
        var book=new SellRelistIntent(f.storage);assertTrue(book.advise(sale,f.storage.accountId(),false,f.tracker));
        Path blocked=f.storage.accountDir().resolve("sell-relist-intent.json.tmp");Files.createDirectory(blocked);
        sale.setState("CANCELLED_SELL");sale.setCollected(true);sale.setQuantityFilled(40);
        when(f.tracker.getOffers()).thenReturn(List.of());when(f.tracker.getSettledOffer(1)).thenReturn(sale);
        book.observe(f.tracker,f.storage.accountId());when(f.tracker.getSettledOffer(1)).thenReturn(null);
        assertNull(book.collectedRequest(f.tracker,f.storage.accountId()));Files.delete(blocked);book.observe(f.tracker,f.storage.accountId());
        var request=new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId());
        assertNotNull(request);assertEquals(60,request.getQuantity());assertFalse(request.requiresManagedCost());
    }
}
