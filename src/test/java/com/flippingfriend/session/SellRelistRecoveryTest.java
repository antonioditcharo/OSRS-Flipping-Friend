package com.flippingfriend.session;

import com.flippingfriend.data.TestStorage;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Guidance recovery only: these observations never create ownership or book executions. */
public class SellRelistRecoveryTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private TrackedOffer sale(int slot, int price, long firstSeen, int filled, String state, boolean collected) {
        var offer = new TrackedOffer(slot,561,false,price,100,firstSeen);
        offer.startEpisodeIdentity(); offer.setQuantityFilled(filled); offer.setState(state);
        offer.setCollected(collected); return offer;
    }
    private void advise(SellRelistIntent intent, TrackedOffer original, OfferTracker tracker,
            com.flippingfriend.data.PluginStorage storage) throws Exception {
        intent.advise(original,storage.accountId(),true,tracker);
    }
    @Test public void collectedProofSurvivesSlotReuseAndTheTrackersRetentionWindow() throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"relist");
        var intent=new SellRelistIntent(storage); var tracker=mock(OfferTracker.class);
        var original=sale(0,120,1000,20,"SELLING",false);
        when(tracker.getOffers()).thenReturn(List.of(original));when(tracker.getOffer(0)).thenReturn(original);
        advise(intent,original,tracker,storage);
        original.setState("CANCELLED_SELL");original.setQuantityFilled(40);original.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);
        when(tracker.getSettledOffer(0)).thenReturn(original);
        assertArrayEquals(new int[]{561,60},intent.collectedRemainder(tracker));
        // This temporary tracker record is removed when the slot is reused or its 12h retention expires.
        when(tracker.getSettledOffer(0)).thenReturn(null);
        var restarted=new SellRelistIntent(storage);
        assertArrayEquals(new int[]{561,60},restarted.collectedRemainder(tracker));
        assertTrue(restarted.requiresManagedCost(storage.accountId()));
    }
    @Test public void anAlreadyWorkingSecondListingIsNotTheReplacement() throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"relist");
        var intent=new SellRelistIntent(storage);var tracker=mock(OfferTracker.class);
        var original=sale(0,120,1000,20,"SELLING",false);
        var existing=sale(1,125,1100,0,"SELLING",false);
        when(tracker.getOffers()).thenReturn(List.of(original,existing));
        when(tracker.getOffer(0)).thenReturn(original);
        advise(intent,original,tracker,storage);
        assertNull(intent.collectedRemainder(tracker));
        original.setState("CANCELLED_SELL");original.setQuantityFilled(40);original.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of(existing));when(tracker.getOffer(0)).thenReturn(null);
        when(tracker.getSettledOffer(0)).thenReturn(original);
        assertArrayEquals(new int[]{561,60},new SellRelistIntent(storage).collectedRemainder(tracker));
    }
    @Test public void aLaterSameSecondEpisodeEndsTheIntentAndStaleAdviceCannotRecreateIt() throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"relist");
        var intent=new SellRelistIntent(storage);var tracker=mock(OfferTracker.class);
        var original=sale(0,120,1000,20,"SELLING",false);
        when(tracker.getOffers()).thenReturn(List.of(original));when(tracker.getOffer(0)).thenReturn(original);
        advise(intent,original,tracker,storage);
        var replacement=sale(0,120,1000,0,"SELLING",false);
        when(tracker.getOffers()).thenReturn(List.of(replacement));when(tracker.getOffer(0)).thenReturn(replacement);
        intent.observe(tracker,storage.accountId());
        // The delayed worker still holds the old instruction. It cannot recreate that intent.
        advise(intent,original,tracker,storage);
        original.setState("CANCELLED_SELL");original.setQuantityFilled(40);original.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);
        when(tracker.getSettledOffer(0)).thenReturn(original);
        assertNull(new SellRelistIntent(storage).collectedRemainder(tracker));
    }
    @Test public void cancellationWithoutCollectionAndCounterRegressionNeverBecomeDurableProof() throws Exception {
        for (boolean regressed : new boolean[]{false,true}) {
            var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"relist");
            var intent=new SellRelistIntent(storage);var tracker=mock(OfferTracker.class);
            var original=sale(0,120,1000,20,"SELLING",false);
            when(tracker.getOffers()).thenReturn(List.of(original));when(tracker.getOffer(0)).thenReturn(original);
            advise(intent,original,tracker,storage);
            original.setState("CANCELLED_SELL");original.setQuantityFilled(regressed?19:40);
            original.setCollected(regressed);
            when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);
            when(tracker.getSettledOffer(0)).thenReturn(original);
            assertNull(intent.collectedRemainder(tracker));
            when(tracker.getSettledOffer(0)).thenReturn(null);
            assertNull(new SellRelistIntent(storage).collectedRemainder(tracker));
        }
    }
    @Test public void aLaterContradictoryCounterWithdrawsTheReceiptAndManagedHintCannotChangeInFlight() throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"relist");
        var intent=new SellRelistIntent(storage);var tracker=mock(OfferTracker.class);
        var original=sale(0,120,1000,20,"SELLING",false);
        when(tracker.getOffers()).thenReturn(List.of(original));when(tracker.getOffer(0)).thenReturn(original);
        advise(intent,original,tracker,storage);
        original.setState("CANCELLED_SELL");original.setQuantityFilled(40);original.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);
        when(tracker.getSettledOffer(0)).thenReturn(original);
        var request=intent.collectedRequest(tracker,storage.accountId());
        assertNotNull(request);assertTrue(request.requiresManagedCost());assertEquals(60,request.getQuantity());
        original.setQuantityFilled(30); // Still above advice's 20, but contradicts the retained 40.
        intent.observe(tracker,storage.accountId());
        when(tracker.getSettledOffer(0)).thenReturn(null);
        assertNull(new SellRelistIntent(storage).collectedRequest(tracker,storage.accountId()));
        assertTrue("An in-flight managed hint cannot become personal after intent withdrawal",request.requiresManagedCost());
    }
    @Test public void theActualOfferCallbackCheckpointsCollectionBeforeAnyWorkerOrSlotReuse() throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"relist");
        var intent=new SellRelistIntent(storage);
        var tracker=new OfferTracker(storage,new PositionBook(storage),mock(BuyLimitTracker.class),
            mock(TradeJournal.class),new com.flippingfriend.model.TaxCalculator(),new TradePlans(storage));
        var field=OfferTracker.class.getDeclaredField("sellRelist");field.setAccessible(true);field.set(tracker,intent);
        tracker.onOfferChanged(0,raw(net.runelite.api.GrandExchangeOfferState.SELLING,561,0));
        advise(intent,tracker.getOffer(0),tracker,storage);
        tracker.onOfferChanged(0,raw(net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL,561,40));
        tracker.onOfferChanged(0,raw(net.runelite.api.GrandExchangeOfferState.EMPTY,561,0));
        // No engine refresh/read of collectedRemainder took place before this other item occupied the slot.
        tracker.onOfferChanged(0,raw(net.runelite.api.GrandExchangeOfferState.BUYING,554,0));
        tracker.save();
        var restartedTracker=new OfferTracker(storage,mock(PositionBook.class),mock(BuyLimitTracker.class),
            mock(TradeJournal.class),new com.flippingfriend.model.TaxCalculator(),new TradePlans(storage));
        restartedTracker.load();
        assertNull(restartedTracker.getSettledOffer(0));
        assertArrayEquals(new int[]{561,60},new SellRelistIntent(storage).collectedRemainder(restartedTracker));
    }
    private net.runelite.api.GrandExchangeOffer raw(net.runelite.api.GrandExchangeOfferState state,int item,int filled) {
        var offer=mock(net.runelite.api.GrandExchangeOffer.class);
        when(offer.getState()).thenReturn(state);when(offer.getItemId()).thenReturn(item);
        when(offer.getPrice()).thenReturn(120L);when(offer.getTotalQuantity()).thenReturn(100);
        when(offer.getQuantitySold()).thenReturn(filled);when(offer.getSpent()).thenReturn(120L*filled);
        return offer;
    }
}
