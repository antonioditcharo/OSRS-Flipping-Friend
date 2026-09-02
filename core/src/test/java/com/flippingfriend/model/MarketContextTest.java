package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The fortnight view exists to catch the things a single day of data cannot show: an item halfway
 * through a slow collapse, one sitting at the top of its range, one whose price level just jumped,
 * and the fact that the same item trades several times faster at peak than overnight.
 */
public class MarketContextTest
{
	private static final long HOUR = 3600L;
	/** A timestamp that lands exactly on midnight UTC, so hour indices are predictable. */
	private static final long MIDNIGHT = 1_700_000_000L - (1_700_000_000L % 86400L);

	private static List<Candle> hourly(int count, java.util.function.IntUnaryOperator price,
		java.util.function.IntUnaryOperator volume)
	{
		List<Candle> series = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			int p = price.applyAsInt(i);
			series.add(new Candle(MIDNIGHT + i * HOUR, p + 5, p - 5, volume.applyAsInt(i) / 2,
				volume.applyAsInt(i) / 2));
		}
		return series;
	}

	@Test
	public void learnsWhichHoursAnItemActuallyTradesIn()
	{
		// Ten times busier between 18:00 and 22:00 UTC than the rest of the day.
		List<Candle> series = hourly(24 * 14, i -> 1000, i ->
		{
			int hour = (int) ((MIDNIGHT + i * HOUR) / HOUR % 24);
			return hour >= 18 && hour < 22 ? 1000 : 100;
		});

		MarketContext context = MarketContext.from(series, 1000);

		assertTrue(context.isUsable());
		assertTrue("peak hours should be marked busier than average",
			context.liquidityMultiplier(20) > 1.2);
		assertTrue("quiet hours should be marked slower than average",
			context.liquidityMultiplier(4) < 0.9);
		assertTrue("peak must exceed quiet", context.liquidityMultiplier(20) > context.liquidityMultiplier(4));
	}

	@Test
	public void multipliersAreClampedSoOneOddDayCannotDominate()
	{
		List<Candle> series = hourly(24 * 14, i -> 1000, i ->
		{
			int hour = (int) ((MIDNIGHT + i * HOUR) / HOUR % 24);
			return hour == 12 ? 100_000 : 1;
		});

		MarketContext context = MarketContext.from(series, 1000);

		for (int hour = 0; hour < 24; hour++)
		{
			double multiplier = context.liquidityMultiplier(hour);
			assertTrue("multiplier out of bounds at " + hour, multiplier >= 0.4 && multiplier <= 2.0);
		}
	}

	@Test
	public void knowsWhereTheCurrentPriceSitsInItsRange()
	{
		// A price that ranges from 1000 up to about 1340 over the fortnight.
		List<Candle> series = hourly(24 * 14, i -> 1000 + i, i -> 100);

		assertTrue("near the top of the range", MarketContext.from(series, 1330).getPricePercentile() > 0.9);
		assertTrue("near the bottom", MarketContext.from(series, 1010).getPricePercentile() < 0.1);
		assertEquals("mid range", 0.5, MarketContext.from(series, 1168).getPricePercentile(), 0.1);
	}

	@Test
	public void spotsAPriceLevelShift()
	{
		// Flat at 1000 for twelve days, then jumps to 2000 — a game update, in effect.
		List<Candle> broken = hourly(24 * 14, i -> i < 24 * 13 ? 1000 : 2000, i -> 100);
		assertTrue(MarketContext.from(broken, 2000).hasStructuralBreak());

		List<Candle> steady = hourly(24 * 14, i -> 1000 + (i % 7), i -> 100);
		assertFalse(MarketContext.from(steady, 1003).hasStructuralBreak());
	}

	@Test
	public void reportsWhenAnItemIsJumpierThanUsual()
	{
		List<Candle> calm = hourly(24 * 14, i -> 1000 + (i % 3), i -> 100);
		MarketContext context = MarketContext.from(calm, 1001);

		double longVol = context.getLongVolatility();
		assertTrue("a much larger short-term volatility should read as a high ratio",
			context.volatilityRatio(longVol * 5) > 4);
		assertEquals("matching volatility should read as neutral", 1.0,
			context.volatilityRatio(longVol), 0.01);
	}

	@Test
	public void staysNeutralWithoutEnoughHistory()
	{
		MarketContext context = MarketContext.from(hourly(10, i -> 1000, i -> 100), 1000);

		assertFalse(context.isUsable());
		assertEquals(1.0, context.liquidityMultiplier(12), 1e-9);
		assertEquals(0.5, context.getPricePercentile(), 1e-9);
		assertFalse(context.hasStructuralBreak());

		assertFalse(MarketContext.from(null, 1000).isUsable());
		assertEquals(1.0, MarketContext.unknown().liquidityMultiplierNow(), 1e-9);
	}

	@Test
	public void seasonalityChangesTheFillEstimate()
	{
		// The point of the whole exercise: a quiet hour must produce a slower estimate.
		FillModel model = new FillModel();
		List<Candle> fiveMinute = new ArrayList<>();
		for (int i = 0; i < 200; i++)
		{
			fiveMinute.add(new Candle(MIDNIGHT + i * 300L, 1010, 990, 100, 100));
		}
		FillCurve curve = FillCurve.from(fiveMinute);

		FillEstimate peak = model.estimateBuy(curve, 1000, 500, 1.0, 1.8);
		FillEstimate quiet = model.estimateBuy(curve, 1000, 500, 1.0, 0.5);

		assertTrue("peak hours should fill faster", peak.getExpectedHours() < quiet.getExpectedHours());
		assertTrue("and more certainly", peak.getProbability() > quiet.getProbability());
	}

	@Test
	public void hourIndexIsBoundsChecked()
	{
		MarketContext context = MarketContext.from(hourly(24 * 14, i -> 1000, i -> 100), 1000);
		assertEquals(1.0, context.liquidityMultiplier(-1), 1e-9);
		assertEquals(1.0, context.liquidityMultiplier(24), 1e-9);
	}
}
