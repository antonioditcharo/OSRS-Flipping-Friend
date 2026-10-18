package com.flippingfriend.core;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ObservedHoldingsSnapshotTest {
    @Test public void physicalEvidenceSurvivesPlanningCopiesAndCannotBeMutated() {
        Map<Integer,Integer> bag=new HashMap<>();bag.put(561,6002);
        var account=new AccountSnapshot("capture",100,100000000,0,8,8,true,false,0)
            .withAccountId(AccountIdentity.fromProfileKey("physical")).withObservedHoldings(bag);
        bag.put(561,9999);
        Map<Integer,String> receipt=new HashMap<>();for(int slot=0;slot<8;slot++)receipt.put(slot,"capture-"+slot);
        var copy=account.withAutomaticTradeScope().withOfferReadSet(receipt).withPlanningExposure(Map.of(561,82L))
            .withPlanningRestrictions(Map.of(561,82L),Set.of(562)).withPlanningOffers(Set.of(562));
        assertEquals(Map.of(561,6002),copy.getObservedHoldings());assertTrue(copy.getPositions().isEmpty());
        try{copy.getObservedHoldings().put(561,0);fail("Physical snapshot must be immutable");}
        catch(UnsupportedOperationException expected){}
        assertNull(new AccountSnapshot("legacy",100,1,0,8,8,true,false,0).getObservedHoldings());
        try{account.withObservedHoldings(Map.of(561,-1));fail("Negative physical counters");}
        catch(IllegalArgumentException expected){}
    }
}
