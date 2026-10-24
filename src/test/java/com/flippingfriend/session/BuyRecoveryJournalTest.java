package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BuyRecoveryJournalTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long start=1800000000;
    final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
    TrackedOffer buy(){var o=new TrackedOffer(0,561,true,1000,10,start-600);o.setState("BUYING");return o;}
    MarketSnapshot market(long now,int low){return new MarketSnapshot(Map.of(),Map.of(561,new LatestPrice(1100,now,low,now)),Map.of(),Map.of(),Instant.ofEpochSecond(now));}
    List<Candle> bars(){return List.of(new Candle(start+300,1100,900,100,100),new Candle(start+600,1100,900,100,100));}
    Set<Integer> paused(BuyRecoveryJournal journal,String account,long now,int low,List<Candle> bars){return journal.pausedItems(account,market(now,low),i->bars,horizon,new TaxCalculator(),50,now);}
    @Test public void acceptedCancellationSurvivesRestartAndRepeatedPresentationWithoutResettingItsClock()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"recovery");var journal=new BuyRecoveryJournal(storage);String account=storage.accountId();
        Path finance=storage.accountDir().resolve("financial-evidence.bin");Files.write(finance,new byte[]{1,7,3});
        assertTrue(journal.record(account,buy(),"Stalled",start,480));
        byte[] original=Files.readAllBytes(storage.accountDir().resolve(BuyRecoveryJournal.FILE));
        assertTrue(journal.record(account,buy(),"Changed card",start+300,480));
        assertArrayEquals(original,Files.readAllBytes(storage.accountDir().resolve(BuyRecoveryJournal.FILE)));
        assertEquals(Set.of(561),paused(new BuyRecoveryJournal(storage),account,start+900,999,bars()));
        assertEquals(Set.of(),paused(new BuyRecoveryJournal(storage),account,start+900,900,bars()));
        assertArrayEquals(new byte[]{1,7,3},Files.readAllBytes(finance));
    }
    @Test public void clockOrNewPublicationAloneCannotReleaseAnIdenticalCancelledOrder()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"recovery");var journal=new BuyRecoveryJournal(storage);String account=storage.accountId();
        assertTrue(journal.record(account,buy(),"Stalled",start,480));
        assertEquals(Set.of(561),paused(journal,account,start+900,999,bars()));
        assertEquals(Set.of(561),paused(journal,account,start+900,900,List.of()));
        assertEquals(Set.of(561),paused(journal,account,start+900,900,List.of(bars().get(0),bars().get(1),bars().get(1))));
        assertEquals(Set.of(561),paused(journal,account,start-1,900,bars()));
    }
    @Test public void failedProspectiveWriteCreatesNoPromiseAndFailedReleaseRemainsPaused()throws Exception {
        var storage=spy(TestStorage.rootedAt(folder.newFolder().toPath(),"recovery"));var journal=new BuyRecoveryJournal(storage);String account=storage.accountId();
        doReturn(false).when(storage).writeDurably(any(),any());
        assertFalse(journal.record(account,buy(),"Stalled",start,480));
        assertEquals(Set.of(),paused(journal,account,start+900,900,bars()));
        doCallRealMethod().when(storage).writeDurably(any(),any());assertTrue(journal.record(account,buy(),"Stalled",start,480));
        doReturn(false).when(storage).writeDurably(any(),any());assertEquals(Set.of(561),paused(journal,account,start+900,900,bars()));
        assertEquals(Set.of(561),paused(new BuyRecoveryJournal(storage),account,start+900,999,bars()));
    }
    @Test public void externalReplacementAndForeignAccountWithdrawPermissionsWithoutOverwritingData()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"recovery");var journal=new BuyRecoveryJournal(storage);String account=storage.accountId();
        assertTrue(journal.record(account,buy(),"Stalled",start,480));
        Path path=storage.accountDir().resolve(BuyRecoveryJournal.FILE);Files.writeString(path,"unreadable original");
        assertNull(paused(journal,account,start+900,900,bars()));assertNull(paused(journal,"foreign",start+900,900,bars()));
        assertFalse(journal.record(account,buy(),"Retry",start+900,480));assertEquals("unreadable original",Files.readString(path));
    }
    @Test public void onlyTheAcceptedExactCancelledEpisodeBypassesManualAbandonmentCooldown()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"recovery");var journal=new BuyRecoveryJournal(storage);String account=storage.accountId();
        var offer=buy();assertTrue(journal.record(account,offer,"Stalled",start,480));assertFalse(journal.isGuidedCancellation(account,offer,start));
        offer.setState("CANCELLED_BUY");offer.setQuantityFilled(2);assertTrue(journal.isGuidedCancellation(account,offer,start));
        var other=new TrackedOffer(0,561,true,1000,10,start+1);other.setState("CANCELLED_BUY");assertFalse(journal.isGuidedCancellation(account,other,start+1));
    }
}
