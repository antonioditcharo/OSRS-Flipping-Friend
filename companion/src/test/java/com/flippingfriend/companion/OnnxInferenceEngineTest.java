package com.flippingfriend.companion;

import static org.junit.Assert.assertNotEquals;

import com.flippingfriend.companion.ai.OnnxInferenceEngine;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Characterisation test for the ONNX inference path.
 *
 * <p>This does not assert correctness — nothing has established what correct output looks like for
 * these models. It records what the engine actually does today, because {@code CandidateFactory}
 * treats any non-zero output as authoritative and replaces the analytical {@code FillModel}
 * estimate with it:
 *
 * <pre>
 *   if (buyProbs[0] &gt; 0) { buyFill = new FillEstimate(buyProbs[0], ...); }
 * </pre>
 *
 * <p>A zero therefore means "fall back to the physics model" and a non-zero means "this number is
 * now sizing the position", via the fractional-Kelly term at CandidateFactory:650. The distinction
 * matters enough to be pinned by a test rather than assumed.
 */
public class OnnxInferenceEngineTest
{
	/** The shape CandidateFactory builds: { price, quantity, season, 0f }. */
	private static float[][] features(float price, float quantity, float season)
	{
		return new float[][]{ { price, quantity, season, 0f } };
	}

	@Test
	public void reportsWhatTheEngineActuallyReturns()
	{
		try (OnnxInferenceEngine engine = new OnnxInferenceEngine())
		{
			float[][] cases = {
				{ 150f, 1000f, 0f },      // cheap, high volume
				{ 150f, 1000f, 3f },      // same, different season bucket
				{ 2_000_000f, 5f, 0f },   // expensive, thin
				{ 30_000f, 200f, 1f },    // mid
			};

			System.out.println("=== fill probability / wait time ===");
			for (float[] c : cases)
			{
				float[][] f = features(c[0], c[1], c[2]);
				float[] prob = engine.predictFillProbabilities(f);
				float[] wait = engine.predictWaitTimes(f);
				System.out.printf(
					"price=%-10.0f qty=%-6.0f season=%.0f -> p=%.6f  wait=%.4f  usedByCandidateFactory=%s%n",
					c[0], c[1], c[2], prob[0], wait[0], prob[0] > 0 ? "YES (replaces FillModel)" : "no (falls back)");
			}

			// Exactly what CandidateFactory:349 passes: new float[n][12][4], allocated and never
			// populated, so every item is scored from an all-zeros tensor.
			System.out.println("=== momentum, production input (3 items, all zeros) ===");
			float[][][] asProduction = new float[3][12][4];
			float[][] mom = engine.predictMomentums(asProduction);
			for (int i = 0; i < mom.length; i++)
			{
				System.out.printf("item %d -> momentum=%.6f  usedByScorer=%s%n",
					i, mom[i][0],
					mom[i][0] != 0.0f ? "YES - collapses the 7-point sell grid" : "no");
			}

			// Does the model respond to its input at all, or is the output a constant?
			System.out.println("=== momentum, a populated sequence for contrast ===");
			float[][][] populated = new float[1][12][4];
			for (int t = 0; t < 12; t++)
			{
				populated[0][t][0] = 0.01f * t;
				populated[0][t][1] = 5000f;
				populated[0][t][2] = 0.002f;
				populated[0][t][3] = t / 12f;
			}
			System.out.printf("populated -> momentum=%.6f%n", engine.predictMomentums(populated)[0][0]);
		}
	}

	/**
	 * The invariant a fill-probability model has to satisfy to be worth consulting: a cheap,
	 * high-volume order and an expensive, thin one must not receive the same probability.
	 *
	 * <p>Ignored because it fails today. {@code fill_prob_v1.onnx} responds only to the
	 * {@code season} feature — a 150 gp order for 1,000 units and a 2,000,000 gp order for 5 units
	 * both return 0.329689. CandidateFactory:641 then substitutes that number for the analytical
	 * FillModel estimate and CandidateFactory:650 sizes the position from it via fractional Kelly.
	 *
	 * <p>Remove the {@code @Ignore} once the model takes liquidity as an input; it then guards the
	 * fix. Do not delete it to make the suite green — that is the failure mode the CI guard job was
	 * added to prevent.
	 */
	@Test
	@Ignore("Fails today: fill_prob_v1.onnx ignores price and quantity. See Build Order T2.6.")
	public void fillProbabilityRespondsToPriceAndQuantity()
	{
		try (OnnxInferenceEngine engine = new OnnxInferenceEngine())
		{
			float cheapLiquid = engine.predictFillProbabilities(features(150f, 1000f, 0f))[0];
			float dearThin = engine.predictFillProbabilities(features(2_000_000f, 5f, 0f))[0];

			assertNotEquals(
				"A 150gp/1000-unit order and a 2,000,000gp/5-unit order must not share a fill probability",
				cheapLiquid, dearThin, 1e-9);
		}
	}
}
