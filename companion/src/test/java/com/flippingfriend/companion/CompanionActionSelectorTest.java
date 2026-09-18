package com.flippingfriend.companion;

import com.flippingfriend.core.CompanionAction;
import com.flippingfriend.core.CompanionActionType;
import com.flippingfriend.core.OfferEvent;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompanionActionSelectorTest
{
        private static OfferEvent offer(int slot, String state, boolean buying, long observedAt,
                int itemId, String itemName, int filled)
        {
                return OfferEvent.builder("c", observedAt, state)
                        .slot(slot)
                        .item(itemId, itemName)
                        .buying(buying)
                        .price(100)
                        .quantities(10, filled)
                        .sequence(1)
                        .build();
        }

        @Test
        public void returnsWaitWhenNothingIsCollectable()
        {
                CompanionActionSelector selector = new CompanionActionSelector();

                CompanionAction action = selector.select(Arrays.asList(
                        offer(1, "BUYING", true, 100, 4151, "Abyssal whip", 2),
                        offer(2, "SELLING", false, 200, 561, "Nature rune", 5)));

                assertEquals(CompanionActionType.WAIT, action.getType());
                assertFalse(action.isActionable());
                assertEquals("No companion action", action.getHeadline());
        }

        @Test
        public void recognizesEveryCollectableTerminalState()
        {
                String[] states = {
                        "BOUGHT", "SOLD", "CANCELLED_BUY", "CANCELLED_SELL"
                };

                for (String state : states)
                {
                        boolean buying = state.endsWith("BUY") || "BOUGHT".equals(state);
                        CompanionAction action = new CompanionActionSelector().select(
                                Collections.singletonList(
                                        offer(3, state, buying, 100, 4151,
                                                "Abyssal whip", 2)));

                        assertEquals(state, CompanionActionType.COLLECT, action.getType());
                        assertEquals(state, 3, action.getSlot());
                        assertTrue(state, action.isActionable());
                }
        }

        @Test
        public void oldestTerminalOfferWinsThenLowestSlotBreaksATie()
        {
                OfferEvent later = offer(1, "BOUGHT", true, 300,
                        4151, "Abyssal whip", 2);
                OfferEvent tiedHigherSlot = offer(6, "SOLD", false, 100,
                        561, "Nature rune", 10);
                OfferEvent tiedLowerSlot = offer(2, "CANCELLED_SELL", false, 100,
                        995, "Wine of zamorak", 4);

                CompanionAction action = new CompanionActionSelector().select(
                        Arrays.asList(later, tiedHigherSlot, tiedLowerSlot));

                assertEquals(CompanionActionType.COLLECT, action.getType());
                assertEquals(995, action.getItemId());
                assertEquals(2, action.getSlot());
                assertEquals("Wine of zamorak", action.getItemName());
        }

        @Test
        public void cancelledOfferWithNoFillStillNeedsCollection()
        {
                CompanionAction action = new CompanionActionSelector().select(
                        Collections.singletonList(
                                offer(4, "CANCELLED_BUY", true, 100,
                                        561, "Nature rune", 0)));

                assertEquals(CompanionActionType.COLLECT, action.getType());
                assertEquals(0, action.getQuantity());
                assertTrue(action.getDetail().contains("pick up your items"));
        }

        @Test
        public void sellCompletionUsesCoinWordingAndMissingNameFallsBack()
        {
                CompanionAction action = new CompanionActionSelector().select(
                        Collections.singletonList(
                                offer(5, "SOLD", false, 100, 561, null, 10)));

                assertEquals("Item 561", action.getItemName());
                assertEquals("Collect your Item 561", action.getHeadline());
                assertTrue(action.getDetail().contains("pick up your coins"));
        }

        @Test
        public void ignoresNullInvalidSlotAndUnknownStates()
        {
                CompanionAction action = new CompanionActionSelector().select(
                        Arrays.asList(
                                null,
                                offer(-1, "BOUGHT", true, 10,
                                        4151, "Abyssal whip", 2),
                                offer(1, "EMPTY", true, 20,
                                        4151, "Abyssal whip", 2)));

                assertEquals(CompanionActionType.WAIT, action.getType());
        }

        @Test
        public void nullCollectionReturnsWait()
        {
                CompanionAction action = new CompanionActionSelector().select(null);

                assertEquals(CompanionActionType.WAIT, action.getType());
                assertFalse(action.isActionable());
        }
}
