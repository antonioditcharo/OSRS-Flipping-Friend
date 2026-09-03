package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import org.junit.Test;

/**
 * Covers the promotion gate: a fitted curve is not the same as a useful one.
 *
 * <p>Until 2 September 2026 every learned component in this project acted the moment it had data,
 * with nothing checking that it helped — which is the failure the whole audit describes. The gate is
 * the answer: one observation in five is withheld from the curve and used only to score it, and the
 * correction is applied only where it beats the uncorrected claim on that held-out evidence.
 *
 * <p>Scoring on the data a curve was fitted to would always flatter it — that is what fitting means.
 * These tests exist to prove the held-out channel is genuinely held out.
 */
public class CalibrationGateTest
{
	private final Gson gson = new Gson();

	private static OfferEvent settled(boolean buying, boolean complete, double claimed)
	{
		return OfferEvent.builder("t", 1_000L, buying ? "BOUGHT" : "SOLD")
			.item(4151, "Abyssal whip")
			.buying(buying)
			.quantities(100, complete ? 100 : 50)
			.recommendation("plan-1", 1_000_000, 100, 0, 30.0, claimed)
			.build();
	}

	/** Feeds n observations claiming {@code claimed} that occur at {@code actualRate}. */
	private static void feed(FillCalibration calibration, boolean buying, int n, double claimed,
		double actualRate)
	{
		int occurring = (int) Math.round(n * actualRate);
		for (int i = 0; i < n; i++)
		{
			calibration.observeSettled(settled(buying, i < occurring, claimed), claimed);
		}
	}

	@Test
	public void anUngatedCalibrationDoesNotCorrect()
	{
		FillCalibration calibration = new FillCalibration();
		// 240 observations: 192 fit the curve (past MIN_OBSERVATIONS), 48 are held out - one short of
		// the 50 the gate requires before it will judge.
		feed(calibration, true, 240, 0.90, 0.60);

		assertFalse("fitted is not the same as proven", calibration.earningItsPlace(true));
		assertEquals("so the raw claim is what ships",
			0.90, calibration.calibrate(true, 0.90), 1e-9);
	}

	/**
	 * A model that is badly wrong in a consistent direction is exactly what isotonic regression
	 * fixes, so given enough evidence the gate should open and the correction should apply.
	 */
	@Test
	public void aCorrectionThatBeatsTheRawClaimIsPromoted()
	{
		FillCalibration calibration = new FillCalibration();
		// Spread across two adjacent bins, as real advice is: a model does not emit one probability
		// forever. With both neighbours populated the interpolation has evidence on each side.
		feed(calibration, true, 1_000, 0.87, 0.30);
		feed(calibration, true, 1_000, 0.92, 0.30);

		assertTrue("a claim of ~90% that happens 30% of the time must be correctable",
			calibration.earningItsPlace(true));
		double corrected = calibration.calibrate(true, 0.90);
		assertTrue("the correction must apply, got " + corrected, corrected < 0.90);
		assertTrue("landing near what actually happened, got " + corrected,
			Math.abs(corrected - 0.30) < 0.15);
	}

	/**
	 * Evidence in a single bin is corrected only halfway, because {@code calibrate} interpolates
	 * between bin centres and the unpopulated neighbour keeps the identity mapping. A claim of 0.90
	 * observed at 0.30 returns 0.5875: the mean of the observed 0.30 and the pass-through 0.875.
	 *
	 * <p>That is the recovered algorithm behaving correctly and conservatively — one populated bin is
	 * thin grounds for rewriting a whole interval — and it is worth pinning, because it looks like an
	 * under-correction until you know why. It also means a model that emits one constant probability
	 * can never be fully calibrated, which is a further reason the ONNX fill model's two-valued output
	 * was unusable.
	 */
	@Test
	public void evidenceInASingleBinIsOnlyHalfTrusted()
	{
		FillCalibration calibration = new FillCalibration();
		feed(calibration, true, 2_000, 0.90, 0.30);

		assertTrue(calibration.earningItsPlace(true));
		assertEquals("halfway between the observed rate and the identity mapping",
			0.5875, calibration.calibrate(true, 0.90), 1e-4);
	}

	/** One leg's evidence must not open the other leg's gate. */
	@Test
	public void gatesArePerLeg()
	{
		FillCalibration calibration = new FillCalibration();
		feed(calibration, true, 2_000, 0.90, 0.30);

		assertTrue(calibration.earningItsPlace(true));
		assertFalse("the sell leg has seen nothing", calibration.earningItsPlace(false));
		assertEquals(0.90, calibration.calibrate(false, 0.90), 1e-9);
	}

	@Test
	public void theGateReportNamesEachCheckAndItsMeasurement()
	{
		FillCalibration calibration = new FillCalibration();
		feed(calibration, true, 2_000, 0.90, 0.30);

		GateReport report = calibration.gate();
		assertEquals("three checks per leg, both legs", 6, report.total());
		assertTrue("the buy leg must pass all three", report.passedCount() >= 3);
		assertFalse("and the untouched sell leg must stop it being a blanket pass",
			report.allPassed());

		String rendered = report.render();
		assertTrue(rendered, rendered.contains("buy leg beats the uncorrected claim"));
		assertTrue("the measurement must be reported, not just the verdict",
			rendered.contains("Brier"));
	}

	/** The gate's evidence must survive a restart, or it re-earns it from scratch every time. */
	@Test
	public void heldOutEvidenceSurvivesRestart()
	{
		FillCalibration before = new FillCalibration();
		feed(before, true, 2_000, 0.90, 0.30);
		assertTrue(before.earningItsPlace(true));
		double corrected = before.calibrate(true, 0.90);

		FillCalibration after = new FillCalibration();
		assertTrue(after.restore(
			gson.fromJson(gson.toJson(before.snapshot()), FillCalibration.Snapshot.class)));

		assertTrue("the gate must reopen immediately, not after 50 more held-out offers",
			after.earningItsPlace(true));
		assertEquals(corrected, after.calibrate(true, 0.90), 1e-12);
	}

	@Test
	public void healthSaysWhetherTheCorrectionIsActuallyApplied()
	{
		FillCalibration cold = new FillCalibration();
		assertTrue(cold.summary(), cold.summary().contains("learning"));

		FillCalibration fitting = new FillCalibration();
		feed(fitting, true, 260, 0.90, 0.60);
		assertTrue(fitting.summary(), fitting.summary().contains("not applied"));

		FillCalibration promoted = new FillCalibration();
		feed(promoted, true, 2_000, 0.90, 0.30);
		assertTrue(promoted.summary(), promoted.summary().contains("applied, skill"));
	}
}
