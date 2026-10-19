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
}
