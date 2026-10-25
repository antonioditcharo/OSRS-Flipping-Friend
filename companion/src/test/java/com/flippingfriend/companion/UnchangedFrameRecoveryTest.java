package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnchangedFrameRecoveryTest {
    OfferEvent event(String id,long at,long sequence,int filled,String session) {
        return OfferEvent.builder("fixture",at,"BUYING").accountId(AccountIdentity.fromProfileKey("fixture"))
            .authenticatedObservation(true).eventIdentity(id,session,"episode").slot(7).sequence(sequence)
            .item(389,"Raw manta ray").buying(true).price(910).quantities(100,filled).spent(filled*910L).build();
    }
    @Test public void onlyAnUnchangedRecoveryWithoutIndependentItemUncertaintyCanProveCostCoverage() {
        var before=event("before",103,1,10,"s");var delayed=event("delayed",102,2,10,"s");var baseline=event("baseline",104,3,10,"s");
        var scope=new AutomaticTradeScope();scope.reconciliationScope.put("delayed",Set.of(389));scope.reconciliationScope.put("baseline",Set.of(389));
        var reasons=Map.of("delayed","authenticated observation is not newer in this session","baseline","authenticated slot baseline; earlier executions remain unresolved");
        assertEquals(Set.of(389),UnchangedFrameRecovery.items(List.of(before,delayed,baseline),scope,reasons));
        for(var changed:List.of(event("baseline",104,3,11,"s"),event("baseline",104,3,10,"other"),event("baseline",104,2,10,"s")))
            assertTrue(UnchangedFrameRecovery.items(List.of(before,delayed,changed),scope,reasons).isEmpty());
        assertTrue(UnchangedFrameRecovery.items(List.of(delayed,baseline),scope,reasons).isEmpty());
        scope.reconciliationScope.put("independent-gap",Set.of(389));
        assertTrue(UnchangedFrameRecovery.items(List.of(before,delayed,baseline),scope,reasons).isEmpty());
        scope.reconciliationScope.remove("independent-gap");scope.unprovenCostItems.add(389);
        assertTrue(UnchangedFrameRecovery.items(List.of(before,delayed,baseline),scope,reasons).isEmpty());
    }
}
