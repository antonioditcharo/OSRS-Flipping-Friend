package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The forecast's own behaviour, before anything is allowed to act on it.
 * <p>
 * A forecast is believed once it exists, so the properties that make it safe to believe are the ones
 * worth pinning: that it reverts toward the item's own level rather than assuming today's price is
 * tomorrow's, that its uncertainty grows with the horizon and then settles, and that it refuses to
 * answer when it has nothing to answer with.
 */
public class PriceForecastTest
{
	private static final int BUCKET_SECONDS = 300;

	/** A series that oscillates around a level, which is how a consumable actually behaves. */
	private static List<Candle> reverting(double level, double amplitude, int count, long seed)
	{
		Random random = new Random(seed);
		List<Candle> series = new ArrayList<>();
		double deviation = 0;
		for (int i = 0; i < count; i++)
		{
			deviation = 0.5 * deviation + random.nextGaussian() * amplitude;
			int low = (int) Math.max(1, Math.round(level + deviation));
			int high = (int) Math.max(1, Math.round(level + deviation + level * 0.02));
			series.add(new Candle(1_700_000_000L + i * BUCKET_SECONDS, high, low, 500, 500));
		}
		return series;
	}

	@Test
	public void aPriceAboveItsOwnLevelIsExpectedToComeBack()
	{
		List<Candle> series = reverting(1000, 20, 200, 1);
		// One last candle far above the level: the forecast must not simply repeat it.
		series.add(new Candle(1_700_000_000L + 200 * BUCKET_SECONDS, 1300, 1300, 500, 500));

		PriceForecast forecast = PriceForecast.fit(series, BUCKET_SECONDS);
		double soon = forecast.centre(PriceForecast.Side.LOW, 0.1);
		double later = forecast.centre(PriceForecast.Side.LOW, 4);

		assertTrue("a spike is not the new normal", later < soon);
		assertTrue("and it comes back toward the level, not below it: " + later,
			later > 950 && later < 1100);
	}

	@Test
	public void uncertaintyGrowsWithTheHorizonAndThenSettles()
	{
		PriceForecast forecast = PriceForecast.fit(reverting(1000, 20, 300, 2), BUCKET_SECONDS);

		double atTenMinutes = forecast.relativeSpread(PriceForecast.Side.HIGH, 1.0 / 6);
		double atOneHour = forecast.relativeSpread(PriceForecast.Side.HIGH, 1);
		double atEightHours = forecast.relativeSpread(PriceForecast.Side.HIGH, 8);

		assertTrue("further out is less certain", atOneHour > atTenMinutes);
		// The point of modelling reversion rather than a random walk: the band stops widening, because
		// the item is pulled back as often as it drifts. A random walk would keep spreading for ever.
		assertTrue("but a reverting item does not become infinitely uncertain: "
			+ atOneHour + " -> " + atEightHours, atEightHours < atOneHour * 1.6);
	}

	@Test
	public void quantilesAreOrdered()
	{
		PriceForecast forecast = PriceForecast.fit(reverting(1000, 20, 200, 3), BUCKET_SECONDS);

		double low = forecast.quantile(PriceForecast.Side.HIGH, 1, 0.25);
		double mid = forecast.quantile(PriceForecast.Side.HIGH, 1, 0.5);
		double high = forecast.quantile(PriceForecast.Side.HIGH, 1, 0.9);

		assertTrue(low < mid);
		assertTrue(mid < high);
	}

	@Test
	public void aQuantileFallsTowardTheMarketAsTheTimeRunsOut()
	{
		// This is what replaces the decay timer. Asking for an optimistic price with eight hours left
		// is reasonable; asking for the same price with ten minutes left is not, and the forecast says
		// so on its own because the distribution narrows.
		PriceForecast forecast = PriceForecast.fit(reverting(1000, 20, 300, 4), BUCKET_SECONDS);

		double plenty = forecast.quantile(PriceForecast.Side.HIGH, 8, 0.75);
		double little = forecast.quantile(PriceForecast.Side.HIGH, 1.0 / 6, 0.75);

		assertTrue("with time in hand it is worth holding out for more: " + plenty + " vs " + little,
			plenty > little);
	}

	@Test
	public void tooLittleHistorySaysSoRatherThanGuessing()
	{
		List<Candle> barely = reverting(1000, 20, 5, 5);

		PriceForecast forecast = PriceForecast.fit(barely, BUCKET_SECONDS);

		assertTrue("five candles is not an item's behaviour",
			!forecast.isUsable(PriceForecast.Side.LOW));
		assertEquals("and an unusable side must not produce a number to act on",
			0, forecast.quantile(PriceForecast.Side.LOW, 1, 0.5), 1e-9);
	}

	@Test
	public void theTwoSidesAreFittedSeparately()
	{
		// The gap between them is the spread, and smearing it into a single mid-price forecast would
		// throw away the only thing a flip earns.
		PriceForecast forecast = PriceForecast.fit(reverting(1000, 20, 200, 6), BUCKET_SECONDS);

		double buySide = forecast.centre(PriceForecast.Side.LOW, 1);
		double sellSide = forecast.centre(PriceForecast.Side.HIGH, 1);

		assertTrue("the sell side sits above the buy side, as it does in the book",
			sellSide > buySide);
	}
}
