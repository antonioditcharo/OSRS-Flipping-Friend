package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.learning.IsotonicCalibrator;
import org.junit.Test;

/**
 * Covers whether what the system learns survives the companion being restarted.
 *
 * <p>This is not a nicety. {@link IsotonicCalibrator} corrects nothing until
 * {@value com.flippingfriend.learning.IsotonicCalibrator#MIN_OBSERVATIONS} settled offers per leg
 * have accumulated, and the companion is restarted whenever the machine reboots or the jar is
 * rebuilt. Without persistence the count resets each time, so on any normal desktop the calibrator
 * could run indefinitely and never once reach the threshold that lets it do its job — the entire
 * loop would be live, correct, and permanently inert.
 */
public class CalibrationPersistenceTest
{
	private final Gson gson = new Gson();

	private static OfferEvent settled(boolean buying, boolean complete)
	{
		return OfferEvent.builder("t", 1_000L, buying ? "BOUGHT" : "SOLD")
			.item(4151, "Abyssal whip")
			.buying(buying)
			.quantities(100, complete ? 100 : 50)
			.recommendation("plan-1", 1_000_000, 100, 0, 30.0, 0.90)
			.build();
	}

	private static FillCalibration trained()
	{
		FillCalibration calibration = new FillCalibration();
		int n = IsotonicCalibrator.MIN_OBSERVATIONS * 2;
		int occurring = (int) Math.round(n * 0.60);
		for (int i = 0; i < n; i++)
		{
			calibration.observeSettled(settled(true, i < occurring), 0.90);
		}
		return calibration;
	}

	@Test
	public void aTrainedCalibrationSurvivesAJsonRoundTrip()
	{
		FillCalibration before = trained();
		double corrected = before.calibrate(true, 0.90);
		assertTrue("precondition: it must actually be correcting", corrected < 0.90);

		String json = gson.toJson(before.snapshot());
		FillCalibration after = new FillCalibration();
		assertTrue("the snapshot must restore",
			after.restore(gson.fromJson(json, FillCalibration.Snapshot.class)));

		assertTrue("and must be active immediately, not after re-earning 200 observations",
			after.isActive());
		assertEquals("the correction must be identical, not merely similar",
			corrected, after.calibrate(true, 0.90), 1e-12);
	}

	@Test
	public void explorationPosteriorsSurviveToo()
	{
		FillCalibration before = trained();
		double drawnBefore = before.explore(4151, 0.90);

		FillCalibration after = new FillCalibration();
		after.restore(gson.fromJson(gson.toJson(before.snapshot()), FillCalibration.Snapshot.class));

		// The draw is random, so compare what the posterior believes rather than one sample: an item
		// observed completing 60% of the time must not come back looking untried.
		double total = 0;
		for (int i = 0; i < 500; i++)
		{
			total += after.explore(4151, 0.90);
		}
		assertTrue("a restored item must not revert to its prior, got " + total / 500,
			total / 500 < 0.85);
		assertNotEquals(0.0, drawnBefore, 0.0);
	}

	@Test
	public void aSnapshotFromAnotherVersionIsRefusedRatherThanMisapplied()
	{
		FillCalibration.Snapshot state = trained().snapshot();
		state.version = FillCalibration.SNAPSHOT_VERSION + 1;

		FillCalibration after = new FillCalibration();
		assertFalse("a version mismatch must be refused", after.restore(state));
		assertFalse("and must leave the calibration cold rather than half-loaded", after.isActive());
		assertEquals("so it passes probabilities through untouched",
			0.90, after.calibrate(true, 0.90), 1e-9);
	}

	@Test
	public void malformedStateIsDiscardedNotStretched()
	{
		FillCalibration.Snapshot state = trained().snapshot();
		state.buyCompletion = new double[]{ 1.0, 2.0, 3.0 };   // wrong shape for the bin count

		FillCalibration after = new FillCalibration();
		after.restore(state);

		assertEquals("a mangled curve is worse than a cold one, so the buy leg stays cold",
			0.90, after.calibrate(true, 0.90), 1e-9);
	}

	@Test
	public void nullAndEmptyAreSafe()
	{
		FillCalibration after = new FillCalibration();
		assertFalse(after.restore(null));
		assertFalse("an empty snapshot leaves nothing behind",
			after.restore(new FillCalibration().snapshot()));
		assertEquals(0.75, after.calibrate(true, 0.75), 1e-9);
	}
}
