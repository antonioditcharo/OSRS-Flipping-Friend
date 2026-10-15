package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.nio.file.Files;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceCanonicalOfferCacheTest
{
    @Test public void retainsAcknowledgedCanonicalIdentityAndLineage() throws Exception
    {
        Fixture fixture = fixture();
        fixture.ledger.recorded("plan-1", 561, true, 100, 500, 0, 30);
        long at=java.time.Instant.now().getEpochSecond();
        fixture.publish(offer(2,561,100,500,0,"BUYING",at),at);
        fixture.publish(offer(2,561,100,500,100,"BUYING",at),at+10);
        OfferEvent event = fixture.client.currentCanonicalOpenBuy();
        Assert.assertNotNull(event);
        Assert.assertEquals("2@"+at+":561", event.getOfferIdentity());
        Assert.assertEquals("plan-1", event.getRecommendationId());
        Assert.assertEquals(400, event.getTotalQuantity() - event.getFilledQuantity());
    }

    @Test public void deterministicallySelectsLowestSlotAndAdvancesIt() throws Exception
    {
        Fixture fixture = fixture();
        fixture.ledger.recorded("plan-1", 561, true, 100, 5, 0, 30);
        fixture.ledger.recorded("plan-2", 562, true, 200, 5, 0, 30);
        long at=java.time.Instant.now().getEpochSecond();
        fixture.publish(offer(5,561,100,5,0,"BUYING",at),at);
        fixture.publish(offer(1,562,200,5,0,"BUYING",at),at);
        Assert.assertEquals(1, fixture.client.currentCanonicalOpenBuy().getSlot());
        fixture.publish(offer(1,562,200,5,5,"BOUGHT",at),at+10);
        Assert.assertEquals(5, fixture.client.currentCanonicalOpenBuy().getSlot());
    }

    @Test public void clearAndTerminalEventsRemoveCachedAuthority() throws Exception
    {
        Fixture fixture = fixture();
        fixture.ledger.recorded("plan-1", 561, true, 100, 5, 0, 30);
        long at=java.time.Instant.now().getEpochSecond();
        fixture.publish(offer(1,561,100,5,0,"BUYING",at),at);
        Assert.assertNotNull(fixture.client.currentCanonicalOpenBuy());
        fixture.client.publishOfferCleared(1);
        Assert.assertNull(fixture.client.currentCanonicalOpenBuy());
    }

    @Test public void unattributedOfferFailsClosedForMaintenance() throws Exception
    {
        Fixture fixture = fixture();
        fixture.client.publishOffer(offer(1, 561, 100, 5, 0, "BUYING", 10));
        Assert.assertNull(fixture.client.currentCanonicalOpenBuy());
    }

    private static Fixture fixture() throws Exception
    {
        PluginStorage storage = org.mockito.Mockito.mock(PluginStorage.class);
        java.nio.file.Path account = Files.createTempDirectory("canonical-offer-cache");
        Gson gson = new Gson();
        org.mockito.Mockito.when(storage.hasAccount()).thenReturn(true);
        org.mockito.Mockito.when(storage.accountId()).thenReturn(
            com.flippingfriend.core.AccountIdentity.fromProfileKey("test"));
        org.mockito.Mockito.when(storage.accountDir()).thenReturn(account);
        org.mockito.Mockito.when(storage.accountDir(org.mockito.Mockito.anyString())).thenReturn(account);
        org.mockito.Mockito.when(storage.gson()).thenReturn(gson);
        SuggestionLedger ledger = new SuggestionLedger();
        CompanionClient client = new CompanionClient(storage, gson, ledger, new OfferEventOutbox(storage));
        client.resumeOfferReplay();
        return new Fixture(client, ledger);
    }

    private static TrackedOffer offer(int slot, int item, int price, int total, int filled,
        String state, long firstSeen)
    {
        TrackedOffer offer = new TrackedOffer(slot, item, true, price, total, firstSeen);
        offer.setItemName(item == 561 ? "Nature rune" : "Chaos rune");
        offer.setQuantityFilled(filled);
        offer.setState(state);
        return offer;
    }

    private static final class Fixture
    {
        final CompanionClient client;
        final SuggestionLedger ledger;
        Fixture(CompanionClient client, SuggestionLedger ledger)
        {
            this.client = client;
            this.ledger = ledger;
        }
        void publish(TrackedOffer offer,long at)throws Exception {
            var method=CompanionClient.class.getDeclaredMethod("publishOffer",TrackedOffer.class,String.class,boolean.class,long.class,boolean.class);
            method.setAccessible(true);
            method.invoke(client,offer,com.flippingfriend.core.AccountIdentity.fromProfileKey("test"),true,at,false);
        }
    }
}
