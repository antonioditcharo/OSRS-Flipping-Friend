package com.flippingfriend;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.overlay.*;
import com.flippingfriend.session.*;
import com.flippingfriend.ui.FlippingFriendPanel;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real refresh orchestration with paused worker/client queues; no game or HTTP input. */
public class AdviceDeliveryBoundaryTest {
    static final class Worker extends AbstractExecutorService {
        final Queue<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable r){tasks.add(r);} public void shutdown(){} public List<Runnable> shutdownNow(){return List.of();}
        public boolean isShutdown(){return false;} public boolean isTerminated(){return false;}
        public boolean awaitTermination(long n,TimeUnit u){return true;}
    }
    static final class Fixture {
        final FlippingFriendPlugin plugin=new FlippingFriendPlugin();
        final Queue<Runnable> clientQueue=new ArrayDeque<>(); final Worker worker=new Worker();
        final StepGuide guide=mock(StepGuide.class);final CompanionClient companion=mock(CompanionClient.class);
        final AccountMonitor monitor=mock(AccountMonitor.class);
        final Suggestion advice=Suggestion.builder(SuggestionType.BUY).item(562,"Fixture").quantity(10).price(100).build();
        Fixture()throws Exception {
            var client=mock(Client.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            GrandExchangeOffer[] slots=new GrandExchangeOffer[8];for(int i=0;i<8;i++) {
                slots[i]=mock(GrandExchangeOffer.class);when(slots[i].getState()).thenReturn(GrandExchangeOfferState.EMPTY);
            }
            when(client.getGrandExchangeOffers()).thenReturn(slots);
            var state=new AccountState(true,true,false,1000000,0,0,8,8,0,false,Map.of(),Map.of(),Map.of());
            when(monitor.refresh()).thenReturn(state);
            var thread=mock(ClientThread.class);doAnswer(c->{clientQueue.add(c.getArgument(0));return null;})
                .when(thread).invokeLater(any(Runnable.class));
            var tracker=mock(OfferTracker.class);when(tracker.hasCompleteOfferEvidence()).thenReturn(true);
            var manager=mock(ConfigManager.class);when(manager.getRSProfileKey()).thenReturn("fixture");
            var market=mock(MarketDataService.class);when(market.getSnapshot()).thenReturn(MarketSnapshot.empty());
            when(companion.nextAction()).thenReturn(advice);when(guide.isPaused()).thenReturn(true);
            set("client",client);set("clientThread",thread);set("worker",worker);set("accountMonitor",monitor);
            var config=mock(FlippingFriendConfig.class);when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);
            set("configManager",manager);set("config",config);
            set("offerTracker",tracker);set("companion",companion);set("stepGuide",guide);
            set("widgetResolver",mock(GeWidgetResolver.class));set("panel",mock(FlippingFriendPanel.class));
            set("engine",mock(SuggestionEngine.class));set("journal",mock(TradeJournal.class));set("positions",mock(PositionBook.class));
            set("buyLimits",mock(BuyLimitTracker.class));set("tradePlans",mock(TradePlans.class));set("marketData",market);
            Field loaded=FlippingFriendPlugin.class.getDeclaredField("accountDataLoaded");loaded.setAccessible(true);
            ((AtomicBoolean)loaded.get(plugin)).set(true);
        }
        void set(String name,Object value)throws Exception {Field f=FlippingFriendPlugin.class.getDeclaredField(name);f.setAccessible(true);f.set(plugin,value);}
        void queueAdvice()throws Exception {
            Method m=FlippingFriendPlugin.class.getDeclaredMethod("requestEngineRefresh");m.setAccessible(true);m.invoke(plugin);
            clientQueue.remove().run();assertEquals(1,worker.tasks.size());worker.tasks.remove().run();
            assertEquals("the completed recommendation awaits client-thread delivery",1,clientQueue.size());
        }
    }
    @Test public void unchangedCaptureCanDeliverAdvice()throws Exception {
        Fixture f=new Fixture();f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(f.advice);
    }
    @Test public void changingSettingsBeforeDeliveryWithdrawsQueuedOldAdvice()throws Exception {
        Fixture f=new Fixture();f.queueAdvice();var e=new ConfigChanged();e.setGroup(FlippingFriendConfig.GROUP);e.setKey("riskProfile");
        f.plugin.onConfigChanged(e);f.clientQueue.remove().run();verify(f.guide,never()).setSuggestion(f.advice);
        verify(f.companion).invalidateEntryAdvice();
    }
    @Test public void changedCashImmediatelyWithdrawsAnAlreadyDisplayedBuy()throws Exception {
        Fixture f=new Fixture();when(f.guide.getSuggestion()).thenReturn(f.advice);
        f.plugin.onItemContainerChanged(new ItemContainerChanged(93,mock(ItemContainer.class)));
        verify(f.guide).setSuggestion(argThat(s->s.getType()==SuggestionType.WAIT));
        verify(f.companion).invalidateEntryAdvice();
    }
}
