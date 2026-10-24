package com.flippingfriend;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.*;
import java.time.Instant;
import java.util.*;
import net.runelite.api.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Production engine + worker/client delivery, with a durable cancelled sale and a busy board. */
public class FullBoardSaleReservationWorkflowTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=Instant.now().getEpochSecond();
    class Fixture {
        final AdviceDeliveryBoundaryTest.Fixture delivery=new AdviceDeliveryBoundaryTest.Fixture();
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"fixture");
        final OfferTracker offers=mock(OfferTracker.class);
        final MarketDataService market=mock(MarketDataService.class);
        final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
        final List<TrackedOffer> board=new ArrayList<>();
        final SellRelistIntent relists=new SellRelistIntent(storage);
        final SuggestionEngine engine;
        final AccountLedgerSnapshot ledger;
        Fixture(int free,boolean collectedSale)throws Exception {
            when(offers.hasCompleteOfferEvidence()).thenReturn(true);
            when(offers.getOffers()).thenAnswer(c->List.copyOf(board));
            when(offers.itemsBeingBought()).thenAnswer(c->{var r=new HashSet<Integer>();for(var o:board)if(o.isBuying())r.add(o.getItemId());return r;});
            when(offers.listedForSale()).thenAnswer(c->{var r=new HashMap<Integer,Integer>();for(var o:board)if(!o.isBuying())r.merge(o.getItemId(),o.getRemaining(),Integer::sum);return r;});
            when(offers.itemsWithOpenOffers()).thenAnswer(c->{var r=new HashSet<Integer>();for(var o:board)r.add(o.getItemId());return r;});
            when(offers.getOffer(anyInt())).thenAnswer(c->{int slot=c.getArgument(0);return board.stream().filter(o->o.getSlot()==slot).findFirst().orElse(null);});
            if(collectedSale) {
                var old=new TrackedOffer(0,561,false,200,7,now-7200);old.setState("SELLING");
                assertTrue(relists.advise(old,storage.accountId(),true,null));
                old.setState("CANCELLED_SELL");old.setCollected(true);when(offers.getSettledOffer(0)).thenReturn(old);
                assertEquals(7,relists.collectedRequest(offers,storage.accountId()).getQuantity());
                when(offers.getSettledOffer(0)).thenReturn(null);
            }
            for(int i=0;i<8-free;i++) {
                var o=new TrackedOffer(i,562+i,i==7, i==7?100:200,10,now-(i==7?1:7200));
                o.setState(i==7?"BUYING":"SELLING");board.add(o);
            }
            when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
            var metadata=new HashMap<Integer,ItemMetadata>();var prices=new HashMap<Integer,LatestPrice>();var trends=new HashMap<Integer,Candle>();
            long bucket=(now/300-1)*300;
            for(int item=561;item<571;item++) {
                metadata.put(item,new ItemMetadata(item,"Item "+item,false,10000,0));
                prices.put(item,new LatestPrice(150,now,140,now));trends.put(item,new Candle(bucket,150,140,1000,1000));
            }
            when(market.getSnapshot()).thenReturn(new MarketSnapshot(metadata,prices,trends,Map.of(),Instant.ofEpochSecond(now)));
            when(market.getSeries(anyInt(),anyString())).thenReturn(List.of(new Candle(bucket,150,140,1000,1000),new Candle(bucket-300,150,140,1000,1000)));
            var tax=new TaxCalculator();var limits=mock(BuyLimitTracker.class);when(limits.remaining(anyInt(),anyInt(),any())).thenReturn(10000);
            var skips=mock(SkipList.class);when(skips.skipped(any())).thenReturn(Set.of());
            engine=new SuggestionEngine(market,new FeatureEngine(),null,null,new Explainer(),null,tax,delivery.monitor,limits,
                mock(PositionBook.class),offers,new SellTimingEngine(new FillModel(),tax),new TradePlans(storage),config,skips,null);
            set(engine,"companion",delivery.companion);set(engine,"sellRelist",new SellRelistIntent(storage));engine.setObservedAccount(storage.accountId());
            ledger=new AccountLedgerSnapshot(storage.accountId(),now,0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(
                List.of(PositionProjection.ready(561,"Retained sale",7,700,now-10000,now,"acquisition","original-buy")),List.of(),Map.of());
            when(delivery.companion.tradingLedger()).thenReturn(ledger);
            when(delivery.companion.nextAction()).thenReturn(null);
            when(delivery.companion.selectBuyMaintenanceCancellation(any())).thenAnswer(c->c.getArgument(0));
            when(delivery.companion.authorizeSale(any())).thenAnswer(c->{Suggestion s=c.getArgument(0);return s==null || s.getType()==SuggestionType.MODIFY_SELL?null:s;});
            when(delivery.companion.nextEntrySuggestion(any(),anySet(),anySet(),anySet())).thenReturn(delivery.advice);
            delivery.set("engine",engine);delivery.set("offerTracker",offers);delivery.set("config",config);delivery.set("marketData",market);
            slots(free,7);
        }
        void slots(int free,int inventory)throws Exception {
            long reserved=board.stream().filter(TrackedOffer::isBuying).mapToLong(o->(long)o.getPrice()*o.getRemaining()).sum();
            var state=new AccountState(true,true,false,100000000,0,0,free,8,reserved,false,Map.of(561,inventory),Map.of(561,inventory),Map.of());
            when(delivery.monitor.getState()).thenReturn(state);when(delivery.monitor.refresh()).thenReturn(state);
            var raw=new GrandExchangeOffer[8];
            for(int i=0;i<8;i++){raw[i]=mock(GrandExchangeOffer.class);when(raw[i].getState()).thenReturn(GrandExchangeOfferState.EMPTY);}
            for(var o:board) {
                var r=raw[o.getSlot()];when(r.getState()).thenReturn(GrandExchangeOfferState.valueOf(o.getState()));
                when(r.getItemId()).thenReturn(o.getItemId());when(r.getPrice()).thenReturn((long)o.getPrice());when(r.getTotalQuantity()).thenReturn(o.getTotalQuantity());
            }
            var field=FlippingFriendPlugin.class.getDeclaredField("client");field.setAccessible(true);
            when(((Client)field.get(delivery.plugin)).getGrandExchangeOffers()).thenReturn(raw);
            assertNotNull(PlanningAccountObservation.capture(storage.accountId(),state,raw,board,now));
        }
        Suggestion run()throws Exception {
            clearInvocations(delivery.guide);delivery.queueAdvice();delivery.clientQueue.remove().run();
            var shown=ArgumentCaptor.forClass(Suggestion.class);verify(delivery.guide).setSuggestion(shown.capture());return shown.getValue();
        }
    }
    @Test public void refusedListedMaintenanceCannotSpendTheCancelledSalesLastSlotOnAnotherBuy()throws Exception {
        var f=new Fixture(1,true);
        // Temporarily unavailable pricing retains a collected sale but still leaves routine maintenance.
        when(f.delivery.companion.tradingLedger()).thenReturn(null);
        for(int pass=0;pass<3;pass++)assertEquals(SuggestionType.WAIT,f.run().getType());
        verify(f.delivery.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
        when(f.delivery.companion.tradingLedger()).thenReturn(f.ledger);
        assertEquals(SuggestionType.SELL,f.run().getType());
        assertEquals(7,new SellRelistIntent(f.storage).collectedRequest(f.offers,f.storage.accountId()).getQuantity());
    }
    @Test public void fullBoardKeepsFreshBuyWhenTheProposedSaleRefusesButStillMakesRoomForAnAuthorizedExit()throws Exception {
        var f=new Fixture(0,false);
        when(f.delivery.companion.authorizeSale(argThat(s->s!=null && s.getType()==SuggestionType.SELL)))
            .thenReturn(Suggestion.waiting("Exit refused","Current evidence changed"));
        assertEquals(SuggestionType.WAIT,f.run().getType());
        assertEquals("BUYING",f.board.get(7).getState());
        when(f.delivery.companion.authorizeSale(argThat(s->s!=null && s.getType()==SuggestionType.SELL))).thenAnswer(c->c.getArgument(0));
        assertEquals(SuggestionType.CANCEL,f.run().getType());
        f.board.remove(7);f.slots(1,7);
        assertEquals("Collection must lead to the intended sale, not another buy",SuggestionType.SELL,f.run().getType());
        verify(f.delivery.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
    }
    @Test public void spareCapacityStillAllowsUnrelatedEntriesDuringSaleReadOutage()throws Exception {
        var f=new Fixture(2,true);when(f.delivery.companion.tradingLedger()).thenReturn(null);
        assertEquals(SuggestionType.BUY,f.run().getType());
        verify(f.delivery.companion).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
    }
    @Test public void aggregateSameItemListingsCannotConsumeAnUnclaimedCollectedOriginDuringLedgerOutage()throws Exception {
        var f=new Fixture(1,true);when(f.delivery.companion.tradingLedger()).thenReturn(null);
        when(f.offers.listedForSale()).thenReturn(Map.of(561,100));
        assertEquals(SuggestionType.WAIT,f.run().getType());
        verify(f.delivery.companion,never()).nextEntrySuggestion(any(),anySet(),anySet(),anySet());
        assertEquals(7,new SellRelistIntent(f.storage).collectedRequest(f.offers,f.storage.accountId()).getQuantity());
    }
    static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
