package com.flippingfriend.core;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfferContinuationTest {
    OfferContinuation.Observation offer(boolean buy,int filled,String state,boolean collected,String id) {
        return new OfferContinuation.Observation(0,561,buy,100,100,filled,1000,id,state,collected);
    }
    @Test public void lateFillsReduceBothKindsOfCollectedRemainderAndSlotLossDoesNotEraseIt() {
        for(boolean buy:new boolean[]{true,false}) {
            var initial=offer(buy,20,buy?"BUYING":"SELLING",false,"original");
            var intent=OfferContinuation.advise(initial,List.of(initial),!buy,1100,buy?2900:0);
            assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(offer(buy,40,initial.state,false,"original")),null,1101));
            var collected=offer(buy,50,buy?"CANCELLED_BUY":"CANCELLED_SELL",true,"original");
            assertEquals(OfferContinuation.Status.COLLECTED,intent.observe(List.of(),collected,1102));
            assertEquals(50,intent.getCollectedRemaining());
            assertEquals(OfferContinuation.Status.COLLECTED,intent.observe(List.of(),null,1103));
        }
    }
    @Test public void liveProgressRegressionCannotBecomeCollectionEvenAboveTheOriginalAdviceCounter() {
        for(boolean buy:new boolean[]{true,false}) {
            var original=offer(buy,20,buy?"BUYING":"SELLING",false,"original");
            var intent=OfferContinuation.advise(original,List.of(original),!buy,1100,0);
            intent.observe(List.of(offer(buy,50,original.state,false,"original")),null,1101);
            assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(),
                offer(buy,40,buy?"CANCELLED_BUY":"CANCELLED_SELL",true,"original"),1102));
        }
    }
    @Test public void reusedEpisodeAndFullCompletionEndThePromiseWithoutAReplacement() {
        for(boolean buy:new boolean[]{true,false}) {
            var original=offer(buy,20,buy?"BUYING":"SELLING",false,"original");
            var intent=OfferContinuation.advise(original,List.of(original),!buy,1100,0);
            assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(offer(buy,0,original.state,false,"new-episode")),null,1101));
            intent=OfferContinuation.advise(original,List.of(original),!buy,1100,0);
            assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(offer(buy,100,buy?"BOUGHT":"SOLD",false,"original")),null,1101));
        }
    }
    @Test public void cancellationDoesNotProveCollectionAndExpiredRequestsCannotResume() {
        var initial=offer(true,20,"BUYING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),false,1100,2900);
        assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(offer(true,40,"CANCELLED_BUY",false,"original")),null,1200));
        assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(),offer(true,40,"CANCELLED_BUY",true,"original"),2901));
    }
    @Test public void clockRollbackAlonePreservesBothKindsOfPromiseWithoutMutation() {
        for(boolean buy:new boolean[]{true,false}) {
            var initial=offer(buy,20,buy?"BUYING":"SELLING",false,"original");
            var intent=OfferContinuation.advise(initial,List.of(initial),!buy,1100,0);
            var collected=offer(buy,50,buy?"CANCELLED_BUY":"CANCELLED_SELL",true,"original");
            long revision=intent.getRevision();assertFalse(intent.valid(1099));assertTrue(intent.hasFutureEvidence(1099));
            assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(),null,1099));
            assertFalse(intent.isClosed());assertEquals(revision,intent.getRevision());assertEquals(0,intent.getCollectedRemaining());
            assertFalse(intent.acknowledgesCancellation(offer(buy,50,buy?"CANCELLED_BUY":"CANCELLED_SELL",false,"original"),1099));
            assertTrue(intent.valid(1100));assertFalse(intent.hasFutureEvidence(1100));
            assertEquals(OfferContinuation.Status.COLLECTED,intent.observe(List.of(),collected,1100));
            assertEquals(50,intent.getCollectedRemaining());
            revision=intent.getRevision();
            assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(),null,1099));
            assertEquals(revision,intent.getRevision());assertFalse(intent.isClosed());
            assertEquals(OfferContinuation.Status.COLLECTED,intent.observe(List.of(),null,1100));
            assertEquals(50,intent.getCollectedRemaining());
        }
    }
    @Test public void futureSaleAcceptanceCannotEraseOriginalSourceOrCollectedRemainder() {
        var initial=offer(false,20,"SELLING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),true,1100,0);
        intent.bindSaleRepriceSource("accepted-later",1200,1800);
        intent.observe(List.of(),offer(false,50,"CANCELLED_SELL",true,"original"),1800);
        long revision=intent.getRevision();
        assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(),null,1799));
        assertEquals(revision,intent.getRevision());assertFalse(intent.isClosed());assertFalse(intent.valid(1799));assertTrue(intent.hasFutureEvidence(1799));
        assertEquals("accepted-later",intent.getSaleRepriceSource());assertEquals(1800,intent.getSaleRepriceAcceptedAt());
        assertEquals(1200,intent.getSaleRepriceProgressAt());assertEquals(50,intent.getCollectedRemaining());
        assertEquals(OfferContinuation.Status.COLLECTED,intent.observe(List.of(),null,1800));
    }
    @Test public void legacyMissingCreationClockStillRequiresItsOriginalFirstSeenClock()throws Exception {
        var initial=offer(false,20,"SELLING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),true,1100,0);
        var creation=OfferContinuation.class.getDeclaredField("createdAt");creation.setAccessible(true);creation.setLong(intent,0);
        assertTrue(intent.isWellFormed());assertFalse(intent.valid(999));assertTrue(intent.hasFutureEvidence(999));long revision=intent.getRevision();
        assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(),null,999));
        assertFalse(intent.isClosed());assertEquals(revision,intent.getRevision());assertEquals(0,intent.getCollectedRemaining());
        assertTrue(intent.valid(1000));assertEquals(OfferContinuation.Status.COLLECTED,
            intent.observe(List.of(),offer(false,50,"CANCELLED_SELL",true,"original"),1000));
    }
    @Test public void futureDeadlineDoesNotSuspendCurrentEvidenceAndMalformedEvidenceStillCloses()throws Exception {
        var initial=offer(true,20,"BUYING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),false,1100,2900);
        assertTrue(intent.valid(1100));assertFalse(intent.hasFutureEvidence(1100));assertEquals(OfferContinuation.Status.COLLECTED,
            intent.observe(List.of(),offer(true,50,"CANCELLED_BUY",true,"original"),1100));
        assertTrue(intent.valid(2900));assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(),null,2901));
        assertFalse(intent.hasFutureEvidence(1099));
        intent=OfferContinuation.advise(initial,List.of(initial),false,1100,0);
        var creation=OfferContinuation.class.getDeclaredField("createdAt");creation.setAccessible(true);creation.setLong(intent,900);
        assertFalse(intent.isWellFormed());assertFalse(intent.hasFutureEvidence(800));
        assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(),null,800));
    }
    @Test public void currentFactsRemainDurableWhileFutureClocksWithholdGuidance() {
        for(boolean buy:new boolean[]{true,false}) {
            var initial=offer(buy,20,buy?"BUYING":"SELLING",false,"original");
            var intent=OfferContinuation.advise(initial,List.of(initial),!buy,1100,0);
            assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(),
                offer(buy,40,buy?"CANCELLED_BUY":"CANCELLED_SELL",true,"original"),1099));
            assertFalse(intent.valid(1099));assertEquals(60,intent.getCollectedRemaining());
            assertEquals(OfferContinuation.Status.WAITING,intent.observe(List.of(offer(buy,50,initial.state,false,"original")),null,1099));
            assertTrue(intent.isWellFormed());assertEquals(50,intent.getCollectedRemaining());
            assertEquals(OfferContinuation.Status.COLLECTED,intent.observe(List.of(),null,1100));
            assertEquals(50,intent.getCollectedRemaining());
        }
    }
    @Test public void currentCompletionFullCountersAndReplacementsEndPromisesEvenWhileClocksAreAhead() {
        for(boolean buy:new boolean[]{true,false})for(String change:List.of("terminal","full-counter","replacement")) {
            var initial=offer(buy,20,buy?"BUYING":"SELLING",false,"original");
            var intent=OfferContinuation.advise(initial,List.of(initial),!buy,1100,0);
            var next=offer(buy,change.equals("replacement")?0:100,
                change.equals("terminal")?(buy?"BOUGHT":"SOLD"):initial.state,false,change.equals("replacement")?"new":"original");
            assertEquals(change,OfferContinuation.Status.CLOSED,intent.observe(List.of(next),null,1099));
            assertFalse(intent.hasFutureEvidence(1099));assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(),null,1100));
        }
    }
    @Test public void cancellationAcknowledgementRequiresTheExactEpisodeAndNonregressingIncompleteCounter() {
        var original=offer(true,20,"BUYING",false,"original");var intent=OfferContinuation.advise(original,List.of(original),false,1100,0);
        intent.observe(List.of(offer(true,40,"BUYING",false,"original")),null,1101);
        assertTrue(intent.acknowledgesCancellation(offer(true,50,"CANCELLED_BUY",false,"original"),1102));
        assertFalse(intent.acknowledgesCancellation(offer(true,30,"CANCELLED_BUY",false,"original"),1102));
        assertFalse(intent.acknowledgesCancellation(offer(true,50,"CANCELLED_BUY",false,"new"),1102));
        assertFalse(intent.acknowledgesCancellation(offer(true,100,"CANCELLED_BUY",false,"original"),1102));
        assertFalse(intent.acknowledgesCancellation(offer(true,50,"CANCELLED_BUY",true,"original"),1102));
        assertFalse(intent.acknowledgesCancellation(offer(true,50,"BUYING",false,"original"),1102));
        assertFalse(intent.acknowledgesCancellation(offer(false,50,"CANCELLED_SELL",false,"original"),1102));
        assertFalse(intent.acknowledgesCancellation(null,1102));
    }
    @Test public void simultaneousCollectionAndLaterCancelledSaleRetiresTheExactPromise() {
        var initial=offer(true,20,"BUYING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),false,1100,0);
        var sale=new OfferContinuation.Observation(1,561,false,120,40,10,1101,"later-sale","CANCELLED_SELL",true);
        assertEquals(OfferContinuation.Status.CLOSED,intent.observe(List.of(sale),offer(true,40,"CANCELLED_BUY",true,"original"),1102));
    }
    @Test public void recordedSaleMustFollowTheOriginalPromiseAndCannotProveCollectionOrDisposeUnits() {
        var initial=offer(true,20,"BUYING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),false,1100,0);
        var later=new ContinuationDisposition(561,"fill","sale",1101,1200);
        assertFalse(intent.supersededBy(later));
        intent.observe(List.of(),offer(true,40,"CANCELLED_BUY",true,"original"),1200);
        assertTrue(intent.supersededBy(later));assertEquals(60,intent.getCollectedRemaining());
        assertFalse(intent.supersededBy(new ContinuationDisposition(561,"old-fill","old-sale",1099,1201)));
        assertFalse(intent.supersededBy(new ContinuationDisposition(562,"other-fill","sale",1101,1201)));
        assertFalse(intent.supersededBy(new ContinuationDisposition(561,"reused","original",1101,1201)));
    }
    @Test public void laterFirstSourceAcceptancePreservesCreationAndDoesNotEraseProgressOrTheRemainder() {
        var initial=offer(false,20,"SELLING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),true,1100,0);
        intent.bindSaleRepriceSource("accepted-later",1200,1800);
        assertTrue(intent.isWellFormed());assertTrue(intent.valid(1800));assertFalse(intent.valid(1799));
        assertEquals(1800,intent.getSaleRepriceAcceptedAt());assertEquals(1200,intent.getSaleRepriceProgressAt());
        long revision=intent.getRevision();intent.bindSaleRepriceSource("accepted-later",1900,2000);
        assertEquals("Refresh cannot move the accepted episode's evidence clocks",revision,intent.getRevision());
        assertEquals(1800,intent.getSaleRepriceAcceptedAt());assertEquals(1200,intent.getSaleRepriceProgressAt());
        assertEquals(OfferContinuation.Status.COLLECTED,intent.observe(List.of(),offer(false,50,"CANCELLED_SELL",true,"original"),2100));
        assertEquals(50,intent.getCollectedRemaining());
    }
    @Test public void invalidAcceptanceCannotPartiallyBindOrReplaceAnExistingSource() {
        var initial=offer(false,20,"SELLING",false,"original");
        var intent=OfferContinuation.advise(initial,List.of(initial),true,1100,0);
        for(long[] clocks:new long[][]{{1200,1100},{1000,1099},{999,1200}}) {
            try{intent.bindSaleRepriceSource("invalid",clocks[0],clocks[1]);fail("Invalid clock ordering");}
            catch(IllegalArgumentException expected){}
            assertNull(intent.getSaleRepriceSource());assertTrue(intent.isWellFormed());
        }
        intent.bindSaleRepriceSource("original-source",1000);
        assertEquals(1100,intent.getSaleRepriceAcceptedAt());
        try{intent.bindSaleRepriceSource("different-source",1200,1300);fail("A bound source is immutable");}
        catch(IllegalStateException expected){}
        assertEquals("original-source",intent.getSaleRepriceSource());assertTrue(intent.valid(1300));
    }
}
