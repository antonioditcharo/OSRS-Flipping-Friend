package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Whether the forecast's confidence is earned.
 * <p>
 * This is the test that decides whether the forecast may be used at all. A band claiming to hold the
 * price half the time has to hold it about half the time, on data the fit never saw — otherwise the
 * sell logic will wait for prices that were never plausible, or bail out of positions that were fine,
 * and it will do so with more conviction than the fixed target it replaced. A miscalibrated forecast
 * is worse than none, because it is believed.
 * <p>
 * Measured against real five-minute candles from the wiki, checked in under {@code test/resources},
 * strictly out of sample: fit on everything up to a point in time, then look at what the price
 * actually did afterwards. Thirteen items the planner really trades, every position in the series
 * used as a separate origin.
 */
public class ForecastCalibrationTest
{
	private static final int BUCKET_SECONDS = 300;
	private static final int[] ITEMS =
		{1987, 561, 560, 554, 1517, 1607, 2357, 1521, 1601, 314, 1515, 453, 440};

	/** Horizons a flip actually cares about, in hours. */
	private static final double[] HORIZONS = {0.5, 1, 2, 4};

	private static List<Candle> load(int itemId) throws Exception
	{
		List<Candle> series = new ArrayList<>();
		try (InputStream in = ForecastCalibrationTest.class
			.getResourceAsStream("/forecast/" + itemId + ".csv"))
		{
			if (in == null)
			{
				throw new IllegalStateException("missing fixture for " + itemId);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith("#") || line.startsWith("timestamp"))
				{
					continue;
				}
				String[] parts = line.split(",");
				series.add(new Candle(Long.parseLong(parts[0]), Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]), 100, 100));
			}
		}
		return series;
	}

	/** How often the realised price landed inside a band of the stated width. */
	private static double coverage(double lower, double upper) throws Exception
	{
		int inside = 0;
		int total = 0;
		for (int itemId : ITEMS)
		{
			List<Candle> series = load(itemId);
			for (double horizonHours : HORIZONS)
			{
				int ahead = (int) Math.round(horizonHours * 3600 / BUCKET_SECONDS);
				// Walk the series, fitting only on the past and grading against the future.
				for (int origin = 120; origin + ahead < series.size(); origin += 5)
				{
					PriceForecast forecast =
						PriceForecast.fit(series.subList(0, origin), BUCKET_SECONDS);
					if (!forecast.isUsable(PriceForecast.Side.HIGH))
					{
						continue;
					}
					Integer actual = series.get(origin + ahead).getAvgHighPrice();
					if (actual == null || actual <= 0)
					{
						continue;
					}
					double low = forecast.quantile(PriceForecast.Side.HIGH, horizonHours, lower);
					double high = forecast.quantile(PriceForecast.Side.HIGH, horizonHours, upper);
					total++;
					if (actual >= low && actual <= high)
					{
						inside++;
					}
				}
			}
		}
		assertTrue("the fixtures must produce a real sample, not a handful", total > 2_000);
		return (double) inside / total;
	}

	@Test
	public void theBandsHoldThePriceAsOftenAsTheyClaim() throws Exception
	{
		double fifty = coverage(0.25, 0.75);
		double eighty = coverage(0.10, 0.90);

		System.out.printf("forecast coverage: 50%% band %.1f%%, 80%% band %.1f%%%n",
			fifty * 100, eighty * 100);

		// Measured at 54% and 81% on this fixture. The bounds are wide enough not to flake on a
		// re-fetch of the data and tight enough to catch the failure that actually happened during
		// development: scaling one-step residuals instead of measuring the horizon gave 35% here, and
		// would have had the sell logic waiting for prices that were never plausible.
		assertTrue("the 50% band held the price " + Math.round(fifty * 100) + "% of the time",
			fifty > 0.42 && fifty < 0.62);
		assertTrue("the 80% band held the price " + Math.round(eighty * 100) + "% of the time",
			eighty > 0.72 && eighty < 0.88);
		assertTrue("a wider band must cover more, or the quantiles are not ordered", eighty > fifty);
	}

	/**
	 * How often the market actually beat the price the forecast said it would beat {@code 1 - quantile}
	 * of the time, over the whole window rather than at its close.
	 */
	private static double touchRate(double quantile) throws Exception
	{
		int touched = 0;
		int total = 0;
		for (int itemId : ITEMS)
		{
			List<Candle> series = load(itemId);
			for (double horizonHours : HORIZONS)
			{
				int ahead = (int) Math.round(horizonHours * 3600 / BUCKET_SECONDS);
				for (int origin = 120; origin + ahead < series.size(); origin += 5)
				{
					PriceForecast forecast =
						PriceForecast.fit(series.subList(0, origin), BUCKET_SECONDS);
					if (!forecast.isUsable(PriceForecast.Side.HIGH))
					{
						continue;
					}
					double claimed =
						forecast.reachableWithin(PriceForecast.Side.HIGH, horizonHours, quantile);
					boolean beaten = false;
					boolean sawAPrice = false;
					for (int step = 1; step <= ahead; step++)
					{
						Integer actual = series.get(origin + step).getAvgHighPrice();
						if (actual == null || actual <= 0)
						{
							continue;
						}
						sawAPrice = true;
						if (actual >= claimed)
						{
							beaten = true;
							break;
						}
					}
					if (!sawAPrice)
					{
						continue;
					}
					total++;
					if (beaten)
					{
						touched++;
					}
				}
			}
		}
		assertTrue("the fixtures must produce a real sample, not a handful", total > 2_000);
		return (double) touched / total;
	}

	@Test
	public void aReachablePriceIsReachedAsOftenAsItClaims() throws Exception
	{
		// The same question asked of the number the sell logic actually acts on. reachableWithin says
		// "the market touched this at some point in a window this long, one window in four" - and if
		// that is wrong in the optimistic direction, every position waits for a price that was never
		// coming and is dumped at market when the hold limit fires. Which is the failure this whole
		// mechanism exists to prevent, arriving by a different route.
		double quarter = touchRate(0.75);
		double half = touchRate(0.5);
		double tenth = touchRate(0.9);

		System.out.printf("reachable touch rate: q75 %.1f%% (claims 25%%), q50 %.1f%% (claims 50%%), "
			+ "q90 %.1f%% (claims 10%%)%n", quarter * 100, half * 100, tenth * 100);

		// 0.5 is the level the sell engine asks for, and the only one this fixture can answer.
		// Measured at 58%. Six points of residual optimism, from overlapping windows that leave far
		// fewer independent observations than rows.
		assertTrue("the 0.5 price was reached " + Math.round(half * 100) + "% of the time",
			half > 0.48 && half < 0.66);

		// The upper tail, pinned as the defect it is rather than as a passing claim. 0.75 says the
		// market beats it one window in four and it beat it 42%; 0.9 says one in ten and it beat it
		// 31%. Both err toward selling too cheap, and neither is fixable at this sample size: a
		// four-hour window over this much history has a handful of independent observations, and an
		// upper tail cannot be read from a handful. If a later change makes these honest, this test
		// fails and REACHABLE_QUANTILE can be reconsidered - which is the point of asserting it.
		assertTrue("0.75 is still optimistic, as recorded: " + Math.round(quarter * 100) + "%",
			quarter > 0.30 && quarter < 0.55);
		assertTrue("0.9 is still optimistic, as recorded: " + Math.round(tenth * 100) + "%",
			tenth > 0.20 && tenth < 0.42);
		assertTrue("a more ambitious price must at least be reached less often: "
			+ tenth + " < " + quarter + " < " + half, tenth < quarter && quarter < half);
	}

	@Test
	public void theForecastBeatsAssumingThePriceStaysPut() throws Exception
	{
		// The bar the forecast has to clear to be worth having: a random walk, which says the best
		// guess at any horizon is the price right now. If reversion adds nothing, this says so.
		double forecastError = 0;
		double flatError = 0;
		int samples = 0;

		for (int itemId : ITEMS)
		{
			List<Candle> series = load(itemId);
			int ahead = (int) Math.round(2 * 3600.0 / BUCKET_SECONDS);
			for (int origin = 120; origin + ahead < series.size(); origin += 5)
			{
				PriceForecast forecast = PriceForecast.fit(series.subList(0, origin), BUCKET_SECONDS);
				if (!forecast.isUsable(PriceForecast.Side.HIGH))
				{
					continue;
				}
				Integer actual = series.get(origin + ahead).getAvgHighPrice();
				Integer now = series.get(origin - 1).getAvgHighPrice();
				if (actual == null || now == null || actual <= 0 || now <= 0)
				{
					continue;
				}
				double predicted = forecast.centre(PriceForecast.Side.HIGH, 2);
				// Relative error, so a 10,000 gp item does not drown out a 5 gp one.
				forecastError += Math.abs(predicted - actual) / (double) actual;
				flatError += Math.abs(now - actual) / (double) actual;
				samples++;
			}
		}

		double forecastMean = forecastError / samples;
		double flatMean = flatError / samples;
		System.out.printf("two-hour mean absolute error: forecast %.4f, price-stays-put %.4f (%d samples)%n",
			forecastMean, flatMean, samples);

		assertTrue("if reversion does not beat assuming the price stays put, it is not worth having: "
			+ forecastMean + " vs " + flatMean, forecastMean < flatMean);
	}
}
