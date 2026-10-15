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

    @Test public void unsupportedPriceClearsCachedAndLateAdviceWithoutChangingOtherAccounts() throws Exception
    {
        PluginStorage storage = com.flippingfriend.data.TestStorage.rootedAt(folder.newFolder().toPath(), "one");
        CompanionClient client = new CompanionClient(storage, new Gson(), new SuggestionLedger());
        PortfolioPlan plan = new PortfolioPlan("p", 0, Long.MAX_VALUE, "READY", "ok", 0, Collections.emptyList());
        client.acceptPlan(plan);
        assertTrue(client.hasFreshPlan());
        java.nio.file.Files.writeString(storage.accountDir().resolve(
            com.flippingfriend.session.OfferTracker.UNSUPPORTED_PRICE_FILE), "{\"price\":3000000000}");
        assertFalse(client.hasFreshPlan());
        assertNull(client.lastPlan());
        client.acceptPlan(plan); // An earlier in-flight response cannot restore a usable plan.
        assertFalse(client.hasFreshPlan());
        assertNull(client.lastPlan());
        PluginStorage other = com.flippingfriend.data.TestStorage.rootedAt(folder.newFolder().toPath(), "two");
        CompanionClient second = new CompanionClient(other, new Gson(), new SuggestionLedger());
        second.acceptPlan(plan);
        assertTrue(second.hasFreshPlan());
    }

    @Test public void staleMalformedAndFutureLedgerResponsesAreNotDisplayed() throws Exception
    {
        PluginStorage storage = Mockito.mock(PluginStorage.class);
        String account = AccountIdentity.fromProfileKey("one");
        Mockito.when(storage.accountId()).thenReturn(account);
        CompanionClient client = new CompanionClient(storage, new Gson(), new SuggestionLedger());
        com.flippingfriend.core.AccountLedgerSnapshot stale = new com.flippingfriend.core.AccountLedgerSnapshot(
            account, Instant.now().minusSeconds(151).getEpochSecond(), 1, 1, 0, 1, 98, 98, 2, 2, Collections.emptyList());
        assertTrue(client.acceptLedger(stale, account)); assertNull(client.lastLedger());
        Gson gson = new Gson();
        com.google.gson.JsonObject json = gson.toJsonTree(stale).getAsJsonObject();
        json.addProperty("schemaVersion", "2");
        assertFalse(client.acceptLedger(gson.fromJson(json, com.flippingfriend.core.AccountLedgerSnapshot.class), account));
        json.addProperty("schemaVersion", "1"); json.addProperty("unresolvedSales", -1);
        assertFalse(client.acceptLedger(gson.fromJson(json, com.flippingfriend.core.AccountLedgerSnapshot.class), account));
        assertNull(client.lastLedger());
    }

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
        offer.setState("BUYING");
        var publish=CompanionClient.class.getDeclaredMethod("publishOffer",TrackedOffer.class,String.class,boolean.class,long.class,boolean.class);
        publish.setAccessible(true);publish.invoke(client,offer,one,true,offer.getFirstSeen(),false);
        OfferEvent event = outbox.pending().get(0);
        assertEquals(one, event.getAccountId());
        assertNotNull(client.currentCanonicalOpenBuy());
        client.acceptPlan(new PortfolioPlan("p", 0, Long.MAX_VALUE, "READY", "ok", 0, Collections.emptyList()));
        assertTrue(client.hasFreshPlan());
        com.flippingfriend.core.AccountLedgerSnapshot ledgerSnapshot = new com.flippingfriend.core.AccountLedgerSnapshot(
            one, Instant.now().getEpochSecond(), 1, 1, 0, 1, 98, 98, 2, 2, Collections.emptyList());
        assertTrue(client.acceptLedger(ledgerSnapshot, one));
        assertNotNull(client.lastLedger());

        Mockito.when(storage.accountId()).thenReturn(two);
        Mockito.when(storage.accountDir()).thenReturn(second);
        Mockito.when(storage.accountDir(Mockito.anyString())).thenReturn(second);
        assertFalse(client.hasFreshPlan());
        assertNull(client.lastPlan());
        assertNull(client.lastLedger());
        assertFalse(client.acceptLedger(ledgerSnapshot, one));
        assertFalse(client.acceptLedger(ledgerSnapshot, two));
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
