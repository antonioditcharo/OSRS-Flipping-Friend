package com.flippingfriend;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.overlay.*;
import com.flippingfriend.session.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real selected-editor worker/client delivery with deliberately changed physical/setup evidence. */
public class SelectedSaleEditorBoundaryTest {
    private static final class Fixture extends AdviceDeliveryBoundaryTest.Fixture {
        final AtomicReference<int[]> setup=new AtomicReference<>(new int[]{13263,1});
        final StepGuide real;
        final Instant now=Instant.now();
        Fixture()throws Exception {
            var client=(Client)field("client");when(client.isClientThread()).thenReturn(true);
            var resolver=(GeWidgetResolver)field("widgetResolver");when(resolver.sellSetup()).thenAnswer(c->setup.get());
            real=new StepGuide(client,resolver);real.setSuggestion(advice);set("stepGuide",real);
            var tax=new TaxCalculator();set("selectedSalePricing",new SelectedSalePricing(new SellTimingEngine(new FillModel(),tax),tax));
            when(((SuggestionEngine)field("engine")).recordPresentedMaintenance(any(),anyString(),anyLong())).thenReturn(true);
            inventory(3);
            String account=AccountIdentity.fromProfileKey("fixture");
            var holding=PositionProjection.ready(13263,"Abyssal bludgeon",3,3203,now.getEpochSecond()-60,now.getEpochSecond(),"buy","episode")
                .withExitContext(new AcquisitionExitContext(1400,900,30).bind("original"));
            when(companion.tradingLedger()).thenReturn(new AccountLedgerSnapshot(account,now.getEpochSecond(),0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(List.of(holding),List.of(),Map.of()));
            var market=(MarketDataService)field("marketData");
            when(market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(),Map.of(13263,new LatestPrice(1000,now.getEpochSecond(),950,now.getEpochSecond())),Map.of(),Map.of(),now));
            long end=now.getEpochSecond()/300*300;
            when(market.getSeries(eq(13263),eq("5m"))).thenReturn(List.of(new Candle(end-600,1400,950,100,100),new Candle(end-300,1400,950,100,100)));
        }
        Object field(String name)throws Exception {var f=FlippingFriendPlugin.class.getDeclaredField(name);f.setAccessible(true);return f.get(plugin);}
        void inventory(int n){when(monitor.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(13263,n),Map.of(13263,n),Map.of()));}
        void selected(){plugin.onVarbitChanged(new net.runelite.api.events.VarbitChanged());}
        void compute(){assertEquals(1,worker.tasks.size());worker.tasks.remove().run();}
        void deliver(){assertEquals(1,clientQueue.size());clientQueue.remove().run();}
    }
    @Test public void selectedSubsetOverridesUnrelatedForegroundOnlyAfterWorkerAuthority()throws Exception {
        var f=new Fixture();f.selected();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        verify(f.companion,never()).authorizeSale(any());f.compute();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        f.deliver();var displayed=f.real.getSuggestion();assertEquals(SuggestionType.SELL,displayed.getType());
        assertEquals(1,displayed.getQuantity());assertEquals(1400,displayed.getPrice());assertTrue(displayed.isProfitKnown());
        verify(f.companion).authorizeSale(same(displayed));verify(f.companion).recordPresentedAdvice(same(displayed),anyString(),anyInt());
    }
    @Test public void backgroundReverificationKeepsOnlyTheStillValidSelectedQuote()throws Exception {
        var f=new Fixture();var clock=new java.util.concurrent.atomic.AtomicLong(100);
        f.set("adviceClock",(java.util.function.LongSupplier)clock::get);
        f.selected();f.compute();f.deliver();var shown=f.real.getSuggestion();assertEquals(SuggestionType.SELL,shown.getType());
        clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(2));f.selected();
        assertSame("Refresh starts before expiration without replacing the valid quote by a spinner",shown,f.real.getSuggestion());
        for(int i=0;i<8;i++)f.selected();assertEquals(1,f.worker.tasks.size());
        when(f.companion.authorizeSale(any())).thenReturn(null);f.compute();f.deliver();
        assertEquals("An actual refusal still withdraws the old price immediately",SuggestionType.WAIT,f.real.getSuggestion().getType());
        clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(1));f.selected();assertEquals(1,f.worker.tasks.size());
    }
    @Test public void aVerifiedFrameWakesAWaitingSaleWithoutWaitingForItsRetryTimer()throws Exception {
        var f=new Fixture();when(f.companion.authorizeSale(any())).thenReturn(Suggestion.waiting("Account frame pending","Need current source proof"));
        f.selected();f.compute();f.deliver();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        when(f.companion.authorizeSale(any())).thenAnswer(c->c.getArgument(0));
        var wake=FlippingFriendPlugin.class.getDeclaredMethod("wakeSelectedSaleAfterFrame",long.class,long.class,long.class);wake.setAccessible(true);
        long epoch=((java.util.concurrent.atomic.AtomicLong)f.field("engineRefreshEpoch")).get();
        long generation=((java.util.concurrent.atomic.AtomicLong)f.field("offerObservationGeneration")).get();
        long revision=((java.util.concurrent.atomic.AtomicLong)f.field("adviceObservationRevision")).get();
        wake.invoke(f.plugin,epoch,generation,revision);assertEquals("A fresh frame is an immediate retry trigger",1,f.worker.tasks.size());
        f.compute();f.deliver();assertEquals(SuggestionType.SELL,f.real.getSuggestion().getType());
        wake.invoke(f.plugin,epoch,generation,revision);assertTrue("A valid sale is not withdrawn/reverified by this wake",f.worker.tasks.isEmpty());
    }
    @Test public void anOlderFrameCannotWakeANewerEditorContext()throws Exception {
        var f=new Fixture();f.selected();f.compute();f.deliver();
        var wake=FlippingFriendPlugin.class.getDeclaredMethod("wakeSelectedSaleAfterFrame",long.class,long.class,long.class);wake.setAccessible(true);
        wake.invoke(f.plugin,-1L,-1L,-1L);assertTrue(f.worker.tasks.isEmpty());assertEquals(SuggestionType.SELL,f.real.getSuggestion().getType());
    }
    @Test public void expiredOrChangedSelectedQuoteIsWithdrawnEvenWhileTheRefreshWorkerWaits()throws Exception {
        var f=new Fixture();var clock=new java.util.concurrent.atomic.AtomicLong(100);
        f.set("adviceClock",(java.util.function.LongSupplier)clock::get);
        f.selected();f.compute();f.deliver();clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(2));f.selected();
        assertEquals(SuggestionType.SELL,f.real.getSuggestion().getType());
        clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(3));f.selected();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        assertEquals(1,f.worker.tasks.size());f.compute();f.deliver();assertEquals(SuggestionType.SELL,f.real.getSuggestion().getType());
        f.setup.set(new int[]{13263,2});f.selected();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
    }
    @Test public void refusalNeverFallsBackToGenericBelowCostQuote()throws Exception {
        var f=new Fixture();when(f.companion.authorizeSale(any())).thenReturn(null);
        f.selected();f.compute();f.deliver();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        verify(f.companion,never()).recordPresentedAdvice(argThat(s->s!=null && s.getType()==SuggestionType.SELL),anyString(),anyInt());
    }
    @Test public void changedInventoryImmediatelyWithdrawsDisplayedAndHiddenSaleAdvice()throws Exception {
        var f=new Fixture();f.selected();f.compute();f.deliver();assertEquals(SuggestionType.SELL,f.real.getSuggestion().getType());
        f.plugin.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(93,null));
        assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        f.real.setSuggestion(Suggestion.builder(SuggestionType.SELL).item(13263,"Stock").quantity(3).price(1400).build());
        f.real.setEditorQuote(Suggestion.waiting("Verifying subset","Still checking"));
        f.plugin.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(93,null));
        f.setup.set(null);f.selected();assertEquals("Closing the editor cannot revive its hidden old sale",SuggestionType.WAIT,f.real.getSuggestion().getType());
    }
    @Test public void inventoryChangeBeforeDeliveryWithdrawsVerifiedSubset()throws Exception {
        var f=new Fixture();f.selected();f.compute();f.inventory(0);f.deliver();
        assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());assertEquals(1,f.worker.tasks.size());
        f.compute();f.deliver();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
    }
    @Test public void closingAndReopeningTheSameEditorCannotReviveItsPriorReceipt()throws Exception {
        var f=new Fixture();f.selected();f.compute();f.setup.set(null);f.selected();assertSame(f.advice,f.real.getSuggestion());
        f.setup.set(new int[]{13263,1});f.selected();f.deliver();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        assertEquals(1,f.worker.tasks.size());f.compute();f.deliver();assertEquals(SuggestionType.SELL,f.real.getSuggestion().getType());
    }
    @Test public void extraSelectedUnitsRemainUnpricedByTheManagedPlan()throws Exception {
        var f=new Fixture();f.inventory(30);f.setup.set(new int[]{13263,4});f.selected();f.compute();f.deliver();
        assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());verify(f.companion,never()).authorizeSale(any());
    }
    @Test public void savedForegroundOriginSurvivesCheckingOverlayAndSelectedQuantityChanges()throws Exception {
        var f=new Fixture();var engine=(SuggestionEngine)f.field("engine");
        f.real.setSuggestion(Suggestion.builder(SuggestionType.SELL).item(13263,"Stock").quantity(2).price(1400).continuationOrigin("saved-B").build());
        f.selected();f.compute();f.deliver();
        verify(engine).selectedSaleReprice(13263,1,"saved-B");
        f.setup.set(new int[]{13263,2});f.selected();f.compute();f.deliver();
        verify(engine).selectedSaleReprice(13263,2,"saved-B");
    }
    @Test public void ambiguousSavedSaleCannotFallThroughToGenericSelectedPricing()throws Exception {
        var f=new Fixture();var engine=(SuggestionEngine)f.field("engine");
        when(engine.hasPendingSaleContinuation(13263)).thenReturn(true);
        f.selected();f.compute();f.deliver();assertEquals(SuggestionType.WAIT,f.real.getSuggestion().getType());
        verify(f.companion,never()).authorizeSale(any());
    }
}
