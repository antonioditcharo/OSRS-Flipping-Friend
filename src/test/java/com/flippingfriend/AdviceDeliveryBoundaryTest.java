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
        Fixture()throws Exception { this(true); }
        Fixture(boolean serverTickObserved)throws Exception {
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
            when(companion.authorizeSale(any())).thenAnswer(call->call.getArgument(0));
            when(guide.getSuggestion()).thenReturn(Suggestion.idle());
            set("client",client);set("clientThread",thread);set("worker",worker);set("accountMonitor",monitor);
            var config=mock(FlippingFriendConfig.class);when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);
            set("configManager",manager);set("config",config);
            set("offerTracker",tracker);set("companion",companion);set("stepGuide",guide);
            set("widgetResolver",mock(GeWidgetResolver.class));set("panel",mock(FlippingFriendPanel.class));
            set("engine",mock(SuggestionEngine.class));set("journal",mock(TradeJournal.class));set("positions",mock(PositionBook.class));
            set("buyLimits",mock(BuyLimitTracker.class));set("tradePlans",mock(TradePlans.class));set("marketData",market);
            Field loaded=FlippingFriendPlugin.class.getDeclaredField("accountDataLoaded");loaded.setAccessible(true);
            ((AtomicBoolean)loaded.get(plugin)).set(true);
            if(serverTickObserved)plugin.onGameTick(new net.runelite.api.events.GameTick());
        }
        void set(String name,Object value)throws Exception {Field f=FlippingFriendPlugin.class.getDeclaredField(name);f.setAccessible(true);f.set(plugin,value);}
        void computeAdvice()throws Exception {
            Method m=FlippingFriendPlugin.class.getDeclaredMethod("requestEngineRefresh");m.setAccessible(true);m.invoke(plugin);
            clientQueue.remove().run();assertEquals(1,worker.tasks.size());worker.tasks.remove().run();
        }
        void queueAdvice()throws Exception {
            computeAdvice();
            assertEquals("the completed recommendation awaits client-thread delivery",1,clientQueue.size());
        }
    }
    @Test public void refusedRoutineMaintenanceContinuesOnlyThroughTheFreshEntryDecision()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");
        var modify=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Listed").quantity(10).price(100).slot(0).build();
        when(engine.refresh(false)).thenReturn(modify);when(f.companion.authorizeSale(modify)).thenReturn(null);
        when(f.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(f.advice);
        f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(f.advice);
        verify(f.guide,never()).setSuggestion(modify);
        verify(f.companion).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
    }
    @Test public void startupAccountSnapshotCannotPublishBeforeFirstLoggedInServerTick()throws Exception {
        Fixture f=new Fixture(false);
        Method refresh=FlippingFriendPlugin.class.getDeclaredMethod("requestEngineRefresh");refresh.setAccessible(true);
        refresh.invoke(f.plugin);f.clientQueue.remove().run();
        assertTrue("default empty array cannot reach worker publication",f.worker.tasks.isEmpty());
        verify(f.companion,never()).publishAccount(any(),any(),anyLong(),anyMap(),anyMap(),anySet(),anySet(),anySet(),
            anyBoolean(),anyCollection(),anyCollection(),anyString(),any());
        f.plugin.onGameTick(new net.runelite.api.events.GameTick());
        f.computeAdvice();assertFalse(f.clientQueue.isEmpty());
    }
    @Test public void unchangedCaptureCanDeliverAdvice()throws Exception {
        Fixture f=new Fixture();f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(f.advice);
        verify((TradeJournal)getField(f.plugin,"journal"),never()).sessionStats();
    }
    @Test public void burstsOfRefreshRequestsCoalesceButNewCashStillGetsAFreshCapture()throws Exception {
        Fixture f=new Fixture();var refresh=FlippingFriendPlugin.class.getDeclaredMethod("requestEngineRefresh");refresh.setAccessible(true);
        for(int i=0;i<100;i++)refresh.invoke(f.plugin);
        assertEquals("A burst must schedule one client capture",1,f.clientQueue.size());
        f.clientQueue.remove().run();assertEquals(1,f.worker.tasks.size());
        // A real change after capture invalidates the old result and requires exactly one successor.
        f.plugin.onItemContainerChanged(new ItemContainerChanged(93,mock(ItemContainer.class)));
        f.clientQueue.remove().run();
        assertTrue("No second decision may queue behind an old calculation",f.worker.tasks.size()==1);
        f.worker.tasks.remove().run();
        assertEquals("One latest capture is queued after the obsolete calculation",1,f.clientQueue.size());
        f.clientQueue.remove().run();assertEquals(1,f.worker.tasks.size());f.worker.tasks.remove().run();
        f.clientQueue.remove().run();verify(f.guide).setSuggestion(f.advice);
        assertTrue(f.clientQueue.isEmpty());assertTrue(f.worker.tasks.isEmpty());
    }
    private Object getField(Object target,String name)throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
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
    @Test public void queuedSellDoesNotEnterAdviceEvidenceBeforeClientDelivery()throws Exception {
        Fixture f=new Fixture();when(f.guide.isPaused()).thenReturn(false);
        f.set("notifier",mock(net.runelite.client.Notifier.class));
        var sale=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(10).price(100).build();
        when(f.companion.nextAction()).thenReturn(sale);f.computeAdvice();
        verify(f.guide,never()).setSuggestion(sale);
        verify(f.companion,never()).recordSellAdvice(sale);
        verify(f.companion,never()).recordPresentedAdvice(any(),anyString(),anyInt());
    }
    @Test public void deliveryRecordsOnlyTheGuidesActuallyAcceptedAdvice()throws Exception {
        Fixture f=new Fixture();when(f.guide.isPaused()).thenReturn(false);
        f.set("notifier",mock(net.runelite.client.Notifier.class));
        var accepted=Suggestion.builder(SuggestionType.SELL).item(561,"Accepted").quantity(10).price(100).build();
        when(f.guide.getSuggestion()).thenReturn(accepted);
        f.computeAdvice();f.clientQueue.remove().run();
        verify(f.guide).setSuggestion(f.advice);
        verify(f.companion).recordPresentedAdvice(eq(accepted),eq(com.flippingfriend.core.AccountIdentity.fromProfileKey("fixture")),anyInt());
        verify(f.companion,never()).recordPresentedAdvice(eq(f.advice),anyString(),anyInt());
    }
    @Test public void pausedDeliveryDoesNotRecordAdvice()throws Exception {
        Fixture f=new Fixture();f.queueAdvice();f.clientQueue.remove().run();
        verify(f.companion,never()).recordPresentedAdvice(any(),anyString(),anyInt());
    }
    @Test public void aProfileChangeRejectsQueuedEvidenceAlongWithTheAdvice()throws Exception {
        Fixture f=new Fixture();f.queueAdvice();
        when(((ConfigManager)getField(f.plugin,"configManager")).getRSProfileKey()).thenReturn("other");
        f.clientQueue.remove().run();verify(f.guide,never()).setSuggestion(f.advice);
        verify(f.companion,never()).recordPresentedAdvice(any(),anyString(),anyInt());
    }
    @Test public void localReplacementCannotReachTheGuideWhenCompanionRefusesEntries()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        var refusal=Suggestion.waiting("Risk paused","Current durable loss allowance is exhausted.");
        when(f.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(refusal);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.guide,never()).setSuggestion(f.advice);
        verify(f.guide).setSuggestion(refusal);
    }
    @Test public void replacementUsesTheActualFreshAuthorizedOrderRatherThanTheLocalProposal()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        var allowed=Suggestion.builder(SuggestionType.BUY).item(562,"Fixture").quantity(5).price(105).targetSellPrice(120)
            .expectedMinutes(100).fillMinutes(10,20).build();
        when(f.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(allowed);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.guide).setSuggestion(allowed);verify(f.guide,never()).setSuggestion(f.advice);
        verify((TradePlans)getField(f.plugin,"tradePlans"),never()).plan(anyInt(),anyInt(),anyInt(),anyDouble(),anyLong());
    }
    @Test public void aDifferentItemOrOversizedFreshEntryCannotReplaceTheCancelledOrder()throws Exception {
        for(boolean different:new boolean[]{false,true}) {
            Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
            var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
            var other=Suggestion.builder(SuggestionType.BUY).item(different?561:562,"Fixture").quantity(different?5:11).price(105).build();
            when(f.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(other);
            f.queueAdvice();f.clientQueue.remove().run();
            verify(f.guide).setSuggestion(argThat(s->s.getType()==SuggestionType.WAIT));
            verify((TradePlans)getField(f.plugin,"tradePlans"),never()).plan(anyInt(),anyInt(),anyInt(),anyDouble(),anyLong());
        }
    }
    @Test public void replacementSelectionKeepsExistingSkipsAndExcludesOtherAllocatedItems()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        when(engine.skippedItems()).thenReturn(Set.of(563));
        var plan=new com.google.gson.Gson().fromJson("{\"status\":\"READY\",\"allocations\":["
            +"{\"candidate\":{\"itemId\":561}},{\"candidate\":{\"itemId\":562}}]}",
            com.flippingfriend.core.PortfolioPlan.class);
        when(f.companion.lastPlan()).thenReturn(plan);
        when(f.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(f.advice);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.companion).nextEntrySuggestion(any(),anySet(),eq(Set.of(561,563)),anySet());
        verify(f.guide).setSuggestion(f.advice);assertEquals(Set.of(563),engine.skippedItems());
    }
    @Test public void anUnavailableReplacementDecisionWithdrawsTheLocalBuy()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.guide).setSuggestion(argThat(s->s.getType()==SuggestionType.WAIT));
        verify((TradePlans)getField(f.plugin,"tradePlans"),never()).plan(anyInt(),anyInt(),anyInt(),anyDouble(),anyLong());
    }
}
