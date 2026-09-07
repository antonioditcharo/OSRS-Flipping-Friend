package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The figure the completion calibrator is scored against, at the point it is read.
 * <p>
 * It used to be derived from the predicted duration — a linear ramp of minutes against the horizon.
 * That is not a probability of anything: whatever it said, it said by construction, so the calibrator
 * was learning its own arithmetic instead of the model's error. The model produces a real probability
 * during planning, and that is what has to arrive here.
 */
public class PredictedCompletionTest
{
	private static OfferEvent settled(double predictedMinutes, double predictedCompletion)
	{
		OfferEvent.Builder builder = OfferEvent.builder("c", 1_700_000_000L, "BOUGHT")
			.slot(0).item(561, "Nature rune").buying(true).price(100).quantities(10, 10);
		builder.recommendation("plan-1", 100, 10, 0, predictedMinutes, predictedCompletion);
		return builder.build();
	}

	@Test
	public void theModelsOwnClaimIsWhatGetsScored()
	{
		// A short predicted duration against a long horizon would make the old ramp report near
		// certainty. The model actually claimed 71%, and 71% is what the outcome must be judged on.
		assertEquals(0.71, CompanionService.predictedCompletion(settled(5, 0.71)), 1e-9);
	}

	@Test
	public void aQuickOrderIsNotAutomaticallyAConfidentOne()
	{
		// The ramp's central mistake, stated as a test: duration and confidence are different things.
		// An order the model expects to fill in five minutes but only rates at 40% must be recorded
		// as 40%, not as the near-1.0 the ramp would have produced.
		assertEquals(0.40, CompanionService.predictedCompletion(settled(5, 0.40)), 1e-9);
	}

	@Test
	public void noClaimMeansNoCalibration()
	{
		// Zero is the signal the caller checks before observing an outcome at all. An offer the plan
		// never scored has nothing to be judged against, and inventing a figure for it is exactly what
		// this method's own comment has always warned against.
		assertEquals(0, CompanionService.predictedCompletion(settled(90, 0)), 1e-9);
	}
}
