package com.flippingfriend.companion;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import com.flippingfriend.learning.FlipFeatures;
import com.flippingfriend.model.TaxCalculator;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Covers the labelled dataset L3 needs and did not have.
 *
 * <p>Before this, {@code FlipFeatures} was recovered and never computed, and no {@code (features,
 * outcome)} pair was stored anywhere — so the GBDT the plan calls for had nothing to train on. The
 * shadow channel already resolved hundreds of paper trades an hour; it just threw away the one thing
 * that made them learnable.
 *
 * <p>The label is completion, not price direction. Completion is what the fill model predicts and
 * what the optimiser weights its entire objective by; price direction is what the existing LSTM
 * predicts and what nothing downstream consumes.
 */
public class TrainingSetTest
{
	private static final int ITEM = 4151;
	private static final long T0 = 1_000_000L;

	private final ShadowTrader shadow = new ShadowTrader(new TaxCalculator());

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

	/** Bars that reach the bid then the ask: the trade completes. */
	private static SeriesSource completing()
	{
		return series(bar(T0 + 300, 990_000, 1_005_000), bar(T0 + 600, 1_020_000, 1_150_000));
	}

	/** Bars that never reach the bid: the trade never happens. */
	private static SeriesSource neverFilling()
	{
		return series(bar(T0 + 300, 1_500_000, 1_600_000), bar(T0 + 600, 1_400_000, 1_500_000));
	}

	private static double[] featureVector(double margin)
	{
		return FlipFeatures.of(margin, 1_000_000, 50, 70,
			com.flippingfriend.model.ItemFeatures.unknown(ITEM),
			com.flippingfriend.model.MarketContext.unknown(), 12).values();
	}

	@Test
	public void aResolvedPositionBecomesALabelledRow()
	{
		double[] features = featureVector(0.10);
		shadow.open(ITEM, "Abyssal whip", null, 1_000_000, 1_100_000, 1, T0, 1.0, features);
		shadow.resolve(completing(), "5m", T0 + 7200);

		ShadowTrader.TrainingSet set = shadow.trainingSet(true);

		assertEquals(1, set.size());
		assertEquals("the full sixteen-element vector, not a summary",
			FlipFeatures.SIZE, set.features()[0].length);
		assertArrayEquals(features, set.features()[0], 1e-12);
		assertEquals("a completed round trip is a positive label", 1, set.labels()[0]);
	}

	@Test
	public void anUnfilledTradeIsANegativeLabel()
	{
		shadow.open(ITEM, "Abyssal whip", null, 1_000_000, 1_100_000, 1, T0, 1.0, featureVector(0.10));
		shadow.resolve(neverFilling(), "5m", T0 + 7200);

		ShadowTrader.TrainingSet set = shadow.trainingSet(true);
		assertEquals(1, set.size());
		assertEquals(0, set.labels()[0]);
		assertEquals(0.0, set.positiveRate(), 1e-9);
	}

	/**
	 * The point of the whole channel. A model trained only on trades the engine took learns the
	 * engine's existing opinion back; the rejected ones are what let it learn an opinion was wrong.
	 */
	@Test
	public void rejectedCandidatesAreIncludedAndSeparable()
	{
		shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0, featureVector(0.10));
		shadow.open(ITEM, "Whip", "too illiquid", 1_000_000, 1_100_000, 1, T0, 1.0, featureVector(0.20));
		shadow.resolve(completing(), "5m", T0 + 7200);

		assertEquals("both are labelled rows", 2, shadow.trainingSet(true).size());
		assertEquals("and the accepted ones can be isolated for comparison",
			1, shadow.trainingSet(false).size());
	}

	/**
	 * An item turned away before there was enough history to build features has no vector, and a row
	 * of zeros would be a lie the model would happily fit.
	 */
	@Test
	public void positionsWithoutFeaturesAreNotTrainingRows()
	{
		shadow.open(ITEM, "Whip", "no history", 1_000_000, 1_100_000, 1, T0, 1.0);
		shadow.resolve(completing(), "5m", T0 + 7200);

		assertEquals("resolved for the veto table", 1, shadow.resolvedCount());
		assertEquals("but not trainable", 0, shadow.trainingSet(true).size());
	}

	@Test
	public void unresolvedPositionsAreNotTrainingRowsEither()
	{
		shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0, featureVector(0.10));

		assertEquals("the outcome is not known yet, so there is no label",
			0, shadow.trainingSet(true).size());
	}

	@Test
	public void positiveRateReportsClassBalance()
	{
		shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0, featureVector(0.10));
		shadow.resolve(completing(), "5m", T0 + 7200);
		shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0, featureVector(0.20));
		shadow.resolve(neverFilling(), "5m", T0 + 7200);

		ShadowTrader.TrainingSet set = shadow.trainingSet(true);
		assertEquals(2, set.size());
		assertEquals("a set that is nearly all one class cannot train anything, so say so",
			0.5, set.positiveRate(), 1e-9);
	}

	@Test
	public void theMatrixIsTheShapeTheTrainerExpects()
	{
		for (int i = 0; i < 5; i++)
		{
			shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0, featureVector(0.05 * i));
		}
		shadow.resolve(completing(), "5m", T0 + 7200);

		ShadowTrader.TrainingSet set = shadow.trainingSet(true);
		assertEquals(5, set.features().length);
		assertEquals(5, set.labels().length);
		for (double[] row : set.features())
		{
			assertTrue("every row must be the full width", row.length == FlipFeatures.SIZE);
		}
	}
}
