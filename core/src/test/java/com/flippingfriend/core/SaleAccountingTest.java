package com.flippingfriend.core;

import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaleAccountingTest
{
    private SaleAccounting current(long gross, int quantity, int minimum, String name, Long cost)
    { return SaleAccounting.calculate(gross, quantity, minimum, name, SaleAccounting.CURRENT_POLICY, cost); }

    @Test public void equalAverageDoesNotProveEqualPricesOrTax()
    {
        SaleAccounting sale = current(100, 2, 1, "Nature rune", 90L);
        // 50 + 50 pays two coins; 49 + 51 pays one despite the exact average of 50.
        assertEquals(Long.valueOf(1), sale.getTaxLower()); assertEquals(Long.valueOf(2), sale.getTaxUpper());
        assertEquals(Long.valueOf(8), sale.getProfitLower()); assertEquals(Long.valueOf(9), sale.getProfitUpper());
    }

    @Test public void boundsContainEveryEnumeratedTwoPriceFillAndCapExample()
    {
        for (int a = 1; a < 200; a++) for (int b = 1; b < 200; b++)
        {
            SaleAccounting sale = current(a + b, 2, 1, "Nature rune", 1L);
            long actualTax = a / 50 + b / 50;
            assertTrue(sale.getTaxLower() <= actualTax && actualTax <= sale.getTaxUpper());
        }
        SaleAccounting capped = current(600_000_000, 2, 1, "Abyssal whip", 1L);
        // Equal 300m fills pay 10m; concentrating value in one fill can hit only one cap.
        assertTrue(capped.getTaxLower() <= 5_000_000 && capped.getTaxUpper() >= 10_000_000);
        SaleAccounting allCapped = current(600_000_000, 2, 250_000_000, "Abyssal whip", 1L);
        assertEquals(Long.valueOf(10_000_000), allCapped.getTaxLower());
        assertEquals(allCapped.getTaxLower(), allCapped.getTaxUpper());
    }

    @Test public void singleFillMinimumPriceAndExemptionCanGiveExactTax()
    {
        assertEquals(Long.valueOf(2), current(149, 1, 1, "Nature rune", 100L).getTaxLower());
        SaleAccounting equalMinimum = current(250, 2, 125, "Nature rune", 200L);
        assertEquals(Long.valueOf(4), equalMinimum.getTaxLower());
        assertEquals(equalMinimum.getTaxLower(), equalMinimum.getTaxUpper());
        assertEquals(Long.valueOf(0), current(1000, 2, 1, "Hammer", 1L).getTaxUpper());
    }

    @Test public void missingCostProceedsClassificationAndPlacementNeverInventExactProfit()
    {
        assertNull(current(100, 1, 1, "Nature rune", null).getProfitLower());
        assertNull(current(0, 1, 1, "Nature rune", 90L).getProfitLower());
        assertNull(current(99, 1, 100, "Nature rune", 90L).getProfitLower());
        assertEquals(Long.valueOf(2), current(100, 1, 1, null, 90L).getTaxUpper());
        SaleAccounting unproven = SaleAccounting.calculate(100, 1, 100, "Nature rune", SaleAccounting.UNPROVEN_PLACEMENT, 90L);
        assertEquals(Long.valueOf(0), unproven.getTaxLower()); assertEquals(Long.valueOf(2), unproven.getTaxUpper());
        assertNotEquals(unproven.getProfitLower(), unproven.getProfitUpper());
    }

    @Test public void blendedCostConservesRemaindersAndHandlesLargeLongCosts()
    {
        long removed = SaleAccounting.allocateCost(301, 3, 2);
        assertEquals(200, removed); assertEquals(101, SaleAccounting.allocateCost(301 - removed, 1, 1));
        long expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2))
            .divide(BigInteger.valueOf(3)).longValueExact();
        assertEquals(expected, SaleAccounting.allocateCost(Long.MAX_VALUE, 3, 2));
    }
}
