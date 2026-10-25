package com.flippingfriend;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real candidate selection -> final authority -> presentation -> durable selected remainder. */
public class OrdinarySaleMaintenanceQueueTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Instant now=Instant.now();
    final String account=AccountIdentity.fromProfileKey("fixture");
    final class Sales {
        final CompanionClient companion;
        final OfferTracker offers=mock(OfferTracker.class);
        final AccountMonitor monitor=mock(AccountMonitor.class);
        final MarketDataService market=mock(MarketDataService.class);
        final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
        final PluginStorage storage;
        final SuggestionEngine engine;
        final TrackedOffer first=sale(0,561,150),second=sale(1,562,145);
        final List<TrackedOffer> active=new ArrayList<>(List.of(first,second));
        final Map<Integer,LatestPrice> prices=new HashMap<>();
        Sales(CompanionClient companion)throws Exception {
            this.companion=companion;storage=TestStorage.rootedAt(folder.newFolder().toPath(),"fixture");
            // Six unrelated listings occupy the other slots. No unsupported price can make them candidates.
            for(int slot=2;slot<8;slot++)active.add(sale(slot,2000+slot,120));
            when(offers.getOffers()).thenReturn(active);when(offers.getOffer(anyInt())).thenAnswer(call->active.stream().filter(o->o.getSlot()==(int)call.getArgument(0)).findFirst().orElse(null));
            when(offers.hasCompleteOfferEvidence()).thenReturn(true);
            when(offers.listedForSale()).thenReturn(Map.of(561,2,562,2));when(offers.itemsBeingBought()).thenReturn(Set.of());
            var state=new AccountState(true,true,false,100000000,0,0,0,8,0,false,Map.of(),Map.of(),Map.of());
            when(monitor.getState()).thenReturn(state);when(monitor.refresh()).thenReturn(state);
            when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
            prices.put(561,price(120,110));prices.put(562,price(120,110));
            when(market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(561,new ItemMetadata(561,"Nature rune",false,1000,0),
                562,new ItemMetadata(562,"Chaos rune",false,1000,0)),prices,Map.of(),Map.of(),now));
            long end=now.getEpochSecond()/300*300;
            when(market.getSeries(anyInt(),anyString())).thenReturn(List.of(new Candle(end-300,120,110,100,100),new Candle(end-600,120,110,100,100)));
            when(companion.tradingLedger()).thenReturn(ledger(positions()));
            when(companion.lastLedger()).thenAnswer(call->companion.tradingLedger());
            var tax=new TaxCalculator();var skips=mock(SkipList.class);when(skips.skipped(any())).thenReturn(Set.of());
            var limits=mock(BuyLimitTracker.class);when(limits.remaining(anyInt(),anyInt(),any())).thenReturn(1000);
            engine=new SuggestionEngine(market,new FeatureEngine(),null,null,new Explainer(),null,tax,monitor,limits,
                mock(PositionBook.class),offers,new SellTimingEngine(new FillModel(),tax),mock(TradePlans.class),config,skips,null);
            set(engine,"companion",companion);set(engine,"sellRelist",new SellRelistIntent(storage));engine.setObservedAccount(account);
        }
        List<PositionProjection> positions(){return List.of(holding(561),holding(562));}
        PositionProjection holding(int item){return PositionProjection.ready(item,"Rune",3,301,now.getEpochSecond()-3600,now.getEpochSecond(),"buy-"+item,"episode-"+item);}
        AccountLedgerSnapshot ledger(List<PositionProjection> positions){return new AccountLedgerSnapshot(account,now.getEpochSecond(),0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(positions,List.of(),Map.of());}
        Suggestion next(){return engine.refresh(false);}
        void publishPrices() {
            var previous=market.getSnapshot();
            when(market.getSnapshot()).thenReturn(new MarketSnapshot(previous.getMetadata(),prices,previous.getFiveMinute(),
                previous.getHourly(),now,previous.getLatestFeedObservedAt()));
        }
        void buy(int low)throws Exception {
            var buy=new TrackedOffer(2,563,true,100,2,now.getEpochSecond()-3600);buy.setState("BUYING");active.set(2,buy);
            var previous=market.getSnapshot();var mapping=new HashMap<>(previous.getMetadata());mapping.put(563,new ItemMetadata(563,"Buy rune",false,1000,0));
            prices.put(563,price(160,low));when(market.getSnapshot()).thenReturn(new MarketSnapshot(mapping,prices,
                Map.of(563,new Candle(now.getEpochSecond()/300*300-300,160,low,100,100)),Map.of(),now));
        }
    }
    TrackedOffer sale(int slot,int item,int ask){var o=new TrackedOffer(slot,item,false,ask,2,now.getEpochSecond()-3600);o.setState("SELLING");return o;}
    LatestPrice price(int high,int low){return new LatestPrice(high,now.getEpochSecond(),low,now.getEpochSecond());}
    static void set(Object target,String name,Object value)throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    @Test public void everyEligibleOrdinarySaleRetainsItsExactSubsetAndOrderWithoutCreatingIntent()throws Exception {
        var f=new Sales(mock(CompanionClient.class));var first=f.next();var queue=f.engine.saleCandidates();
        assertEquals(SuggestionType.MODIFY_SELL,first.getType());assertEquals(2,queue.size());assertSame(first,queue.get(0));
        assertEquals(561,queue.get(0).getItemId());assertEquals(562,queue.get(1).getItemId());
        for(var candidate:queue){assertEquals(2,candidate.getQuantity());assertEquals(119,candidate.getPrice());assertFalse(candidate.isLossCut());
            assertEquals(new TaxCalculator().netProceeds(candidate.getItemId(),119,2)-200,candidate.getExpectedProfit());}
        assertFalse(new SellRelistIntent(f.storage).requiresManagedCost(account));
        try{queue.clear();fail("Queue must be immutable");}catch(UnsupportedOperationException expected){}
    }
    @Test public void realPipelineTriesLaterSaleAndOnlyPersistsThePresentedEpisodeThroughLateFillAndRestart()throws Exception {
        var delivery=new AdviceDeliveryBoundaryTest.Fixture();var f=new Sales(delivery.companion);
        delivery.set("engine",f.engine);delivery.set("offerTracker",f.offers);delivery.set("accountMonitor",f.monitor);delivery.set("config",f.config);
        var clientField=FlippingFriendPlugin.class.getDeclaredField("client");clientField.setAccessible(true);
        var client=(net.runelite.api.Client)clientField.get(delivery.plugin);
        net.runelite.api.GrandExchangeOffer[] live=new net.runelite.api.GrandExchangeOffer[8];
        for(var offer:f.active) {
            var observed=mock(net.runelite.api.GrandExchangeOffer.class);when(observed.getState()).thenReturn(net.runelite.api.GrandExchangeOfferState.SELLING);
            when(observed.getItemId()).thenReturn(offer.getItemId());when(observed.getPrice()).thenReturn((long)offer.getPrice());
            when(observed.getTotalQuantity()).thenReturn(offer.getTotalQuantity());live[offer.getSlot()]=observed;
        }
        when(client.getGrandExchangeOffers()).thenReturn(live);
        when(delivery.companion.nextAction()).thenReturn(null);when(delivery.guide.isPaused()).thenReturn(false);
        when(delivery.companion.authorizeSale(any())).thenAnswer(call->{Suggestion s=call.getArgument(0);if(s.getItemId()==561)return null;s.bindSaleRepriceSource("second-authority");return s;});
        when(delivery.guide.getSuggestion()).thenAnswer(call->f.engine.saleCandidates().get(1));
        delivery.set("notifier",mock(net.runelite.client.Notifier.class));
        delivery.queueAdvice();var queue=f.engine.saleCandidates();delivery.clientQueue.remove().run();
        verify(delivery.companion).authorizeSale(queue.get(0));verify(delivery.companion).authorizeSale(queue.get(1));
        verify(delivery.guide).withdrawTrade(eq(queue.get(0)),any());verify(delivery.guide).setSuggestion(queue.get(1));
        verify(delivery.companion).recordPresentedAdvice(eq(queue.get(1)),anyString(),anyInt());
        verify(delivery.companion,never()).recordPresentedAdvice(eq(queue.get(0)),anyString(),anyInt());
        f.second.setState("CANCELLED_SELL");f.second.setQuantityFilled(1);f.second.setSpent(145);f.second.setCollected(true);
        assertTrue(new SellRelistIntent(f.storage).collectedRequests(f.offers,account).isEmpty());
        f.active.remove(f.second);when(f.offers.getSettledOffer(1)).thenReturn(f.second);
        var remaining=new SellRelistIntent(f.storage).collectedRequests(f.offers,account);
        assertEquals(1,remaining.size());assertEquals(562,remaining.get(0).getItemId());assertEquals(1,remaining.get(0).getQuantity());
        assertEquals("second-authority",remaining.get(0).getSaleRepriceSource());assertEquals("SELLING",f.first.getState());
    }
    @Test public void recentProgressStalePricesAndCostFloorsDoNotBecomeCandidates()throws Exception {
        var f=new Sales(mock(CompanionClient.class));f.first.observeFillAt(now.getEpochSecond()-1);
        assertEquals(562,f.next().getItemId());assertEquals(1,f.engine.saleCandidates().size());
        f.prices.put(562,new LatestPrice(120,now.getEpochSecond()-301,110,now.getEpochSecond()));
        f.publishPrices();
        assertEquals(SuggestionType.WAIT,f.next().getType());assertTrue(f.engine.saleCandidates().isEmpty());
        f.prices.put(562,price(120,110));when(f.companion.tradingLedger()).thenReturn(f.ledger(List.of(f.holding(561),
            PositionProjection.ready(562,"Rune",3,600,now.getEpochSecond()-3600,now.getEpochSecond(),"buy-562","episode-562"))));
        f.publishPrices();
        assertEquals(SuggestionType.WAIT,f.next().getType());assertTrue(f.engine.saleCandidates().isEmpty());
    }
    @Test public void accountChangesAndSlotReuseCannotRetainThePreviousCandidateQueue()throws Exception {
        var f=new Sales(mock(CompanionClient.class));var old=f.next();f.active.set(0,sale(0,561,140));
        f.next();assertFalse(f.engine.saleCandidates().contains(old));assertEquals(562,f.engine.saleCandidates().get(0).getItemId());
        f.engine.setObservedAccount(AccountIdentity.fromProfileKey("another"));f.next();assertTrue(f.engine.saleCandidates().isEmpty());
    }
    @Test public void laterFreshStopStillPrecedesOrdinaryMaintenance()throws Exception {
        var f=new Sales(mock(CompanionClient.class));when(f.companion.tradingLedger()).thenReturn(f.ledger(List.of(f.holding(561),
            f.holding(562).withExitContext(new AcquisitionExitContext(160,125,180).bind("buy-562")))));
        var next=f.next();assertEquals(562,next.getItemId());assertTrue(next.isLossCut());assertTrue(f.engine.saleCandidates().get(0).isLossCut());
    }
    @Test public void collectingLaterSalesDoesNotEvaluateOrPromiseALowerPriorityBuy()throws Exception {
        var f=new Sales(mock(CompanionClient.class));f.buy(115);
        assertEquals(SuggestionType.MODIFY_SELL,f.next().getType());assertEquals(2,f.engine.saleCandidates().size());
        verify(f.offers,never()).freezeBuyMaintenanceDeadline(eq(2),anyString(),anyDouble(),anyDouble(),anyLong(),anyString());
        var field=SuggestionEngine.class.getDeclaredField("reprice");field.setAccessible(true);assertNull(field.get(f.engine));
    }
    @Test public void afterSalesRefuseOnlyUnexcludedWorkingBuysAreReassessedWithoutAnUnseenPromise()throws Exception {
        var f=new Sales(mock(CompanionClient.class));f.buy(115);assertEquals(SuggestionType.MODIFY_SELL,f.next().getType());
        var next=f.engine.buyMaintenanceAfterSaleRefusal(Set.of());assertNotNull(next);
        assertEquals(SuggestionType.MODIFY_BUY,next.getType());assertEquals(563,next.getItemId());assertTrue(next.getPrice()>100);
        assertEquals(2,next.getQuantity());assertNotNull(next.getMaintenanceOfferIdentity());
        var field=SuggestionEngine.class.getDeclaredField("reprice");field.setAccessible(true);assertNull(field.get(f.engine));
        assertNull(f.engine.buyMaintenanceAfterSaleRefusal(Set.of(next.getMaintenanceOfferIdentity())));
        assertEquals(2,f.engine.saleCandidates().size());
    }
    @Test public void fallbackBuyMaintenanceStillRejectsRecentFillsAndStaleQuotes()throws Exception {
        for(boolean recent:List.of(true,false)) {
            var f=new Sales(mock(CompanionClient.class));f.buy(115);assertEquals(SuggestionType.MODIFY_SELL,f.next().getType());
            if(recent)f.active.get(2).observeFillAt(now.getEpochSecond()-1);
            else f.prices.put(563,new LatestPrice(160,now.getEpochSecond()-301,115,now.getEpochSecond()));
            f.publishPrices();
            assertNull(f.engine.buyMaintenanceAfterSaleRefusal(Set.of()));
        }
    }
    @Test public void anEarlierEligibleBuyRetainsItsOriginalSelectionPriority()throws Exception {
        var f=new Sales(mock(CompanionClient.class));f.buy(130);var next=f.next();
        assertEquals(SuggestionType.MODIFY_BUY,next.getType());assertEquals(563,next.getItemId());assertTrue(f.engine.saleCandidates().isEmpty());
    }
}
