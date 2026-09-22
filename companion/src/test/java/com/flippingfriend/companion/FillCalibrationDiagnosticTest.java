package com.flippingfriend.companion;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FillCalibrationDiagnosticTest
{
	@Test
	public void diagnosticCalibrationUsesExplicitBoundsWithoutChangingNeutral()
	{
		Map<Integer, Double> items = new HashMap<>();
		items.put(1, 0.12);
		items.put(2, 3.0);

		FillCalibration diagnostic = FillCalibration.forDiagnostics(
				items, 0.035688, 0.0, Double.POSITIVE_INFINITY);

		assertEquals(0.035688, diagnostic.overall(), 1e-12);
		assertEquals(0.12, diagnostic.waitMultiplier(1), 1e-12);
		assertEquals(3.0, diagnostic.waitMultiplier(2), 1e-12);
		assertEquals(0.035688, diagnostic.waitMultiplier(3), 1e-12);

		assertEquals(1.0, FillCalibration.NEUTRAL.overall(), 1e-12);
		assertEquals(1.0, FillCalibration.NEUTRAL.waitMultiplier(1), 1e-12);
	}

	@Test
	public void diagnosticCalibrationClampsToItsOwnBoundsAndCopiesItems()
	{
		Map<Integer, Double> items = new HashMap<>();
		items.put(1, 0.10);
		items.put(2, 4.0);

		FillCalibration diagnostic =
				FillCalibration.forDiagnostics(items, 0.30, 0.20, 2.0);

		items.put(1, 1.50);

		assertEquals(0.20, diagnostic.waitMultiplier(1), 1e-12);
		assertEquals(2.0, diagnostic.waitMultiplier(2), 1e-12);
		assertEquals(0.30, diagnostic.waitMultiplier(3), 1e-12);
		assertEquals(0.30, diagnostic.overall(), 1e-12);
	}

	@Test
	public void diagnosticCalibrationRejectsInvalidInputs()
	{
		assertInvalid(Double.NaN, 0.0, 2.0);
		assertInvalid(1.0, -0.1, 2.0);
		assertInvalid(1.0, 2.0, 1.0);

		Map<Integer, Double> invalidItems = new HashMap<>();
		invalidItems.put(1, Double.NaN);

		try
		{
			FillCalibration.forDiagnostics(invalidItems, 1.0, 0.0, 2.0);
			fail("non-finite item multiplier must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			assertEquals(
					"diagnostic calibration entries must be finite",
					expected.getMessage());
		}
	}

	private static void assertInvalid(double global, double minimum, double maximum)
	{
		try
		{
			FillCalibration.forDiagnostics(
					new HashMap<>(), global, minimum, maximum);
			fail("invalid diagnostic calibration must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			assertEquals(
					"invalid diagnostic calibration bounds",
					expected.getMessage());
		}
	}
}
