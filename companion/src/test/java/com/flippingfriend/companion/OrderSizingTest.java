package com.flippingfriend.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How large an order the planner is willing to place, and what decides it.
 * <p>
 * The size comes from a fractional Kelly stake, and the stake is acutely sensitive to the chance
 * both legs complete. That made it the place where a bad probability estimate did the most damage:
 * an ONNX fill classifier was wired in ahead of the measured fill model, reporting roughly 0.25 for
 * every item, and every order on the board was pinned to the Kelly floor — a tenth of what the
 * market could actually absorb.
 * <p>
 * The classifier that shipped is {@code fill_prob_v1.onnx}, and
 * {@code ml-forecaster/train_models.py} fits it to {@code np.random.rand(100, 4)} against
 * {@code np.random.randint(0, 2, 100)}. It is a placeholder written so the loading path could be
 * tested before there was a model; it was never something to size real orders with.
 */
public class OrderSizingTest
{
	/** Profit against the cost of unwinding, on an ordinary flip. */
	private static final double TYPICAL_ODDS = 1.5;

	private static CandidateFactory factory()
	{
		return new CandidateFactory(null);
	}


	@Test
	public void aConfidentFlipIsOrderedInSize()
	{
		double confident = CandidateFactory.kellyFraction(0.80, TYPICAL_ODDS);

		assertTrue("a trade the model likes has to be worth more than the floor: " + confident,
			confident > 0.2);
	}

	@Test
	public void theNoiseModelsAnswerPinsEveryOrderToTheFloor()
	{
		// 0.25 is what a classifier trained on random labels reports: about a coin flip on each leg.
		// This is the arithmetic behind "the trades it suggests are not worth enough" -- not a
		// judgement about any particular item, just the same wrong number applied to all of them.
		assertEquals(0.1, CandidateFactory.kellyFraction(0.25, TYPICAL_ODDS), 1e-9);
		assertEquals(0.1, CandidateFactory.kellyFraction(0.25, 0.5), 1e-9);
		assertEquals(0.1, CandidateFactory.kellyFraction(0.25, 4.0), 1e-9);

		assertTrue("the measured model's own answer for the same trade is several times larger",
			CandidateFactory.kellyFraction(0.80, TYPICAL_ODDS)
				> 2 * CandidateFactory.kellyFraction(0.25, TYPICAL_ODDS));
	}

	@Test
	public void theFractionStaysWithinItsBounds()
	{
		assertEquals("a hopeless trade still gets the floor rather than a negative order",
			0.1, CandidateFactory.kellyFraction(0.01, TYPICAL_ODDS), 1e-9);
		assertEquals("even a certainty only takes its share of the full stake, because the "
				+ "probability behind it is an estimate",
			0.35, CandidateFactory.kellyFraction(1.0, 1_000.0), 1e-9);
		assertEquals("no odds at all is not a reason to size up",
			0.1, CandidateFactory.kellyFraction(0.5, -1.0), 1e-9);
	}
}
