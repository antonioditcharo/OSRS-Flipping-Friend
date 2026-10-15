package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PlanningStateInvalidationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    void set(CompanionService s,String field,Object value)throws Exception {
        var f=CompanionService.class.getDeclaredField(field);f.setAccessible(true);f.set(s,value);
    }
    @Test public void aNewOfferWithdrawsCachedBuysEvenInsidePlanSpacingWindow()throws Exception {
        long now=Instant.now().getEpochSecond();
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("fixture.db"));var service=new CompanionService(new Gson(),store)) {
            var account=new AccountSnapshot("snapshot",now,1000000,0,3,8,true,false,0);
            var candidate=new PortfolioCandidate(562,"Clean item","",100,100,100,120,120,120,10,100000,200L,
                1,1,.95,.95,.4,.4,1.5,now+100);
            set(service,"account",account);set(service,"lastPlanAt",now);set(service,"lastPlanFreeSlots",3);
            set(service,"lastPlanSellOnly",false);
            set(service,"plan",new PortfolioPlan("ready",now,now+90,"READY","ready",100,List.of(new PortfolioAllocation(1,candidate,"BUY"))));
            assertEquals(1,service.plan().getAllocations().size());
            service.offer(OfferEvent.builder("fixture",now,"BUYING").eventIdentity("open","s","buy").slot(0)
                .item(561,"Working buy").buying(true).price(100).quantities(10,0).sequence(1).build());
            assertTrue("Old cash/free slots cannot support cached buys after a new offer",service.plan().getAllocations().isEmpty());
        }
    }
}
