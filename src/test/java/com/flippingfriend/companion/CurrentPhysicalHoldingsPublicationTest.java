package com.flippingfriend.companion;
import com.flippingfriend.session.AccountState;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CurrentPhysicalHoldingsPublicationTest {
    AccountState state(Map<Integer,Integer> all,Map<Integer,Integer> bank) {
        return new AccountState(true,true,false,100000000,0,0,8,8,0,true,all,Map.of(561,2),bank);
    }
    @Test public void cachedBankItemsAreNotRepublishedAsFreshPhysicalProof() {
        var all=new HashMap<>(Map.of(561,6002,562,100));var bank=Map.of(561,6000,562,100);
        assertEquals(Map.of(561,2),CompanionClient.currentPhysicalHoldings(state(all,bank)));
        assertEquals(Integer.valueOf(6002),all.get(561)); // No shared container map mutation.
    }
    @Test public void overflowedOrContradictoryTotalsCannotWrapIntoPositivePhysicalEvidence() {
        for(var all:List.of(Map.of(561,Integer.MIN_VALUE),Map.<Integer,Integer>of(),Map.of(561,1))) {
            try{CompanionClient.currentPhysicalHoldings(state(all,Map.of(561,2)));fail("Invalid physical evidence");}
            catch(IllegalArgumentException expected){}
        }
    }
}
