package com.flippingfriend.session;

import com.flippingfriend.companion.*;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.runelite.api.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real callback-before-capture ordering, forced outbox, persisted claims and account files. */
public class DistinctContinuationWorkflowTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    class Fixture {
        final boolean buying;
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"distinct");
        final OfferEventOutbox outbox=new OfferEventOutbox(storage);
        final CompanionClient companion=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox);
        final BuyRepriceIntent buys=new BuyRepriceIntent(storage);
        final SellRelistIntent sells=new SellRelistIntent(storage);
        final OfferTracker tracker=new OfferTracker(storage,new PositionBook(storage),mock(BuyLimitTracker.class),
            mock(TradeJournal.class),new TaxCalculator(),new TradePlans(storage));
        final String a,b;
        Fixture(boolean buying)throws Exception {
            this.buying=buying;
            inject(tracker,"buyReprice",buys);inject(tracker,"sellRelist",sells);
            tracker.setContinuationSourceReader(companion::continuationSourceEpisodes);
            frame(0,100,20,active(),true);frame(1,100,30,active(),true);
            a=tracker.getOffer(0).getContinuationIdentity();b=tracker.getOffer(1).getContinuationIdentity();
            assertTrue(buying?buys.advise(tracker.getOffer(0),storage.accountId(),tracker,now()):sells.advise(tracker.getOffer(0),storage.accountId(),false,tracker));
            assertTrue(buying?buys.advise(tracker.getOffer(1),storage.accountId(),tracker,now()):sells.advise(tracker.getOffer(1),storage.accountId(),false,tracker));
            frame(0,100,20,cancelled(),true);frame(0,100,0,GrandExchangeOfferState.EMPTY,true);
            frame(1,100,30,cancelled(),true);frame(1,100,0,GrandExchangeOfferState.EMPTY,true);
            assertEquals(2,caps().size());
        }
        GrandExchangeOfferState active(){return buying?GrandExchangeOfferState.BUYING:GrandExchangeOfferState.SELLING;}
        GrandExchangeOfferState cancelled(){return buying?GrandExchangeOfferState.CANCELLED_BUY:GrandExchangeOfferState.CANCELLED_SELL;}
        void frame(int slot,int total,int filled,GrandExchangeOfferState state,boolean capture) {
            var raw=mock(GrandExchangeOffer.class);when(raw.getState()).thenReturn(state);when(raw.getItemId()).thenReturn(561);
            when(raw.getPrice()).thenReturn(100L);when(raw.getTotalQuantity()).thenReturn(total);
            when(raw.getQuantitySold()).thenReturn(filled);when(raw.getSpent()).thenReturn(100L*filled);
            tracker.onOfferChanged(slot,raw);
            if(capture) {
                if(state==GrandExchangeOfferState.EMPTY)companion.captureObservedOfferCleared(slot,storage.accountId(),now());
                else {var copy=tracker.getOffer(slot).copyObservation();companion.captureObservedOffer(copy,storage.accountId(),Math.max(copy.getFirstSeen(),now()));}
                tracker.reconcileContinuationSources();
            }
        }
        void accept(String origin,int quantity) {
            var proof=buying?buys.acceptSuccessor(origin,561,100,quantity,tracker,storage.accountId(),now())
                :sells.acceptSuccessor(origin,561,100,quantity,tracker,storage.accountId(),now());
            assertNotNull(proof);
            var advice=Suggestion.builder(buying?SuggestionType.BUY:SuggestionType.SELL).item(561,"Nature rune")
                .price(100).quantity(quantity).continuationOrigin(origin).build();
            advice.bindContinuationSuccessorProof(proof);companion.recordPresentedAdvice(advice,storage.accountId());
        }
        Map<String,Integer> caps() {
            var result=new LinkedHashMap<String,Integer>();
            if(buying)for(var request:buys.collectedRequests(tracker,storage.accountId(),now()))result.put(request.originIdentity,request.quantity);
            else for(var request:sells.collectedRequests(tracker,storage.accountId()))result.put(request.getOriginIdentity(),request.getQuantity());
            return result;
        }
        void ack()throws Exception{for(var e:outbox.pending())assertTrue(outbox.acknowledge(e.getEventId(),e.getSessionId(),e.getSequence()));}
    }
    @Test public void callbackBarrierThenExactSuccessorsPreserveSiblingThroughAckAndRestart()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.accept(f.a,80);f.frame(2,80,0,f.active(),false);
            assertTrue("Uncaptured new order must withhold both promises",f.caps().isEmpty());
            var child=f.tracker.getOffer(2).copyObservation();f.companion.captureObservedOffer(child,f.storage.accountId(),child.getFirstSeen());
            f.tracker.reconcileContinuationSources();assertEquals(Map.of(f.b,70),f.caps());
            var event=f.outbox.pending().get(f.outbox.pending().size()-1);assertNotNull(event.getContinuationSuccessorProof());
            assertEquals(f.a,event.getContinuationSuccessorProof().getOriginIdentity());f.ack();
            assertEquals(3,new OfferEventOutbox(f.storage).observedSuccessorEpisodes().size());
            if(buying)assertEquals(70,new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now()).quantity);
            else assertEquals(70,new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId()).getQuantity());
            f.accept(f.b,70);f.frame(3,70,0,f.active(),true);assertTrue(f.caps().isEmpty());
        }
    }
    @Test public void smallerSuccessorKeepsResidualAndChildCancellationCannotReplenishRoot()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.accept(f.a,50);f.frame(2,50,0,f.active(),true);
            assertEquals(Map.of(f.a,30,f.b,70),f.caps());
            var child=f.tracker.getOffer(2);String childId=child.getContinuationIdentity();
            assertTrue(buying?f.buys.advise(child,f.storage.accountId(),f.tracker,now()):f.sells.advise(child,f.storage.accountId(),false,f.tracker));
            f.frame(2,50,12,f.cancelled(),true);f.frame(2,50,0,GrandExchangeOfferState.EMPTY,true);
            assertEquals(Map.of(f.a,30,f.b,70,childId,38),f.caps());
            f.accept(childId,38);f.frame(4,38,0,f.active(),true);
            assertEquals("Grandchild reserves its immediate parent only",Map.of(f.a,30,f.b,70),f.caps());
        }
    }
    @Test public void unboundOrAlreadyFilledNewOrderCannotBorrowSavedOrigin()throws Exception {
        for(boolean buying:new boolean[]{true,false})for(boolean filled:new boolean[]{true,false}) {
            var f=new Fixture(buying);if(filled)f.accept(f.a,80);
            f.frame(2,80,filled?1:0,f.active(),true);
            assertTrue("Durably unqualified new order retains conservative supersession",f.caps().isEmpty());
            assertNull(f.outbox.observedSuccessorEpisodes().get(2).getContinuationSuccessorProof());
        }
    }
    @Test public void failedSuccessorAcceptanceCannotSelfCommitDuringCallbackRetries()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);Path blocked=f.storage.accountDir().resolve((buying?"buy-reprice-intent.json":"sell-relist-intent.json")+".tmp");
            Files.createDirectory(blocked);
            assertNull(buying?f.buys.acceptSuccessor(f.a,561,100,80,f.tracker,f.storage.accountId(),now())
                :f.sells.acceptSuccessor(f.a,561,100,80,f.tracker,f.storage.accountId(),now()));
            Files.delete(blocked);f.frame(2,80,0,f.active(),true);assertTrue(f.caps().isEmpty());
            assertNull(f.outbox.observedSuccessorEpisodes().get(2).getContinuationSuccessorProof());
        }
    }
    @Test public void selectedEditorUsesExplicitOriginOrUniqueFitNeverAnArbitrarySameItemHint()throws Exception {
        var f=new Fixture(false);var monitor=mock(AccountMonitor.class);
        when(monitor.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,false,
            Map.of(561,150),Map.of(561,150),Map.of()));
        var engine=new SuggestionEngine(null,null,null,null,null,null,null,monitor,null,null,f.tracker,null,null,null,null,null);
        inject(engine,"sellRelist",f.sells);engine.setObservedAccount(f.storage.accountId());
        assertNull("Both saved origins fit 50 units",engine.selectedSaleReprice(561,50));
        assertEquals(f.b,engine.selectedSaleReprice(561,50,f.b).getOriginIdentity());
        assertEquals(f.a,engine.selectedSaleReprice(561,75).getOriginIdentity());
        assertNull("A selected origin cannot silently borrow the other cap",engine.selectedSaleReprice(561,75,f.b));
        assertNull(engine.selectedSaleReprice(561,81));assertTrue(engine.hasPendingSaleContinuation(561));
    }
    @Test public void relationshipCommitFailureRetriesFromOriginalDurableSourceAfterAckAndRestart()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.accept(f.a,80);
            var blocked=f.storage.accountDir().resolve((buying?"buy-reprice-intent.json":"sell-relist-intent.json")+".tmp");
            Files.createDirectory(blocked);f.frame(2,80,0,f.active(),true);
            assertTrue("Uncommitted allocation cannot publish the sibling",f.caps().isEmpty());
            var first=f.outbox.successorBindings().get(0);f.ack();Files.delete(blocked);
            if(buying)assertEquals(70,new BuyRepriceIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId(),now()).quantity);
            else assertEquals(70,new SellRelistIntent(f.storage).collectedRequest(f.tracker,f.storage.accountId()).getQuantity());
            assertEquals(first.getEventId(),new OfferEventOutbox(f.storage).successorBindings().get(0).getEventId());
        }
    }
    @Test public void failedOutboxCaptureThenClearRetainsPendingBarrierUntilOriginalSourceCommits()throws Exception {
        for(boolean buying:new boolean[]{true,false}) {
            var f=new Fixture(buying);f.accept(f.a,80);
            var blocked=f.storage.accountDir().resolve("offer-event-outbox.json.tmp");Files.createDirectory(blocked);
            // The outbox cleans failed temporary files; keep a nonempty directory to model
            // storage remaining unavailable throughout the subsequent callbacks.
            Files.writeString(blocked.resolve("unavailable"),"fixture");
            f.frame(2,80,0,f.active(),true);assertTrue(f.caps().isEmpty());
            f.frame(2,80,0,f.cancelled(),true);f.frame(2,80,0,GrandExchangeOfferState.EMPTY,true);
            assertTrue("Offer absence cannot erase failed first-source classification",f.caps().isEmpty());
            Files.delete(blocked.resolve("unavailable"));Files.delete(blocked);
            f.companion.captureObservedOfferCleared(2,f.storage.accountId(),now());f.tracker.reconcileContinuationSources();
            assertEquals(Map.of(f.b,70),f.caps());assertEquals(f.a,f.outbox.successorBindings().get(0).getContinuationSuccessorProof().getOriginIdentity());
        }
    }
    @Test public void differentOriginsRemainDifferentInstructionsEvenWithIdenticalNumbers() {
        var a=Suggestion.builder(SuggestionType.SELL).item(561,"Rune").price(100).quantity(50).continuationOrigin("A").build();
        var b=Suggestion.builder(SuggestionType.SELL).item(561,"Rune").price(100).quantity(50).continuationOrigin("B").build();
        assertFalse(a.sameAs(b));assertFalse(a.isSameTradeAs(b));
        try{a.bindContinuationOrigin("B");fail();}catch(IllegalStateException expected){}
    }
    private static long now(){return Instant.now().getEpochSecond();}
    private static void inject(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
