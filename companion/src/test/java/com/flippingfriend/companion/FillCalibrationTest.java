package com.flippingfriend.companion;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the account's own settled offers are allowed to teach the planner, and what they are not.
 * <p>
 * The loop this closes had been open at the last inch for the life of the companion: every settled
 * offer was written to {@code execution_stat}, {@code executionStats()} could read them back, and
 * nothing called it. 1,386 observations across 280 items, none of which had ever reached a decision.
 * <p>
 * The reason to be careful rather than merely enthusiastic is that the obvious way to use them is
 * wrong. {@code fill_minutes} counts only offers that <em>completed</em>, and a completion is not a
 * random offer — it is one that happened to be quick. On the account this was written against, 531
 * completions averaged 2.8 minutes against 27 predicted while the 855 that did not complete
 * contributed nothing at all, so the naive ratio came out at 0.10. A planner told that fills take a
 * tenth of the predicted time would rank and size every trade on that belief.
 */
public class FillCalibrationTest
{
	private static final int ITEM = 2361;
	private static final int OTHER = 1987;

	/** An offer that finished: it has a fill time, and it counts as an uncensored observation. */
	private static SqliteStore.ExecutionStat completed(int count, double actualEach,
		double predictedEach)
	{
		return new SqliteStore.ExecutionStat(count, count, actualEach * count,
			predictedEach * count, actualEach * count, count,
			actualEach * count, predictedEach * count, count);
	}

	/**
	 * Offers that were cancelled before finishing. They have no fill time, but they were on the book
	 * for a real length of time, and that is the observation the old columns threw away.
	 */
	private static SqliteStore.ExecutionStat cancelled(int count, double openEach,
		double predictedEach)
	{
		return new SqliteStore.ExecutionStat(0, count, 0, 0, 0, 0,
			openEach * count, predictedEach * count, count);
	}

	@Test
	public void withNoHistoryEveryEstimateIsLeftExactlyAsItWas()
	{
		assertEquals(1.0, FillCalibration.NEUTRAL.waitMultiplier(ITEM), 1e-9);
		assertEquals(1.0, FillCalibration.from(new HashMap<>()).waitMultiplier(ITEM), 1e-9);
		assertEquals(1.0, FillCalibration.from(null).waitMultiplier(ITEM), 1e-9);
	}

	@Test
	public void aHandfulOfOffersIsAnecdoteRatherThanEvidence()
	{
		// This multiplier scales every duration the planner ranks on. Five offers saying the model is
		// four times too slow is five offers, and acting on it would move the whole board.
		Map<Integer, SqliteStore.ExecutionStat> stats = new HashMap<>();
		stats.put(ITEM, completed(5, 10, 40));

		assertEquals("below the evidence bar nothing is corrected", 1.0,
			FillCalibration.from(stats).waitMultiplier(ITEM), 1e-9);
	}

	@Test
	public void aModelThatRunsLongIsPulledIn()
	{
		// Forty offers, each taking half of what was predicted. That is a real, repeated bias.
		Map<Integer, SqliteStore.ExecutionStat> stats = new HashMap<>();
		stats.put(ITEM, completed(40, 20, 40));

		double multiplier = FillCalibration.from(stats).waitMultiplier(ITEM);

		assertTrue("a consistently slow prediction has to come down: " + multiplier,
			multiplier < 0.85);
		assertTrue("but never past the bound: " + multiplier, multiplier >= 0.5);
	}

	@Test
	public void theOffersThatNeverFilledAreCountedToo()
	{
		// The trap, in one comparison. Ten offers completed quickly; forty ran their full predicted
		// time and were cancelled unfilled. Judged on completions alone the model looks four times
		// too slow; judged on every offer it is close to right, which is the truth.
		Map<Integer, SqliteStore.ExecutionStat> stats = new HashMap<>();
		stats.put(ITEM, completed(10, 10, 40));
		stats.put(OTHER, cancelled(40, 40, 40));

		double whole = FillCalibration.from(stats).waitMultiplier(OTHER);

		assertTrue("an item whose offers ran their full predicted time must not be marked down: "
			+ whole, whole > 0.9);
	}

	@Test
	public void anItemWithNoHistoryOfItsOwnBorrowsTheAccountsBias()
	{
		// The point of the shrinkage: something never traded before still benefits from the fact
		// that this account's fills generally run short.
		Map<Integer, SqliteStore.ExecutionStat> stats = new HashMap<>();
		stats.put(ITEM, completed(60, 20, 40));

		FillCalibration calibration = FillCalibration.from(stats);

		assertEquals("an unseen item takes the account-wide correction",
			calibration.overall(), calibration.waitMultiplier(999_999), 1e-9);
		assertTrue(calibration.overall() < 1.0);
	}

	@Test
	public void oneStrangeItemCannotRunAwayWithTheBoard()
	{
		// A single item reporting a sixty-fold error is a bug or a fluke, not a market. The bound is
		// what stops it becoming the plan.
		Map<Integer, SqliteStore.ExecutionStat> stats = new HashMap<>();
		stats.put(ITEM, completed(40, 20, 40));
		stats.put(OTHER, completed(40, 2_400, 40));

		FillCalibration calibration = FillCalibration.from(stats);

		assertTrue("clamped above: " + calibration.waitMultiplier(OTHER),
			calibration.waitMultiplier(OTHER) <= 2.0);
		assertTrue("clamped below: " + calibration.waitMultiplier(ITEM),
			calibration.waitMultiplier(ITEM) >= 0.5);
	}

	@Test
	public void rowsWrittenBeforeTheCensoredColumnsExistedAreIgnored()
	{
		// Every existing install is in this state: hundreds of completions-only rows whose new
		// columns default to zero. They are exactly the biased sample, and being old is not what
		// disqualifies them -- being completions-only is.
		Map<Integer, SqliteStore.ExecutionStat> legacy = new HashMap<>();
		legacy.put(ITEM, new SqliteStore.ExecutionStat(531, 1_386, 1_471, 14_478, 1_471, 531));

		assertEquals("a completions-only history teaches this nothing", 1.0,
			FillCalibration.from(legacy).waitMultiplier(ITEM), 1e-9);
	}
}
