package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks the statistics behave the way the accept/reject logic assumes — in particular that a
 * single absurd trade cannot drag the centre of the distribution with it, since resisting exactly
 * that is why median and MAD were chosen over mean and standard deviation.
 */
public class FeatureEngineTest
{
	private static final int BUCKET_SECONDS = 300;

	private FeatureEngine engine;

	@Before
	public void setUp()
	{
		engine = new FeatureEngine();
	}

	@Test
	public void reportsUnusableWithoutEnoughHistory()
	{
		ItemFeatures features = engine.compute(1, flat(1000, 5), BUCKET_SECONDS);
		assertFalse(features.isUsable());

		assertFalse(engine.compute(1, null, BUCKET_SECONDS).isUsable());
		assertFalse(engine.compute(1, new ArrayList<>(), BUCKET_SECONDS).isUsable());
	}

	@Test
	public void medianIsUnmovedByASingleAbsurdTrade()
	{
		List<Candle> series = flat(1000, 40);
		// One trade at fifty times the going rate, exactly the shape of a manipulation attempt.
		series.set(20, candle(20, 50_000, 50_000, 1, 1));

		ItemFeatures features = engine.compute(1, series, BUCKET_SECONDS);

		assertEquals("median should stay at the real price", 1000.0, features.getMedianMid(), 1.0);
		assertTrue("the outlier should be many MADs away", features.madDistance(50_000) > 10);
	}

	@Test
	public void detectsARisingTrend()
	{
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < 60; i++)
		{
			int price = 1000 + i * 20;
			series.add(candle(i, price + 5, price - 5, 100, 100));
		}

		ItemFeatures features = engine.compute(1, series, BUCKET_SECONDS);

		assertEquals(Regime.RISING, features.getRegime());
		assertTrue(features.getTrendSlopePerHour() > 0);
	}

	@Test
	public void detectsAFallingTrend()
	{
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < 60; i++)
		{
			int price = 3000 - i * 20;
			series.add(candle(i, price + 5, price - 5, 100, 100));
		}

		assertEquals(Regime.FALLING, engine.compute(1, series, BUCKET_SECONDS).getRegime());
	}

	@Test
	public void treatsAFlatPriceAsStable()
	{
		ItemFeatures features = engine.compute(1, flat(1000, 60), BUCKET_SECONDS);

		assertEquals(Regime.STABLE, features.getRegime());
		assertTrue("a flat price has almost no volatility", features.getVolatility() < 0.005);
	}

	@Test
	public void convertsBucketVolumeIntoAnHourlyRate()
	{
		// 50 + 50 traded every five minutes is 1200 an hour.
		ItemFeatures features = engine.compute(1, flat(1000, 60), BUCKET_SECONDS);
		assertEquals(1200.0, features.getHourlyVolume(), 1.0);
	}

	@Test
	public void countsBucketsWhereNothingTraded()
	{
		List<Candle> series = flat(1000, 40);
		for (int i = 0; i < 10; i++)
		{
			series.set(i, candle(i, null, null, 0, 0));
		}

		ItemFeatures features = engine.compute(1, series, BUCKET_SECONDS);
		assertEquals(0.25, features.getEmptyBucketFraction(), 0.01);
	}

	@Test
	public void medianAndMadMatchKnownValues()
	{
		assertEquals(3.0, FeatureEngine.median(Arrays.asList(1.0, 2.0, 3.0, 4.0, 100.0)), 1e-9);
		assertEquals(2.5, FeatureEngine.median(Arrays.asList(1.0, 2.0, 3.0, 4.0)), 1e-9);
		// Deviations from the median of 3 are 2,1,0,1,97; their median is 1, scaled by 1.4826.
		assertEquals(1.4826,
			FeatureEngine.medianAbsoluteDeviation(Arrays.asList(1.0, 2.0, 3.0, 4.0, 100.0), 3.0), 1e-4);
	}

	@Test
	public void linearSlopeMatchesAKnownGradient()
	{
		assertEquals(2.0, FeatureEngine.linearSlope(Arrays.asList(0.0, 2.0, 4.0, 6.0, 8.0)), 1e-9);
		assertEquals(0.0, FeatureEngine.linearSlope(Arrays.asList(5.0, 5.0, 5.0, 5.0)), 1e-9);
	}

	private static List<Candle> flat(int price, int count)
	{
		List<Candle> series = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			series.add(candle(i, price + 5, price - 5, 50, 50));
		}
		return series;
	}

	private static Candle candle(int index, Integer high, Integer low, int highVolume, int lowVolume)
	{
		return new Candle(1_700_000_000L + (long) index * BUCKET_SECONDS, high, low, highVolume, lowVolume);
	}
}
