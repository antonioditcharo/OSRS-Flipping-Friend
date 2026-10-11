package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import static org.junit.Assert.*;

public class AccountClientIsolationTest
{
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void switchingAccountsClearsAdviceAndRejectsLateAcknowledgement() throws Exception
    {
        PluginStorage storage = Mockito.mock(PluginStorage.class);
        Gson gson = new Gson();
        String one = AccountIdentity.fromProfileKey("one"), two = AccountIdentity.fromProfileKey("two");
        Path first = folder.newFolder("first").toPath(), second = folder.newFolder("second").toPath();
        Mockito.when(storage.accountId()).thenReturn(one);
        Mockito.when(storage.hasAccount()).thenReturn(true);
        Mockito.when(storage.accountDir()).thenReturn(first);
        Mockito.when(storage.accountDir(Mockito.anyString())).thenReturn(first);
        Mockito.when(storage.gson()).thenReturn(gson);
        SuggestionLedger ledger = new SuggestionLedger();
        OfferEventOutbox outbox = new OfferEventOutbox(storage);
        CompanionClient client = new CompanionClient(storage, gson, ledger, outbox);
        client.resumeOfferReplay();
        ledger.recorded("plan-one", 561, true, 100, 10, 0, 30);
        TrackedOffer offer = new TrackedOffer(0, 561, true, 100, 10, Instant.now().getEpochSecond());
        offer.setState("BUYING"); client.publishOffer(offer);
        OfferEvent event = outbox.pending().get(0);
        assertEquals(one, event.getAccountId());
        assertNotNull(client.currentCanonicalOpenBuy());
        client.acceptPlan(new PortfolioPlan("p", 0, Long.MAX_VALUE, "READY", "ok", 0, Collections.emptyList()));
        assertTrue(client.hasFreshPlan());

        Mockito.when(storage.accountId()).thenReturn(two);
        Mockito.when(storage.accountDir()).thenReturn(second);
        Mockito.when(storage.accountDir(Mockito.anyString())).thenReturn(second);
        assertFalse(client.hasFreshPlan());
        assertNull(client.lastPlan());
        assertNull(client.currentCanonicalOpenBuy());
        assertNull(ledger.attribute(561, true, Instant.now().getEpochSecond()));
        String acknowledgement = "{\"accepted\":true,\"eventId\":\"" + event.getEventId()
            + "\",\"sessionId\":\"" + event.getSessionId() + "\",\"sequence\":" + event.getSequence() + "}";
        assertFalse(client.consumeAcknowledgement(event, acknowledgement));
        assertTrue(outbox.pending().isEmpty());
        Mockito.when(storage.accountId()).thenReturn(one);
        Mockito.when(storage.accountDir()).thenReturn(first);
        Mockito.when(storage.accountDir(Mockito.anyString())).thenReturn(first);
        assertEquals(event.getEventId(), outbox.pending().get(0).getEventId());
    }
}
