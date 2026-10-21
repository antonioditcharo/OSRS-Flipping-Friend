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
        boolean reject;
        public void execute(Runnable r){if(reject)throw new RejectedExecutionException("fixture unavailable executor");tasks.add(r);} public void shutdown(){} public List<Runnable> shutdownNow(){return List.of();}
        public boolean isShutdown(){return false;} public boolean isTerminated(){return false;}
        public boolean awaitTermination(long n,TimeUnit u){return true;}
    }
    static class Fixture {
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
    @Test public void aRefusedSaleCannotStayActionableBehindTheRealMidEntryGuide()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");
        var rejected=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Listed").quantity(10).price(100).slot(0)
            .maintenanceSource("original-sale").build();
        when(engine.refresh(false)).thenReturn(rejected);when(engine.recordPresentedMaintenance(any(),anyString(),anyLong())).thenReturn(true);
        when(f.companion.authorizeSale(rejected)).thenReturn(null);
        when(f.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(f.advice);
        var client=(Client)getField(f.plugin,"client");when(client.isClientThread()).thenReturn(true);
        var resolver=mock(GeWidgetResolver.class);when(resolver.isSetupOpen()).thenReturn(true);
        var quantity=mock(net.runelite.api.widgets.Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var guide=new StepGuide(client,resolver);guide.setSuggestion(rejected);
        var click=mock(net.runelite.api.events.MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);guide.onMenuOptionClicked(click);
        f.set("stepGuide",guide);f.queueAdvice();f.clientQueue.remove().run();
        assertSame("The declined sell must be withdrawn before a successor can be deferred behind it",f.advice,guide.getSuggestion());
        assertNull(guide.getPending());verify(f.companion,never()).recordPresentedAdvice(eq(rejected),anyString(),anyInt());
    }
    @Test public void aSameItemStopReplacesTheRealMidEntryBuyAndCommitsBeforeItsAdviceEvidence()throws Exception {
        Fixture f=new Fixture();when(f.guide.isPaused()).thenReturn(false);
        var client=(Client)getField(f.plugin,"client");when(client.isClientThread()).thenReturn(true);
        var resolver=mock(GeWidgetResolver.class);when(resolver.isSetupOpen()).thenReturn(true);
        var quantity=mock(net.runelite.api.widgets.Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var guide=new StepGuide(client,resolver);guide.setSuggestion(f.advice);
        var click=mock(net.runelite.api.events.MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);guide.onMenuOptionClicked(click);
        f.set("stepGuide",guide);f.set("notifier",mock(net.runelite.client.Notifier.class));
        var stop=Suggestion.builder(SuggestionType.SELL).item(562,"Fixture").quantity(4).price(80).lossCut(true).build();
        when(f.companion.nextAction()).thenReturn(stop);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.recordPresentedMaintenance(eq(stop),anyString(),anyLong())).thenReturn(true);
        f.queueAdvice();f.clientQueue.remove().run();assertSame(stop,guide.getSuggestion());assertNull(guide.getPending());
        var order=inOrder(engine,f.companion);
        order.verify(engine).recordPresentedMaintenance(eq(stop),anyString(),anyLong());
        order.verify(f.companion).recordPresentedAdvice(eq(stop),anyString(),anyInt());
        verify(f.companion,never()).recordPresentedAdvice(eq(f.advice),anyString(),anyInt());
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
        verify(f.guide).withdrawAdvice(argThat(s->s.getType()==SuggestionType.WAIT));
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
        when(((SuggestionEngine)getField(f.plugin,"engine")).recordPresentedMaintenance(eq(accepted),anyString(),anyLong())).thenReturn(true);
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
        when(f.companion.replacementSuggestion(anyInt(),anyInt(),any(),anySet(),anySet(),anySet())).thenReturn(refusal);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.guide,never()).setSuggestion(f.advice);
        verify(f.guide).setSuggestion(refusal);
    }
    @Test public void refusedFirstRemainderCannotHideALaterAuthorizedReplacement()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        var second=Suggestion.builder(SuggestionType.BUY).item(28997,"Dual macuahuitl").quantity(2).price(3725000).build();
        when(engine.replacementCandidates()).thenReturn(List.of(f.advice,second));
        when(f.companion.replacementSuggestion(eq(562),eq(10),any(),anySet(),anySet(),anySet()))
            .thenReturn(Suggestion.waiting("Insufficient funds","The earlier remainder is retained."));
        when(f.companion.replacementSuggestion(eq(28997),eq(2),any(),anySet(),anySet(),anySet())).thenReturn(second);
        f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(second);
        verify(f.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
    }
    @Test public void refusedRemainderOffersVerifiedPartialExitThroughFinalSaleAuthority()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        when(f.companion.replacementSuggestion(anyInt(),anyInt(),any(),anySet(),anySet(),anySet()))
            .thenReturn(Suggestion.waiting("Replacement paused","Current entry authority refused."));
        var exit=Suggestion.builder(SuggestionType.SELL).item(562,"Partial").quantity(3).price(150).expectedProfit(50).build();
        when(engine.exitAfterReplacementRefusal(Set.of(562))).thenReturn(exit);
        f.queueAdvice();f.clientQueue.remove().run();verify(f.companion).authorizeSale(exit);verify(f.guide).setSuggestion(exit);
        verify(f.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
    }
    private Suggestion queuedSale(int item) {
        return Suggestion.builder(SuggestionType.SELL).item(item,"Stock "+item).quantity(3).price(150).expectedProfit(50).build();
    }
    @Test public void refusedFirstRelistCannotHideALaterVerifiedExit()throws Exception {
        var f=new Fixture();when(f.companion.nextAction()).thenReturn(null);var engine=(SuggestionEngine)getField(f.plugin,"engine");
        var first=queuedSale(561);var second=queuedSale(562);
        when(f.guide.isPaused()).thenReturn(false);when(f.guide.getSuggestion()).thenReturn(second);
        when(engine.recordPresentedMaintenance(eq(second),anyString(),anyLong())).thenReturn(true);
        f.set("notifier",mock(net.runelite.client.Notifier.class));
        when(engine.refresh(false)).thenReturn(first);when(engine.saleCandidates()).thenReturn(List.of(first,second));
        when(f.companion.authorizeSale(first)).thenReturn(Suggestion.waiting("Source changed","Retained for retry."));
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.companion).authorizeSale(second);verify(f.guide).setSuggestion(second);
        verify(f.companion).recordPresentedAdvice(eq(second),anyString(),anyInt());
        verify(f.companion,never()).recordPresentedAdvice(eq(first),anyString(),anyInt());
        verify(f.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
    }
    @Test public void allRefusedUnlistedExitsKeepTheirWaitAndNeverFallThroughToBuying()throws Exception {
        var f=new Fixture();when(f.companion.nextAction()).thenReturn(null);var engine=(SuggestionEngine)getField(f.plugin,"engine");
        var first=queuedSale(561);var second=queuedSale(562);var wait=Suggestion.waiting("Acquisition changed","Retained for retry.");
        when(engine.refresh(false)).thenReturn(first);when(engine.saleCandidates()).thenReturn(List.of(first,second));
        when(f.companion.authorizeSale(first)).thenReturn(wait);when(f.companion.authorizeSale(second)).thenReturn(null);
        f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(wait);
        verify(f.companion).authorizeSale(second);verify(f.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
    }
    @Test public void everyRefusedExitIsWithdrawnFromTheRealMidEntryGuide()throws Exception {
        var f=new Fixture();when(f.companion.nextAction()).thenReturn(null);var engine=(SuggestionEngine)getField(f.plugin,"engine");
        var first=queuedSale(561);var second=queuedSale(562);when(engine.refresh(false)).thenReturn(first);
        when(engine.saleCandidates()).thenReturn(List.of(first,second));when(f.companion.authorizeSale(any())).thenReturn(null);
        var client=(Client)getField(f.plugin,"client");when(client.isClientThread()).thenReturn(true);
        var resolver=mock(GeWidgetResolver.class);when(resolver.isSetupOpen()).thenReturn(true);
        var quantity=mock(net.runelite.api.widgets.Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var guide=new StepGuide(client,resolver);guide.setSuggestion(first);
        var click=mock(net.runelite.api.events.MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);guide.onMenuOptionClicked(click);
        f.set("stepGuide",guide);f.queueAdvice();f.clientQueue.remove().run();
        assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());assertNull(guide.getPending());
        verify(f.companion).authorizeSale(second);verify(f.companion,never()).recordPresentedAdvice(eq(first),anyString(),anyInt());
    }
    @Test public void changedInventoryDuringFirstAuthorityCheckStopsTheRemainingOldQueue()throws Exception {
        var f=new Fixture();when(f.companion.nextAction()).thenReturn(null);var engine=(SuggestionEngine)getField(f.plugin,"engine");
        var first=queuedSale(561);var second=queuedSale(562);when(engine.refresh(false)).thenReturn(first);
        when(engine.saleCandidates()).thenReturn(List.of(first,second));
        when(f.companion.authorizeSale(first)).thenAnswer(call->{f.plugin.onItemContainerChanged(new ItemContainerChanged(93,mock(ItemContainer.class)));return null;});
        f.computeAdvice();verify(f.companion,never()).authorizeSale(second);verify(f.guide,never()).setSuggestion(second);
    }
    @Test public void anUnrelatedProposalCannotConsumeThePreviousSaleQueue()throws Exception {
        var f=new Fixture();when(f.companion.nextAction()).thenReturn(null);var engine=(SuggestionEngine)getField(f.plugin,"engine");
        var first=queuedSale(561);var old=queuedSale(562);when(engine.refresh(false)).thenReturn(first);
        when(engine.saleCandidates()).thenReturn(List.of(old));when(f.companion.authorizeSale(first)).thenReturn(null);
        f.queueAdvice();f.clientQueue.remove().run();verify(f.companion,never()).authorizeSale(old);
        verify(f.guide).setSuggestion(argThat(s->s.getType()==SuggestionType.WAIT));
    }
    @Test public void replacementUsesTheActualFreshAuthorizedOrderRatherThanTheLocalProposal()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        var allowed=Suggestion.builder(SuggestionType.BUY).item(562,"Fixture").quantity(5).price(105).targetSellPrice(120)
            .expectedMinutes(100).fillMinutes(10,20).build();
        when(f.companion.replacementSuggestion(anyInt(),anyInt(),any(),anySet(),anySet(),anySet())).thenReturn(allowed);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.guide).setSuggestion(allowed);verify(f.guide,never()).setSuggestion(f.advice);
        verify((TradePlans)getField(f.plugin,"tradePlans"),never()).plan(anyInt(),anyInt(),anyInt(),anyDouble(),anyLong());
    }
    @Test public void aDifferentItemOrOversizedFreshEntryCannotReplaceTheCancelledOrder()throws Exception {
        for(boolean different:new boolean[]{false,true}) {
            Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
            var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
            var other=Suggestion.builder(SuggestionType.BUY).item(different?561:562,"Fixture").quantity(different?5:11).price(105).build();
            when(f.companion.replacementSuggestion(anyInt(),anyInt(),any(),anySet(),anySet(),anySet())).thenReturn(other);
            f.queueAdvice();f.clientQueue.remove().run();
            verify(f.guide).setSuggestion(argThat(s->s.getType()==SuggestionType.WAIT));
            verify((TradePlans)getField(f.plugin,"tradePlans"),never()).plan(anyInt(),anyInt(),anyInt(),anyDouble(),anyLong());
        }
    }
    @Test public void replacementSelectionKeepsTheExactCapAndSkipsWithoutBorrowingTheLargerPlan()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        when(engine.skippedItems()).thenReturn(Set.of(563));
        var plan=new com.google.gson.Gson().fromJson("{\"status\":\"READY\",\"allocations\":["
            +"{\"candidate\":{\"itemId\":561}},{\"candidate\":{\"itemId\":562}}]}",
            com.flippingfriend.core.PortfolioPlan.class);
        when(f.companion.lastPlan()).thenReturn(plan);
        when(f.companion.replacementSuggestion(anyInt(),anyInt(),any(),anySet(),anySet(),anySet())).thenReturn(f.advice);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.companion).replacementSuggestion(eq(562),eq(10),any(),anySet(),eq(Set.of(563)),anySet());
        verify(f.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
        verify(f.guide).setSuggestion(f.advice);assertEquals(Set.of(563),engine.skippedItems());
    }
    @Test public void maintenancePromisesUseOnlyTheGuidesAcceptedInstructionAfterClientDelivery()throws Exception {
        Fixture f=new Fixture();when(f.guide.isPaused()).thenReturn(false);
        f.set("notifier",mock(net.runelite.client.Notifier.class));
        var accepted=Suggestion.builder(SuggestionType.MODIFY_BUY).item(561,"Accepted").quantity(10).price(100)
            .slot(0).maintenanceSource("accepted-episode").build();
        when(f.guide.getSuggestion()).thenReturn(accepted);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");
        when(engine.recordPresentedMaintenance(eq(accepted),anyString(),anyLong())).thenReturn(true);
        f.computeAdvice();verify(engine,never()).recordPresentedMaintenance(any(),anyString(),anyLong());
        f.clientQueue.remove().run();
        verify(engine).recordPresentedMaintenance(eq(accepted),eq(com.flippingfriend.core.AccountIdentity.fromProfileKey("fixture")),anyLong());
        verify(engine,never()).recordPresentedMaintenance(eq(f.advice),anyString(),anyLong());
    }
    @Test public void anUnsavedMaintenancePromiseWithdrawsInsteadOfRecordingOrNotifyingIt()throws Exception {
        for(var type:List.of(SuggestionType.MODIFY_BUY,SuggestionType.MODIFY_SELL,SuggestionType.CANCEL)) {
            Fixture f=new Fixture();when(f.guide.isPaused()).thenReturn(false);
            var notifier=mock(net.runelite.client.Notifier.class);f.set("notifier",notifier);
            var accepted=Suggestion.builder(type).item(561,"Uncommitted").quantity(10).price(100).slot(0).maintenanceSource("episode").build();
            when(f.guide.getSuggestion()).thenReturn(accepted);
            doAnswer(call->{when(f.guide.getSuggestion()).thenReturn(call.getArgument(0));return null;})
                .when(f.guide).withdrawAdvice(any());
            f.computeAdvice();f.clientQueue.remove().run();
            verify(f.guide).withdrawAdvice(argThat(s->s.getType()==SuggestionType.WAIT));
            verify(f.companion,never()).recordPresentedAdvice(any(),anyString(),anyInt());verifyNoInteractions(notifier);
        }
    }
    @Test public void anUnavailableReplacementDecisionWithdrawsTheLocalBuy()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        f.queueAdvice();f.clientQueue.remove().run();
        verify(f.guide).setSuggestion(argThat(s->s.getType()==SuggestionType.WAIT));
        verify((TradePlans)getField(f.plugin,"tradePlans"),never()).plan(anyInt(),anyInt(),anyInt(),anyDouble(),anyLong());
    }
    @Test public void anOldLifecycleCaptureCannotPublishThroughTheRestartedWorker()throws Exception {
        Fixture f=new Fixture();var request=FlippingFriendPlugin.class.getDeclaredMethod("requestEngineRefresh");request.setAccessible(true);
        request.invoke(f.plugin);
        ((java.util.concurrent.atomic.AtomicLong)getField(f.plugin,"engineRefreshEpoch")).incrementAndGet();
        ((AtomicBoolean)getField(f.plugin,"engineRefreshInFlight")).set(false);
        ((AtomicBoolean)getField(f.plugin,"engineRefreshRequested")).set(false);
        request.invoke(f.plugin);assertEquals(2,f.clientQueue.size());
        f.clientQueue.remove().run();assertTrue("A queued callback from the old load cannot publish or take worker ownership",f.worker.tasks.isEmpty());
        f.clientQueue.remove().run();assertEquals(1,f.worker.tasks.size());f.worker.tasks.remove().run();
        f.clientQueue.remove().run();verify(f.guide).setSuggestion(f.advice);
    }
    @Test public void aRejectedWorkerSubmissionDoesNotBreakTheClientCallbackOrLatchFutureRefreshes()throws Exception {
        Fixture f=new Fixture();var request=FlippingFriendPlugin.class.getDeclaredMethod("requestEngineRefresh");request.setAccessible(true);
        f.worker.reject=true;request.invoke(f.plugin);f.clientQueue.remove().run();
        assertFalse(((AtomicBoolean)getField(f.plugin,"engineRefreshInFlight")).get());
        f.worker.reject=false;f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(f.advice);
    }
    @Test public void aRefusedReplacementStillLetsTheFreshPlanOfferAnUnrelatedBuy()throws Exception {
        Fixture f=new Fixture();when(f.companion.nextAction()).thenReturn(null);
        var engine=(SuggestionEngine)getField(f.plugin,"engine");when(engine.refresh(false)).thenReturn(f.advice);
        var refusal=Suggestion.waiting("Replacement paused","Its resale value is unpriced; its exact remainder is retained.");
        when(f.companion.replacementSuggestion(anyInt(),anyInt(),any(),anySet(),anySet(),anySet())).thenReturn(refusal);
        var unrelated=Suggestion.builder(SuggestionType.BUY).item(563,"Unrelated").quantity(5).price(100).build();
        when(f.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(unrelated);
        f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(unrelated);verify(f.guide,never()).setSuggestion(refusal);
    }
    @Test public void adviceExpiringInTheClientQueueIsWithdrawnAndImmediatelyRecomputed()throws Exception {
        Fixture f=new Fixture();f.queueAdvice();
        var expiry=Suggestion.class.getDeclaredField("adviceExpiresAt");expiry.setAccessible(true);
        expiry.setLong(f.advice,java.time.Instant.now().getEpochSecond()-1);
        f.clientQueue.remove().run();verify(f.guide,never()).setSuggestion(f.advice);
        assertFalse("A discarded result must schedule fresh guidance",f.clientQueue.isEmpty());
    }
    @Test public void persistentRefreshFailureWithdrawsARealMidEntrySaleAndItsPersonalQuote()throws Exception {
        var f=new Fixture();var client=(Client)getField(f.plugin,"client");when(client.isClientThread()).thenReturn(true);
        var resolver=mock(GeWidgetResolver.class);when(resolver.isSetupOpen()).thenReturn(true);
        var quantity=mock(net.runelite.api.widgets.Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var guide=new StepGuide(client,resolver);guide.setSuggestion(Suggestion.builder(SuggestionType.SELL).item(561,"Stock").quantity(10).price(110).build());
        var click=mock(net.runelite.api.events.MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);guide.onMenuOptionClicked(click);
        guide.setEditorQuote(Suggestion.builder(SuggestionType.SELL).item(562,"Personal").quantity(2).price(99).build());
        f.set("stepGuide",guide);when(f.companion.nextAction()).thenThrow(new IllegalStateException("fixture unavailable"));
        for(int i=0;i<3;i++) {f.computeAdvice();while(!f.clientQueue.isEmpty())f.clientQueue.remove().run();}
        assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());assertNull(guide.getPending());
        doReturn(f.advice).when(f.companion).nextAction();f.queueAdvice();f.clientQueue.remove().run();assertSame(f.advice,guide.getSuggestion());
    }
    @Test public void localAdviceCannotReceiveAFreshLifetimeAfterWaitingTooLongInTheClientQueue()throws Exception {
        for(long delay:new long[]{StepGuide.MAX_ADVICE_AGE_NANOS,-1}) {
            var f=new Fixture();var clock=new java.util.concurrent.atomic.AtomicLong(100);
            f.set("adviceClock",(java.util.function.LongSupplier)clock::get);f.queueAdvice();clock.addAndGet(delay);
            f.clientQueue.remove().run();verify(f.guide,never()).setSuggestion(f.advice);
            verify(f.guide).withdrawAdvice(argThat(s->s.getType()==SuggestionType.WAIT));assertFalse(f.clientQueue.isEmpty());
        }
    }
    @Test public void aStopSaleCannotBeRecordedOrLeftVisibleWhenItsOldRebuyCannotBeSuperseded()throws Exception {
        var f=new Fixture();var engine=(SuggestionEngine)getField(f.plugin,"engine");when(f.companion.nextAction()).thenReturn(null);
        var cut=Suggestion.builder(SuggestionType.SELL).item(561,"Stopped stock").quantity(6).price(800).lossCut(true).profitKnown(true).build();
        when(engine.refresh(false)).thenReturn(cut);when(engine.recordPresentedMaintenance(eq(cut),anyString(),anyLong())).thenReturn(false);
        var client=(Client)getField(f.plugin,"client");var guide=new StepGuide(client,mock(GeWidgetResolver.class));f.set("stepGuide",guide);
        f.queueAdvice();f.clientQueue.remove().run();assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());
        verify(f.companion,never()).recordPresentedAdvice(eq(cut),anyString(),anyInt());
    }
}
