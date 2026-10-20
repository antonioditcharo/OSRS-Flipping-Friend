package com.flippingfriend.session;

import com.flippingfriend.data.TestStorage;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContinuationQueueRecoveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=java.time.Instant.now().getEpochSecond();
    TrackedOffer offer(boolean buy,int slot,int item,int filled) {
        var offer=new TrackedOffer(slot,item,buy,100,100,now-600);
        offer.startEpisodeIdentity();offer.setQuantityFilled(filled);offer.setState(buy?"BUYING":"SELLING");return offer;
    }
    @Test public void multipleAcceptedBuyAndSellContinuationsSurviveCollectionSlotLossAndRestartIndependently()throws Exception {
        for(boolean buy:new boolean[]{true,false}) {
            var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"queue");var tracker=mock(OfferTracker.class);
            var first=offer(buy,0,561,20);var second=offer(buy,1,562,30);
            when(tracker.getOffers()).thenReturn(List.of(first,second));when(tracker.getOffer(0)).thenReturn(first);when(tracker.getOffer(1)).thenReturn(second);
            if(buy) {
                var book=new BuyRepriceIntent(storage);book.advise(first,storage.accountId(),tracker,now);book.advise(second,storage.accountId(),tracker,now);
                assertTrue(book.isPending(561,tracker,storage.accountId(),now));assertTrue(book.isPending(562,tracker,storage.accountId(),now));
            }else {
                var book=new SellRelistIntent(storage);book.advise(first,storage.accountId(),true,tracker);book.advise(second,storage.accountId(),false,tracker);
            }
            first.setState(buy?"CANCELLED_BUY":"CANCELLED_SELL");first.setQuantityFilled(40);first.setCollected(true);
            second.setState(buy?"CANCELLED_BUY":"CANCELLED_SELL");second.setQuantityFilled(50);second.setCollected(true);
            when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);when(tracker.getOffer(1)).thenReturn(null);
            when(tracker.getSettledOffer(0)).thenReturn(first);when(tracker.getSettledOffer(1)).thenReturn(second);
            if(buy)new BuyRepriceIntent(storage).observe(tracker,storage.accountId(),now);
            else new SellRelistIntent(storage).observe(tracker,storage.accountId());
            when(tracker.getSettledOffer(0)).thenReturn(null);when(tracker.getSettledOffer(1)).thenReturn(null);
            if(buy) {
                var book=new BuyRepriceIntent(storage);var requests=book.collectedRequests(tracker,storage.accountId(),now+2*86400);
                assertEquals(2,requests.size());assertEquals(561,requests.get(0).itemId);assertEquals(60,requests.get(0).quantity);
                assertEquals(562,requests.get(1).itemId);assertEquals(50,requests.get(1).quantity);
                book.clearItem(storage.accountId(),561);
                assertEquals(562,new BuyRepriceIntent(storage).collectedRequests(tracker,storage.accountId(),now+2*86400).get(0).itemId);
            }else {
                var requests=new SellRelistIntent(storage).collectedRequests(tracker,storage.accountId());
                assertEquals(2,requests.size());assertEquals(60,requests.get(0).getQuantity());assertTrue(requests.get(0).requiresManagedCost());
                assertEquals(50,requests.get(1).getQuantity());assertFalse(requests.get(1).requiresManagedCost());
                var replacement=offer(false,2,561,0);when(tracker.getOffers()).thenReturn(List.of(replacement));
                requests=new SellRelistIntent(storage).collectedRequests(tracker,storage.accountId());
                assertEquals(1,requests.size());assertEquals(562,requests.get(0).getItemId());
            }
        }
    }
    @Test public void unreadableForeignAndFutureDocumentsAreRetainedInsteadOfBeingOverwrittenAsEmpty()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"queue");var tracker=mock(OfferTracker.class);
        var original=offer(true,0,561,20);when(tracker.getOffers()).thenReturn(List.of(original));when(tracker.getOffer(0)).thenReturn(original);
        var path=storage.accountDir().resolve("buy-reprice-intent.json");
        for(String invalid:List.of("{broken", "{\"formatVersion\":9,\"accountId\":\"foreign\",\"continuations\":[]}",
                "{\"formatVersion\":2,\"accountId\":\"foreign\",\"continuations\":[]}")) {
            Files.writeString(path,invalid);var book=new BuyRepriceIntent(storage);book.advise(original,storage.accountId(),tracker,now);
            assertTrue(book.collectedRequests(tracker,storage.accountId(),now).isEmpty());assertEquals(invalid,Files.readString(path));
        }
    }
    @Test public void aRetainedLegacyBuyReceiptCannotDisappearBecauseItsOldThirtyMinuteAdviceClockExpired()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"queue");var tracker=mock(OfferTracker.class);
        when(tracker.getOffers()).thenReturn(List.of());
        String json="{\"item\":561,\"slot\":0,\"quantity\":80,\"price\":100,\"total\":100,\"filled\":20,\"firstSeen\":"+(now-7200)
            +",\"createdAt\":"+(now-3600)+",\"expiresAt\":"+(now-1800)
            +",\"episode\":\"retained-original\",\"buying\":true,\"collectionVerified\":true,\"collectedRemaining\":60,\"collectedFilled\":40,\"maximumFilled\":40,\"existingSales\":[]}";
        var path=storage.accountDir().resolve("buy-reprice-intent.json");Files.writeString(path,json);
        var requests=new BuyRepriceIntent(storage).collectedRequests(tracker,storage.accountId(),now);
        assertEquals(1,requests.size());assertEquals(60,requests.get(0).quantity);
        assertEquals(60,new BuyRepriceIntent(storage).collectedRequests(tracker,storage.accountId(),now+86400).get(0).quantity);
        assertTrue(Files.readString(path).contains("retained-original"));assertTrue(Files.readString(path).contains("\"expiresAt\":0"));
    }
}
