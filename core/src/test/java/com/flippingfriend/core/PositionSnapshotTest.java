package com.flippingfriend.core;

import org.junit.Assert;
import org.junit.Test;

public class PositionSnapshotTest
{
        @Test
        public void exposesDurablePositionState()
        {
                PositionSnapshot snapshot = new PositionSnapshot(
                        4151, "Abyssal whip", 10, 15_000_000L,
                        true, 1_700_000_000L, 1_600_000, 1_450_000, 42.5);

                Assert.assertEquals(4151, snapshot.getItemId());
                Assert.assertEquals("Abyssal whip", snapshot.getItemName());
                Assert.assertEquals(10, snapshot.getQuantity());
                Assert.assertEquals(15_000_000L, snapshot.getTotalCost());
                Assert.assertTrue(snapshot.isCostKnown());
                Assert.assertEquals(1_500_000, snapshot.getAverageCost());
                Assert.assertEquals(1_700_000_000L, snapshot.getOpenedAt());
                Assert.assertEquals(1_600_000, snapshot.getTargetSellPrice());
                Assert.assertEquals(1_450_000, snapshot.getStopPrice());
                Assert.assertEquals(42.5, snapshot.getPredictedSellMinutes(), 0.0);
        }

        @Test
        public void clampsInvalidValuesAndProtectsUnknownCost()
        {
                PositionSnapshot snapshot = new PositionSnapshot(
                        2, null, -1, -10, true, -20, -30, -40, -50);

                Assert.assertEquals("Item 2", snapshot.getItemName());
                Assert.assertEquals(0, snapshot.getQuantity());
                Assert.assertEquals(0, snapshot.getTotalCost());
                Assert.assertFalse(snapshot.isCostKnown());
                Assert.assertEquals(0, snapshot.getAverageCost());
                Assert.assertEquals(0, snapshot.getOpenedAt());
                Assert.assertEquals(0, snapshot.getTargetSellPrice());
                Assert.assertEquals(0, snapshot.getStopPrice());
                Assert.assertEquals(0.0, snapshot.getPredictedSellMinutes(), 0.0);
        }
}
