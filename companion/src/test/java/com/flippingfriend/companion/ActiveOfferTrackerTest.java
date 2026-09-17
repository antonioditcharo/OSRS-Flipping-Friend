package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ActiveOfferTrackerTest
{
        private static OfferEvent offer(int slot, String state)
        {
                return OfferEvent.builder("c", 100, state)
                        .slot(slot)
                        .item(4151, "Abyssal whip")
                        .buying(true)
                        .price(1_500_000)
                        .quantities(2, 2)
                        .spent(3_000_000)
                        .sequence(1)
                        .firstSeenAt(50)
                        .build();
        }

        @Test
        public void completedAndCancelledOffersRemainUntilCollection()
        {
                String[] collectableStates = {
                        "BOUGHT", "SOLD", "CANCELLED_BUY", "CANCELLED_SELL"
                };

                for (String state : collectableStates)
                {
                        ActiveOfferTracker tracker = new ActiveOfferTracker();
                        OfferEvent completed = offer(3, state);

                        tracker.apply(completed);

                        assertEquals(state, 1, tracker.getActiveOffers().size());
                        assertSame(state, completed, tracker.getActiveOffers().iterator().next());

                        tracker.apply(offer(3, "EMPTY"));

                        assertEquals(state, 0, tracker.getActiveOffers().size());
                }
        }

        @Test
        public void emptyClearsOnlyItsOwnSlot()
        {
                ActiveOfferTracker tracker = new ActiveOfferTracker();
                OfferEvent first = offer(1, "BOUGHT");
                OfferEvent second = offer(2, "SOLD");

                tracker.apply(first);
                tracker.apply(second);
                tracker.apply(offer(1, "EMPTY"));

                assertEquals(1, tracker.getActiveOffers().size());
                assertSame(second, tracker.getActiveOffers().iterator().next());
        }

        @Test
        public void laterStateReplacesTheSameSlot()
        {
                ActiveOfferTracker tracker = new ActiveOfferTracker();
                OfferEvent running = offer(4, "BUYING");
                OfferEvent completed = offer(4, "BOUGHT");

                tracker.apply(running);
                tracker.apply(completed);

                assertEquals(1, tracker.getActiveOffers().size());
                assertSame(completed, tracker.getActiveOffers().iterator().next());
        }
}
