package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Replays actual receipt rotation without game input, network calls or live account writes. */
public class PlanningHeartbeatRecoveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    final String identity=AccountIdentity.fromProfileKey("heartbeat");
    Object get(Object target,String name)throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    void set(Object target,String name,Object value)throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    OfferEvent empty(int slot,int cycle,long at) {
        return OfferEvent.builder("fixture",at,"EMPTY").accountId(identity).authenticatedObservation(true)
            .eventIdentity("cycle-"+cycle+"-"+slot,"session",null).slot(slot).sequence(cycle*8+slot+1).build();
    }
    AccountSnapshot account(int cycle,long at) {
        Map<Integer,String> receipt=new HashMap<>();
        for(int slot=0;slot<8;slot++)receipt.put(slot,empty(slot,cycle,at).getEventId());
        return new AccountSnapshot("cycle-"+cycle,at,100000000,0,8,8,true,false,0).withAccountId(identity)
            .withAutomaticTradeScope().withOfferReadSet(receipt);
    }
    void seed(SqliteStore store,CompanionService service,long at)throws Exception {
        for(int slot=0;slot<8;slot++) {var e=empty(slot,0,at);store.recordAndProjectOffer(e,gson.toJson(e),true);}
        set(service,"account",account(0,at));
        var captured=account(0,at);store.recordAndReconcileAccount(captured,PositionStateView.from(captured),gson.toJson(captured));
        var market=get(service,"market");
        set(market,"state",new MarketIngestionService.MarketState(Map.of(),new JsonObject(),new JsonObject(),new JsonObject(),at));
        set(service,"lastPlanFreeSlots",8);set(service,"lastPlanSellOnly",false);
    }
    void drain(CompanionService service)throws Exception {
        ((ExecutorService)get(service,"planThread")).submit(()->{}).get(5,TimeUnit.SECONDS);
    }
    @Test public void aNewCoherentReceiptRetriesUnavailablePlanningInsideSpacingWindow()throws Exception {
        long now=Instant.now().getEpochSecond();
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("retry.db"));var service=new CompanionService(gson,store)) {
            seed(store,service,now-2);
            var failed=PortfolioPlan.unavailable("old",PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,"In-flight read was incomplete",now);
            set(service,"plan",failed);set(service,"lastPlanAt",now);
            for(int slot=0;slot<8;slot++)service.offer(empty(slot,1,now-1));
            service.account(account(1,now-1));drain(service);
            assertEquals("A proven current snapshot must be planned instead of retaining an old exposure refusal",
                PortfolioPlanOutcome.NO_ELIGIBLE_CANDIDATE,service.plan().getOutcome());
        }
    }
    @Test public void identicalHeartbeatDoesNotComputeAgainstTheOldAccountReceipt()throws Exception {
        long now=Instant.now().getEpochSecond();
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("rotation.db"));var service=new CompanionService(gson,store)) {
            seed(store,service,now-2);
            var cached=new PortfolioPlan("cached",now,now+90,"READY","fixture",1,List.of());
            set(service,"plan",cached);set(service,"lastPlanAt",0L);
            service.offer(empty(0,1,now-1));drain(service);
            assertSame("A same-economics heartbeat must wait for its account snapshot, not replace the plan with a transient failure",
                cached,get(service,"plan"));
            assertEquals("Presentation still refuses the incomplete read-set",PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
                service.plan().getOutcome());
        }
    }
}
