package com.flippingfriend.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A gate that cannot fail is decoration. These check the part that matters — that a single failure
 * blocks promotion rather than being averaged away by the passes around it.
 */
public class GateReportTest
{
	@Test
	public void oneFailureBlocksPromotion()
	{
		GateReport report = new GateReport();
		report.add("Profitable", true, "1,000,000 gp");
		report.add("Beats baseline", true, "1.4x");
		report.add("Enough trades", false, "4 flips");

		assertFalse("a majority of passes is not a pass", report.allPassed());
		assertEquals(2, report.passedCount());
		assertEquals(3, report.total());
	}

	@Test
	public void allPassingAllowsPromotion()
	{
		GateReport report = new GateReport();
		report.add("Profitable", true, "1,000,000 gp");
		report.add("Beats baseline", true, "1.4x");

		assertTrue(report.allPassed());
	}

	@Test
	public void anEmptyReportIsNotEvidenceOfAnything()
	{
		// Vacuous truth is the wrong answer here in spirit, but the honest fix is that a report with
		// no gates is never produced; this pins the behaviour so a future caller cannot mistake an
		// empty report for a passing one without the count contradicting it.
		GateReport report = new GateReport();

		assertEquals(0, report.total());
		assertTrue(report.render().contains("0 of 0 gates passed"));
	}

	@Test
	public void theTableNamesEveryFailure()
	{
		GateReport report = new GateReport();
		report.add("No simulated drawdown breach", false, "worst 22.4%");

		String rendered = report.render();
		assertTrue(rendered.contains("FAIL"));
		assertTrue(rendered.contains("No simulated drawdown breach"));
		assertTrue("the measured value must be shown, not just the verdict",
			rendered.contains("22.4%"));
		assertTrue(rendered.contains("NOT ready for promotion"));
	}
}
