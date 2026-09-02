package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The curve exists purely to make the scorer faster, so the property that matters is that it did
 * not change any answers. These tests check it against the obvious, slow, obviously-correct
 * calculation over randomised data — an optimisation that quietly shifts results would be far worse
 * than no optimisation.
 */
public class FillCurveTest
{
	private static final int BUCKET_SECONDS = 300;

	@Test
	public void matchesTheNaiveScanForBuys()
	{
		List<Candle> series = randomSeries(400, 7);

		FillCurve curve = FillCurve.from(series);

		for (int price = 900; price <= 1100; price += 7)
		{
			// The reference is a brute-force pass applying the same share rule, so this still does
			// the job it was written for -- proving the binary search and prefix sums agree with a
			// linear scan -- rather than pinning the all-or-nothing threshold they used to share.
			int expectedBuckets = 0;
			long expectedVolume = 0;
			for (Candle candle : series)
			{
				Integer low = candle.getAvgLowPrice();
				if (low != null && low > 0)
				{
					if (low <= price)
					{
						expectedBuckets++;
					}
					expectedVolume += (long) (candle.getLowPriceVolume()
						* shareAtOrBelow(price, low, curve.dispersion()));
				}
			}

			assertEquals("bucket count at " + price, expectedBuckets, curve.buyReachableBuckets(price));
			assertEquals("volume at " + price, expectedVolume, curve.buyVolumeAtOrBelow(price));
		}
	}

	@Test
	public void matchesTheNaiveScanForSells()
	{
		List<Candle> series = randomSeries(400, 11);

		FillCurve curve = FillCurve.from(series);

		for (int price = 900; price <= 1100; price += 7)
		{
			int expectedBuckets = 0;
			long expectedVolume = 0;
			for (Candle candle : series)
			{
				Integer high = candle.getAvgHighPrice();
				if (high != null && high > 0)
				{
					if (high >= price)
					{
						expectedBuckets++;
					}
					expectedVolume += (long) (candle.getHighPriceVolume()
						* shareAtOrAbove(price, high, curve.dispersion()));
				}
			}

			assertEquals("bucket count at " + price, expectedBuckets, curve.sellReachableBuckets(price));
			assertEquals("volume at " + price, expectedVolume, curve.sellVolumeAtOrAbove(price));
		}
	}

	@Test
	public void producesIdenticalEstimatesToTheSeriesOverload()
	{
		List<Candle> series = randomSeries(300, 3);
		FillModel model = new FillModel();
		FillCurve curve = FillCurve.from(series);

		for (int price = 950; price <= 1050; price += 5)
		{
			FillEstimate viaSeries = model.estimateBuy(series, price, 250, 1.0);
			FillEstimate viaCurve = model.estimateBuy(curve, price, 250, 1.0);
			assertEquals(viaSeries.getProbability(), viaCurve.getProbability(), 1e-12);
			assertEquals(viaSeries.getUnitsPerHour(), viaCurve.getUnitsPerHour(), 1e-9);

			FillEstimate sellSeries = model.estimateSell(series, price, 250, 1.0);
			FillEstimate sellCurve = model.estimateSell(curve, price, 250, 1.0);
			assertEquals(sellSeries.getProbability(), sellCurve.getProbability(), 1e-12);
			assertEquals(sellSeries.getUnitsPerHour(), sellCurve.getUnitsPerHour(), 1e-9);
		}
	}

	@Test
	public void copesWithGapsAndEmptyInput()
	{
		List<Candle> gappy = new ArrayList<>();
		for (int i = 0; i < 50; i++)
		{
			// Every other bucket traded on one side only.
			Integer high = i % 2 == 0 ? 1010 : null;
			Integer low = i % 3 == 0 ? 990 : null;
			gappy.add(new Candle(1_700_000_000L + (long) i * BUCKET_SECONDS, high, low,
				high == null ? 0 : 20, low == null ? 0 : 15));
		}

		FillCurve curve = FillCurve.from(gappy);
		assertTrue(curve.buyScoredBuckets() > 0);
		assertTrue(curve.sellScoredBuckets() > 0);
		assertEquals(0, curve.buyReachableBuckets(1));
		assertEquals(0, curve.sellReachableBuckets(999_999));

		assertTrue(FillCurve.from(null).isEmpty());
		assertTrue(FillCurve.from(new ArrayList<>()).isEmpty());
	}

	@Test
	public void isDramaticallyFasterOverAPriceGrid()
	{
		List<Candle> series = randomSeries(365, 42);
		FillModel model = new FillModel();

		// Warm up so this measures the algorithms rather than class loading.
		for (int i = 0; i < 200; i++)
		{
			model.estimateBuy(series, 1000, 100, 1.0);
			model.estimateBuy(FillCurve.from(series), 1000, 100, 1.0);
		}

		int passes = 2000;

		long naiveStart = System.nanoTime();
		for (int i = 0; i < passes; i++)
		{
			model.estimateBuy(series, 950 + (i % 100), 100, 1.0);
		}
		long naive = System.nanoTime() - naiveStart;

		FillCurve curve = FillCurve.from(series);
		long curveStart = System.nanoTime();
		for (int i = 0; i < passes; i++)
		{
			model.estimateBuy(curve, 950 + (i % 100), 100, 1.0);
		}
		long fast = System.nanoTime() - curveStart;

		// Deliberately loose: this is a guard against the optimisation silently regressing, not a
		// benchmark, and CI machines are noisy.
		assertTrue("expected the curve to be faster (naive " + naive / 1000 + "us, curve "
			+ fast / 1000 + "us)", fast * 3 < naive);
	}

	@Test
	public void aBucketAveragingOurPriceCountsForHalfOfItself()
	{
		// The point of the share rule. A bucket's price is the volume-weighted mean of what traded
		// there, so if that mean is exactly our price then half of what it carried traded above us
		// and is not ours to claim. The old rule claimed all of it.
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < 20; i++)
		{
			series.add(new Candle(1_700_000_000L + (long) i * BUCKET_SECONDS, 1020, 1000, 100, 100));
		}

		FillCurve curve = FillCurve.from(series);

		assertEquals("dispersion is half the bid-ask spread", 10, curve.dispersion());
		assertEquals("half of 20 buckets x 100 units", 1_000L, curve.buyVolumeAtOrBelow(1000));
		assertEquals("bidding the full dispersion above claims all of it",
			2_000L, curve.buyVolumeAtOrBelow(1010));
		assertEquals("bidding the full dispersion below claims none of it",
			0L, curve.buyVolumeAtOrBelow(990));
	}

	@Test
	public void sparseBucketsAboveOurPriceStillContribute()
	{
		// Where the old rule cost the most. These buckets averaged 4gp above our bid, so under an
		// all-or-nothing threshold they vanished entirely -- including the trades inside them that
		// did cross at our price. Sparse, expensive items are made almost entirely of this case.
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < 10; i++)
		{
			series.add(new Candle(1_700_000_000L + (long) i * BUCKET_SECONDS, 1020, 1004, 100, 100));
		}

		FillCurve curve = FillCurve.from(series);

		assertEquals(8, curve.dispersion());
		assertEquals("the old rule returned zero here", 250L, curve.buyVolumeAtOrBelow(1000));
	}

	private static double shareAtOrBelow(int price, int mean, int dispersion)
	{
		return dispersion <= 0 ? (mean <= price ? 1 : 0)
			: clamp((price - (mean - dispersion)) / (2.0 * dispersion));
	}

	private static double shareAtOrAbove(int price, int mean, int dispersion)
	{
		return dispersion <= 0 ? (mean >= price ? 1 : 0)
			: clamp(((mean + dispersion) - price) / (2.0 * dispersion));
	}

	private static double clamp(double share)
	{
		return share < 0 ? 0 : share > 1 ? 1 : share;
	}

	private static List<Candle> randomSeries(int size, long seed)
	{
		Random random = new Random(seed);
		List<Candle> series = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
		{
			boolean hasLow = random.nextInt(10) > 0;
			boolean hasHigh = random.nextInt(10) > 0;
			Integer low = hasLow ? 950 + random.nextInt(80) : null;
			Integer high = hasHigh ? 990 + random.nextInt(80) : null;
			series.add(new Candle(1_700_000_000L + (long) i * BUCKET_SECONDS, high, low,
				hasHigh ? random.nextInt(200) : 0, hasLow ? random.nextInt(200) : 0));
		}
		return series;
	}
}
