package com.flippingfriend.session;
import com.flippingfriend.data.TestStorage;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class SellRelistIntentTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private TrackedOffer offer(String state, int filled, boolean collected, long firstSeen) {
        TrackedOffer offer=new TrackedOffer(0,561,false,120,100,firstSeen);
        offer.setState(state); offer.setQuantityFilled(filled); offer.setCollected(collected); return offer;
    }
    @Test public void relistWaitsForCancellationAndCollectionAndSurvivesRestart() throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"one");
        var intent=new SellRelistIntent(storage); var tracker=mock(OfferTracker.class);
        TrackedOffer live=offer("SELLING",20,false,1000); intent.advise(live);
        when(tracker.getOffers()).thenReturn(List.of(live)); when(tracker.getOffer(0)).thenReturn(live);
        assertNull(intent.collectedRemainder(tracker));
        live.setState("CANCELLED_SELL"); live.setQuantityFilled(40);
        assertNull(intent.collectedRemainder(tracker));
        when(tracker.getOffers()).thenReturn(List.of()); when(tracker.getOffer(0)).thenReturn(null);
        live.setCollected(true); when(tracker.getSettledOffer(0)).thenReturn(live);
        intent=new SellRelistIntent(storage);
        assertArrayEquals(new int[]{561,60},intent.collectedRemainder(tracker));
    }
    @Test public void actualReplacementOrFullSaleEndsIntent() throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"one");
        var intent=new SellRelistIntent(storage); var tracker=mock(OfferTracker.class);
        TrackedOffer live=offer("SELLING",0,false,1000); intent.advise(live);
        when(tracker.getOffers()).thenReturn(List.of(offer("SELLING",0,false,2000)));
        assertNull(intent.collectedRemainder(tracker));
        when(tracker.getOffers()).thenReturn(List.of());
        when(tracker.getSettledOffer(0)).thenReturn(offer("CANCELLED_SELL",0,true,1000));
        assertNull(new SellRelistIntent(storage).collectedRemainder(tracker));
        intent.advise(live); live.setState("SOLD"); live.setQuantityFilled(100);
        when(tracker.getOffers()).thenReturn(List.of(live)); when(tracker.getOffer(0)).thenReturn(live);
        assertNull(intent.collectedRemainder(tracker));
    }
    @Test public void anotherAccountAndUnrelatedCollectedOfferCannotResumeSale() throws Exception {
        var root=folder.newFolder().toPath(); var storage=TestStorage.rootedAt(root,"one");
        var intent=new SellRelistIntent(storage); intent.advise(offer("SELLING",0,false,1000));
        var tracker=mock(OfferTracker.class); when(tracker.getOffers()).thenReturn(List.of());
        when(tracker.getSettledOffer(0)).thenReturn(offer("CANCELLED_SELL",0,true,999));
        assertNull(intent.collectedRemainder(tracker));
        assertNull(new SellRelistIntent(TestStorage.rootedAt(root,"two")).collectedRemainder(tracker));
    }
}
