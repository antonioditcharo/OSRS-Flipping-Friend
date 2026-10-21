package com.flippingfriend.session;

import com.flippingfriend.core.AccountIdentity;
import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BuyMaintenanceDeadlinesTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=1800000600;
    TrackedOffer buy(){var offer=new TrackedOffer(0,561,true,1000,10,now-600);offer.setState("BUYING");return offer;}
    @Test public void frozenDeadlinesSurviveRestartWithoutRewritingExecutionSnapshots()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"deadline");String account=storage.accountId();
        Path offers=storage.accountDir().resolve("offers.json");byte[] original="execution snapshot retained byte for byte".getBytes(StandardCharsets.UTF_8);Files.write(offers,original);
        long deadline=new BuyMaintenanceDeadlines(storage).freeze(account,buy(),20,180,now);
        assertEquals(now+600,deadline);
        assertEquals(deadline,new BuyMaintenanceDeadlines(storage).freeze(account,buy(),300,400,now+60));
        assertArrayEquals(original,Files.readAllBytes(offers));
    }
    @Test public void failedCommitRetriesTheSameFallbackClockAndPreservesChangedFiles()throws Exception {
        var storage=spy(TestStorage.rootedAt(folder.newFolder().toPath(),"deadline"));String account=storage.accountId();
        var deadlines=new BuyMaintenanceDeadlines(storage);doReturn(false).when(storage).writeDurably(any(),any());
        assertEquals(0,deadlines.freeze(account,buy(),Double.NaN,180,now));
        doCallRealMethod().when(storage).writeDurably(any(),any());
        assertEquals(now+180*60,deadlines.freeze(account,buy(),Double.NaN,300,now+600));
        Path file=storage.accountDir().resolve(BuyMaintenanceDeadlines.FILE_NAME);byte[] changed="externally changed metadata".getBytes(StandardCharsets.UTF_8);Files.write(file,changed);
        assertEquals(0,deadlines.freeze(account,buy(),1,1,now+601));assertArrayEquals(changed,Files.readAllBytes(file));
    }
    @Test public void workerCannotWriteTheOldOffersDeadlineIntoANewlySelectedAccount()throws Exception {
        Path root=folder.newFolder().toPath();String old=AccountIdentity.fromProfileKey("old"),other=AccountIdentity.fromProfileKey("other");
        var active=new AtomicReference<>(old);var storage=spy(TestStorage.rootedAt(root,"old"));
        doAnswer(c->active.get()).when(storage).accountId();
        doAnswer(c->Files.createDirectories(root.resolve(active.get()))).when(storage).accountDir();
        doAnswer(c->{String expected=c.getArgument(0);if(!expected.equals(active.get()))throw new IllegalStateException("account changed");return Files.createDirectories(root.resolve(expected));}).when(storage).accountDir(anyString());
        var offer=buy();Files.writeString(storage.accountDir().resolve("offers.json"),storage.gson().toJson(List.of(offer)),StandardCharsets.UTF_8);
        var tracker=new OfferTracker(storage,mock(PositionBook.class),mock(BuyLimitTracker.class),mock(TradeJournal.class),new TaxCalculator(),mock(TradePlans.class));tracker.load();
        active.set(other);
        assertEquals(0,tracker.freezeBuyMaintenanceDeadline(0,offer.getContinuationIdentity(),20,180,now,old));
        assertEquals(0,tracker.freezeBuyMaintenanceDeadline(0,offer.getContinuationIdentity(),20,180,now,other));
        assertFalse(Files.exists(root.resolve(other).resolve(BuyMaintenanceDeadlines.FILE_NAME)));
        assertFalse(Files.exists(root.resolve(old).resolve(BuyMaintenanceDeadlines.FILE_NAME)));
    }
    @Test public void accountSwitchDuringCommitCanOnlyWriteTheAlreadyResolvedOldMetadataFile()throws Exception {
        var storage=spy(TestStorage.rootedAt(folder.newFolder().toPath(),"deadline"));String old=storage.accountId();
        var active=new AtomicReference<>(old);doAnswer(c->active.get()).when(storage).accountId();
        doAnswer(c->{active.set(AccountIdentity.fromProfileKey("other"));return c.callRealMethod();}).when(storage).writeDurably(any(),any());
        assertEquals(0,new BuyMaintenanceDeadlines(storage).freeze(old,buy(),20,180,now));
        assertTrue(Files.exists(storage.accountDir().resolve(BuyMaintenanceDeadlines.FILE_NAME)));
        assertEquals(0,new BuyMaintenanceDeadlines(storage).freeze(old,buy(),20,180,now+1));
    }
}
