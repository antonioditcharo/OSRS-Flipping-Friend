package com.flippingfriend;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.model.Calibrator;
import com.flippingfriend.model.SuggestionEngine;
import com.flippingfriend.session.BuyLimitTracker;
import com.flippingfriend.session.AccountMonitor;
import com.flippingfriend.session.OfferTracker;
import com.flippingfriend.session.PositionBook;
import com.flippingfriend.session.SkipList;
import com.flippingfriend.session.TradePlans;
import com.flippingfriend.session.TrackedOffer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Synthetic client observations and a paused worker; no game, HTTP or live account files. */
public class OfferObservationBoundaryTest
{
    @Test public void loginResetDoesNotClearPersistedOffers() throws Exception
    {
        Fixture f = new Fixture();
        when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        f.plugin.onGrandExchangeOfferChanged(changed(2, observation(GrandExchangeOfferState.EMPTY, 0)));
        f.worker.runPending();
        verify(f.tracker, never()).onOfferChanged(anyInt(), any());
        verify(f.companion, never()).publishObservedOfferCleared(anyInt(), anyString());
    }

    @Test public void queuedPublicationRetainsTheObservedPartialFill() throws Exception
    {
        Fixture f = new Fixture();
        TrackedOffer tracked = new TrackedOffer(2, 4151, false, 1000, 10, 100);
        tracked.setItemName("Abyssal whip");
        tracked.setState("SELLING"); tracked.setQuantityFilled(2); tracked.setSpent(2000);
        Method publish = FlippingFriendPlugin.class.getDeclaredMethod("publishOfferToCompanion", TrackedOffer.class);
        publish.setAccessible(true); publish.invoke(f.plugin, tracked);
        // The client advances the same mutable record before the worker gets its turn.
        tracked.setQuantityFilled(10); tracked.setSpent(10000); tracked.setState("SOLD");
        f.worker.runPending();
        ArgumentCaptor<TrackedOffer> sent = ArgumentCaptor.forClass(TrackedOffer.class);
        verify(f.companion).publishObservedOffer(sent.capture(), eq(com.flippingfriend.core.AccountIdentity.fromProfileKey("synthetic-profile")));
        assertEquals("SELLING", sent.getValue().getState());
        assertEquals(2, sent.getValue().getQuantityFilled());
        assertEquals(2000, sent.getValue().getSpent());
        assertNotSame(tracked, sent.getValue());
    }

    @Test public void bufferedObservationDoesNotReadLaterClientCounters() throws Exception
    {
        Fixture f = new Fixture();
        f.setLoaded(false); when(f.configManager.getRSProfileKey()).thenReturn(null);
        GrandExchangeOffer offer = observation(GrandExchangeOfferState.BUYING, 2);
        f.plugin.onGrandExchangeOfferChanged(changed(1, offer));
        when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
        when(offer.getQuantitySold()).thenReturn(10); when(offer.getSpent()).thenReturn(10000L);
        f.setLoaded(true); f.drain();
        ArgumentCaptor<GrandExchangeOffer> seen = ArgumentCaptor.forClass(GrandExchangeOffer.class);
        verify(f.tracker).onOfferChanged(eq(1), seen.capture());
        assertEquals(GrandExchangeOfferState.BUYING, seen.getValue().getState());
        assertEquals(2, seen.getValue().getQuantitySold()); assertEquals(2000, seen.getValue().getSpent());
        assertNotSame(offer, seen.getValue());
    }

    @Test public void bufferedTerminalPrecedesItsCollection() throws Exception
    {
        Fixture f = new Fixture();
        f.setLoaded(false); when(f.configManager.getRSProfileKey()).thenReturn(null);
        f.plugin.onGrandExchangeOfferChanged(changed(1, observation(GrandExchangeOfferState.BOUGHT, 10)));
        f.plugin.onGrandExchangeOfferChanged(changed(1, observation(GrandExchangeOfferState.EMPTY, 0)));
        f.setLoaded(true); f.drain();
        ArgumentCaptor<GrandExchangeOffer> seen = ArgumentCaptor.forClass(GrandExchangeOffer.class);
        verify(f.tracker, times(2)).onOfferChanged(eq(1), seen.capture());
        assertEquals(GrandExchangeOfferState.BOUGHT, seen.getAllValues().get(0).getState());
        assertEquals(GrandExchangeOfferState.EMPTY, seen.getAllValues().get(1).getState());
    }

    @Test public void logoutWithdrawsQueuedOldAccountPublication() throws Exception
    {
        Fixture f = new Fixture();
        Method publish = FlippingFriendPlugin.class.getDeclaredMethod("publishOfferToCompanion", TrackedOffer.class);
        publish.setAccessible(true);
        TrackedOffer tracked = new TrackedOffer(2, 4151, true, 1000, 10, 100);
        tracked.setState("BUYING"); publish.invoke(f.plugin, tracked);
        GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        f.plugin.onGameStateChanged(logout);
        f.worker.runPending();
        verify(f.companion, never()).publishObservedOffer(any(), anyString());
    }

    @Test public void loggedInCollectionStillPublishesEmpty() throws Exception
    {
        Fixture f = new Fixture();
        f.plugin.onGrandExchangeOfferChanged(changed(2, observation(GrandExchangeOfferState.EMPTY, 0)));
        f.worker.runPending();
        verify(f.tracker).onOfferChanged(eq(2), any());
        verify(f.companion).publishObservedOfferCleared(2, com.flippingfriend.core.AccountIdentity.fromProfileKey("synthetic-profile"));
    }

    @Test public void bufferedTerminalAndCollectionBothReachCompanionInOrder() throws Exception
    {
        Fixture f = new Fixture();
        f.setLoaded(false); when(f.configManager.getRSProfileKey()).thenReturn(null);
        f.plugin.onGrandExchangeOfferChanged(changed(1, observation(GrandExchangeOfferState.BOUGHT, 10)));
        f.plugin.onGrandExchangeOfferChanged(changed(1, observation(GrandExchangeOfferState.EMPTY, 0)));
        TrackedOffer terminal = new TrackedOffer(1, 4151, true, 1000, 10, 100);
        terminal.setState("BOUGHT"); terminal.setQuantityFilled(10); terminal.setSpent(10000);
        when(f.tracker.getOffer(1)).thenReturn(terminal);
        when(f.configManager.getRSProfileKey()).thenReturn("synthetic-profile");
        f.setLoaded(true); f.drain(); f.worker.runPending();
        org.mockito.InOrder order = inOrder(f.companion);
        order.verify(f.companion).publishObservedOffer(argThat(o -> "BOUGHT".equals(o.getState()) && o.getSpent() == 10000), anyString());
        order.verify(f.companion).publishObservedOfferCleared(eq(1), anyString());
    }

    @Test public void authenticatedSlotArrayRestoresMissedOfflineCompletion() throws Exception
    {
        Fixture f = new Fixture();
        when(f.client.getGameState()).thenReturn(GameState.LOGGING_IN);
        GrandExchangeOffer terminal = observation(GrandExchangeOfferState.BOUGHT, 10);
        f.plugin.onGrandExchangeOfferChanged(changed(0, terminal));
        verify(f.tracker, never()).onOfferChanged(anyInt(), any());
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(f.client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[] { terminal });
        Method restore = FlippingFriendPlugin.class.getDeclaredMethod("restoreCurrentOffers", long.class, String.class);
        restore.setAccessible(true); restore.invoke(f.plugin, 0L, "synthetic-profile");
        verify(f.tracker).onOfferChanged(eq(0), argThat(o -> o.getState() == GrandExchangeOfferState.BOUGHT && o.getSpent() == 10000));
    }

    @Test public void bufferedUnknownProfileEventsCannotCrossLogout() throws Exception
    {
        Fixture f = new Fixture();
        f.setLoaded(false); when(f.configManager.getRSProfileKey()).thenReturn(null);
        f.plugin.onGrandExchangeOfferChanged(changed(1, observation(GrandExchangeOfferState.BOUGHT, 10)));
        GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        f.plugin.onGameStateChanged(logout);
        when(f.configManager.getRSProfileKey()).thenReturn("another-profile");
        f.setLoaded(true); f.drain();
        verify(f.tracker, never()).onOfferChanged(anyInt(), any());
    }

    private static GrandExchangeOfferChanged changed(int slot, GrandExchangeOffer offer)
    {
        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setSlot(slot); event.setOffer(offer); return event;
    }

    private static GrandExchangeOffer observation(GrandExchangeOfferState state, int filled)
    {
        GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
        when(offer.getState()).thenReturn(state); when(offer.getItemId()).thenReturn(4151);
        when(offer.getPrice()).thenReturn(1000L); when(offer.getTotalQuantity()).thenReturn(10);
        when(offer.getQuantitySold()).thenReturn(filled); when(offer.getSpent()).thenReturn(filled * 1000L);
        return offer;
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static final class Fixture
    {
        final FlippingFriendPlugin plugin = new FlippingFriendPlugin();
        final Client client = mock(Client.class);
        final OfferTracker tracker = mock(OfferTracker.class);
        final CompanionClient companion = mock(CompanionClient.class);
        final ConfigManager configManager = mock(ConfigManager.class);
        final PausedWorker worker = new PausedWorker();
        Fixture() throws Exception
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(tracker.hasCompleteOfferEvidence()).thenReturn(true);
            MarketDataService market = mock(MarketDataService.class);
            when(market.getSnapshot()).thenReturn(MarketSnapshot.empty());
            inject(plugin, "client", client); inject(plugin, "clientThread", mock(ClientThread.class));
            inject(plugin, "offerTracker", tracker); inject(plugin, "companion", companion);
            inject(plugin, "marketData", market); inject(plugin, "worker", worker);
            when(configManager.getRSProfileKey()).thenReturn("synthetic-profile");
            inject(plugin, "configManager", configManager);
            inject(plugin, "accountMonitor", mock(AccountMonitor.class));
            inject(plugin, "buyLimits", mock(BuyLimitTracker.class)); inject(plugin, "skipList", mock(SkipList.class));
            inject(plugin, "positions", mock(PositionBook.class)); inject(plugin, "tradePlans", mock(TradePlans.class));
            inject(plugin, "calibrator", mock(Calibrator.class)); inject(plugin, "engine", mock(SuggestionEngine.class));
            setLoaded(true);
        }
        void setLoaded(boolean value) throws Exception
        { Field loaded = FlippingFriendPlugin.class.getDeclaredField("accountDataLoaded"); loaded.setAccessible(true); ((AtomicBoolean) loaded.get(plugin)).set(value); }
        void drain() throws Exception
        { Method drain = FlippingFriendPlugin.class.getDeclaredMethod("drainPendingOffers"); drain.setAccessible(true); drain.invoke(plugin); }
    }

    private static final class PausedWorker extends AbstractExecutorService
    {
        final List<Runnable> tasks = new ArrayList<>();
        boolean stopped;
        public void execute(Runnable task) { tasks.add(task); }
        void runPending() { for (Runnable task : new ArrayList<>(tasks)) task.run(); tasks.clear(); }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; return new ArrayList<>(tasks); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }
}
