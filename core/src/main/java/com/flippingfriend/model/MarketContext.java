package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The long view of an item: where today sits in its recent range, whether the market has changed
 * character, and what time of day it actually trades.
 * <p>
 * This exists because the five-minute series the scorer works from only covers about thirty hours.
 * That is the right window for judging a spread, and the wrong one for judging everything else. An
 * item can look perfectly stable across a day and still be halfway through a week-long collapse, or
 * be priced at the very top of a fortnight's range, or simply be in its quiet hours. None of that is
 * visible in thirty hours of data, and all of it changes whether a trade is worth making.
 * <p>
 * Built from the one-hour series, which covers roughly fifteen days from the same endpoint we
 * already call — so this costs one extra request per shortlisted item and no new dependency.
 */
public final class MarketContext
{
	private static final MarketContext UNKNOWN = new MarketContext(flatHours(), 0.5, 0, false, 0);

	private static final int HOURS = 24;
	/** Enough coverage that an hourly average is not one or two observations. */
	private static final int MIN_SAMPLES = 72;
	/** Hours compared against the preceding period when looking for a level shift. */
	private static final int BREAK_WINDOW = 24;
	/** How large a shift has to be, in long-run standard deviations, to count as a break. */
	private static final double BREAK_SIGMA = 3.0;
	/**
	 * Floor on the dispersion used to judge a shift, as a fraction of price.
	 * <p>
	 * A perfectly steady item has a standard deviation of zero, and dividing a shift by zero means
	 * no shift is ever large enough to register. That would disable the check precisely on the items
	 * it matters most for: a rock-steady item that suddenly jumps is the clearest possible signal
	 * that something in the game changed.
	 */
	private static final double MIN_RELATIVE_SPREAD = 0.002;
	/** Seasonal adjustment is clamped: the pattern is real but noisy, and this is a multiplier. */
	private static final double MIN_MULTIPLIER = 0.4;
	private static final double MAX_MULTIPLIER = 2.0;

	private final double[] hourlyMultiplier;
	private final double pricePercentile;
	private final double longVolatility;
	private final boolean structuralBreak;
	private final int sampleCount;

	private MarketContext(double[] hourlyMultiplier, double pricePercentile, double longVolatility,
		boolean structuralBreak, int sampleCount)
	{
		this.hourlyMultiplier = hourlyMultiplier;
		this.pricePercentile = pricePercentile;
		this.longVolatility = longVolatility;
		this.structuralBreak = structuralBreak;
		this.sampleCount = sampleCount;
	}

	public static MarketContext unknown()
	{
		return UNKNOWN;
	}

	/**
	 * @param hourlySeries the one-hour timeseries, roughly a fortnight
	 * @param currentPrice the price being considered right now
	 */
	public static MarketContext from(List<Candle> hourlySeries, double currentPrice)
	{
		if (hourlySeries == null || hourlySeries.size() < MIN_SAMPLES)
		{
			return UNKNOWN;
		}

		List<Double> mids = new ArrayList<>(hourlySeries.size());
		double[] volumeByHour = new double[HOURS];
		int[] countByHour = new int[HOURS];

		for (Candle candle : hourlySeries)
		{
			double mid = candle.midPrice();
			if (mid > 0)
			{
				mids.add(mid);
			}

			int hour = Instant.ofEpochSecond(candle.getTimestamp()).atZone(ZoneOffset.UTC).getHour();
			volumeByHour[hour] += candle.getTotalVolume();
			countByHour[hour]++;
		}

		if (mids.size() < MIN_SAMPLES)
		{
			return UNKNOWN;
		}

		return new MarketContext(
			hourlyMultipliers(volumeByHour, countByHour),
			percentileOf(currentPrice, mids),
			FeatureEngine.logReturnStdDev(mids),
			hasStructuralBreak(mids),
			mids.size());
	}

	/**
	 * Average volume for each hour of the day, normalised so the mean hour is 1.0.
	 * <p>
	 * The swing between an item's busiest and quietest hour is routinely close to threefold, so a
	 * fill estimate that averages across the whole day is optimistic at 4am and pessimistic at peak.
	 */
	private static double[] hourlyMultipliers(double[] volumeByHour, int[] countByHour)
	{
		double[] average = new double[HOURS];
		double total = 0;
		int populated = 0;

		for (int hour = 0; hour < HOURS; hour++)
		{
			if (countByHour[hour] > 0)
			{
				average[hour] = volumeByHour[hour] / countByHour[hour];
				total += average[hour];
				populated++;
			}
		}

		if (populated == 0 || total <= 0)
		{
			return flatHours();
		}

		double mean = total / populated;
		double[] multipliers = new double[HOURS];
		for (int hour = 0; hour < HOURS; hour++)
		{
			double value = countByHour[hour] > 0 ? average[hour] / mean : 1.0;
			multipliers[hour] = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
		}
		return multipliers;
	}

	/**
	 * Detects a recent shift in price level, which is what a game update, a drop-rate change or a
	 * crash looks like in the data. After a break the older history describes an item that no longer
	 * exists, so the usual mean-reversion reasoning is actively misleading.
	 */
	private static boolean hasStructuralBreak(List<Double> mids)
	{
		if (mids.size() < BREAK_WINDOW * 2)
		{
			return false;
		}

		List<Double> recent = mids.subList(mids.size() - BREAK_WINDOW, mids.size());
		List<Double> earlier = mids.subList(mids.size() - BREAK_WINDOW * 2, mids.size() - BREAK_WINDOW);

		double recentMean = FeatureEngine.mean(recent);
		double earlierMean = FeatureEngine.mean(earlier);

		if (earlierMean <= 0)
		{
			return false;
		}

		double spread = Math.max(FeatureEngine.stdDev(earlier, earlierMean),
			earlierMean * MIN_RELATIVE_SPREAD);

		return Math.abs(recentMean - earlierMean) > BREAK_SIGMA * spread;
	}

	private static double percentileOf(double price, List<Double> mids)
	{
		if (price <= 0 || mids.isEmpty())
		{
			return 0.5;
		}
		int below = 0;
		for (double mid : mids)
		{
			if (mid < price)
			{
				below++;
			}
		}
		return (double) below / mids.size();
	}

	private static double[] flatHours()
	{
		double[] flat = new double[HOURS];
		Arrays.fill(flat, 1.0);
		return flat;
	}

	public boolean isUsable()
	{
		return sampleCount >= MIN_SAMPLES;
	}

	/** How busy this item is at the given UTC hour, relative to its own daily average. */
	public double liquidityMultiplier(int utcHour)
	{
		if (utcHour < 0 || utcHour >= HOURS)
		{
			return 1.0;
		}
		return hourlyMultiplier[utcHour];
	}

	public double liquidityMultiplierNow()
	{
		return liquidityMultiplier(Instant.now().atZone(ZoneOffset.UTC).getHour());
	}

	/**
	 * Where the current price sits within its fortnight range, from 0 (the cheapest it has been) to
	 * 1 (the dearest). Buying near the top of the range is a materially different proposition from
	 * buying near the bottom, even when the immediate spread looks the same.
	 */
	public double getPricePercentile()
	{
		return pricePercentile;
	}

	/** Volatility measured over the fortnight rather than the last day. */
	public double getLongVolatility()
	{
		return longVolatility;
	}

	/** True when the item's price level has recently shifted enough that its history is stale. */
	public boolean hasStructuralBreak()
	{
		return structuralBreak;
	}

	public int getSampleCount()
	{
		return sampleCount;
	}

	/**
	 * Ratio of the last day's volatility to the fortnight's. Above one means the item is currently
	 * jumpier than it normally is, which is a reason for caution that a single-window measure of
	 * volatility cannot express.
	 */
	public double volatilityRatio(double shortVolatility)
	{
		if (longVolatility <= 0 || shortVolatility <= 0)
		{
			return 1.0;
		}
		return shortVolatility / longVolatility;
	}
}
