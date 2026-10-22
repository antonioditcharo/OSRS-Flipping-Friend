package com.flippingfriend.session;

import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.*;
import net.runelite.api.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** A failed presentation commit must not become a new promise during observation retries. */
public class ContinuationAcceptanceTransactionTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    class Fixture {
        final boolean buying;
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"acceptance");
        final BuyRepriceIntent buys=new BuyRepriceIntent(storage);
        final SellRelistIntent sells=new SellRelistIntent(storage);
        final OfferTracker tracker=new OfferTracker(storage,new PositionBook(storage),mock(BuyLimitTracker.class),
            mock(TradeJournal.class),new TaxCalculator(),new TradePlans(storage));
        final Path path,blocked;
        Fixture(boolean buying)throws Exception {
            this.buying=buying;
            for(var pair:new Object[][]{{"buyReprice",buys},{"sellRelist",sells}}){var field=OfferTracker.class.getDeclaredField((String)pair[0]);field.setAccessible(true);field.set(tracker,pair[1]);}
            path=storage.accountDir().resolve(buying?"buy-reprice-intent.json":"sell-relist-intent.json");blocked=path.resolveSibling(path.getFileName()+".tmp");
            tracker.onOfferChanged(0,raw(buying,20,buying?GrandExchangeOfferState.BUYING:GrandExchangeOfferState.SELLING));
        }
        boolean present(){return buying?buys.advise(tracker.getOffer(0),storage.accountId(),tracker,now()):sells.advise(tracker.getOffer(0),storage.accountId(),true,tracker,"unshown-source");}
        void unrelatedCallback(){tracker.onOfferChanged(1,rawItem(554,true,0,GrandExchangeOfferState.BUYING));}
        void cancelCollect(){tracker.onOfferChanged(0,raw(buying,40,buying?GrandExchangeOfferState.CANCELLED_BUY:GrandExchangeOfferState.CANCELLED_SELL));tracker.onOfferChanged(0,raw(buying,0,GrandExchangeOfferState.EMPTY));}
    }
    @Test public void failedInitialBuyAcceptanceCannotSelfCommitOnAnUnrelatedCallback()throws Exception {
        var f=new Fixture(true);Files.createDirectory(f.blocked);assertFalse(f.present());assertFalse(Files.exists(f.path));
        Files.delete(f.blocked);f.unrelatedCallback();
        var cancelled=f.tracker.getOffer(0).copyObservation();cancelled.setState("CANCELLED_BUY");cancelled.setQuantityFilled(40);
        assertFalse("The withdrawn initial promise was never accepted again",f.buys.isGuidedCancellation(cancelled,f.storage.accountId(),now()));
        f.cancelCollect();assertNull(f.buys.collectedRequest(f.tracker,f.storage.accountId(),now()));
    }
    @Test public void failedInitialSellAcceptanceCannotProduceARelistAfterCallbackRetryAndRestart()throws Exception {
        var f=new Fixture(false);Files.createDirectory(f.blocked);assertFalse(f.present());assertFalse(Files.exists(f.path));
        Files.delete(f.blocked);f.unrelatedCallback();f.cancelCollect();
        assertNull("A never-accepted sale modification cannot create a relist promise",new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId()));
    }
    @Test public void aFailedSourceUpgradeCannotPiggybackOnLaterObservedProgressOfAnAcceptedHint()throws Exception {
        var f=new Fixture(false);assertTrue(f.sells.advise(f.tracker.getOffer(0),f.storage.accountId(),false,f.tracker));
        String original=Files.readString(f.path);Files.createDirectory(f.blocked);assertFalse(f.present());assertEquals(original,Files.readString(f.path));
        Files.delete(f.blocked);
        f.tracker.onOfferChanged(0,raw(false,30,GrandExchangeOfferState.SELLING));f.cancelCollect();
        var request=new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId());
        assertNotNull("Previously accepted workflow and later observed progress must survive",request);assertEquals(60,request.getQuantity());
        assertNull("A failed presentation cannot supply new target-sacrifice authority",request.getSaleRepriceSource());
        assertFalse("Only the prior accepted semantic context survives the failed upgrade",request.requiresManagedCost());
    }
    @Test public void renewedForegroundAcceptanceCanCommitThePreviouslyFailedInitialPromise()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);Files.createDirectory(f.blocked);assertFalse(f.present());
            Files.delete(f.blocked);assertTrue("A new explicit acceptance may retry the prospective promise",f.present());f.cancelCollect();
            if(buying)assertEquals(60,new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now()).quantity);
            else assertEquals(60,new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId()).getQuantity());
        }
    }
    @Test public void accountSwitchAndObservationRetryCannotPromoteAFailedInitialPromise()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);Files.createDirectory(f.blocked);assertFalse(f.present());
            profile(f.storage,"other");
            if(buying)f.buys.observe(f.tracker,f.storage.accountId(),now());else f.sells.observe(f.tracker,f.storage.accountId());
            Files.delete(f.blocked);profile(f.storage,"acceptance");f.unrelatedCallback();f.cancelCollect();
            if(buying)assertNull(new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now()));
            else assertNull(new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId()));
        }
    }
    @Test public void failedNewHintDoesNotDiscardTheOlderAcceptedDirtyCollection()throws Exception {
        var f=new Fixture(true);assertTrue(f.present());Files.createDirectory(f.blocked);f.cancelCollect();
        assertNull("The observed collection must wait for its own commit",f.buys.collectedRequest(f.tracker,f.storage.accountId(),now()));
        f.tracker.onOfferChanged(1,rawItem(562,true,0,GrandExchangeOfferState.BUYING));
        assertFalse(f.buys.advise(f.tracker.getOffer(1),f.storage.accountId(),f.tracker,now()));Files.delete(f.blocked);
        f.tracker.onOfferChanged(2,rawItem(554,true,0,GrandExchangeOfferState.BUYING));
        var requests=new BuyRepriceIntent(f.storage).collectedRequests(f.tracker,f.storage.accountId(),now());
        assertEquals(1,requests.size());assertEquals(561,requests.get(0).itemId);assertEquals(60,requests.get(0).quantity);
        assertFalse("The failed second hint must not join the accepted collection commit",f.buys.isPending(562,f.tracker,f.storage.accountId(),now()));
    }
    @Test public void changedFileRefusesAnUpgradeWithoutChangingItsAcceptedContext()throws Exception {
        var f=new Fixture(false);assertTrue(f.sells.advise(f.tracker.getOffer(0),f.storage.accountId(),false,f.tracker));
        String changed=Files.readString(f.path)+"\n";Files.writeString(f.path,changed);
        assertFalse(f.present());f.cancelCollect();assertEquals(changed,Files.readString(f.path));
        assertNull("The loaded book must retain its file conflict",f.sells.collectedRequest(f.tracker,f.storage.accountId()));
        var restored=new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId());
        assertNotNull(restored);assertEquals(60,restored.getQuantity());assertNull(restored.getSaleRepriceSource());assertFalse(restored.requiresManagedCost());
    }
    @Test public void renewedSourceAcceptanceUsesTheNewAcceptanceInsteadOfTheFailedCandidate()throws Exception {
        var f=new Fixture(false);assertTrue(f.sells.advise(f.tracker.getOffer(0),f.storage.accountId(),false,f.tracker));
        Files.createDirectory(f.blocked);assertFalse(f.present());Files.delete(f.blocked);
        assertTrue(f.sells.advise(f.tracker.getOffer(0),f.storage.accountId(),true,f.tracker,"accepted-later"));f.cancelCollect();
        var restored=new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId());
        assertNotNull(restored);assertEquals(60,restored.getQuantity());assertEquals("accepted-later",restored.getSaleRepriceSource());assertTrue(restored.requiresManagedCost());
    }
    private static void profile(PluginStorage storage,String value)throws Exception {
        var field=PluginStorage.class.getDeclaredField("configManager");field.setAccessible(true);
        when(((net.runelite.client.config.ConfigManager)field.get(storage)).getRSProfileKey()).thenReturn(value);
    }
    private static long now(){return java.time.Instant.now().getEpochSecond();}
    private static GrandExchangeOffer raw(boolean buying,int filled,GrandExchangeOfferState state){return rawItem(561,buying,filled,state);}
    private static GrandExchangeOffer rawItem(int item,boolean buying,int filled,GrandExchangeOfferState state) {
        var offer=mock(GrandExchangeOffer.class);when(offer.getState()).thenReturn(state);when(offer.getItemId()).thenReturn(item);
        when(offer.getPrice()).thenReturn(100L);when(offer.getTotalQuantity()).thenReturn(100);when(offer.getQuantitySold()).thenReturn(filled);when(offer.getSpent()).thenReturn(100L*filled);return offer;
    }
}
