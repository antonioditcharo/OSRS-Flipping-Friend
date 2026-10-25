package com.flippingfriend.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Presentation only. Financial authority continues to consume the original bounds. */
public final class RoundedEstimate {
    private RoundedEstimate() { }

    public static long midpoint(long lower, long upper) {
        if (lower > upper) throw new IllegalArgumentException("reversed estimate bounds");
        return new BigDecimal(BigInteger.valueOf(lower).add(BigInteger.valueOf(upper)))
            .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP).longValueExact();
    }

    public static String text(Long lower, Long upper) {
        if (lower == null || upper == null) return "Unknown";
        return (lower.equals(upper) ? "" : "Est. ") + midpoint(lower, upper);
    }
}
