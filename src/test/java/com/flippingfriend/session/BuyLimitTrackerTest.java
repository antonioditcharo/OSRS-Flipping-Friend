package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The buy limit window is anchored to the first purchase, not sliding. Getting that wrong lets the
 * plugin suggest purchases the game will silently refuse to fill, which strands an offer in a slot
 * with no error message — so it is worth pinning down precisely.
 */
public class BuyLimitTrackerTest
{
	private static final int ITEM = 561;
	private static final int LIMIT = 10_000;

	private BuyLimitTracker tracker;
	private Instant start;

	@Before
	public void setUp()
	{
		tracker = new BuyLimitTracker(Mockito.mock(PluginStorage.class));
		start = Instant.parse("2026-08-17T12:00:00Z");
	}

	@Test
	public void countsPurchasesAgainstTheLimit()
	{
		tracker.recordPurchase(ITEM, 3000, start);

		assertEquals(3000, tracker.purchasedInWindow(ITEM, start));
		assertEquals(7000, tracker.remaining(ITEM, LIMIT, start));
	}

	@Test
	public void accumulatesPartialFillsWithinTheSameWindow()
	{
		tracker.recordPurchase(ITEM, 2000, start);
		tracker.recordPurchase(ITEM, 1500, start.plusSeconds(600));
		tracker.recordPurchase(ITEM, 500, start.plusSeconds(3600));

		assertEquals(4000, tracker.purchasedInWindow(ITEM, start.plusSeconds(3600)));
		assertEquals(6000, tracker.remaining(ITEM, LIMIT, start.plusSeconds(3600)));
	}

	@Test
	public void windowIsAnchoredToTheFirstPurchaseNotTheLatest()
	{
		tracker.recordPurchase(ITEM, 6000, start);
		// Three hours later, still inside the original window.
		tracker.recordPurchase(ITEM, 1000, start.plusSeconds(3 * 3600));

		assertEquals(7000, tracker.purchasedInWindow(ITEM, start.plusSeconds(3 * 3600)));

		// Four hours after the first purchase the whole allowance returns, even though the second
		// purchase was only an hour ago. A sliding window would still be blocking here.
		Instant afterReset = start.plusSeconds(4 * 3600 + 1);
		assertEquals(0, tracker.purchasedInWindow(ITEM, afterReset));
		assertEquals(LIMIT, tracker.remaining(ITEM, LIMIT, afterReset));
	}

	@Test
	public void startsAFreshWindowAfterExpiry()
	{
		tracker.recordPurchase(ITEM, LIMIT, start);
		assertEquals(0, tracker.remaining(ITEM, LIMIT, start));

		Instant afterReset = start.plusSeconds(4 * 3600 + 1);
		tracker.recordPurchase(ITEM, 100, afterReset);

		assertEquals(100, tracker.purchasedInWindow(ITEM, afterReset));
		assertEquals(LIMIT - 100, tracker.remaining(ITEM, LIMIT, afterReset));
	}

	@Test
	public void reportsWhenTheAllowanceComesBack()
	{
		tracker.recordPurchase(ITEM, 500, start);

		assertEquals(start.plusSeconds(4 * 3600), tracker.resetsAt(ITEM, start));
		assertNull("an untouched item has no window", tracker.resetsAt(999, start));
	}

	@Test
	public void neverReportsNegativeRemaining()
	{
		tracker.recordPurchase(ITEM, LIMIT + 5000, start);
		assertEquals(0, tracker.remaining(ITEM, LIMIT, start));
	}

	@Test
	public void prunesExpiredWindows()
	{
		tracker.recordPurchase(ITEM, 500, start);
		tracker.prune(start.plusSeconds(4 * 3600 + 1));

		assertEquals(0, tracker.purchasedInWindow(ITEM, start.plusSeconds(4 * 3600 + 1)));
		assertEquals(0, tracker.activeWindows(start.plusSeconds(4 * 3600 + 1)).size());
	}

}
