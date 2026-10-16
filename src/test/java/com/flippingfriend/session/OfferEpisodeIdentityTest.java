package com.flippingfriend.session;

import com.flippingfriend.companion.*;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import com.google.gson.Gson;
import java.nio.file.Path;
import net.runelite.api.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real local tracker, persisted copies and paused outbox; no game or HTTP sender. */
public class OfferEpisodeIdentityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    class Fixture {
        final PluginStorage storage;final OfferTracker tracker;final OfferEventOutbox outbox;final CompanionClient companion;
        Fixture(Path root) {
            storage=TestStorage.rootedAt(root,"episode");
            var positions=new PositionBook(storage);
            tracker=new OfferTracker(storage,positions,new BuyLimitTracker(storage),
                new TradeJournal(storage,null,new AccountMonitor(null,null,null)),new TaxCalculator(),new TradePlans(storage));
            outbox=new OfferEventOutbox(storage);companion=new CompanionClient(storage,gson,new SuggestionLedger(),outbox);
        }
        OfferEvent publish()throws Exception {
            var copy=gson.fromJson(gson.toJson(tracker.getOffer(0).copyObservation()),TrackedOffer.class);
            companion.publishObservedOffer(copy,storage.accountId());
            var pending=outbox.pending();return pending.get(pending.size()-1);
        }
    }
    GrandExchangeOffer offer(GrandExchangeOfferState state,int filled) {
        var offer=mock(GrandExchangeOffer.class);when(offer.getState()).thenReturn(state);
        when(offer.getItemId()).thenReturn(561);when(offer.getPrice()).thenReturn(1000L);
        when(offer.getTotalQuantity()).thenReturn(9);when(offer.getQuantitySold()).thenReturn(filled);
        when(offer.getSpent()).thenReturn(filled*1000L);return offer;
    }
    @Test public void newZeroFillOrderAfterZeroCancelGetsADifferentPersistentIdentity()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());
        f.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.BUYING,0));var first=f.publish();
        f.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.CANCELLED_BUY,0));
        f.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.EMPTY,0));
        f.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.BUYING,0));var second=f.publish();
        assertNotEquals(first.getOfferIdentity(),second.getOfferIdentity());
    }
    @Test public void newEpisodesHaveExplicitIdsThatSurviveCopyDiskAndTerminalReplay()throws Exception {
        Path root=folder.newFolder().toPath();var f=new Fixture(root);
        f.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.BUYING,0));var opened=f.publish();
        assertTrue("new identities cannot collide on slot/second/item",opened.getOfferIdentity().startsWith("ge-"));
        f.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.BOUGHT,9));f.tracker.save();
        var restarted=new Fixture(root);restarted.tracker.load();
        assertEquals(opened.getOfferIdentity(),restarted.publish().getOfferIdentity());
        restarted.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.EMPTY,0));restarted.tracker.save();
        var collected=new Fixture(root);collected.tracker.load();
        collected.tracker.onOfferChanged(0,offer(GrandExchangeOfferState.BOUGHT,9));
        assertEquals("late replay retains the original identity",opened.getOfferIdentity(),collected.publish().getOfferIdentity());
    }
    @Test public void legacySavedOfferRetainsItsShippedIdentity()throws Exception {
        var f=new Fixture(folder.newFolder().toPath());
        var legacy=gson.fromJson("{\"slot\":0,\"itemId\":561,\"buying\":true,\"price\":1000,\"totalQuantity\":9,\"state\":\"BUYING\",\"firstSeen\":123}",TrackedOffer.class);
        f.companion.publishObservedOffer(legacy,f.storage.accountId());
        assertEquals("0@123:561",f.outbox.pending().get(0).getOfferIdentity());
    }
}
