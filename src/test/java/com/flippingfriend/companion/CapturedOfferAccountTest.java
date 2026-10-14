package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** A queued observation is scoped before delivery; only disposable outbox files are used. */
public class CapturedOfferAccountTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void delayedOfferAndEmptyCannotBeRedirectedToAnotherAccount() throws Exception
    {
        Fixture f = fixture();
        f.account.set(AccountIdentity.fromProfileKey("other"));
        f.client.publishOffer(offer(), f.original);
        f.client.publishOfferCleared(1, f.original);
        assertTrue(f.outbox.pending().isEmpty());
    }

    @Test public void switchDuringEventConstructionRefusesTheWrite() throws Exception
    {
        Fixture f = fixture();
        TrackedOffer offer = spy(offer());
        doAnswer(call -> { f.account.set(AccountIdentity.fromProfileKey("other")); return "Abyssal whip"; })
            .when(offer).getItemName();
        f.client.publishOffer(offer, f.original);
        assertTrue(f.outbox.pending().isEmpty());
    }

    @Test public void matchingAccountRetainsIdentityAndCounters() throws Exception
    {
        Fixture f = fixture(); f.client.publishOffer(offer(), f.original);
        assertEquals(1, f.outbox.pending().size());
        com.flippingfriend.core.OfferEvent event = f.outbox.pending().get(0);
        assertEquals(f.original, event.getAccountId()); assertNotNull(event.getEventId());
        assertEquals("1@100:4151", event.getOfferIdentity()); assertEquals(2, event.getFilledQuantity());
        assertEquals(2000, event.getSpent()); assertEquals(1, event.getSequence());
    }

    private Fixture fixture() throws Exception
    {
        PluginStorage storage = mock(PluginStorage.class); Gson gson = new Gson();
        String original = AccountIdentity.fromProfileKey("original");
        AtomicReference<String> account = new AtomicReference<>(original);
        Path directory = temporary.newFolder().toPath();
        when(storage.accountId()).thenAnswer(call -> account.get()); when(storage.hasAccount()).thenReturn(true);
        when(storage.accountDir(anyString())).thenReturn(directory); when(storage.gson()).thenReturn(gson);
        OfferEventOutbox outbox = new OfferEventOutbox(storage);
        CompanionClient client = new CompanionClient(storage, gson, new SuggestionLedger(), outbox);
        // Replay remains paused: no HTTP is sent by this fixture.
        return new Fixture(original, account, outbox, client);
    }
    private static TrackedOffer offer()
    {
        TrackedOffer offer = new TrackedOffer(1, 4151, true, 1000, 10, 100);
        offer.setState("BUYING"); offer.setQuantityFilled(2); offer.setSpent(2000); return offer;
    }
    private static final class Fixture
    {
        final String original; final AtomicReference<String> account;
        final OfferEventOutbox outbox; final CompanionClient client;
        Fixture(String original, AtomicReference<String> account, OfferEventOutbox outbox, CompanionClient client)
        { this.original = original; this.account = account; this.outbox = outbox; this.client = client; }
    }
}
