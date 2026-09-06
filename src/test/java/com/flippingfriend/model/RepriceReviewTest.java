package com.flippingfriend.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * When moving an offer is worth the interruption, and when it is noise.
 *
 * <p>The plugin used to notice only offers that had become too passive to fill. An offer the market
 * had moved in FAVOUR of was never mentioned: a sell listed at 100 while buyers moved to 110 simply
 * filled at 100 and nobody said anything.
 *
 * <p>Fixing that one-directional blindness by itself would be worse than leaving it. Prices move
 * every few seconds, an offer is almost never at exactly the number the engine would pick this
 * instant, and advice that fires on every coin of drift is advice nobody can follow. So most of this
 * class is about NOT speaking.
 */
public class RepriceReviewTest
{
	private static final int SLOT = 3;
	private static final int ITEM = 4151;
	private static final long MIN_PROFIT = 5_000;
	private static final long T0 = 1_700_000_000L;

	private final RepriceReview review = new RepriceReview();

	@Test
	public void aWorthwhileImprovementIsWorthSaying()
	{
		// 40,000 gp on the table, 8,000 more available: a fifth better, and well past the floor.
		assertTrue(review.worthMoving(SLOT, ITEM, 100, 110, 40_000, 48_000, MIN_PROFIT, T0));
	}

	@Test
	public void aCoinOfDriftIsNot()
	{
		// The case that would make the plugin unusable if only the direction were fixed.
		assertFalse("40 gp on a 40,000 gp position is noise",
			review.worthMoving(SLOT, ITEM, 100, 101, 40_000, 40_040, MIN_PROFIT, T0));
	}

	@Test
	public void anImprovementUnderTheFloorIsNot()
	{
		// A tenth of the player's own minimum profit per flip. They have already said what a trade
		// must be worth to be worth doing; an adjustment is held to a fraction of the same bar.
		assertFalse("400 gp is under the 500 gp floor a 5,000 minimum implies",
			review.worthMoving(SLOT, ITEM, 100, 110, 1_000, 1_400, MIN_PROFIT, T0));
		assertTrue("600 gp clears it",
			review.worthMoving(SLOT, ITEM, 100, 110, 1_000, 1_600, MIN_PROFIT, T0));
	}

	@Test
	public void aProportionallySmallGainIsNotWorthIt()
	{
		// Clears the absolute floor and is still 1% of what is already on the table.
		assertFalse("1,000 gp on a 100,000 gp position is not worth a retype",
			review.worthMoving(SLOT, ITEM, 100, 110, 100_000, 101_000, MIN_PROFIT, T0));
	}

	@Test
	public void aStalledOfferIsRescuedHoweverSmallTheGain()
	{
		// Worth nothing as it stands, which is what an offer that cannot fill looks like. Proportion
		// has nothing to say when the current value is zero, so only the absolute floor applies.
		assertTrue("any real gain beats nothing",
			review.worthMoving(SLOT, ITEM, 100, 110, 0, 800, MIN_PROFIT, T0));
	}

	@Test
	public void movingBackwardsIsNeverSuggested()
	{
		assertFalse(review.worthMoving(SLOT, ITEM, 100, 90, 40_000, 30_000, MIN_PROFIT, T0));
	}

	@Test
	public void thePriceItAlreadyHasIsNotAChange()
	{
		assertFalse(review.worthMoving(SLOT, ITEM, 100, 100, 40_000, 90_000, MIN_PROFIT, T0));
	}

	@Test
	public void havingNoOpinionIsNotAChange()
	{
		assertFalse(review.worthMoving(SLOT, ITEM, 100, 0, 40_000, 90_000, MIN_PROFIT, T0));
	}

	@Test
	public void aTargetIsNotWalkedBackAndForth()
	{
		// The oscillation a threshold alone cannot prevent. Two prices either side of the bar will
		// trade places for as long as the market jitters, and each swap passes every other test on
		// its own merits.
		assertTrue(review.worthMoving(SLOT, ITEM, 100, 110, 40_000, 48_000, MIN_PROFIT, T0));
		review.noteAdvised(SLOT, ITEM, 110, T0);

		assertFalse("moved a moment ago; let it settle",
			review.worthMoving(SLOT, ITEM, 110, 120, 48_000, 56_000, MIN_PROFIT, T0 + 60));
		assertTrue("and once the cooldown is up it may move again",
			review.worthMoving(SLOT, ITEM, 110, 120, 48_000, 56_000, MIN_PROFIT,
				T0 + RepriceReview.COOLDOWN_SECONDS));
	}

	@Test
	public void standingAdviceIsNotSuppressedByItsOwnCooldown()
	{
		// The trap in a naive cooldown. Advice the player has not acted on yet must keep being shown;
		// only a DIFFERENT target waits. Otherwise a recommendation would vanish minutes after being
		// made, for no reason the player could see.
		assertTrue(review.worthMoving(SLOT, ITEM, 100, 110, 40_000, 48_000, MIN_PROFIT, T0));
		review.noteAdvised(SLOT, ITEM, 110, T0);

		assertTrue("the same recommendation, still standing",
			review.worthMoving(SLOT, ITEM, 100, 110, 40_000, 48_000, MIN_PROFIT, T0 + 60));
	}

	@Test
	public void repeatingAdviceDoesNotKeepItsOwnCooldownOpen()
	{
		// noteAdvised is called every time the advice is shown. If repeating it restarted the clock,
		// a standing recommendation would hold the cooldown open indefinitely and no better price
		// could ever replace it.
		review.noteAdvised(SLOT, ITEM, 110, T0);
		review.noteAdvised(SLOT, ITEM, 110, T0 + 100);
		review.noteAdvised(SLOT, ITEM, 110, T0 + 200);

		assertTrue("the clock ran from the first time it was said",
			review.worthMoving(SLOT, ITEM, 110, 120, 48_000, 56_000, MIN_PROFIT,
				T0 + RepriceReview.COOLDOWN_SECONDS));
	}

	@Test
	public void slotsAreIndependent()
	{
		review.noteAdvised(SLOT, ITEM, 110, T0);

		assertTrue("one offer settling says nothing about another",
			review.worthMoving(SLOT + 1, ITEM, 100, 110, 40_000, 48_000, MIN_PROFIT, T0 + 60));
	}

	@Test
	public void aSlotReusedByAnotherItemDoesNotInheritTheCooldown()
	{
		// Slots are reused. A finished trade would otherwise hand its cooldown to whatever went into
		// that slot next and silence the first five minutes of a completely unrelated offer -- and
		// nothing would have said so. Carrying the item makes a slot's history self-expiring, with no
		// separate clean-up call for anyone to forget to make.
		review.noteAdvised(SLOT, ITEM, 110, T0);

		assertTrue("a different item in the same slot is a different offer",
			review.worthMoving(SLOT, 1603, 100, 120, 40_000, 48_000, MIN_PROFIT, T0 + 60));
		assertFalse("and the same item is still settling",
			review.worthMoving(SLOT, ITEM, 110, 120, 48_000, 56_000, MIN_PROFIT, T0 + 60));
	}

	@Test
	public void aMinimumOfZeroStillHasAFloor()
	{
		// Someone who sets no minimum profit has not asked to be told about every coin.
		assertFalse(review.worthMoving(SLOT, ITEM, 100, 110, 0, 50, 0, T0));
		assertTrue(review.worthMoving(SLOT, ITEM, 100, 110, 0, RepriceReview.ABSOLUTE_FLOOR_GP, 0, T0));
	}
}
