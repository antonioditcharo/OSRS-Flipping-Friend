package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import net.runelite.api.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual tracker callbacks and engine decisions, with disposable account files and no game input. */
public class BuyContinuationWorkflowTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=Instant.now().getEpochSecond();
    class Fixture {
        final PluginStorage storage;final PositionBook positions;final BuyLimitTracker limits;final OfferTracker tracker;
        final MarketDataService market=mock(MarketDataService.class);final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
        final AccountMonitor account=mock(AccountMonitor.class);BuyRepriceIntent intent;SuggestionEngine engine;
        Fixture()throws Exception {
            storage=TestStorage.rootedAt(folder.newFolder().toPath(),"buy-continuation");
            positions=new PositionBook(storage);limits=new BuyLimitTracker(storage);
            tracker=new OfferTracker(storage,positions,limits,mock(TradeJournal.class),new TaxCalculator(),new TradePlans(storage));
            var original=new TrackedOffer(0,561,true,1000,100,now-3600);original.startEpisodeIdentity();original.setState("BUYING");
            storage.writeJson(storage.accountDir().resolve("offers.json"),List.of(original),new com.google.gson.reflect.TypeToken<List<TrackedOffer>>(){}.getType());
            tracker.load();progress(GrandExchangeOfferState.BUYING,0);
            when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
            when(account.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(),Map.of(),Map.of()));
            when(market.getSeries(anyInt(),anyString())).thenReturn(List.of());quote(now,now);
            restart();
        }
        void quote(long highTime,long lowTime) {
            when(market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(561,new ItemMetadata(561,"Nature rune",false,1000,0)),
                Map.of(561,new LatestPrice(1500,highTime,1100,lowTime)),Map.of(561,new Candle((now/300-1)*300,1500,1100,1000,1000)),Map.of(),Instant.ofEpochSecond(now)));
        }
        void restart()throws Exception {
            intent=new BuyRepriceIntent(storage);inject(tracker,"buyReprice",intent);
            var skips=mock(SkipList.class);when(skips.skipped(any())).thenReturn(Set.of());
            engine=new SuggestionEngine(market,new FeatureEngine(),null,null,new Explainer(),null,new TaxCalculator(),
                account,limits,positions,tracker,new SellTimingEngine(new FillModel(),new TaxCalculator()),new TradePlans(storage),config,skips,null);
            inject(engine,"companion",mock(CompanionClient.class));inject(engine,"buyReprice",intent);engine.setObservedAccount(storage.accountId());
        }
        void progress(GrandExchangeOfferState state,int filled) {
            var o=mock(GrandExchangeOffer.class);when(o.getState()).thenReturn(state);when(o.getItemId()).thenReturn(561);
            when(o.getPrice()).thenReturn(1000L);when(o.getTotalQuantity()).thenReturn(100);when(o.getQuantitySold()).thenReturn(filled);
            when(o.getSpent()).thenReturn(filled*1000L);tracker.onOfferChanged(0,o);
        }
        void cancelCollect() {
            progress(GrandExchangeOfferState.BUYING,4);progress(GrandExchangeOfferState.CANCELLED_BUY,6);
            progress(GrandExchangeOfferState.EMPTY,0);
        }
        Suggestion present() {
            var advice=engine.refresh(false);engine.recordPresentedMaintenance(advice,storage.accountId(),now);return advice;
        }
    }
    static void inject(Object target,String name,Object value)throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private SkipList cancellationListener(Fixture f,Suggestion visible)throws Exception {
        var plugin=new FlippingFriendPlugin();var skips=mock(SkipList.class);
        inject(plugin,"engine",f.engine);inject(plugin,"skipList",skips);inject(plugin,"config",f.config);
        inject(plugin,"companion",mock(CompanionClient.class));inject(plugin,"client",mock(Client.class));
        inject(plugin,"widgetResolver",mock(com.flippingfriend.overlay.GeWidgetResolver.class));
        var guide=mock(com.flippingfriend.overlay.StepGuide.class);when(guide.getSuggestion()).thenReturn(visible);inject(plugin,"stepGuide",guide);
        var profiles=mock(net.runelite.client.config.ConfigManager.class);when(profiles.getRSProfileKey()).thenReturn("buy-continuation");inject(plugin,"configManager",profiles);
        var callback=FlippingFriendPlugin.class.getDeclaredMethod("onBuyAbandoned",TrackedOffer.class,long.class);callback.setAccessible(true);
        f.tracker.setAbandonedBuyListener((cancelled,minutes)-> {
            try {callback.invoke(plugin,cancelled,minutes);}catch(Exception e){throw new AssertionError(e);}
        });
        return skips;
    }
    @Test public void committedRepriceCancellationSurvivesRestartAndAChangedVisibleCard()throws Exception {
        var f=new Fixture();f.present();f.restart();
        var skips=cancellationListener(f,Suggestion.waiting("Checking current offers","The original workflow is retained."));
        f.cancelCollect();
        verify(skips,never()).skipUntilCooldownEnds(anyInt(),any());
        assertEquals(94,f.intent.collectedRequest(f.tracker,f.storage.accountId(),now).quantity);
    }
    @Test public void aVisibleSameItemCardCannotExcuseAnUncommittedCancellation()throws Exception {
        var f=new Fixture();var unshown=f.engine.refresh(false);
        var skips=cancellationListener(f,unshown);f.cancelCollect();
        verify(skips).skipUntilCooldownEnds(eq(561),any());
    }
    @Test public void anInitialFailedCommitDoesNotExcuseCancellationButDirtyProgressOnAnAcceptedPromiseDoes()throws Exception {
        for(boolean committed:new boolean[]{false,true}) {
            var f=new Fixture();var shown=f.engine.refresh(false);
            if(committed)assertTrue(f.engine.recordPresentedMaintenance(shown,f.storage.accountId(),now));
            var blocked=f.storage.accountDir().resolve("buy-reprice-intent.json.tmp");java.nio.file.Files.createDirectory(blocked);
            if(!committed)assertFalse(f.engine.recordPresentedMaintenance(shown,f.storage.accountId(),now));
            var skips=cancellationListener(f,committed?Suggestion.idle():shown);f.cancelCollect();
            verify(skips,committed?never():times(1)).skipUntilCooldownEnds(eq(561),any());
            if(committed) {
                assertNull(f.intent.collectedRequest(f.tracker,f.storage.accountId(),now));java.nio.file.Files.delete(blocked);
                assertEquals(94,f.intent.collectedRequest(f.tracker,f.storage.accountId(),now).quantity);
            }
        }
    }
    @Test public void aNewSameItemEpisodeCannotBorrowTheOldSavedCancellationExemption()throws Exception {
        var f=new Fixture();var old=f.present();f.cancelCollect();f.progress(GrandExchangeOfferState.BUYING,0);
        var skips=cancellationListener(f,old);f.cancelCollect();verify(skips).skipUntilCooldownEnds(eq(561),any());
        assertNull(f.intent.collectedRequest(f.tracker,f.storage.accountId(),now));
    }
    @Test public void aRestoredPromiseIsScopedToItsAccountEvenBeforeTheFirstNewEngineRefresh()throws Exception {
        var f=new Fixture();f.present();f.restart();f.engine.setObservedAccount(null);
        var cancelled=f.tracker.getOffer(0).copyObservation();cancelled.setState("CANCELLED_BUY");cancelled.setQuantityFilled(6);
        assertTrue(f.engine.isGuidedBuyCancellation(cancelled,f.storage.accountId(),now));
        assertFalse(f.engine.isGuidedBuyCancellation(cancelled,com.flippingfriend.core.AccountIdentity.fromProfileKey("other"),now));
        assertFalse(f.engine.isGuidedBuyCancellation(cancelled,null,now));
    }
    private void stoppedStock(Fixture f,int quantity,boolean collected,long quoteTime) throws Exception {
        var companion=(CompanionClient)read(f.engine,"companion");
        var stock=com.flippingfriend.core.PositionProjection.ready(561,"Nature rune",quantity,quantity*1000L,now-600,now,"buy","buy-episode")
            .withExitContext(new com.flippingfriend.core.AcquisitionExitContext(1500,900,120).bind("buy"));
        var ledger=new com.flippingfriend.core.AccountLedgerSnapshot(f.storage.accountId(),now,0,0,0,0,0,0,0,0,List.of())
            .withTradeEvidence(List.of(stock),List.of());when(companion.tradingLedger()).thenReturn(ledger);
        when(f.account.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,collected?8:7,8,0,false,
            collected?Map.of(561,quantity):Map.of(),collected?Map.of(561,quantity):Map.of(),Map.of()));
        when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(561,new ItemMetadata(561,"Nature rune",false,1000,0)),
            Map.of(561,new LatestPrice(850,quoteTime,800,quoteTime)),Map.of(),Map.of(),Instant.ofEpochSecond(now)));
    }
    private static Object read(Object target,String name)throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    /** Run the real client capture with an open matching editor, holding the worker for later. */
    private void captureOpenMaintenanceEditor(Fixture f,Suggestion shown)throws Exception {
        var plugin=new FlippingFriendPlugin();var client=mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getVarpValue(net.runelite.api.gameval.VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(shown.getItemId());
        var resolver=mock(com.flippingfriend.overlay.GeWidgetResolver.class);when(resolver.isSetupOpen()).thenReturn(true);
        var guide=new com.flippingfriend.overlay.StepGuide(client,resolver);guide.setSuggestion(shown);
        var thread=mock(net.runelite.client.callback.ClientThread.class);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        var executor=mock(java.util.concurrent.ExecutorService.class);
        var profiles=mock(net.runelite.client.config.ConfigManager.class);when(profiles.getRSProfileKey()).thenReturn("buy-continuation");
        var state=new AccountState(true,true,false,100000000,0,0,7,8,100000,false,Map.of(),Map.of(),Map.of());
        when(f.account.refresh()).thenReturn(state);
        GrandExchangeOffer[] raw=new GrandExchangeOffer[8];
        for(int i=0;i<8;i++) {raw[i]=mock(GrandExchangeOffer.class);when(raw[i].getState()).thenReturn(GrandExchangeOfferState.EMPTY);}
        when(raw[0].getState()).thenReturn(GrandExchangeOfferState.BUYING);when(raw[0].getItemId()).thenReturn(561);
        when(raw[0].getPrice()).thenReturn(1000L);when(raw[0].getTotalQuantity()).thenReturn(100);
        when(client.getGrandExchangeOffers()).thenReturn(raw);
        inject(plugin,"client",client);inject(plugin,"clientThread",thread);inject(plugin,"worker",executor);
        inject(plugin,"engine",f.engine);inject(plugin,"stepGuide",guide);inject(plugin,"widgetResolver",resolver);
        inject(plugin,"accountMonitor",f.account);inject(plugin,"offerTracker",f.tracker);inject(plugin,"configManager",profiles);
        inject(plugin,"companion",read(f.engine,"companion"));inject(plugin,"panel",mock(com.flippingfriend.ui.FlippingFriendPanel.class));
        ((java.util.concurrent.atomic.AtomicBoolean)read(plugin,"accountDataLoaded")).set(true);inject(plugin,"offerSnapshotReady",true);
        var capture=FlippingFriendPlugin.class.getDeclaredMethod("beginEngineRefresh",long.class);capture.setAccessible(true);capture.invoke(plugin,0L);
        verify(executor).execute(any(Runnable.class));
    }
    @Test public void anOpenMaintenanceEditorCannotPinAnOldRepriceAheadOfAFreshStop()throws Exception {
        var f=new Fixture();var old=f.present();captureOpenMaintenanceEditor(f,old);
        f.progress(GrandExchangeOfferState.BUYING,4);stoppedStock(f,4,false,now);
        var next=f.engine.refresh(false);assertEquals(SuggestionType.CANCEL,next.getType());assertTrue(next.isLossCut());
        assertTrue("Revalidation does not erase the saved promise before acceptance",f.intent.isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void anOpenMaintenanceEditorCannotHideACompletedOrCancelledOffer()throws Exception {
        for(var state:new GrandExchangeOfferState[]{GrandExchangeOfferState.BOUGHT,GrandExchangeOfferState.CANCELLED_BUY}) {
            var f=new Fixture();var old=f.present();captureOpenMaintenanceEditor(f,old);f.progress(state,state==GrandExchangeOfferState.BOUGHT?100:6);
            var next=f.engine.refresh(false);assertEquals(SuggestionType.COLLECT,next.getType());assertEquals(0,next.getSlot());
        }
    }
    @Test public void anOpenMaintenanceEditorCannotRenewAnUnsupportedMarketDecision()throws Exception {
        var f=new Fixture();var old=f.present();captureOpenMaintenanceEditor(f,old);
        when(f.market.getSnapshot()).thenReturn(MarketSnapshot.empty());when(f.market.unavailableReason()).thenReturn("Fixture feed unavailable");
        var next=f.engine.refresh(false);assertEquals(SuggestionType.WAIT,next.getType());assertFalse(next.isActionable());
        assertTrue(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void terminalCollectionAndExplicitWindDownDoNotRequireMarketPrices()throws Exception {
        for(var state:new GrandExchangeOfferState[]{GrandExchangeOfferState.BOUGHT,GrandExchangeOfferState.CANCELLED_BUY}) {
            var f=new Fixture();f.progress(state,state==GrandExchangeOfferState.BOUGHT?100:6);
            when(f.market.getSnapshot()).thenReturn(MarketSnapshot.empty());
            var next=f.engine.refresh(false);assertEquals(SuggestionType.COLLECT,next.getType());assertEquals(561,next.getItemId());
            assertEquals(state==GrandExchangeOfferState.BOUGHT?100:6,next.getQuantity());
        }
        var f=new Fixture();f.engine.setSellOnly(true);when(f.market.getSnapshot()).thenReturn(MarketSnapshot.empty());
        var next=f.engine.refresh(false);assertEquals(SuggestionType.CANCEL,next.getType());assertEquals(561,next.getItemId());
        assertEquals("BUYING",f.tracker.getOffer(0).getState());assertNull(f.positions.get(561));
    }
    @Test public void collectingAnUnpricedPersonalSaleDoesNotEnrollStockOrInventItsCost()throws Exception {
        for(var state:new GrandExchangeOfferState[]{GrandExchangeOfferState.SOLD,GrandExchangeOfferState.CANCELLED_SELL}) {
            var f=new Fixture();var sale=mock(GrandExchangeOffer.class);when(sale.getState()).thenReturn(state);
            when(sale.getItemId()).thenReturn(562);when(sale.getPrice()).thenReturn(120L);when(sale.getTotalQuantity()).thenReturn(10);
            int filled=state==GrandExchangeOfferState.SOLD?10:5;
            when(sale.getQuantitySold()).thenReturn(filled);when(sale.getSpent()).thenReturn(filled*120L);
            f.tracker.onOfferChanged(1,sale);when(f.market.getSnapshot()).thenReturn(MarketSnapshot.empty());
            var next=f.engine.refresh(false);assertEquals(SuggestionType.COLLECT,next.getType());assertEquals(1,next.getSlot());
            assertEquals(562,next.getItemId());assertEquals(filled,next.getQuantity());assertNull(f.positions.get(562));
        }
    }
    @Test public void aSavedRepriceMustNotHideAFreshStopOnTheWorkingPartialBuy()throws Exception {
        var f=new Fixture();f.present();f.progress(GrandExchangeOfferState.BUYING,4);stoppedStock(f,4,false,now);
        var next=f.engine.refresh(false);assertNotNull(next);assertEquals(SuggestionType.CANCEL,next.getType());assertTrue(next.isLossCut());
        assertTrue("Proposing the stop must not erase a promise before foreground acceptance",f.intent.isPending(561,f.tracker,f.storage.accountId(),now));
        assertTrue(f.engine.recordPresentedMaintenance(next,f.storage.accountId(),now));
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void aCollectedReplacementCannotRebuyAStockWhoseOriginalStopHasTriggered()throws Exception {
        var f=new Fixture();f.present();f.cancelCollect();f.restart();stoppedStock(f,6,true,now);
        var next=f.engine.refresh(false);assertNotNull(next);assertEquals(SuggestionType.SELL,next.getType());assertTrue(next.isLossCut());assertEquals(6,next.getQuantity());
        assertTrue(f.intent.isPending(561,f.tracker,f.storage.accountId(),now));
        assertTrue(f.engine.recordPresentedMaintenance(next,f.storage.accountId(),now));
        assertFalse("The accepted stop must not resurrect an old rebuy after restart",new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void refusedReplacementExitsOnlyTheAcquiredPartialAndRetiresPromiseOnlyOnAcceptance()throws Exception {
        var f=new Fixture();f.present();f.cancelCollect();f.restart();stoppedStock(f,6,true,now);f.quote(now,now);
        var candidate=f.engine.refresh(false);assertEquals(SuggestionType.BUY,candidate.getType());assertEquals(94,candidate.getQuantity());
        var exit=f.engine.exitAfterReplacementRefusal(Set.of(561));assertNotNull(exit);assertEquals(SuggestionType.SELL,exit.getType());
        assertFalse(exit.isLossCut());assertEquals(6,exit.getQuantity());assertTrue(exit.isProfitKnown());
        assertEquals(94,new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now).quantity);
        assertNull(f.engine.exitAfterReplacementRefusal(Set.of(562)));
        assertTrue(f.engine.recordPresentedMaintenance(exit,f.storage.accountId(),now));
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
        assertEquals(6000,f.positions.get(561).getTotalCost());assertEquals(6,f.positions.get(561).getQuantity());
    }
    @Test public void laterObservedPartialSaleClosesOldRebuyWithoutChangingAcquisitionOrAdoptingOtherUnits()throws Exception {
        for(boolean later:new boolean[]{false,true}) {
            var f=new Fixture();assertTrue(f.intent.advise(f.tracker.getOffer(0),f.storage.accountId(),f.tracker,now-100));f.cancelCollect();
            var sale=new TrackedOffer(1,561,false,1500,6,now+(later?-1:-3601));sale.startEpisodeIdentity();sale.setState("SELLING");
            var type=new com.google.gson.reflect.TypeToken<List<TrackedOffer>>(){}.getType();
            f.storage.writeJson(f.storage.accountDir().resolve("offers.json"),List.of(sale),type);
            var restored=new OfferTracker(f.storage,f.positions,f.limits,mock(TradeJournal.class),new TaxCalculator(),new TradePlans(f.storage));restored.load();
            assertTrue("A cached sale alone cannot retire a retained promise",f.intent.isPending(561,restored,f.storage.accountId(),now+2));
            var observed=mock(GrandExchangeOffer.class);when(observed.getState()).thenReturn(GrandExchangeOfferState.SELLING);
            when(observed.getItemId()).thenReturn(561);when(observed.getPrice()).thenReturn(1500L);when(observed.getTotalQuantity()).thenReturn(6);
            restored.onOfferChanged(1,observed);
            assertEquals(!later,f.intent.isPending(561,restored,f.storage.accountId(),now+2));
            assertEquals(!later,new BuyRepriceIntent(f.storage).isPending(561,restored,f.storage.accountId(),now+2));
            assertEquals(6,f.positions.get(561).getQuantity());assertEquals(6000,f.positions.get(561).getTotalCost());
        }
    }
    @Test public void cachedCollectedSaleNeedsRecordedDispositionToCloseOldRebuyAfterTrackerReload()throws Exception {
        var f=new Fixture();assertTrue(f.intent.advise(f.tracker.getOffer(0),f.storage.accountId(),f.tracker,now-100));f.cancelCollect();
        var sale=new TrackedOffer(1,561,false,1500,6,now-1);sale.startEpisodeIdentity();sale.setState("CANCELLED_SELL");
        sale.setQuantityFilled(2);sale.setCollected(true);
        f.storage.writeJson(f.storage.accountDir().resolve("offers.json"),List.of(sale),new com.google.gson.reflect.TypeToken<List<TrackedOffer>>(){}.getType());
        var restored=new OfferTracker(f.storage,f.positions,f.limits,mock(TradeJournal.class),new TaxCalculator(),new TradePlans(f.storage));restored.load();
        assertTrue(restored.getOffers().isEmpty());assertTrue(f.intent.isPending(561,restored,f.storage.accountId(),now+2));
        var ledger=new com.flippingfriend.core.AccountLedgerSnapshot(f.storage.accountId(),now,0,0,0,0,0,0,0,0,List.of())
            .withContinuationDispositions(List.of(new com.flippingfriend.core.ContinuationDisposition(561,"recorded-partial-sale",sale.getEpisodeIdentity(),now-1,now)));
        assertTrue(f.intent.observeRecordedSales(ledger,f.storage.accountId(),now+2));
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,restored,f.storage.accountId(),now+2));
        assertEquals(6,f.positions.get(561).getQuantity());assertEquals(6000,f.positions.get(561).getTotalCost());
    }
    @Test public void engineConsultsRecordedDispositionBeforeSuggestingAHistoricalRemainder()throws Exception {
        var f=new Fixture();assertTrue(f.intent.advise(f.tracker.getOffer(0),f.storage.accountId(),f.tracker,now-100));
        f.cancelCollect();f.restart();
        var ledger=new com.flippingfriend.core.AccountLedgerSnapshot(f.storage.accountId(),now,0,0,0,0,0,0,0,0,List.of())
            .withContinuationDispositions(List.of(new com.flippingfriend.core.ContinuationDisposition(561,"past-sale","later-sale",now-50,now-10)));
        when(((CompanionClient)read(f.engine,"companion")).tradingLedger()).thenReturn(ledger);
        var next=f.engine.refresh(false);assertTrue(next==null || next.getType()!=SuggestionType.BUY);
        assertTrue(f.engine.replacementCandidates().isEmpty());
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
        assertEquals(6,f.positions.get(561).getQuantity());assertEquals(6000,f.positions.get(561).getTotalCost());
    }
    @Test public void locallyUnaffordableRemainderCannotStrandTheAcquiredPartialBehindTheLastSlotReserve()throws Exception {
        var f=new Fixture();f.present();f.cancelCollect();f.restart();stoppedStock(f,6,true,now);f.quote(now,now);
        when(f.account.getState()).thenReturn(new AccountState(true,true,false,0,0,0,1,8,0,false,Map.of(561,1000),Map.of(561,1000),Map.of()));
        var exit=f.engine.refresh(false);assertNotNull(exit);assertEquals(SuggestionType.SELL,exit.getType());assertEquals(6,exit.getQuantity());
        assertFalse(exit.isLossCut());assertEquals(94,f.intent.collectedRequest(f.tracker,f.storage.accountId(),now).quantity);
    }
    @Test public void failedHealthyExitSupersessionKeepsTheRemainderDurableForRetry()throws Exception {
        var f=new Fixture();f.present();f.cancelCollect();f.restart();stoppedStock(f,6,true,now);f.quote(now,now);
        f.engine.refresh(false);var exit=f.engine.exitAfterReplacementRefusal(Set.of(561));assertNotNull(exit);
        var blocked=f.storage.accountDir().resolve("buy-reprice-intent.json.tmp");java.nio.file.Files.createDirectory(blocked);
        assertFalse(f.engine.recordPresentedMaintenance(exit,f.storage.accountId(),now));
        assertEquals(94,new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now).quantity);
        java.nio.file.Files.delete(blocked);assertTrue(f.engine.recordPresentedMaintenance(exit,f.storage.accountId(),now));
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void staleOrFutureStopQuotesKeepTheHealthyReplacementCommitment()throws Exception {
        for(long quoteTime:new long[]{now-301,now+60}) {
            var f=new Fixture();f.present();f.cancelCollect();f.restart();stoppedStock(f,6,true,quoteTime);
            var next=f.engine.refresh(false);assertTrue(next==null || !next.isLossCut());
            assertTrue(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
            assertEquals(6,f.positions.get(561).getQuantity());assertEquals(6000,f.positions.get(561).getTotalCost());
        }
    }
    @Test public void anAcceptedStopMustCommitItsSupersessionBeforeAnOldRebuyCanBeForgotten()throws Exception {
        var f=new Fixture();f.present();f.cancelCollect();f.restart();stoppedStock(f,6,true,now);
        var stop=f.engine.refresh(false);assertEquals(SuggestionType.SELL,stop.getType());assertTrue(stop.isLossCut());
        var path=f.storage.accountDir().resolve("buy-reprice-intent.json");var before=java.nio.file.Files.readAllBytes(path);
        var blocked=f.storage.accountDir().resolve("buy-reprice-intent.json.tmp");java.nio.file.Files.createDirectory(blocked);
        assertFalse(f.engine.recordPresentedMaintenance(stop,f.storage.accountId(),now));assertArrayEquals(before,java.nio.file.Files.readAllBytes(path));
        assertTrue("The old durable image remains protected until removal can commit",new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
        java.nio.file.Files.delete(blocked);assertTrue(f.engine.recordPresentedMaintenance(stop,f.storage.accountId(),now));
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
        assertEquals(6000,f.positions.get(561).getTotalCost());
    }
    @Test public void restartAfterLateFillCancellationAndSlotReuseRequestsOnlyTheExactRemainder()throws Exception {
        var f=new Fixture();assertEquals(SuggestionType.MODIFY_BUY,f.present().getType());
        f.cancelCollect(); // No worker read between cancellation/collection and reuse.
        var other=mock(GrandExchangeOffer.class);when(other.getState()).thenReturn(GrandExchangeOfferState.BUYING);
        when(other.getItemId()).thenReturn(554);when(other.getPrice()).thenReturn(1L);when(other.getTotalQuantity()).thenReturn(1);
        f.tracker.onOfferChanged(0,other);f.restart();
        var replacement=f.engine.refresh(false);assertEquals(SuggestionType.BUY,replacement.getType());assertEquals(94,replacement.getQuantity());
        assertEquals(6,f.positions.get(561).getQuantity());assertEquals(6000,f.positions.get(561).getTotalCost());
        var otherAccount=new BuyRepriceIntent(TestStorage.rootedAt(f.storage.root(),"another"));
        assertNull(otherAccount.collectedRequest(f.tracker,com.flippingfriend.core.AccountIdentity.fromProfileKey("another"),now));
    }
    @Test public void cancellationGuidanceRequiresItsDurableCommitAndCanRetryWithoutAnotherProposal()throws Exception {
        var f=new Fixture();var advice=f.engine.refresh(false);assertEquals(SuggestionType.MODIFY_BUY,advice.getType());
        var blocked=f.storage.accountDir().resolve("buy-reprice-intent.json.tmp");java.nio.file.Files.createDirectory(blocked);
        assertFalse(f.engine.recordPresentedMaintenance(advice,f.storage.accountId(),now));
        assertNull(f.positions.get(561));assertEquals("BUYING",f.tracker.getOffer(0).getState());
        java.nio.file.Files.delete(blocked);
        assertTrue(f.engine.recordPresentedMaintenance(advice,f.storage.accountId(),now));
        f.cancelCollect();f.restart();assertEquals(94,f.intent.collectedRequest(f.tracker,f.storage.accountId(),now).quantity);
        assertEquals(6,f.positions.get(561).getQuantity());assertEquals(6000,f.positions.get(561).getTotalCost());
    }
    @Test public void anUnsavableOrdinaryModificationDoesNotStarveFreshUnrelatedEntries()throws Exception {
        var f=new Fixture();var advice=f.engine.refresh(false);assertEquals(SuggestionType.MODIFY_BUY,advice.getType());
        var blocked=f.storage.accountDir().resolve("buy-reprice-intent.json.tmp");java.nio.file.Files.createDirectory(blocked);
        assertFalse(f.engine.recordPresentedMaintenance(advice,f.storage.accountId(),now));
        assertTrue(f.engine.deferUnsavedMaintenance(advice,now));
        assertNull("Null hands the next unrelated entry to the authoritative companion",f.engine.refresh(false));
        assertFalse("The withdrawn initial instruction cannot create a pending promise",f.intent.isPending(561,f.tracker,f.storage.accountId(),now));
        java.nio.file.Files.delete(blocked);assertTrue(f.engine.recordPresentedMaintenance(advice,f.storage.accountId(),now));
    }
    @Test public void fullCompletionAndSellOnlyDoNotLeaveAnOldBuyPromiseForTheNextLogin()throws Exception {
        var f=new Fixture();f.present();f.progress(GrandExchangeOfferState.BOUGHT,100);f.progress(GrandExchangeOfferState.EMPTY,0);f.restart();
        assertEquals(0,f.intent.pendingItemId(f.tracker,f.storage.accountId(),now));
        f=new Fixture();f.present();f.engine.setSellOnly(true);f.cancelCollect();f.restart();
        assertEquals(0,f.intent.pendingItemId(f.tracker,f.storage.accountId(),now));
    }
    @Test public void unshownExhaustedOverdueAndUnprofitableCancellationsCannotRetireAnAcceptedReplacement()throws Exception {
        for(String reason:List.of("limit","overdue","unprofitable")) {
        var f=new Fixture();
        if(reason.equals("overdue")) {
            assertTrue(f.intent.advise(f.tracker.getOffer(0),f.storage.accountId(),f.tracker,now));
            var field=SuggestionEngine.class.getDeclaredField("companion");field.setAccessible(true);
            // This cancellation-commit fixture supplies an already verified original timeout.
            // Actual source availability and fallback refusal are covered by the client/outbox workflow tests.
            when(((CompanionClient)field.get(f.engine)).freezeBuyMaintenanceDeadline(any(),any(),anyDouble(),anyLong()))
                .thenAnswer(call->{TrackedOffer expected=call.getArgument(0);OfferTracker tracker=call.getArgument(1);
                    return tracker.freezeBuyMaintenanceDeadline(expected.getSlot(),expected.getContinuationIdentity(),1,
                        call.getArgument(2),call.getArgument(3),f.storage.accountId());});
        } else f.present();
        var path=f.storage.accountDir().resolve("buy-reprice-intent.json");
        byte[] before=java.nio.file.Files.readAllBytes(path);
        if(reason.equals("limit"))f.limits.recordPurchase(561,1000,Instant.ofEpochSecond(now));
        if(reason.equals("unprofitable"))when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(
            Map.of(561,new ItemMetadata(561,"Nature rune",false,1000,0)),Map.of(561,new LatestPrice(1100,now,1100,now)),
            Map.of(561,new Candle((now/300-1)*300,1100,1100,1000,1000)),Map.of(),Instant.ofEpochSecond(now)));
        var cancel=f.engine.refresh(false);assertEquals(SuggestionType.CANCEL,cancel.getType());
        assertArrayEquals("A worker proposal is not accepted cancellation",before,java.nio.file.Files.readAllBytes(path));
        assertTrue(f.intent.isPending(561,f.tracker,f.storage.accountId(),now));
        assertEquals(f.tracker.getOffer(0).getContinuationIdentity(),cancel.getMaintenanceOfferIdentity());
        assertTrue(f.engine.recordPresentedMaintenance(cancel,f.storage.accountId(),now));
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
        }
    }
    private Suggestion cancellation(TrackedOffer offer) {
        return Suggestion.builder(SuggestionType.CANCEL).item(offer.getItemId(),offer.getItemName())
            .slot(offer.getSlot()).price(offer.getPrice()).quantity(offer.getRemaining())
            .maintenanceSource(offer.getContinuationIdentity()).build();
    }
    @Test public void anOldCancellationCannotRetireANewerSameItemEpisode()throws Exception {
        var f=new Fixture();f.present();var old=cancellation(f.tracker.getOffer(0));
        f.cancelCollect();f.progress(GrandExchangeOfferState.BUYING,0);
        assertTrue(f.intent.advise(f.tracker.getOffer(0),f.storage.accountId(),f.tracker,now));
        var path=f.storage.accountDir().resolve("buy-reprice-intent.json");byte[] before=java.nio.file.Files.readAllBytes(path);
        assertFalse("Item and slot cannot substitute for the accepted episode",f.engine.recordPresentedMaintenance(old,f.storage.accountId(),now));
        assertArrayEquals(before,java.nio.file.Files.readAllBytes(path));
        assertTrue(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void acceptedCancellationMustCommitBeforeRetiringTheOriginalPromiseAndCanRetry()throws Exception {
        var f=new Fixture();f.present();var cancel=cancellation(f.tracker.getOffer(0));
        var path=f.storage.accountDir().resolve("buy-reprice-intent.json");byte[] before=java.nio.file.Files.readAllBytes(path);
        var blocked=path.resolveSibling(path.getFileName()+".tmp");java.nio.file.Files.createDirectory(blocked);
        assertFalse(f.engine.recordPresentedMaintenance(cancel,f.storage.accountId(),now));
        assertArrayEquals(before,java.nio.file.Files.readAllBytes(path));
        assertTrue(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
        java.nio.file.Files.delete(blocked);assertTrue(f.engine.recordPresentedMaintenance(cancel,f.storage.accountId(),now));
        assertFalse(new BuyRepriceIntent(f.storage).isPending(561,f.tracker,f.storage.accountId(),now));
    }
    @Test public void changedCancellationCountersPriceStateAndMissingIdentityCannotRetireAWorkflow()throws Exception {
        for(String change:List.of("quantity","price","state","identity")) {
            var f=new Fixture();f.present();var offer=f.tracker.getOffer(0);var cancel=cancellation(offer);
            if(change.equals("quantity"))f.progress(GrandExchangeOfferState.BUYING,4);
            if(change.equals("price"))cancel=Suggestion.builder(SuggestionType.CANCEL).item(561,"Nature rune").slot(0)
                .price(1001).quantity(100).maintenanceSource(offer.getContinuationIdentity()).build();
            if(change.equals("state"))f.progress(GrandExchangeOfferState.CANCELLED_BUY,0);
            if(change.equals("identity"))cancel=Suggestion.builder(SuggestionType.CANCEL).item(561,"Nature rune").slot(0).price(1000).quantity(100).build();
            var path=f.storage.accountDir().resolve("buy-reprice-intent.json");byte[] before=java.nio.file.Files.readAllBytes(path);
            assertFalse(change,f.engine.recordPresentedMaintenance(cancel,f.storage.accountId(),now));
            assertArrayEquals(change,before,java.nio.file.Files.readAllBytes(path));
        }
    }
    @Test public void anUnshownOrOldEpisodeMaintenanceProposalCannotCreateAPromise()throws Exception {
        var f=new Fixture();var proposed=f.engine.refresh(false);assertEquals(SuggestionType.MODIFY_BUY,proposed.getType());
        f.cancelCollect();f.restart();assertEquals(0,f.intent.pendingItemId(f.tracker,f.storage.accountId(),now));
        // Same item/slot/size does not make a later episode the source of the earlier instruction.
        f.progress(GrandExchangeOfferState.BUYING,0);
        f.engine.recordPresentedMaintenance(proposed,f.storage.accountId(),now);
        assertEquals(0,f.intent.pendingItemId(f.tracker,f.storage.accountId(),now));
        var newer=Suggestion.builder(proposed.getType()).item(proposed.getItemId(),proposed.getItemName())
            .price(proposed.getPrice()).quantity(proposed.getQuantity()).slot(proposed.getSlot()).maintenanceSource("another-episode").build();
        assertFalse("A reused slot must reset the walkthrough's old maintenance context",proposed.sameAs(newer));
        assertFalse(proposed.isSameTradeAs(newer));
    }
    @Test public void staleAndFutureTradePricesCannotCancelAnOutbidBuyForReplacement()throws Exception {
        for(long time:new long[]{now-301,now+60}) {
            var f=new Fixture();f.quote(time,now);var suggestion=f.engine.refresh(false);
            assertTrue(suggestion==null || suggestion.getType()!=SuggestionType.MODIFY_BUY);
            assertEquals(0,f.intent.pendingItemId(f.tracker,f.storage.accountId(),now));
        }
    }
    @Test public void anOldSellReceiptRetainsItsExactShapeAndManagedRequirementWithoutBackfill()throws Exception {
        var f=new Fixture();
        var json="{\"item\":561,\"slot\":2,\"quantity\":80,\"firstSeen\":1000,\"episode\":\"old-sale\",\"price\":120,"+
            "\"total\":100,\"filled\":20,\"managed\":true,\"collectionVerified\":true,\"collectedRemaining\":60,\"collectedFilled\":40,\"existingSales\":[]}";
        java.nio.file.Files.writeString(f.storage.accountDir().resolve("sell-relist-intent.json"),json);
        var request=new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId());
        assertNotNull(request);assertEquals(60,request.getQuantity());assertTrue(request.requiresManagedCost());
        assertEquals(json,java.nio.file.Files.readString(f.storage.accountDir().resolve("sell-relist-intent.json")));
    }
    @Test public void aCollectedReplacementMustNotHideAnUnrelatedReadySaleEvenWhenItsQuoteIsStale()throws Exception {
        for(boolean stale:new boolean[]{false,true}) {
            var f=new Fixture();f.present();f.cancelCollect();f.restart();
            var field=SuggestionEngine.class.getDeclaredField("companion");field.setAccessible(true);
            var companion=(CompanionClient)field.get(f.engine);
            var stock=com.flippingfriend.core.PositionProjection.ready(562,"Chaos rune",4,400,now-600,now,"known-buy","known-episode");
            var ledger=new com.flippingfriend.core.AccountLedgerSnapshot(f.storage.accountId(),now,0,0,0,0,0,0,0,0,List.of())
                .withTradeEvidence(List.of(stock),List.of());
            when(companion.tradingLedger()).thenReturn(ledger);
            when(f.account.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,false,
                Map.of(561,6,562,4),Map.of(561,6,562,4),Map.of()));
            when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(
                561,new ItemMetadata(561,"Nature rune",false,1000,0),562,new ItemMetadata(562,"Chaos rune",false,1000,0)),
                Map.of(561,new LatestPrice(1500,stale?now-901:now,1100,now),562,new LatestPrice(120,now,120,now)),Map.of(),Map.of(),Instant.ofEpochSecond(now)));
            var next=f.engine.refresh(false);
            assertEquals("A buy continuation must never starve another holding's exit",SuggestionType.SELL,next.getType());
            assertEquals(562,next.getItemId());assertEquals(4,next.getQuantity());
            assertEquals(94,new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now).quantity);
            assertEquals(6,f.positions.get(561).getQuantity());assertEquals(6000,f.positions.get(561).getTotalCost());
        }
    }
}
