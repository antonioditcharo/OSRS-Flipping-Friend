package com.flippingfriend.model;

import com.flippingfriend.session.FlipRecord;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The calibrator's whole job is to say how wrong the predictions have been, so the one thing it must
 * not do is be wrong in a flattering direction. Averaging per-flip ratios does exactly that, and the
 * error is invisible in any test that uses flips which were all wrong the same way.
 */
public class CalibratorTest
{
	private static final int ITEM = 4151;

	/** Bought at t=0, sold {@code actualMinutes} later, against a prediction of {@code predicted}. */
	private static FlipRecord flip(double predictedMinutes, double actualMinutes,
		long predictedProfit, long profit)
	{
		return new FlipRecord(ITEM, "Abyssal whip", 1, 1_000, 1_100, 0, profit,
			0, (long) (actualMinutes * 60), predictedMinutes, predictedProfit, "MODERATE");
	}

	private static Calibrator trainedOn(List<FlipRecord> history)
	{
		Calibrator calibrator = new Calibrator();
		calibrator.rebuild(history);
		return calibrator;
	}

	@Test
	public void offsettingErrorsCancelInsteadOfAveragingUpwards()
	{
		// Two flips, one that filled twice as fast as predicted and one twice as slow. Together they
		// took exactly as long as predicted, so the honest speed correction is 1.0.
		//
		// A mean of the two ratios is (2.0 + 0.5) / 2 = 1.25, which claims a 25% speed edge that is
		// not there. Jensen's inequality means this can only ever err in that direction, and
		// scoreMultiplier multiplies the result straight into the ranking.
		List<FlipRecord> history = Arrays.asList(
			flip(60, 30, 1_000, 1_000),
			flip(60, 120, 1_000, 1_000));

		double speed = trainedOn(history).speedMultiplier(ITEM);

		assertTrue("a pooled correction must not exceed the naive average of the ratios: " + speed,
			speed < 1.2);
		assertTrue("and it must not claim the fills were faster than predicted: " + speed,
			speed <= 1.0);
	}

	@Test
	public void aConsistentOverestimateIsReportedAsOne()
	{
		// The control. When every flip is wrong the same way, pooling and averaging agree, so this
		// pins that the fix did not simply move the answer.
		List<FlipRecord> history = Arrays.asList(
			flip(60, 120, 1_000, 500),
			flip(60, 120, 1_000, 500),
			flip(60, 120, 1_000, 500));

		Calibrator calibrator = trainedOn(history);

		assertTrue("fills took twice as long, so the speed correction must be below one",
			calibrator.speedMultiplier(ITEM) < 1.0);
		assertTrue("and half the profit arrived, so the profit correction must be below one",
			calibrator.profitMultiplier(ITEM) < 1.0);
	}

	@Test
	public void bigFlipsCountForMoreThanSmallOnes()
	{
		// The other half of pooling. One flip predicted at ten hours and one at six minutes are not
		// equally informative about timing, and averaging their ratios treats them as though they
		// were.
		List<FlipRecord> history = Arrays.asList(
			flip(600, 600, 10_000, 10_000),
			flip(6, 60, 100, 10));

		double speed = trainedOn(history).speedMultiplier(ITEM);

		// Pooled: 606 predicted against 660 actual, so a shade under one. Averaged, the tiny flip's
		// 0.1 ratio would drag it to about 0.55.
		assertTrue("the small flip must not dominate: " + speed, speed > 0.8);
	}

	@Test
	public void knowingNothingCorrectsNothing()
	{
		Calibrator calibrator = new Calibrator();

		assertEquals(1.0, calibrator.speedMultiplier(ITEM), 1e-9);
		assertEquals(1.0, calibrator.profitMultiplier(ITEM), 1e-9);
	}
}
