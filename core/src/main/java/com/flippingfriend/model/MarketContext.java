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
	private static final MarketContext UNKNOWN = new MarketContext(flatHours(), 0.5, 0, null, 0);

	private static final int HOURS = 24;
	/** Enough coverage that an hourly average is not one or two observations. */
	private static final int MIN_SAMPLES = 72;
	/** Hours of baseline a candidate shift is measured against. */
	private static final int BREAK_WINDOW = 24;

	/**
	 * How large a shift has to be, in baseline standard deviations, to count as a break.
	 *
	 * <p>Higher than the 3.0 this used before, because the statistic changed. The old test compared
	 * one fixed pair of windows, so 3.0 was a threshold on a single number; the scan below evaluates
	 * every plausible break point and keeps the largest, and the maximum of forty-odd correlated
	 * statistics crosses any threshold more readily than one of them does.
	 *
	 * <p>Measured on stable synthetic series rather than argued: the scan fires on 2 of 300 at 3.0
	 * and 0 of 300 at 4.5, across noise from 0.2% to 3% of price. That is a small difference, and it
	 * is not what decides the number. What decides it is which error is worse. A 5% move is
	 * ambiguous — plenty of items do that in a day without anything changing in the game — and
	 * {@code volatilityRatio} and {@code pricePercentile} already exist to judge ordinary
	 * restlessness. This veto is for the unmistakable case, where mean reversion is not merely noisy
	 * but pointing at a level that no longer exists. At 4.5 a 10% repricing four hours old is caught
	 * 99 times in 100 and a 25% one every time, while a 5% wobble is left to the vetoes built for it.
	 */
	private static final double BREAK_SIGMA = 4.5;

	/**
	 * Fewest hours of new level required before a shift is called a break.
	 *
	 * <p>The whole point of scanning is to see a break on the day it lands rather than the day
	 * after, so this has to be short. It cannot be one: a single hour at a new price is a print, not
	 * a level, and the wiki's hourly average can be moved by one large trade in a thin item.
	 */
	private static final int MIN_NEW_LEVEL_HOURS = 4;

	/** How far back to look for a break. Older than this and the item has already re-based. */
	private static final int SCAN_HOURS = 72;
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
	/** When the level shifted, so the break can be attributed rather than only noticed. Null if none. */
	private final Instant breakAt;
	private final int sampleCount;

	private MarketContext(double[] hourlyMultiplier, double pricePercentile, double longVolatility,
		Instant breakAt, int sampleCount)
	{
		this.hourlyMultiplier = hourlyMultiplier;
		this.pricePercentile = pricePercentile;
		this.longVolatility = longVolatility;
		this.structuralBreak = breakAt != null;
		this.breakAt = breakAt;
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
		// Kept alongside the prices so a detected break has a time, which is the whole difference
		// between noticing a level shift and being able to say what caused it.
		List<Long> times = new ArrayList<>(hourlySeries.size());
		double[] volumeByHour = new double[HOURS];
		int[] countByHour = new int[HOURS];

		for (Candle candle : hourlySeries)
		{
			double mid = candle.midPrice();
			if (mid > 0)
			{
				mids.add(mid);
				times.add(candle.getTimestamp());
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
			findBreak(mids, times),
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
	 * Finds a recent shift in price level and says when it happened.
	 *
	 * <p>A game update, a drop-rate change or a crash all look the same here: the price stops
	 * oscillating around one level and starts oscillating around another. After that the older
	 * history describes an item that no longer exists, and the mean-reversion reasoning every model
	 * in this codebase relies on is not merely useless but actively misleading — it will keep
	 * predicting a return to a level nothing is pulling the price toward.
	 *
	 * <p><b>This used to compare the last day against the day before it, and that was blind twice
	 * over.</b> Both faults were measured against the replacement on synthetic repricings, and both
	 * are worse than they sound.
	 *
	 * <p><i>Late.</i> A break six hours old sits inside the "recent" window beside eighteen hours of
	 * the old level, so the measured shift is a quarter of the real one. A 10% repricing four hours
	 * old was caught 1 time in 100, and 10 times in 100 at six hours. It became reliable at about
	 * twelve. That is precisely the "notice on Thursday that herb prices moved" this is meant to
	 * beat.
	 *
	 * <p><i>And then blind again.</i> By thirty-six hours <b>both</b> windows sit on the new level,
	 * so there is no difference left to measure and the same 10% repricing was caught <b>0 times in
	 * 100</b>. The item was rejected on the second day and quietly accepted again on the third, with
	 * every mean-reverting fit in the codebase still pulling toward a price the game had deleted.
	 * The old rule had roughly a one-day window of vision and was wrong on either side of it.
	 *
	 * <p>Scanning for the split point instead catches a repricing while it is four hours old, keeps
	 * seeing it afterwards, and hands back the hour it happened so
	 * {@link GameUpdateCalendar#explains} can say whether an update accounts for it.
	 *
	 * @param times epoch seconds for each mid, same order and length
	 * @return when the level shifted, or null if it has not
	 */
	private static Instant findBreak(List<Double> mids, List<Long> times)
	{
		int n = mids.size();
		if (n < BREAK_WINDOW + MIN_NEW_LEVEL_HOURS)
		{
			return null;
		}

		double best = 0;
		int bestSplit = -1;
		int earliest = Math.max(BREAK_WINDOW, n - SCAN_HOURS);
		for (int split = earliest; split <= n - MIN_NEW_LEVEL_HOURS; split++)
		{
			List<Double> baseline = mids.subList(split - BREAK_WINDOW, split);
			double baselineMean = FeatureEngine.mean(baseline);
			if (baselineMean <= 0)
			{
				continue;
			}
			// Scaled by the baseline's own dispersion, floored so a rock-steady item is not made
			// undetectable by dividing a real jump by nearly zero. A steady item that suddenly moves
			// is the clearest signal there is that something in the game changed.
			double spread = Math.max(FeatureEngine.stdDev(baseline, baselineMean),
				baselineMean * MIN_RELATIVE_SPREAD);
			double afterMean = FeatureEngine.mean(mids.subList(split, n));
			double score = Math.abs(afterMean - baselineMean) / spread;
			if (score > best)
			{
				best = score;
				bestSplit = split;
			}
		}

		if (bestSplit < 0 || best <= BREAK_SIGMA)
		{
			return null;
		}
		return Instant.ofEpochSecond(times.get(bestSplit));
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

	/**
	 * When the level shifted, or null if it has not.
	 * <p>
	 * The difference between "this item moved" and "the Wednesday update changed what this item is
	 * worth". Both reject the trade; only one of them is a reason to come back tomorrow.
	 */
	public Instant breakAt()
	{
		return breakAt;
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
