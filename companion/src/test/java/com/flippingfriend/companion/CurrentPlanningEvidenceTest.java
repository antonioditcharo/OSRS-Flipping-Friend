package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Wire corruption, source provenance and uncertain capital, using disposable stores only. */
public class CurrentPlanningEvidenceTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final BoundedPlanningStateTest f=new BoundedPlanningStateTest();
    AccountSnapshot change(AccountSnapshot original,String field,Object value) {
        var body=f.gson.toJsonTree(original).getAsJsonObject();body.add(field,f.gson.toJsonTree(value));
        return f.gson.fromJson(body,AccountSnapshot.class);
    }
    @Test public void malformedWireReceiptsAndResourceContradictionsAbstain()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("proof.db"))) {
            var receipt=f.seed(s);var original=f.snapshot(receipt);var read=s.planningProjections(f.account);
            assertTrue(CurrentOfferReadSet.valid(read,original,f.now));
            var missing=new HashMap<>(receipt);missing.remove(7);
            var wrong=new HashMap<>(receipt);wrong.put(7,"not-the-current-event");
            var duplicate=new HashMap<>(receipt);duplicate.put(7,receipt.get(6));
            for(var map:List.of(missing,wrong,duplicate))
                assertFalse(CurrentOfferReadSet.valid(read,change(original,"offerReadSet",map),f.now));
            assertFalse(CurrentOfferReadSet.valid(read,change(original,"freeSlots",7),f.now));
            assertFalse(CurrentOfferReadSet.valid(read,change(original,"committedCoins",100),f.now));
            assertFalse(CurrentOfferReadSet.valid(read,change(original,"spendableCoins",-1),f.now));
            assertFalse(CurrentOfferReadSet.valid(read,change(original,"observedAt",f.now+1),f.now));
            assertFalse(CurrentOfferReadSet.valid(read,change(original,"buyLimitUsed",Map.of(561,-1)),f.now));
            assertFalse(CurrentOfferReadSet.valid(read,original,f.now+151));
            var f2p=change(change(change(original,"totalSlots",3),"freeSlots",3),"members",false);
            assertTrue(CurrentOfferReadSet.valid(read,f2p,f.now));
        }
    }
    @Test public void cachedOrAlteredSourceCannotServeAsAuthenticatedCurrentProof()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("source.db"))) {
            var receipt=f.seed(s);var original=f.snapshot(receipt);var read=s.planningProjections(f.account);
            var e=read.currentEvents.get(0);
            for(String field:List.of("authenticatedObservation","sessionId","observedAt","sequence","accountId")) {
                var body=f.gson.toJsonTree(e).getAsJsonObject();
                if(field.equals("authenticatedObservation"))body.addProperty(field,false);
                else if(field.equals("observedAt"))body.addProperty(field,f.now-1);
                else if(field.equals("sequence"))body.addProperty(field,999);
                else body.addProperty(field,field.equals("accountId")?AccountIdentity.fromProfileKey("other"):"other");
                read.currentEvents=new HashMap<>(read.currentEvents);
                read.currentEvents.put(0,f.gson.fromJson(body,OfferEvent.class));
                assertFalse(field,CurrentOfferReadSet.valid(read,original,f.now));
            }
        }
    }
    @Test public void partialOpenBuyIsInsideItsFullEnvelopeAndLegacyCostsRemainGuards()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("bound.db"))) {
            f.seed(s);var receipt=new HashMap<Integer,String>();
            for(int slot=0;slot<8;slot++) {
                var e=f.offer("partial-current-"+slot,slot,slot==0?561:0,slot==0?"BUYING":"EMPTY",slot==0?4:0,30+slot,f.now+1,true);
                f.accept(s,e);receipt.put(slot,e.getEventId());
            }
            var snapshot=change(change(change(f.snapshot(receipt),"observedAt",f.now+1),"freeSlots",7),"committedCoins",600);
            var read=s.planningProjections(f.account);
            var bound=DurablePlanningExposure.forAccount(read,snapshot,f.now+1);
            assertNotNull(bound);assertEquals(600,bound.reservedBuyCoins);
            assertEquals(Long.valueOf(2000),bound.committedByItem.get(561));
            var guarded=new SqliteStore.PlanningProjections(List.of(PositionProjection.ready(562,"Legacy",1,500,f.now,"legacy","old")),read.offers);
            guarded.tradeScope=read.tradeScope;guarded.currentEvents=read.currentEvents;guarded.history=read.history;
            assertEquals(Long.valueOf(500),DurablePlanningExposure.forAccount(guarded,snapshot,f.now+1).committedByItem.get(562));
            var overflow=new SqliteStore.PlanningProjections(List.of(PositionProjection.ready(561,"Legacy",1,Long.MAX_VALUE,f.now,"legacy","old")),read.offers);
            overflow.tradeScope=read.tradeScope;overflow.currentEvents=read.currentEvents;overflow.history=read.history;
            assertNull(DurablePlanningExposure.forAccount(overflow,snapshot,f.now+1));
            var conflict=new SqliteStore.PlanningProjections(List.of(PositionProjection.reconcile(562,"Unknown source",1,500,true,f.now,f.now,"missing","old","gap")),read.offers);
            conflict.tradeScope=read.tradeScope;conflict.currentEvents=read.currentEvents;conflict.history=read.history;
            assertNull(DurablePlanningExposure.forAccount(conflict,snapshot,f.now+1));
        }
    }
    @Test public void contradictoryHistoricalFundingCannotBeCalledAnUpperBound()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("funding.db"))) {
            var receipt=f.seed(s);var read=s.planningProjections(f.account);
            var history=new ArrayList<>(read.history);
            var bad=f.gson.toJsonTree(history.get(1)).getAsJsonObject();bad.addProperty("spent",2000);
            history.set(1,f.gson.fromJson(bad,OfferEvent.class));read.history=history;
            assertNull("An offer for at most 1000 GP cannot establish a bound over contradictory spending",
                DurablePlanningExposure.forAccount(read,f.snapshot(receipt),f.now));
        }
    }
}
