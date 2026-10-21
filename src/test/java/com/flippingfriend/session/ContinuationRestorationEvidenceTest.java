package com.flippingfriend.session;

import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.*;
import net.runelite.api.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Cached offer snapshots are recovery data, not new observations of a retained workflow. */
public class ContinuationRestorationEvidenceTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    class Fixture {
        final boolean buying;
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"restoration");
        final OfferTracker tracker;
        final BuyRepriceIntent buys=new BuyRepriceIntent(storage);
        final SellRelistIntent sells=new SellRelistIntent(storage);
        Fixture(boolean buying)throws Exception {
            this.buying=buying;tracker=tracker(storage,buys,sells);
            tracker.onOfferChanged(0,raw(buying,20,buying?GrandExchangeOfferState.BUYING:GrandExchangeOfferState.SELLING));
            assertTrue(buying?buys.advise(tracker.getOffer(0),storage.accountId(),tracker,now()):
                sells.advise(tracker.getOffer(0),storage.accountId(),true,tracker));
        }
        void cancel(){tracker.onOfferChanged(0,raw(buying,40,buying?GrandExchangeOfferState.CANCELLED_BUY:GrandExchangeOfferState.CANCELLED_SELL));}
        void collect(){tracker.onOfferChanged(0,raw(buying,0,GrandExchangeOfferState.EMPTY));}
        OfferTracker restart()throws Exception {
            var restored=tracker(storage,new BuyRepriceIntent(storage),new SellRelistIntent(storage));restored.load();return restored;
        }
        int remainder(OfferTracker restored) {
            if(buying){var request=new BuyRepriceIntent(storage).collectedRequest(restored,storage.accountId(),now());return request==null?0:request.quantity;}
            var request=new SellRelistIntent(storage).collectedRequest(restored,storage.accountId());return request==null?0:request.getQuantity();
        }
    }
    @Test public void durableCollectionSurvivesStaleActiveCacheAndAnUnrelatedFirstCallback()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.tracker.save(); // Simulate the last successful offer snapshot.
            f.cancel();f.collect();assertEquals(60,f.remainder(f.tracker));
            // Collection committed, but the process stopped before offers.json could be saved.
            var restored=f.restart();
            restored.onOfferChanged(1,rawItem(554,true,0,GrandExchangeOfferState.BUYING));
            assertEquals("An untouched cached counter must not delete durable collection",60,f.remainder(restored));
            restored.onOfferChanged(0,raw(buying,0,GrandExchangeOfferState.EMPTY));
            assertEquals(60,f.remainder(restored));
        }
    }
    @Test public void freshEmptyCannotTurnAnUnobservedCachedCancellationIntoCollectionProof()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.cancel();f.tracker.save();
            var restored=f.restart();restored.onOfferChanged(0,raw(buying,0,GrandExchangeOfferState.EMPTY));
            assertEquals("EMPTY has no counters and cannot authenticate the cached predecessor",0,f.remainder(restored));
            assertTrue(Files.readString(f.storage.accountDir().resolve(buying?"buy-reprice-intent.json":"sell-relist-intent.json"))
                .contains(f.tracker.getSettledOffer(0)==null?f.tracker.getOffer(0).getEpisodeIdentity():f.tracker.getSettledOffer(0).getEpisodeIdentity()));
        }
    }
    @Test public void freshCounterRegressionStillWithdrawsTheCollectedReceipt()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.tracker.save();f.cancel();f.collect();
            var restored=f.restart();restored.onOfferChanged(0,raw(buying,30,
                buying?GrandExchangeOfferState.CANCELLED_BUY:GrandExchangeOfferState.CANCELLED_SELL));
            assertEquals(0,f.remainder(restored));
            restored.onOfferChanged(0,raw(buying,0,GrandExchangeOfferState.EMPTY));assertEquals(0,f.remainder(restored));
        }
    }
    @Test public void aCurrentCancelledPredecessorCanProveCollectionAfterRestart()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.cancel();f.tracker.save();var restored=f.restart();
            restored.onOfferChanged(0,raw(buying,50,buying?GrandExchangeOfferState.CANCELLED_BUY:GrandExchangeOfferState.CANCELLED_SELL));
            restored.onOfferChanged(0,raw(buying,0,GrandExchangeOfferState.EMPTY));assertEquals(50,f.remainder(restored));
        }
    }
    @Test public void aSameAccountLoginResetWithdrawsPreviouslyObservedCachedPredecessors()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.cancel();f.tracker.save();
            f.tracker.resetContinuationObservations();f.tracker.load(); // Same file: load intentionally returns early.
            f.collect();assertEquals(0,f.remainder(f.tracker));
        }
    }
    @Test public void anotherAccountsFreshBoardCannotDeleteTheNewProfilesCollectedReceipt()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.cancel();f.collect();f.tracker.save();
            var path=f.storage.accountDir().resolve(buying?"buy-reprice-intent.json":"sell-relist-intent.json");
            String committed=Files.readString(path);profile(f.storage,"other");
            var foreign=tracker(f.storage,new BuyRepriceIntent(f.storage),new SellRelistIntent(f.storage));
            foreign.onOfferChanged(0,raw(buying,0,buying?GrandExchangeOfferState.BUYING:GrandExchangeOfferState.SELLING));
            profile(f.storage,"restoration");
            assertEquals("Foreign board evidence must withhold guidance",0,f.remainder(foreign));
            assertEquals("Switching profiles before tracker.load must preserve the other account",committed,Files.readString(path));
            foreign.load();assertEquals(60,f.remainder(foreign));
        }
    }
    private static void profile(PluginStorage storage,String value)throws Exception {
        var field=PluginStorage.class.getDeclaredField("configManager");field.setAccessible(true);
        when(((net.runelite.client.config.ConfigManager)field.get(storage)).getRSProfileKey()).thenReturn(value);
    }
    private static long now(){return java.time.Instant.now().getEpochSecond();}
    private OfferTracker tracker(PluginStorage storage,BuyRepriceIntent buys,SellRelistIntent sells)throws Exception {
        var tracker=new OfferTracker(storage,new PositionBook(storage),mock(BuyLimitTracker.class),mock(TradeJournal.class),new TaxCalculator(),new TradePlans(storage));
        for(var pair:new Object[][]{{"buyReprice",buys},{"sellRelist",sells}}){var field=OfferTracker.class.getDeclaredField((String)pair[0]);field.setAccessible(true);field.set(tracker,pair[1]);}
        return tracker;
    }
    private static GrandExchangeOffer raw(boolean buying,int filled,GrandExchangeOfferState state){return rawItem(561,buying,filled,state);}
    private static GrandExchangeOffer rawItem(int item,boolean buying,int filled,GrandExchangeOfferState state) {
        var offer=mock(GrandExchangeOffer.class);when(offer.getState()).thenReturn(state);when(offer.getItemId()).thenReturn(item);
        when(offer.getPrice()).thenReturn(100L);when(offer.getTotalQuantity()).thenReturn(100);when(offer.getQuantitySold()).thenReturn(filled);when(offer.getSpent()).thenReturn(100L*filled);return offer;
    }
}
