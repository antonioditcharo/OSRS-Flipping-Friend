package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import org.junit.Test;

/**
 * One physical offer, counted once.
 *
 * <p>124 of 784 settled offers in the live database are byte-identical repeats carrying different
 * correlation ids -- the same fill, reported twice. The event log deduplicates on the correlation id,
 * so two ids meant two events and every estimator downstream counted both.
 *
 * <p>Everything settled feeds capture, the fill hazard, calibration and the buy-limit ledger, and all
 * of them weight by quantity. A duplicated offer therefore does not merely add a row -- it doubles
 * that offer's say in what the model believes.
 */
public class SettledOnceTest
{
	private static final long T0 = 1_700_000_000L;

	private static OfferEvent offer(String type, int itemId, int slot, int filled, int total,
		long firstSeenAt, long observedAt)
	{
		return OfferEvent.builder(java.util.UUID.randomUUID().toString(), observedAt, type)
			.item(itemId, "test")
			.slot(slot)
			.buying(type.contains("BUY") || "BOUGHT".equals(type))
			.price(100)
			.quantities(total, filled)
			.firstSeenAt(firstSeenAt)
			.build();
	}

	@Test
	public void theSameOfferReportedTwiceIsCountedOnce()
	{
		// Deliberately different correlation ids, because that is what makes these two events rather
		// than one, and it is the only thing that differs.
		SettledOnce once = new SettledOnce();

		assertTrue(once.firstSighting(offer("BOUGHT", 561, 3, 5_000, 5_000, T0, T0 + 900)));
		assertFalse("the second report is the same offer",
			once.firstSighting(offer("BOUGHT", 561, 3, 5_000, 5_000, T0, T0 + 900)));
		assertEquals(1, once.duplicatesRefused());
	}

	@Test
	public void twoRealOffersOnTheSameItemBothCount()
	{
		// Same item, same slot, same price -- and genuinely two offers, because they did not begin and
		// end in the same second. Nothing here may collapse them.
		SettledOnce once = new SettledOnce();

		assertTrue(once.firstSighting(offer("BOUGHT", 561, 3, 5_000, 5_000, T0, T0 + 900)));
		assertTrue("a later offer is a different offer",
			once.firstSighting(offer("BOUGHT", 561, 3, 5_000, 5_000, T0 + 901, T0 + 1_800)));
		assertEquals(0, once.duplicatesRefused());
	}

	@Test
	public void thePartialAndTheCompleteAreDifferentEvents()
	{
		// The same offer reporting progress is not a duplicate of itself: the fill differs, and both
		// readings are real.
		SettledOnce once = new SettledOnce();

		assertTrue(once.firstSighting(offer("CANCELLED_BUY", 561, 3, 1_000, 5_000, T0, T0 + 900)));
		assertTrue(once.firstSighting(offer("CANCELLED_BUY", 561, 3, 2_000, 5_000, T0, T0 + 900)));
	}

	@Test
	public void theSameItemInADifferentSlotIsADifferentOffer()
	{
		SettledOnce once = new SettledOnce();

		assertTrue(once.firstSighting(offer("SOLD", 561, 1, 5_000, 5_000, T0, T0 + 900)));
		assertTrue(once.firstSighting(offer("SOLD", 561, 2, 5_000, 5_000, T0, T0 + 900)));
	}

	@Test
	public void memoryIsBoundedAndDropsTheOldest()
	{
		// A hot path, so what is remembered is capped. A repeat arriving after hundreds of other
		// settlements is not a repeat of anything still being reasoned about.
		SettledOnce once = new SettledOnce(2);

		once.firstSighting(offer("SOLD", 1, 1, 1, 1, T0, T0 + 1));
		once.firstSighting(offer("SOLD", 2, 1, 1, 1, T0, T0 + 2));
		once.firstSighting(offer("SOLD", 3, 1, 1, 1, T0, T0 + 3));

		assertTrue("the oldest has been forgotten, so it reads as new again",
			once.firstSighting(offer("SOLD", 1, 1, 1, 1, T0, T0 + 1)));
		assertFalse("the newest is still remembered",
			once.firstSighting(offer("SOLD", 3, 1, 1, 1, T0, T0 + 3)));
	}

	@Test
	public void seedingFromTheRecordDoesNotCountAsADuplicate()
	{
		// Startup teaches it what is already on the log, so a login replay does not re-count. That is
		// not a duplicate being refused, it is a memory being restored, and the health line should not
		// report it as one.
		SettledOnce once = new SettledOnce();
		once.remember(offer("BOUGHT", 561, 3, 5_000, 5_000, T0, T0 + 900));

		assertEquals("nothing has been refused yet", 0, once.duplicatesRefused());
		assertFalse("but the replay is recognised",
			once.firstSighting(offer("BOUGHT", 561, 3, 5_000, 5_000, T0, T0 + 900)));
	}
}
