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
    @Test public void preparedPairsMatchFullReaderAcrossAppendsChangedReasonsScopeAndReplacement() {
        var before=event("before",103,1,10,"s");var delayed=event("delayed",102,2,10,"s");var baseline=event("baseline",104,3,10,"s");
        var scope=new AutomaticTradeScope();scope.reconciliationScope.put("delayed",Set.of(389));scope.reconciliationScope.put("baseline",Set.of(389));
        var reasons=new HashMap<>(Map.of("delayed","authenticated observation is not newer in this session","baseline","authenticated slot baseline; earlier executions remain unresolved"));
        var index=new UnchangedFrameRecovery.Index();List<OfferEvent> previous=null;
        for(var prefix:List.of(List.of(before),List.of(before,delayed),List.of(before,delayed,baseline))) {
            index.appendCanonical(previous,prefix);assertEquals(UnchangedFrameRecovery.items(prefix,scope,reasons),index.read(prefix,scope,reasons));previous=prefix;
        }
        assertEquals(3,index.processedCount());assertEquals(Set.of(389),index.read(previous,scope,reasons));assertEquals(3,index.processedCount());
        reasons.remove("delayed");assertEquals(UnchangedFrameRecovery.items(previous,scope,reasons),index.read(previous,scope,reasons));
        reasons.put("delayed","authenticated observation is not newer in this session");assertEquals(Set.of(389),index.read(previous,scope,reasons));
        scope.unprovenCostItems.add(389);assertTrue(index.read(previous,scope,reasons).isEmpty());scope.unprovenCostItems.clear();
        scope.reconciliationScope.put("other-gap",Set.of(389));assertTrue(index.read(previous,scope,reasons).isEmpty());scope.reconciliationScope.remove("other-gap");
        scope.hasUnboundedReconciliation=true;assertTrue(index.read(previous,scope,reasons).isEmpty());scope.hasUnboundedReconciliation=false;
        assertEquals(Set.of(389),index.read(previous,scope,reasons));
        var replacement=List.of(delayed,baseline);index.appendCanonical(null,replacement);
        assertEquals(UnchangedFrameRecovery.items(replacement,scope,reasons),index.read(replacement,scope,reasons));assertTrue(index.read(replacement,scope,reasons).isEmpty());
    }
    @Test public void preparedTailDoesNotReadOldRowsButLateReasonsRebuildThem() {
        var before=event("before",103,1,10,"s");var delayed=event("delayed",102,2,10,"s");var baseline=event("baseline",104,3,10,"s");
        var index=new UnchangedFrameRecovery.Index();var scope=new AutomaticTradeScope();List<OfferEvent> first=List.of(before,delayed);
        var reasons=Map.of("delayed","authenticated observation is not newer in this session","baseline","authenticated slot baseline; earlier executions remain unresolved");
        index.appendCanonical(null,first);index.read(first,scope,reasons);
        List<OfferEvent> tail=new AbstractList<OfferEvent>() {
            public int size(){return 3;}public OfferEvent get(int i){assertEquals(2,i);return baseline;}
        };
        index.appendCanonical(first,tail);assertEquals(Set.of(389),index.read(tail,scope,reasons));assertEquals(3,index.processedCount());
        var full=List.of(before,delayed,baseline);index.appendCanonical(null,full);index.read(full,scope,Map.of());
        assertEquals(UnchangedFrameRecovery.items(full,scope,reasons),index.read(full,scope,reasons));
    }
}
