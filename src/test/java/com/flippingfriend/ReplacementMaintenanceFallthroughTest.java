package com.flippingfriend;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.runelite.api.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real engine and refresh orchestration with a persisted cancelled BUY; no HTTP/game input. */
public class ReplacementMaintenanceFallthroughTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=Instant.now().getEpochSecond();
    class Fixture {
        final AdviceDeliveryBoundaryTest.Fixture refresh=new AdviceDeliveryBoundaryTest.Fixture();
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"fixture");
        final OfferTracker offers=mock(OfferTracker.class);final MarketDataService market=mock(MarketDataService.class);
        final FlippingFriendConfig config=mock(FlippingFriendConfig.class);final BuyRepriceIntent intent=new BuyRepriceIntent(storage);
        final BuyLimitTracker limits=mock(BuyLimitTracker.class);final SuggestionEngine engine;
        final TrackedOffer first=new TrackedOffer(1,562,false,200,10,now-3600);
        final TrackedOffer second=new TrackedOffer(2,563,false,200,10,now-3500);
        final List<TrackedOffer> board=new ArrayList<>();
        final Suggestion refused=Suggestion.waiting("Checking your replacement","Fresh entry authority declined this saved remainder.");
        Fixture()throws Exception {
            when(offers.hasCompleteOfferEvidence()).thenReturn(true);
            var buy=new TrackedOffer(0,561,true,100,10,now-3600);buy.setState("BUYING");
            when(offers.getOffers()).thenReturn(List.of(buy));when(offers.getOffer(0)).thenReturn(buy);
            assertTrue(intent.advise(buy,storage.accountId(),offers,now));
            buy.setState("CANCELLED_BUY");buy.setCollected(true);when(offers.getOffer(0)).thenReturn(null);
            when(offers.getSettledOffer(0)).thenReturn(buy);
            first.setState("SELLING");second.setState("SELLING");board.add(first);board.add(second);
            when(offers.getOffers()).thenAnswer(call->List.copyOf(board));
            when(offers.listedForSale()).thenReturn(Map.of(562,10,563,10));
            when(offers.itemsWithOpenOffers()).thenReturn(Set.of(562,563));when(offers.itemsBeingBought()).thenReturn(Set.of());
            when(offers.getOffer(1)).thenReturn(first);when(offers.getOffer(2)).thenReturn(second);
            when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
            when(limits.remaining(anyInt(),anyInt(),any())).thenReturn(1000);
            var state=new AccountState(true,true,false,100000000,0,0,6,8,0,false,Map.of(),Map.of(),Map.of());
            when(refresh.monitor.getState()).thenReturn(state);when(refresh.monitor.refresh()).thenReturn(state);
            var positions=mock(PositionBook.class);var skips=mock(SkipList.class);when(skips.skipped(any())).thenReturn(Set.of());
            engine=new SuggestionEngine(market,new FeatureEngine(),null,null,new Explainer(),null,new TaxCalculator(),
                refresh.monitor,limits,positions,offers,new SellTimingEngine(new FillModel(),new TaxCalculator()),new TradePlans(storage),config,skips,null);
            inject(engine,"buyReprice",intent);inject(engine,"companion",refresh.companion);engine.setObservedAccount(storage.accountId());
            var ledger=new AccountLedgerSnapshot(storage.accountId(),now,0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(List.of(
                PositionProjection.ready(562,"Listed one",10,1000,now-7200,"buy-one","buy-one-offer"),
                PositionProjection.ready(563,"Listed two",10,1000,now-7200,"buy-two","buy-two-offer")),List.of(),Map.of());
            when(refresh.companion.tradingLedger()).thenReturn(ledger);
            refresh.set("engine",engine);refresh.set("marketData",market);refresh.set("offerTracker",offers);
            refresh.set("config",config);refresh.set("buyLimits",limits);refresh.set("positions",positions);
            when(refresh.companion.nextAction()).thenReturn(null);
            when(refresh.companion.replacementSuggestion(anyInt(),anyInt(),any(),anySet(),anySet(),anySet())).thenReturn(refused);
            when(refresh.companion.selectBuyMaintenanceCancellation(any())).thenAnswer(call->call.getArgument(0));
            quotes(now);syncClientSlots(state);
            assertEquals(10,intent.collectedRequest(offers,storage.accountId(),now).quantity);
        }
        void quotes(long saleTime) {
            var metadata=new HashMap<Integer,ItemMetadata>();var prices=new HashMap<Integer,LatestPrice>();var trends=new HashMap<Integer,Candle>();
            long bucket=(now/300-1)*300;
            for(int item:new int[]{561,562,563,564}) {
                metadata.put(item,new ItemMetadata(item,"Item "+item,false,1000,0));
                prices.put(item,new LatestPrice(item==561?200:150,item==561?now:saleTime,item==561?100:140,item==561?now:saleTime));
                trends.put(item,new Candle(bucket,item==561?200:150,item==561?100:140,1000,1000));
            }
            when(market.getSnapshot()).thenReturn(new MarketSnapshot(metadata,prices,trends,Map.of(),Instant.ofEpochSecond(now)));
            when(market.getSeries(anyInt(),anyString())).thenReturn(List.of(new Candle(bucket,150,140,1000,1000),new Candle(bucket-300,150,140,1000,1000)));
        }
        void syncClientSlots(AccountState state)throws Exception {
            var client=(Client)read(refresh.plugin,"client");var raw=new GrandExchangeOffer[8];
            for(int slot=0;slot<8;slot++) {
                raw[slot]=mock(GrandExchangeOffer.class);when(raw[slot].getState()).thenReturn(GrandExchangeOfferState.EMPTY);
                for(var offer:board)if(offer.getSlot()==slot) {
                    when(raw[slot].getState()).thenReturn(GrandExchangeOfferState.valueOf(offer.getState()));when(raw[slot].getItemId()).thenReturn(offer.getItemId());
                    when(raw[slot].getPrice()).thenReturn((long)offer.getPrice());when(raw[slot].getTotalQuantity()).thenReturn(offer.getTotalQuantity());
                    when(raw[slot].getQuantitySold()).thenReturn(offer.getQuantityFilled());when(raw[slot].getSpent()).thenReturn(offer.getSpent());
                }
            }
            assertNotNull("Fixture cash/commitment and all eight physical slots must agree",
                PlanningAccountObservation.capture(storage.accountId(),state,raw,board,now));
            when(client.getGrandExchangeOffers()).thenReturn(raw);
        }
        void run()throws Exception{refresh.queueAdvice();refresh.clientQueue.remove().run();}
        void retained() {assertEquals(10,intent.collectedRequest(offers,storage.accountId(),now).quantity);}
    }
    @Test public void declinedDurableReplacementFallsThroughToAuthorizedListedMaintenanceInActualRefresh()throws Exception {
        var f=new Fixture();f.run();
        var shown=ArgumentCaptor.forClass(Suggestion.class);verify(f.refresh.guide).setSuggestion(shown.capture());
        assertEquals(SuggestionType.MODIFY_SELL,shown.getValue().getType());
        verify(f.refresh.companion).authorizeSale(shown.getValue());verify(f.refresh.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
        verify(f.refresh.companion,times(1)).replacementSuggestion(eq(561),eq(10),any(),anySet(),anySet(),anySet());f.retained();
    }
    @Test public void refusedFirstListedReductionStillTriesTheLaterEligibleSale()throws Exception {
        var f=new Fixture();when(f.refresh.companion.authorizeSale(argThat(s->s!=null && s.getItemId()==562))).thenReturn(null);f.run();
        var shown=ArgumentCaptor.forClass(Suggestion.class);verify(f.refresh.guide).setSuggestion(shown.capture());
        assertEquals(563,shown.getValue().getItemId());assertEquals(SuggestionType.MODIFY_SELL,shown.getValue().getType());
        verify(f.refresh.companion).authorizeSale(argThat(s->s!=null && s.getItemId()==562));
        verify(f.refresh.companion).authorizeSale(argThat(s->s!=null && s.getItemId()==563));f.retained();
    }
    @Test public void recentProgressAndStalePricesStillWithholdFallbackMaintenanceWithoutDroppingReplacement()throws Exception {
        for(boolean stale:new boolean[]{true,false}) {
            var f=new Fixture();if(stale)f.quotes(now-301);else {f.first.observeFillAt(now-30);f.second.observeFillAt(now-30);}
            f.run();verify(f.refresh.companion,never()).authorizeSale(any());verify(f.refresh.guide).setSuggestion(f.refused);f.retained();
        }
    }
    @Test public void refusedReplacementAlsoAllowsIndependentSupportedBuyMaintenance()throws Exception {
        var f=new Fixture();f.quotes(now-301);
        var buy=new TrackedOffer(3,564,true,100,10,now-3600);buy.setState("BUYING");f.board.add(buy);
        when(f.offers.getOffer(3)).thenReturn(buy);when(f.offers.itemsBeingBought()).thenReturn(Set.of(564));
        when(f.offers.itemsWithOpenOffers()).thenReturn(Set.of(562,563,564));
        var snapshot=f.market.getSnapshot();var prices=new HashMap<>(snapshot.getLatest());prices.put(564,new LatestPrice(150,now,140,now));
        when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(snapshot.getMetadata(),prices,snapshot.getFiveMinute(),Map.of(),Instant.ofEpochSecond(now)));
        var state=new AccountState(true,true,false,100000000,0,0,5,8,1000,false,Map.of(),Map.of(),Map.of());
        when(f.refresh.monitor.getState()).thenReturn(state);when(f.refresh.monitor.refresh()).thenReturn(state);f.syncClientSlots(state);f.run();
        var shown=ArgumentCaptor.forClass(Suggestion.class);verify(f.refresh.guide).setSuggestion(shown.capture());
        assertEquals(SuggestionType.MODIFY_BUY,shown.getValue().getType());assertEquals(564,shown.getValue().getItemId());
        assertTrue(shown.getValue().getPrice()>100);verify(f.refresh.companion).selectBuyMaintenanceCancellation(shown.getValue());
        verify(f.refresh.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());f.retained();
    }
    private static void inject(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private static Object read(Object target,String name)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
}
