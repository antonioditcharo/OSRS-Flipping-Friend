package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import com.flippingfriend.learning.FlipFeatures;
import com.flippingfriend.model.TaxCalculator;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

/**
 * Covers the gate that decides whether a trained model is allowed to influence a trade.
 *
 * <p>The audit's recurring complaint is models that act without ever having been scored against
 * anything. The answer here is that the analytical {@code FillModel} is the thing to beat, the
 * comparison happens on rows neither has seen, and the weight is the measured skill of one against
 * the other — so a model that learns nothing gets weight zero and the engine behaves exactly as it
 * did before. The failure mode of training on noise is <em>no change</em>, not a confident wrong
 * answer, and these tests exist to prove that specific claim.
 */
public class LearnedFillModelTest
{
	private static final int ITEM = 4151;
	private static final long T0 = 1_000_000L;

	private static Candle bar(long at, int low, int high)
	{
		return new Candle(at, high, low, 1000, 1000);
	}

	private static SeriesSource series(Candle... bars)
	{
		Map<Integer, List<Candle>> map = new HashMap<>();
		map.put(ITEM, Arrays.asList(bars));
		return (itemId, timestep) -> map.getOrDefault(itemId, Collections.emptyList());
	}

	private static SeriesSource completing()
	{
		return series(bar(T0 + 300, 990_000, 1_005_000), bar(T0 + 600, 1_020_000, 1_150_000));
	}

	private static SeriesSource neverFilling()
	{
		return series(bar(T0 + 300, 1_500_000, 1_600_000), bar(T0 + 600, 1_400_000, 1_500_000));
	}

	private static double[] vector(Random random, double signal)
	{
		double[] features = new double[FlipFeatures.SIZE];
		features[0] = 1.0;
		for (int i = 1; i < FlipFeatures.SIZE; i++)
		{
			features[i] = random.nextDouble();
		}
		// Feature 1 carries the truth; the rest is noise.
		features[1] = signal;
		return features;
	}

	/**
	 * Builds a resolved set where completion depends on feature 1, and the recorded analytical
	 * prediction is deliberately uninformative — so a model that finds the signal genuinely beats it.
	 */
	private static ShadowTrader learnableSet(int rows, double analytical)
	{
		ShadowTrader shadow = new ShadowTrader(new TaxCalculator());
		Random random = new Random(5);
		for (int i = 0; i < rows; i++)
		{
			boolean completes = i % 2 == 0;
			double signal = completes ? 0.8 + random.nextDouble() * 0.2
				: random.nextDouble() * 0.2;
			shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0,
				vector(random, signal), analytical);
			shadow.resolve(completes ? completing() : neverFilling(), "5m", T0 + 7200);
		}
		return shadow;
	}

	@Test
	public void withoutEnoughRowsNothingIsTrainedAndNothingChanges()
	{
		LearnedFillModel learned = new LearnedFillModel();

		assertFalse(learned.retrain(learnableSet(50, 0.5).trainingSet(true)));
		assertFalse(learned.isInfluencing());
		assertEquals("the analytical value passes through untouched",
			0.73, learned.adjust(0.73, new double[FlipFeatures.SIZE]), 1e-12);
		assertTrue(learned.summary(), learned.summary().contains("of " + LearnedFillModel.MIN_ROWS));
	}

	@Test
	public void aModelThatBeatsFillModelEarnsInfluence()
	{
		LearnedFillModel learned = new LearnedFillModel();

		// The analytical prediction is a flat 0.5 on every row: knowable signal, uninformative prior.
		assertTrue("a model that finds real signal must earn influence",
			learned.retrain(learnableSet(900, 0.5).trainingSet(true)));

		assertTrue(learned.isInfluencing());
		assertTrue("weight is the measured skill, capped", learned.weight() > 0 && learned.weight() <= 0.5);
		assertTrue(learned.summary(), learned.summary().contains("beating FillModel"));
	}

	/**
	 * The property that makes shipping this safe. Given labels that cannot be predicted, the gate
	 * must refuse the model outright rather than hand it a small weight.
	 */
	@Test
	public void aModelTrainedOnNoiseIsRefusedOutright()
	{
		ShadowTrader shadow = new ShadowTrader(new TaxCalculator());
		Random random = new Random(9);
		for (int i = 0; i < 900; i++)
		{
			boolean completes = random.nextBoolean();
			// Features are pure noise: nothing to learn, and the analytical prior is already right
			// about the base rate.
			shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0,
				vector(random, random.nextDouble()), 0.5);
			shadow.resolve(completes ? completing() : neverFilling(), "5m", T0 + 7200);
		}

		LearnedFillModel learned = new LearnedFillModel();
		assertFalse("noise must not earn influence", learned.retrain(shadow.trainingSet(true)));
		assertFalse(learned.isInfluencing());
		assertEquals(0.0, learned.weight(), 0.0);
		assertEquals("and the engine behaves exactly as it did before",
			0.61, learned.adjust(0.61, vector(random, 0.5)), 1e-12);
	}

	/** Blending happens in log-odds, so a correction near the ends is not disproportionate. */
	@Test
	public void theCorrectionIsBoundedByTheEarnedWeight()
	{
		LearnedFillModel learned = new LearnedFillModel();
		learned.retrain(learnableSet(900, 0.5).trainingSet(true));

		Random random = new Random(3);
		double analytical = 0.90;
		double adjusted = learned.adjust(analytical, vector(random, 0.05));

		assertTrue("a contrary model must be able to move the number", adjusted != analytical);
		assertTrue("but never past what its weight justifies, got " + adjusted,
			adjusted > 0.30 && adjusted < 0.95);
	}

	@Test
	public void rowsWithoutAnAnalyticalPredictionCannotBeScoredAgainst()
	{
		LearnedFillModel learned = new LearnedFillModel();

		// -1 means FillModel's view was never recorded, so there is nothing to beat.
		assertFalse(learned.retrain(learnableSet(900, -1).trainingSet(true)));
		assertTrue(learned.summary(), learned.summary().contains("nothing comparable"));
	}

	@Test
	public void importanceIsAvailableOnlyWhileAModelIsTrusted()
	{
		LearnedFillModel learned = new LearnedFillModel();
		org.junit.Assert.assertNull("nothing trained yet", learned.importance());

		learned.retrain(learnableSet(900, 0.5).trainingSet(true));

		double[] importance = learned.importance();
		assertTrue("a trusted model must be able to explain itself", importance != null);
		assertEquals(FlipFeatures.SIZE, importance.length);
	}
}
