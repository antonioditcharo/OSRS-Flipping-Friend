package com.flippingfriend.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UncertainCapitalBoundsTest {
    @Test public void financialMeaningSurvivesPlanCopiesWithoutChangingAllocations() {
        var original=PortfolioPlan.unavailable("fixture",PortfolioPlanOutcome.NO_ELIGIBLE_CANDIDATE,"fixture",100);
        Map<Integer,Long> bounds=new HashMap<>(Map.of(561,1000L));
        var guarded=original.withUncertainCapitalBounds(bounds).withDiagnostics(null).withBoard(0,0);
        bounds.put(561,0L);
        assertEquals(Map.of(561,1000L),guarded.getUncertainCapitalBounds());
        assertTrue(original.getUncertainCapitalBounds().isEmpty());
        assertTrue(guarded.getAllocations().isEmpty());assertEquals(original.getOutcome(),guarded.getOutcome());
        try {guarded.getUncertainCapitalBounds().put(562,10L);fail("mutable financial guard");}
        catch(UnsupportedOperationException expected) { }
    }
}
