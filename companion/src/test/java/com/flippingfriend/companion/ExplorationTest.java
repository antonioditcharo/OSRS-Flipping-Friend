package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.learning.ThompsonSampler;
import java.util.Random;
import org.junit.Test;

/**
 * Covers the policy half of the loop: that ranking explores, that it explores less as it learns, and
 * that what the player is shown is not the random draw.
 *
 * <p>Seeded throughout. An unseeded Thompson sampler makes a flaky test, and a flaky test in a suite
 * that guards a trading engine gets deleted rather than fixed — which is how five test files came to
 * be dropped on 1 September 2026.
 */
public class ExplorationTest
{
	private static OfferEvent settledBuy(int itemId, boolean complete)
	{
		return OfferEvent.builder("t", 1_000L, "BOUGHT")
			.item(itemId, "Item " + itemId)
			.buying(true)
			.quantities(100, complete ? 100 : 50)
			.recommendation("plan-1", 1_000_000, 100, 0, 30.0, 0.80)
			.build();
	}

	/**
	 * An item with no history must sometimes rank above its point estimate, or it can never win a
	 * slot from a model that has quietly underrated it.
	 */
	@Test
	public void anUntriedItemIsSometimesRankedAboveItsEstimate()
	{
		FillCalibration calibration = new FillCalibration();
		int above = 0;
		int trials = 400;
		for (int i = 0; i < trials; i++)
		{
			if (calibration.explore(4151, 0.50) > 0.50)
			{
				above++;
			}
		}
		assertTrue("draws must straddle the estimate, got " + above + "/" + trials,
			above > trials / 4 && above < trials * 3 / 4);
		assertTrue("and the rate must be reported", calibration.explorationRate() > 0.0);
	}

	/** The draw is centred on what it was given, so exploration perturbs the ranking rather than replacing it. */
	@Test
	public void drawsAreCentredOnTheEstimateTheyWereGiven()
	{
		FillCalibration calibration = new FillCalibration();
		double total = 0;
		int trials = 2_000;
		for (int i = 0; i < trials; i++)
		{
			total += calibration.explore(4151, 0.30);
		}
		assertEquals("mean draw must track the prior", 0.30, total / trials, 0.06);
	}

	/**
	 * The point of Thompson sampling: uncertainty shrinks with evidence, so the policy anneals on its
	 * own rather than on a schedule someone has to tune.
	 */
	@Test
	public void explorationNarrowsAsEvidenceAccumulates()
	{
		ThompsonSampler cold = new ThompsonSampler(new Random(1));
		ThompsonSampler warm = new ThompsonSampler(new Random(1));
		for (int i = 0; i < 400; i++)
		{
			warm.observe(4151, i % 2 == 0);
		}

		assertEquals(0, cold.observationsFor(4151));
		assertEquals(400, warm.observationsFor(4151));
		assertTrue("spread must shrink: cold " + spread(cold) + " vs warm " + spread(warm),
			spread(warm) < spread(cold));
	}

	private static double spread(ThompsonSampler sampler)
	{
		double min = 1.0;
		double max = 0.0;
		for (int i = 0; i < 500; i++)
		{
			double drawn = sampler.sample(4151, 0.50);
			min = Math.min(min, drawn);
			max = Math.max(max, drawn);
		}
		return max - min;
	}

	/** Evidence moves the posterior toward what happened, not toward what was claimed. */
	@Test
	public void repeatedFailuresPullTheItemDown()
	{
		FillCalibration calibration = new FillCalibration();
		for (int i = 0; i < 200; i++)
		{
			calibration.observeSettled(settledBuy(4151, false), 0.80);
		}

		double total = 0;
		for (int i = 0; i < 500; i++)
		{
			total += calibration.explore(4151, 0.80);
		}
		assertTrue("an item that never completes must rank below its claim, got " + total / 500,
			total / 500 < 0.50);
	}

	/** One item's evidence must not move another's. */
	@Test
	public void posteriorsArePerItem()
	{
		FillCalibration calibration = new FillCalibration();
		for (int i = 0; i < 200; i++)
		{
			calibration.observeSettled(settledBuy(4151, false), 0.80);
		}

		double other = 0;
		for (int i = 0; i < 500; i++)
		{
			other += calibration.explore(99999, 0.80);
		}
		assertEquals("an unrelated item keeps its prior", 0.80, other / 500, 0.08);
	}

	@Test
	public void healthReportsTheMeasuredRateRatherThanTheIntention()
	{
		FillCalibration calibration = new FillCalibration();
		for (int i = 0; i < 200; i++)
		{
			calibration.observeSettled(settledBuy(4151, i % 3 == 0), 0.80);
			calibration.explore(4151, 0.80);
		}
		String summary = calibration.summary();
		assertTrue("must state the measured draw rate: " + summary,
			summary.contains("of draws above estimate"));
		assertTrue("and how many items are being tracked: " + summary, summary.contains("items"));
	}
}
