package com.flippingfriend.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundedEstimateTest {
    @Test public void preservesWholeGpAndRoundsHalfAwayFromZeroWithoutOverflow() {
        assertEquals(11, RoundedEstimate.midpoint(10, 11));
        assertEquals(-11, RoundedEstimate.midpoint(-11, -10));
        assertEquals(-1, RoundedEstimate.midpoint(Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, RoundedEstimate.midpoint(Long.MAX_VALUE-1, Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, RoundedEstimate.midpoint(Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(0, RoundedEstimate.midpoint(-10, 10));
        assertEquals("Unknown", RoundedEstimate.text(null, null));
        assertEquals("Est. -11", RoundedEstimate.text(-11L, -10L));
    }
    @Test public void hourlyMidpointRoundsOnlyOnceAndCannotOverflowOrUsePositiveTieRoundingForLosses() {
        assertEquals("1",RoundedEstimate.hourlyMidpoint(0,1,3600).toString());
        assertEquals("-1",RoundedEstimate.hourlyMidpoint(-1,0,3600).toString());
        assertEquals("-30",RoundedEstimate.hourlyMidpoint(Long.MIN_VALUE,Long.MAX_VALUE,60).toString());
        assertEquals("553402322211286548420",RoundedEstimate.hourlyMidpoint(Long.MAX_VALUE,Long.MAX_VALUE,60).toString());
        assertEquals("0",RoundedEstimate.hourlyMidpoint(-10,10,60).toString());
    }
}
