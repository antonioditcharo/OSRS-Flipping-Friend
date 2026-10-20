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
            tracker.load();when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
            when(account.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(),Map.of(),Map.of()));
            when(market.getSeries(anyInt(),anyString())).thenReturn(List.of());quote(now,now);
            restart();
        }
        void quote(long highTime,long lowTime) {
            when(market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(561,new ItemMetadata(561,"Nature rune",false,1000,0)),
                Map.of(561,new LatestPrice(1500,highTime,1100,lowTime)),Map.of(),Map.of(),Instant.ofEpochSecond(now)));
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
        assertTrue(f.intent.isPending(561,f.tracker,f.storage.accountId(),now));
        java.nio.file.Files.delete(blocked);assertTrue(f.engine.recordPresentedMaintenance(advice,f.storage.accountId(),now));
    }
    @Test public void fullCompletionAndSellOnlyDoNotLeaveAnOldBuyPromiseForTheNextLogin()throws Exception {
        var f=new Fixture();f.present();f.progress(GrandExchangeOfferState.BOUGHT,100);f.progress(GrandExchangeOfferState.EMPTY,0);f.restart();
        assertEquals(0,f.intent.pendingItemId(f.tracker,f.storage.accountId(),now));
        f=new Fixture();f.present();f.engine.setSellOnly(true);f.cancelCollect();f.restart();
        assertEquals(0,f.intent.pendingItemId(f.tracker,f.storage.accountId(),now));
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
