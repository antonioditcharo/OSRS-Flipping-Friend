package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.learning.IsotonicCalibrator;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class FillCalibrationTest
{
	private final FillCalibration calibration = new FillCalibration();

	/** A settled offer: BOUGHT/SOLD are terminal, and completeness follows from the filled quantity. */
	private static OfferEvent settled(boolean buying, boolean complete, double claimed)
	{
		int wanted = 100;
		return OfferEvent.builder("t", 1_000L, buying ? "BOUGHT" : "SOLD")
			.item(4151, "Abyssal whip")
			.buying(buying)
			.quantities(wanted, complete ? wanted : wanted / 2)
			.recommendation("plan-1", 1_000_000, wanted, 0, 30.0, claimed)
			.build();
	}

	private void feed(boolean buying, int count, double claimed, double actualRate)
	{
		for (int i = 0; i < count; i++)
		{
			boolean occurred = i < (int) Math.round(count * actualRate);
			calibration.observeSettled(settled(buying, occurred, claimed), claimed);
		}
	}

	@Test
	public void inertUntilThereIsEvidence()
	{
		assertFalse(calibration.isActive());
		// A cold calibrator must return its input untouched. Wiring this in cannot move a price
		// before it has grounds to.
		assertEquals(0.93, calibration.calibrate(true, 0.93), 1e-9);
		assertEquals(1.0, calibration.durationMultiplier(4151), 1e-9);
		assertTrue(calibration.summary().contains("learning"));
	}

	/**
	 * The comparison the whole system is built around: the model claimed 93%, it happened 74% of the
	 * time. Once there is enough evidence, the correction must pull the claim toward what happened.
	 */
	@Test
	public void correctsAnOptimisticClaimTowardWhatActuallyHappened()
	{
		feed(true, IsotonicCalibrator.MIN_OBSERVATIONS * 2, 0.93, 0.74);

		double corrected = calibration.calibrate(true, 0.93);
		assertTrue("must correct downward, got " + corrected, corrected < 0.93);
		assertTrue("must land near the realised rate, got " + corrected,
			Math.abs(corrected - 0.74) < 0.10);
		assertTrue(calibration.isActive());
		assertTrue("health must report the optimism: " + calibration.summary(),
			calibration.summary().contains("optimistic"));
	}

	/**
	 * Buy and sell legs fail for unrelated reasons — a buy rests below the market, a sell above it —
	 * so pooling them produces a curve describing neither. Evidence about one must not move the other.
	 */
	@Test
	public void legsAreCalibratedSeparately()
	{
		feed(true, IsotonicCalibrator.MIN_OBSERVATIONS * 2, 0.90, 0.50);

		assertTrue("the buy leg learned", calibration.calibrate(true, 0.90) < 0.90);
		assertEquals("the sell leg saw nothing and must be untouched",
			0.90, calibration.calibrate(false, 0.90), 1e-9);
	}

	/**
	 * An offer with no claim attached is not evidence that the model predicted zero. Feeding it in
	 * would teach the calibrator that the model is confidently wrong about everything.
	 */
	@Test
	public void offersWithoutAClaimAreIgnored()
	{
		for (int i = 0; i < IsotonicCalibrator.MIN_OBSERVATIONS * 2; i++)
		{
			assertFalse(calibration.observeSettled(settled(true, false, 0.0), 0.0));
		}
		assertFalse("unclaimed offers must not activate anything", calibration.isActive());
		assertEquals(0.93, calibration.calibrate(true, 0.93), 1e-9);
	}

	@Test
	public void durationsRefreshOnATimerRatherThanEveryFill()
	{
		assertTrue("due on a cold start", calibration.durationsDue(1_000L));

		Map<Integer, SqliteStore.ExecutionStat> stats = new HashMap<>();
		// 40 completions, 80 realised minutes against 40 predicted: fills take twice as long.
		stats.put(4151, new SqliteStore.ExecutionStat(40, 40, 80.0, 40.0, 80.0, 40));
		calibration.refreshDurations(stats, 1_000L);

		assertFalse("not due again immediately", calibration.durationsDue(1_010L));
		assertTrue("due once the interval elapses", calibration.durationsDue(1_000L + 120L));

		double multiplier = calibration.durationMultiplier(4151);
		assertTrue("must exceed 1.0 when fills run long, got " + multiplier, multiplier > 1.0);
		assertTrue("shrunk toward neutral, so below the raw 2.0, got " + multiplier, multiplier < 2.0);
		assertEquals("an unseen item falls back to the pooled ratio",
			multiplier, calibration.durationMultiplier(99999), 0.5);
	}
}
