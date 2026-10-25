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
}
