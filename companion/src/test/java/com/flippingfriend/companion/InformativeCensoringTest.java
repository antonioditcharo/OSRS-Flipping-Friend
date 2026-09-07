package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Measuring the one bias in the fill hazard that cannot be seen from inside it.
 *
 * <p>A hazard assumes censoring is independent of the event. A player cancelling the offers that are
 * not filling breaks that assumption, and the estimate then leans optimistic about long waits by an
 * amount nobody could name. Both {@link FillHazard} and {@link CaptureRates} shipped carrying that as
 * a written caveat, which is a sentence nobody can act on.
 *
 * <p>The observable form of it is narrower and honest: <b>how many cancellations followed this
 * system's own advice to cancel?</b> Those are informative censoring of the most direct kind, because
 * the model's own opinion is what stopped the observation. What is recorded is that circumstance and
 * not a motive — the game supplies no reason with a cancellation, and a bias estimate built on a
 * guess about the player's state of mind would be worse than no estimate.
 */
public class InformativeCensoringTest
{
	private static final int ITEM = 561;
	private static final int SLOT = 3;
	private static final long T0 = 1_700_000_000L;

	@Test
	public void adviceExplainsACancellationThatFollowsIt()
	{
		CancelAdvice advice = new CancelAdvice();
		advice.advised(ITEM, SLOT, T0);

		assertTrue("eleven minutes later is plainly the same episode",
			advice.wasAdvised(ITEM, SLOT, T0 + 11 * 60));
	}

	@Test
	public void adviceDoesNotExplainACancellationHoursLater()
	{
		// An alert from two sessions ago did not cause this. Crediting it would inflate the measured
		// bias, which is the one number that decides whether the bias is worth correcting.
		CancelAdvice advice = new CancelAdvice();
		advice.advised(ITEM, SLOT, T0);

		assertFalse(advice.wasAdvised(ITEM, SLOT, T0 + 4 * 3600));
	}

	@Test
	public void adviceDoesNotExplainACancellationThatCameFirst()
	{
		CancelAdvice advice = new CancelAdvice();
		advice.advised(ITEM, SLOT, T0 + 600);

		assertFalse("a cancel before the alert is not a response to it",
			advice.wasAdvised(ITEM, SLOT, T0));
	}

	@Test
	public void oneAlertExplainsOneCancellation()
	{
		// The record is consumed as it answers. The next offer on the same slot is a different offer,
		// and an alert that explained the last one has nothing to say about it.
		CancelAdvice advice = new CancelAdvice();
		advice.advised(ITEM, SLOT, T0);

		assertTrue(advice.wasAdvised(ITEM, SLOT, T0 + 60));
		assertFalse("and not the one after it", advice.wasAdvised(ITEM, SLOT, T0 + 120));
	}

	@Test
	public void slotsAreKeptApart()
	{
		CancelAdvice advice = new CancelAdvice();
		advice.advised(ITEM, 1, T0);

		assertFalse("the same item in another slot is another offer",
			advice.wasAdvised(ITEM, 2, T0 + 60));
		assertTrue(advice.wasAdvised(ITEM, 1, T0 + 60));
	}

	@Test
	public void staleAdviceIsForgotten()
	{
		CancelAdvice advice = new CancelAdvice();
		for (int slot = 0; slot < 8; slot++)
		{
			advice.advised(ITEM, slot, T0);
		}

		advice.prune(T0 + 4 * 3600);

		assertEquals("nothing worth keeping from four hours ago", 0, advice.tracked());
	}

	// --- what the hazard does with it ---

	@Test
	public void aSampleWithNoAdvisedCancelsReportsNoInformativeCensoring()
	{
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 50; i++)
		{
			hazard.observe(ITEM, true, 30, false, false);
		}

		assertEquals("fifty cancellations, none of them ours", 0.0,
			hazard.informativeCensoringShare(), 1e-9);
		assertEquals(50, hazard.censoredCount());
	}

	@Test
	public void theShareIsTheFractionOfCancelsWeAskedFor()
	{
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 40; i++)
		{
			hazard.observe(ITEM, true, 30, false, i < 10);
		}
		// Completions are not censoring at all and must not dilute the denominator.
		for (int i = 0; i < 60; i++)
		{
			hazard.observe(ITEM, true, 5, true, false);
		}

		assertEquals("ten of forty cancellations followed our own alert", 0.25,
			hazard.informativeCensoringShare(), 1e-9);
		assertEquals("and the hundred offers are all in the sample", 100,
			hazard.observationCount());
	}

	@Test
	public void aCompletionIsNeverCountedAsCensoring()
	{
		// A filled offer is the event, not the absence of it. Counting one here would understate the
		// share and make the bias look smaller than it is, which is the wrong direction to err.
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 30; i++)
		{
			hazard.observe(ITEM, true, 5, true, true);
		}

		assertEquals(0, hazard.censoredCount());
		assertEquals(0.0, hazard.informativeCensoringShare(), 1e-9);
	}

	@Test
	public void theHealthLineSaysHowBigTheProblemIs()
	{
		// The point of the whole exercise: turning "the censoring is informative" from a sentence in
		// a javadoc into a number on a line somebody reads.
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 20; i++)
		{
			hazard.observe(ITEM, true, 30, false, i < 5);
		}

		String summary = hazard.summary();

		assertTrue(summary, summary.contains("25% of 20 cancels were on our own advice"));
	}
}
