package com.flippingfriend.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Operational allocation only. These fixtures neither book fills nor attribute acquisition cost. */
public class OfferContinuationSuccessorTest {
    final String account=AccountIdentity.fromProfileKey("successor-core");
    OfferContinuation.Observation offer(boolean buy,int slot,int total,int filled,long first,String episode,String state,boolean collected) {
        return new OfferContinuation.Observation(slot,561,buy,100,total,filled,first,episode,state,collected);
    }
    List<OfferContinuation> pair(boolean buy) {
        var a=offer(buy,0,100,20,1000,"A",buy?"BUYING":"SELLING",false);
        var b=offer(buy,1,100,30,1001,"B",buy?"BUYING":"SELLING",false);
        var first=OfferContinuation.advise(a,List.of(a,b),!buy,1100,0);
        var second=OfferContinuation.advise(b,List.of(a,b),!buy,1100,0);
        first.observe(List.of(),offer(buy,0,100,20,1000,"A",buy?"CANCELLED_BUY":"CANCELLED_SELL",true),1101);
        second.observe(List.of(),offer(buy,1,100,30,1001,"B",buy?"CANCELLED_BUY":"CANCELLED_SELL",true),1101);
        return List.of(first,second);
    }
    ContinuationSuccessorProof proof(boolean buy,String origin,int slot,int total,long first,String episode) {
        var accepted=ContinuationSuccessorProof.accepted(account,origin,"accepted-"+episode,561,buy,100,total,first-1,CaptureOrder.next());
        var source=OfferEvent.builder("fixture",first,buy?"BUYING":"SELLING").accountId(account)
            .eventIdentity("event-"+episode,"session",episode).authenticatedObservation(true)
            .slot(slot).item(561,"Rune").buying(buy).price(100).quantities(total,0).spent(0).sequence(slot+10).firstSeenAt(first).build();
        var bound=accepted.captured(CaptureOrder.next()).bindTo(source);assertNotNull(bound);return bound;
    }
    @Test public void exactPartialSuccessorConsumesItsOriginOnceAndPreservesTheOtherCap() {
        for(boolean buy:new boolean[]{true,false}) {
            var pair=pair(buy);var a=pair.get(0);var b=pair.get(1);
            var child=offer(buy,2,50,0,1200,"child-A",buy?"BUYING":"SELLING",false);
            assertEquals(OfferContinuation.Status.UNKNOWN,a.observe(List.of(child),null,1200,List.of(),Set.of("child-A")));
            assertEquals(OfferContinuation.Status.UNKNOWN,b.observe(List.of(child),null,1200,List.of(),Set.of("child-A")));
            assertFalse(a.isClosed());assertEquals(80,a.getCollectedRemaining());assertEquals(70,b.getCollectedRemaining());
            var association=proof(buy,"A",2,50,1200,"child-A");
            for(int duplicate=0;duplicate<3;duplicate++) {
                assertEquals(OfferContinuation.Status.COLLECTED,a.observe(List.of(child),null,1200,List.of(association,association),Set.of()));
                assertEquals(OfferContinuation.Status.COLLECTED,b.observe(List.of(child),null,1200,List.of(association),Set.of()));
                assertEquals(50,a.getClaimedQuantity());assertEquals(80,a.getCollectedCap());
                assertEquals(30,a.getCollectedRemaining());assertEquals(70,b.getCollectedRemaining());
            }
            assertEquals(OfferContinuation.Status.COLLECTED,a.observe(List.of(),null,1201));
            assertEquals(30,a.getCollectedRemaining());assertTrue(a.isWellFormed());
        }
    }
    @Test public void fullClaimIsRetainableAllocationEvidenceAndLateOriginalFillsExposeContradiction() {
        for(boolean buy:new boolean[]{true,false}) {
            var a=pair(buy).get(0);var association=proof(buy,"A",2,80,1200,"child-A");
            assertEquals(OfferContinuation.Status.CLOSED,a.observe(List.of(),null,1200,List.of(association),Set.of()));
            assertTrue(a.isFullyClaimed());assertFalse(a.isClosed());assertEquals(0,a.getCollectedRemaining());
            var unrelated=offer(buy,4,1,0,1202,"unbound",buy?"BUYING":"SELLING",false);
            assertEquals(OfferContinuation.Status.WAITING,a.observeConsumedBudget(List.of(unrelated),null,List.of(association)));
            var late=offer(buy,0,100,30,1000,"A",buy?"BUYING":"SELLING",false);
            assertEquals(OfferContinuation.Status.UNKNOWN,a.observeConsumedBudget(List.of(late),null,List.of(association)));
            assertEquals(70,a.getCollectedCap());assertEquals(80,a.getClaimedQuantity());assertTrue(a.isWellFormed());
            assertEquals(OfferContinuation.Status.UNKNOWN,a.observeConsumedBudget(List.of(),null,List.of(association)));
        }
    }
    @Test public void lateOriginalProgressNarrowsResidualAndCannotReplenishAClaim() {
        for(boolean buy:new boolean[]{true,false}) {
            var a=pair(buy).get(0);var association=proof(buy,"A",2,50,1200,"child-A");
            a.observe(List.of(),null,1200,List.of(association),Set.of());
            var late=offer(buy,0,100,40,1000,"A",buy?"BUYING":"SELLING",false);
            assertEquals(OfferContinuation.Status.WAITING,a.observe(List.of(late),null,1201,List.of(association),Set.of()));
            assertEquals(60,a.getCollectedCap());assertEquals(10,a.getCollectedRemaining());
            late=offer(buy,0,100,60,1000,"A",buy?"BUYING":"SELLING",false);
            assertEquals(OfferContinuation.Status.UNKNOWN,a.observe(List.of(late),null,1202,List.of(association),Set.of()));
            assertEquals(40,a.getCollectedCap());assertEquals(0,a.getCollectedRemaining());assertTrue(a.isWellFormed());
            assertEquals(OfferContinuation.Status.UNKNOWN,a.observe(List.of(),null,1203,List.of(association),Set.of()));
        }
    }
    @Test public void childMaintenanceConsumesOnlyItsImmediateParentAndNeverRechargesTheRoot() {
        for(boolean buy:new boolean[]{true,false}) {
            var pair=pair(buy);var root=pair.get(0);var sibling=pair.get(1);
            var child=offer(buy,2,50,0,1200,"child-A",buy?"BUYING":"SELLING",false);
            var first=proof(buy,"A",2,50,1200,"child-A");root.observe(List.of(child),null,1200,List.of(first),Set.of());
            var maintenance=OfferContinuation.advise(child,List.of(child),!buy,1201,0);
            assertEquals(OfferContinuation.Status.COLLECTED,maintenance.observe(List.of(),
                offer(buy,2,50,12,1200,"child-A",buy?"CANCELLED_BUY":"CANCELLED_SELL",true),1300));
            var second=proof(buy,"child-A",4,38,1400,"grandchild-A");
            var grandchild=offer(buy,4,38,0,1400,"grandchild-A",buy?"BUYING":"SELLING",false);
            var proofs=List.of(first,second);
            assertEquals(OfferContinuation.Status.CLOSED,maintenance.observe(List.of(grandchild),null,1400,proofs,Set.of()));
            assertEquals(OfferContinuation.Status.COLLECTED,root.observe(List.of(grandchild),null,1400,proofs,Set.of()));
            assertEquals(OfferContinuation.Status.COLLECTED,sibling.observe(List.of(grandchild),null,1400,proofs,Set.of()));
            assertEquals(50,root.getClaimedQuantity());assertEquals(30,root.getCollectedRemaining());
            assertEquals(38,maintenance.getClaimedQuantity());assertEquals(70,sibling.getCollectedRemaining());
        }
    }
    @Test public void unknownOrChangedOrdersCannotBorrowAQualifiedSuccessorExemption() {
        for(boolean buy:new boolean[]{true,false})for(String change:List.of("episode","price","quantity","slot","first-seen")) {
            var sibling=pair(buy).get(1);var proof=proof(buy,"A",2,50,1200,"child-A");
            var changed=new OfferContinuation.Observation(change.equals("slot")?3:2,561,buy,change.equals("price")?101:100,
                change.equals("quantity")?51:50,0,change.equals("first-seen")?1201:1200,
                change.equals("episode")?"unknown":"child-A",buy?"BUYING":"SELLING",false);
            assertEquals(change,OfferContinuation.Status.CLOSED,sibling.observe(List.of(changed),null,1201,List.of(proof),Set.of()));
        }
    }
    @Test public void descendantFillsAndCancellationDoNotReleaseTheRootsAlreadyClaimedQuantity() {
        for(boolean buy:new boolean[]{true,false}) {
            var root=pair(buy).get(0);var proof=proof(buy,"A",2,50,1200,"child-A");
            for(int filled:new int[]{0,12,50}) {
                var child=offer(buy,2,50,filled,1200,"child-A",filled==50?(buy?"BOUGHT":"SOLD"):(buy?"BUYING":"SELLING"),false);
                assertEquals(OfferContinuation.Status.COLLECTED,root.observe(List.of(child),null,1300,List.of(proof),Set.of()));
                assertEquals(30,root.getCollectedRemaining());assertEquals(50,root.getClaimedQuantity());
            }
            assertEquals(OfferContinuation.Status.COLLECTED,root.observe(List.of(),null,1301));assertEquals(30,root.getCollectedRemaining());
        }
    }
}
