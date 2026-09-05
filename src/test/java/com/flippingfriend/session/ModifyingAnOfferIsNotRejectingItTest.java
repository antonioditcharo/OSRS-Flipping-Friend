package com.flippingfriend.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Changing an offer is not the same as giving up on the item.
 *
 * <p>The Grand Exchange has no modify: changing a live offer means aborting it and placing another,
 * so both arrive as one indistinguishable event. {@link SkipList#isRejection} counts any part-filled
 * buy that was cancelled as a rejection, so adjusting the price of an order that had begun to fill
 * put the item on an eight-hour cooldown every single time — the plan dropped it and moved to the
 * next item while the player was still halfway through re-placing it.
 *
 * <p>The plugin tried to tell the two apart by reading the interface at the instant the cancel fired,
 * which is a race the client usually wins: the offer is cancelled first, the setup panel opens after.
 * That is why the three ways of cancelling an offer — the modify button, an abort from the slot's
 * right-click menu, an abort from inside the offer window — all behaved differently, and why the
 * exemptions that were supposed to cover this did not fire.
 *
 * <p>None of these tests knows which of those three routes was taken, and that is the point: the
 * signal is what the player does next, which is identical for all of them.
 */
public class ModifyingAnOfferIsNotRejectingItTest
{
	private static final int GRAPES = 1987;
	private static final int WHIP = 4151;
	private static final long GRACE = 60_000;

	@Test
	public void rePlacingTheOfferWithdrawsTheJudgement()
	{
		AbandonedBuys judge = new AbandonedBuys(GRACE);
		long t = 1_000_000;

		judge.cancelled(GRAPES, t);
		// However they cancelled it, this is what modifying looks like: they buy it again.
		assertTrue("the judgement was there to withdraw", judge.replaced(GRAPES));

		assertTrue("and nothing is left to hold against them",
			judge.due(t + GRACE * 10).isEmpty());
	}

	@Test
	public void walkingAwayStillCountsAsARejection()
	{
		// The behaviour that has to survive: cancelling and not going back means the player does not
		// want the item, and the cooldown should still land.
		AbandonedBuys judge = new AbandonedBuys(GRACE);
		long t = 1_000_000;

		judge.cancelled(GRAPES, t);

		assertTrue("not yet -- give them time to come back", judge.due(t + GRACE - 1).isEmpty());
		assertEquals("now it counts", java.util.Collections.singletonList(GRAPES),
			judge.due(t + GRACE));
	}

	@Test
	public void aVerdictIsOnlyDeliveredOnce()
	{
		AbandonedBuys judge = new AbandonedBuys(GRACE);
		long t = 1_000_000;

		judge.cancelled(GRAPES, t);
		assertEquals(1, judge.due(t + GRACE).size());

		assertTrue("the tick runs every two seconds; it must not skip the item again each time",
			judge.due(t + GRACE * 5).isEmpty());
	}

	@Test
	public void itemsAreJudgedIndependently()
	{
		// Modifying one order must not rescue an item the player genuinely abandoned, and vice versa.
		AbandonedBuys judge = new AbandonedBuys(GRACE);
		long t = 1_000_000;

		judge.cancelled(GRAPES, t);
		judge.cancelled(WHIP, t);
		judge.replaced(GRAPES);

		assertEquals(java.util.Collections.singletonList(WHIP), judge.due(t + GRACE));
	}

	@Test
	public void repeatedlyAbortingTheSameItemCannotHoldTheVerdictOpenForEver()
	{
		// Re-arming keeps the original deadline. Otherwise a player who kept aborting and re-placing
		// the same offer would push the decision out indefinitely and it would never be made.
		AbandonedBuys judge = new AbandonedBuys(GRACE);
		long t = 1_000_000;

		judge.cancelled(GRAPES, t);
		judge.cancelled(GRAPES, t + GRACE / 2);
		judge.cancelled(GRAPES, t + GRACE - 1);

		assertEquals("still decided on the first deadline",
			java.util.Collections.singletonList(GRAPES), judge.due(t + GRACE));
	}

	@Test
	public void whatIsAwaitingAVerdictCanBeInspected()
	{
		// The plugin walks this list to ask a question only it can answer -- is the setup panel open
		// on this item right now -- which is the same check that used to race the cancel event and
		// now runs seconds later, when the client has finished transitioning.
		AbandonedBuys judge = new AbandonedBuys(GRACE);
		long t = 1_000_000;

		judge.cancelled(GRAPES, t);

		List<Integer> awaiting = judge.awaiting();
		assertEquals(1, awaiting.size());
		assertEquals(Integer.valueOf(GRAPES), awaiting.get(0));
		assertTrue(judge.isAwaiting(GRAPES));
		assertFalse(judge.isAwaiting(WHIP));
	}

	@Test
	public void aPartFilledBuyIsStillWhatTriggersAllThis()
	{
		// The premise, pinned so the reason this class exists stays visible: a cancelled buy that had
		// begun to fill reads as a rejection, and modifying a price mid-fill is the ordinary way to
		// arrive there.
		assertTrue("500 of 2000 bought, then cancelled",
			SkipList.isRejection(500, 2000, 5, 30));
		assertFalse("a completed buy cleared from the slot is not a view about anything",
			SkipList.isRejection(2000, 2000, 5, 30));
	}
}
