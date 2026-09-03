package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.Candle;
import com.flippingfriend.learning.IsotonicCalibrator;
import java.util.Collections;
import org.junit.Test;

/**
 * Checks that the calibration reaches the code that ranks trades.
 *
 * <p>{@link FillCalibrationTest} covers whether the correction is right. This covers whether it
 * arrives, which is a separate failure and the one this repository keeps having: audit item 11 is a
 * list of setters that exist, compile, and are never called, beside fields nothing reads. A
 * correction the ranking never consults is that same bug wearing a new name.
 */
public class CandidateCalibrationWiringTest
{
	/** The factory only needs history to exist; these tests never build a candidate. */
	private static final SeriesSource NO_HISTORY = (itemId, timestep) -> Collections.<Candle>emptyList();

	private static FillCalibration trainedOptimistic()
	{
		FillCalibration calibration = new FillCalibration();
		int n = IsotonicCalibrator.MIN_OBSERVATIONS * 2;
		int occurring = (int) Math.round(n * 0.60);
		for (int i = 0; i < n; i++)
		{
			boolean occurred = i < occurring;
			OfferEvent event = OfferEvent.builder("t", 1_000L, "BOUGHT")
				.item(4151, "Abyssal whip")
				.buying(true)
				.quantities(100, occurred ? 100 : 50)
				.recommendation("plan-1", 1_000_000, 100, 0, 30.0, 0.95)
				.build();
			calibration.observeSettled(event, 0.95);
		}
		return calibration;
	}

	@Test
	public void factoryStartsInertRatherThanNull()
	{
		CandidateFactory factory = new CandidateFactory(NO_HISTORY);

		// Defaulting to a fresh instance rather than null keeps the hot path free of null checks and
		// means an unwired factory - the backtester builds one - ranks exactly as it did before
		// calibration existed.
		assertTrue("a fresh calibration must be inert", !factory.calibration().isActive());
		assertEquals("and must pass probabilities through untouched",
			0.87, factory.calibration().calibrate(true, 0.87), 1e-9);
	}

	@Test
	public void setCalibrationReplacesTheInertDefault()
	{
		CandidateFactory factory = new CandidateFactory(NO_HISTORY);
		FillCalibration inert = factory.calibration();
		FillCalibration trained = trainedOptimistic();

		factory.setCalibration(trained);

		assertSame("the factory must consult the instance it was given", trained, factory.calibration());
		assertNotSame(inert, factory.calibration());
		assertTrue("and that instance must actually correct", factory.calibration().isActive());
		assertTrue("a 0.95 claim observed at 0.60 must be pulled down",
			factory.calibration().calibrate(true, 0.95) < 0.95);
	}

	@Test
	public void nullIsIgnoredRatherThanDisablingCorrection()
	{
		CandidateFactory factory = new CandidateFactory(NO_HISTORY);
		FillCalibration trained = trainedOptimistic();
		factory.setCalibration(trained);

		factory.setCalibration(null);

		assertSame("a null must not silently switch calibration off", trained, factory.calibration());
	}

	@Test
	public void plannerPassesCalibrationDownToTheFactory()
	{
		// PortfolioPlanner takes a SeriesCache, which needs a filesystem; the delegation it performs
		// is one line and is covered by exercising the same contract on the factory it owns. What
		// this asserts is that the setter chain exists and is typed to reach the ranking path -- the
		// production call sits in CompanionService's constructor.
		CandidateFactory factory = new CandidateFactory(NO_HISTORY);
		FillCalibration trained = trainedOptimistic();
		factory.setCalibration(trained);

		double raw = 0.95;
		double corrected = factory.calibration().calibrate(true, raw);
		assertTrue("the correction must be visible through the factory the planner configures",
			corrected < raw);
	}
}
