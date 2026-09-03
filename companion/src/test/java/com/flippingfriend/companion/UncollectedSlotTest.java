package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import org.junit.Test;

/**
 * Covers a slot being held by an offer that has finished but not been collected.
 *
 * <p>The game reports {@code BOUGHT} or {@code SOLD} while the slot is still occupied, and only
 * reports {@code EMPTY} once the items and coins have been taken. The tracker used to forget an
 * offer the moment it reached a terminal state, so the companion believed the slot was free while
 * the game still showed it held — and {@code AccountMonitor} on the plugin side has always counted
 * occupancy as {@code state != EMPTY}, so the two disagreed and the companion was the one that was
 * wrong.
 *
 * <p>It matters because an uncollected offer is a slot earning nothing, and the value of collecting
 * is whatever that slot would otherwise make. Forgetting the offer made that cost invisible.
 */
public class UncollectedSlotTest
{
	private static OfferEvent event(int slot, String state)
	{
		return OfferEvent.builder("t", 1_000L, state)
			.item(4151, "Abyssal whip")
			.slot(slot)
			.buying(true)
			.quantities(100, 100)
			.build();
	}

	private final ActiveOfferTracker tracker = new ActiveOfferTracker();

	@Test
	public void aFinishedOfferStillHoldsItsSlot()
	{
		tracker.apply(event(1, "BUYING"));
		assertEquals(1, tracker.getActiveOffers().size());

		tracker.apply(event(1, "BOUGHT"));

		assertEquals("finished is not collected, and the slot is still held",
			1, tracker.getActiveOffers().size());
		assertEquals(1, tracker.awaitingCollection().size());
	}

	/**
	 * A cancelled offer leaves coins or items in the box, so it holds its slot exactly as a completed
	 * one does. Treating cancellation as freeing the slot would have the planner offer capacity that
	 * is not there.
	 */
	@Test
	public void aCancelledOfferAlsoHoldsItsSlot()
	{
		tracker.apply(event(2, "BUYING"));
		tracker.apply(event(2, "CANCELLED_BUY"));

		assertEquals(1, tracker.getActiveOffers().size());
		assertEquals(1, tracker.awaitingCollection().size());
	}

	@Test
	public void onlyCollectionFreesTheSlot()
	{
		tracker.apply(event(3, "SOLD"));
		assertEquals(1, tracker.getActiveOffers().size());

		tracker.apply(event(3, "EMPTY"));

		assertTrue("EMPTY is the only state that frees a slot",
			tracker.getActiveOffers().isEmpty());
		assertTrue(tracker.awaitingCollection().isEmpty());
	}

	@Test
	public void anOfferStillTradingIsNotAwaitingCollection()
	{
		tracker.apply(event(4, "BUYING"));

		assertEquals("it holds a slot", 1, tracker.getActiveOffers().size());
		assertTrue("but there is nothing to collect yet", tracker.awaitingCollection().isEmpty());
		assertFalse(ActiveOfferTracker.isAwaitingCollection(event(4, "BUYING")));
	}

	@Test
	public void theClassifierAgreesWithTheTracker()
	{
		for (String terminal : new String[]{ "BOUGHT", "SOLD", "CANCELLED_BUY", "CANCELLED_SELL" })
		{
			assertTrue(terminal + " holds a slot pending collection",
				ActiveOfferTracker.isAwaitingCollection(event(1, terminal)));
		}
		for (String open : new String[]{ "BUYING", "SELLING", "EMPTY" })
		{
			assertFalse(open + " is not awaiting collection",
				ActiveOfferTracker.isAwaitingCollection(event(1, open)));
		}
		assertFalse(ActiveOfferTracker.isAwaitingCollection(null));
	}

	/** Slots are keyed independently: collecting one must not clear another. */
	@Test
	public void slotsAreTrackedIndependently()
	{
		tracker.apply(event(1, "BOUGHT"));
		tracker.apply(event(2, "BUYING"));
		tracker.apply(event(1, "EMPTY"));

		assertEquals(1, tracker.getActiveOffers().size());
		assertEquals("the one still trading survives",
			"BUYING", tracker.getActiveOffers().iterator().next().getEventType());
	}
}
