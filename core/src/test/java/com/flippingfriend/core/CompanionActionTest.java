package com.flippingfriend.core;

import org.junit.Assert;
import org.junit.Test;

public class CompanionActionTest
{
        @Test
        public void waitingActionIsExplicitAndNotActionable()
        {
                CompanionAction action = CompanionAction.waiting(
                        "Nothing to do", "No companion-owned action is available.");

                Assert.assertEquals(CompanionActionType.WAIT, action.getType());
                Assert.assertEquals(-1, action.getItemId());
                Assert.assertNull(action.getItemName());
                Assert.assertEquals(-1, action.getSlot());
                Assert.assertEquals(0, action.getQuantity());
                Assert.assertEquals("Nothing to do", action.getHeadline());
                Assert.assertEquals(
                        "No companion-owned action is available.", action.getDetail());
                Assert.assertFalse(action.isActionable());
        }

        @Test
        public void collectActionCarriesTheCompletedOffer()
        {
                CompanionAction action = CompanionAction.collect(
                        4151, "Abyssal whip", 3, 2,
                        "Collect your Abyssal whip",
                        "Your buy offer has finished.");

                Assert.assertEquals(CompanionActionType.COLLECT, action.getType());
                Assert.assertEquals(4151, action.getItemId());
                Assert.assertEquals("Abyssal whip", action.getItemName());
                Assert.assertEquals(3, action.getSlot());
                Assert.assertEquals(2, action.getQuantity());
                Assert.assertEquals("Collect your Abyssal whip", action.getHeadline());
                Assert.assertEquals("Your buy offer has finished.", action.getDetail());
                Assert.assertTrue(action.isActionable());
        }

        @Test
        public void collectActionClampsQuantityAndProvidesAnItemFallback()
        {
                CompanionAction action = CompanionAction.collect(
                        561, null, 2, -10, null, null);

                Assert.assertEquals(CompanionActionType.COLLECT, action.getType());
                Assert.assertEquals("Item 561", action.getItemName());
                Assert.assertEquals(0, action.getQuantity());
                Assert.assertEquals("", action.getHeadline());
                Assert.assertEquals("", action.getDetail());
                Assert.assertTrue(action.isActionable());
        }
}
