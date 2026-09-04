package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where an item's price is likely to be in a while, and how sure that is.
 * <p>
 * Everything else in this system answers "what is the price now". That is enough to decide whether a
 * spread exists, but not to decide when to leave a position, which is a question about the future:
 * the target you set an hour ago is only worth waiting for if the price can still plausibly reach it
 * in the time you have left. Without a forecast the only way to express that is a timer — walk the
 * target down as the position ages and hope the schedule matches the market. It does not, and it
 * cannot, because the schedule knows nothing about the item.
 * <p>
 * <b>Two sides, fitted separately.</b> The price you can buy at and the price you can sell into move
 * differently — the gap between them is the spread, and it widens and narrows on its own. Forecasting
 * the mid and adding half a spread would smear that away, so each side gets its own fit.
 * <p>
 * <b>Mean reversion, not a random walk.</b> These are consumables with a production cost and a use,
 * not equities. An item three percent above its own recent median is more likely to fall back than to
 * keep climbing, and a random walk — which says the best guess for any horizon is the price right now
 * — would never let a target be reached by anything but luck. The reversion rate is estimated per
 * item from its own history rather than assumed, because a bond and a bucket of milk do not behave
 * alike.
 * <p>
 * <b>The bands are measured, not assumed.</b> Quantiles come from replaying this same forecast rule
 * over the item's own history at the horizon being asked about, and looking at how wrong it actually
 * was. Not from a normal curve, which prices do not follow — and not from one-step residuals scaled
 * up either. That was tried, and produced a band claiming half the outcomes while holding 35% of them,
 * because errors compound over a horizon in ways a single step cannot show. Measuring the horizon you
 * are actually asking about is the only way the number means what it says, and a forecast that is
 * believed and miscalibrated is worse than no forecast at all.
 */
public final class PriceForecast
{
	/** Which side of the book to forecast. */
	public enum Side
	{
		/** What you can buy at. */
		LOW,
		/** What you can sell into. */
		HIGH
	}

	/**
	 * Fewest observations worth fitting on.
	 * <p>
	 * An autoregression on a handful of points measures the handful, not the item. Two hours of
	 * five-minute candles is the floor at which the reversion estimate stops being noise.
	 */
	private static final int MIN_SAMPLES = 24;

	/**
	 * Ceiling on the fitted persistence.
	 * <p>
	 * A measured value of one means "never reverts", which makes the dispersion grow without limit and
	 * the centre never move. Clamped just short so a very persistent item behaves like a random walk
	 * over the horizons anyone actually holds for, rather than dividing by zero.
	 */
	private static final double MAX_PHI = 0.995;

	private final SideFit low;
	private final SideFit high;
	private final double bucketHours;

	private PriceForecast(SideFit low, SideFit high, double bucketHours)
	{
		this.low = low;
		this.high = high;
		this.bucketHours = bucketHours;
	}

	/**
	 * Fits a forecast to an item's recent history.
	 *
	 * @param series        candles oldest first, as the wiki returns them
	 * @param bucketSeconds the spacing those candles represent
	 * @return a forecast, which may be unusable when there is too little history to fit
	 */
	public static PriceForecast fit(List<Candle> series, int bucketSeconds)
	{
		double bucketHours = Math.max(1, bucketSeconds) / 3600.0;
		if (series == null || series.isEmpty())
		{
			return new PriceForecast(SideFit.unusable(), SideFit.unusable(), bucketHours);
		}
		return new PriceForecast(
			SideFit.fit(prices(series, Side.LOW)),
			SideFit.fit(prices(series, Side.HIGH)),
			bucketHours);
	}

	private static double[] prices(List<Candle> series, Side side)
	{
		List<Double> values = new ArrayList<>(series.size());
		for (Candle candle : series)
		{
			Integer price = side == Side.LOW ? candle.getAvgLowPrice() : candle.getAvgHighPrice();
			if (price != null && price > 0)
			{
				values.add((double) price);
			}
		}
		double[] out = new double[values.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = values.get(i);
		}
		return out;
	}

	/** False when there was not enough history to say anything, in which case callers must not guess. */
	public boolean isUsable(Side side)
	{
		return fitFor(side).usable;
	}

	/**
	 * The price this side is expected to sit at after {@code horizonHours}, with no allowance either
	 * way. The centre of the forecast, not a price to trade on.
	 */
	public double centre(Side side, double horizonHours)
	{
		SideFit fit = fitFor(side);
		return fit.centre(steps(horizonHours));
	}

	/**
	 * A quantile of the forecast distribution.
	 * <p>
	 * The 0.5 quantile is the median outcome; 0.75 is a price the market beats one time in four. Which
	 * to ask for is a judgement about patience, and belongs to the caller: a seller waiting for a good
	 * price wants a high quantile early and a low one as the time runs out, and that fall happens on
	 * its own here because the distribution narrows as the horizon shrinks.
	 *
	 * @param quantile between 0 and 1
	 * @return the price, or 0 when this side could not be fitted
	 */
	public double quantile(Side side, double horizonHours, double quantile)
	{
		SideFit fit = fitFor(side);
		if (!fit.usable)
		{
			return 0;
		}
		int steps = steps(horizonHours);
		return Math.max(1, fit.centre(steps) + fit.errorQuantile(steps, quantile));
	}

	/**
	 * The best price the market can be expected to <em>touch</em> at some point within the horizon.
	 * <p>
	 * {@link #quantile} answers "where will this be in an hour", which is the wrong question for anyone
	 * holding an offer. A sell order fills the first moment the price reaches it and does not care where
	 * the price ends up; the seller is exposed to the whole path, not to its last point. Pricing the
	 * endpoint understates what an hour of patience is worth, and - because a mean-reverting fit stops
	 * moving once {@code phi^steps} has decayed - it gives back almost nothing as the hour runs out. Six
	 * hours and twenty minutes produced targets one coin apart, which is not a walk-down.
	 * <p>
	 * Measured, like everything else here: stand at each point in the history, predict the horizon the
	 * way {@link #centre} does, and record how far the <em>highest</em> price over the following window
	 * ran past that prediction. The spread of those excesses is what a window of this length has
	 * actually been worth. It falls to {@link #quantile} at a one-step horizon, which is correct - with
	 * one look left, touching and ending are the same event - and it carries the autocorrelation for
	 * free, where multiplying single-step probabilities would have assumed the steps were independent
	 * and overstated the chance of a lucky excursion in a market that trends.
	 *
	 * @param quantile the fraction of past windows in which the market failed to beat the answer, so
	 *                 0.75 is a price it touched one window in four
	 * @return the price, or 0 when this side could not be fitted
	 */
	public double reachableWithin(Side side, double horizonHours, double quantile)
	{
		SideFit fit = fitFor(side);
		if (!fit.usable)
		{
			return 0;
		}
		int steps = steps(horizonHours);
		return Math.max(1, fit.centre(steps) + fit.runningMaxQuantile(steps, quantile));
	}

	/**
	 * How wide the middle of the distribution is, as a fraction of the centre.
	 * <p>
	 * A cheap read on whether the forecast is saying anything: a band spanning half the price is a
	 * forecast in name only, and a caller is better off ignoring it than acting on its midpoint.
	 */
	public double relativeSpread(Side side, double horizonHours)
	{
		SideFit fit = fitFor(side);
		if (!fit.usable)
		{
			return Double.MAX_VALUE;
		}
		double centre = fit.centre(steps(horizonHours));
		if (centre <= 0)
		{
			return Double.MAX_VALUE;
		}
		return (quantile(side, horizonHours, 0.75) - quantile(side, horizonHours, 0.25)) / centre;
	}

	private SideFit fitFor(Side side)
	{
		return side == Side.LOW ? low : high;
	}

	/** Horizons shorter than one candle still get one step; the forecast cannot resolve finer. */
	private int steps(double horizonHours)
	{
		return Math.max(1, (int) Math.round(horizonHours / bucketHours));
	}

	/**
	 * One side's autoregression: how far it sits from its median, how fast it returns, and how wrong
	 * that has been in the past.
	 */
	private static final class SideFit
	{
		private final boolean usable;
		private final double median;
		private final double last;
		private final double phi;
		/** The history the fit was made from, kept so the same rule can be replayed against it. */
		private final double[] prices;

		private SideFit(boolean usable, double median, double last, double phi, double[] prices)
		{
			this.usable = usable;
			this.median = median;
			this.last = last;
			this.phi = phi;
			this.prices = prices;
		}

		static SideFit unusable()
		{
			return new SideFit(false, 0, 0, 0, new double[0]);
		}

		static SideFit fit(double[] prices)
		{
			if (prices.length < MIN_SAMPLES)
			{
				return unusable();
			}

			double median = median(prices.clone());
			// Deviations from the median, which is what reverts. Fitting the prices themselves would
			// measure the level rather than the behaviour.
			double[] deviations = new double[prices.length];
			for (int i = 0; i < prices.length; i++)
			{
				deviations[i] = prices[i] - median;
			}

			double covariance = 0;
			double variance = 0;
			for (int i = 0; i < deviations.length - 1; i++)
			{
				covariance += deviations[i] * deviations[i + 1];
				variance += deviations[i] * deviations[i];
			}
			if (variance <= 0)
			{
				// A perfectly flat item. The forecast is the price, with no width -- which is true, and
				// the caller can act on it.
				return new SideFit(true, median, prices[prices.length - 1], 0, prices);
			}
			double phi = Math.max(0, Math.min(MAX_PHI, covariance / variance));
			return new SideFit(true, median, prices[prices.length - 1], phi, prices);
		}

		/** Where the price is expected to be after n steps: part of the way back to the median. */
		double centre(int steps)
		{
			return median + (last - median) * Math.pow(phi, steps);
		}

		/**
		 * How wrong this forecast has been at this horizon, as a quantile of its own past errors.
		 * <p>
		 * The same rule, replayed: stand at each point in the history, predict {@code steps} ahead the
		 * way {@link #centre} does now, and record the gap to what actually happened. The spread of
		 * those gaps <em>is</em> the uncertainty, measured on the question being asked rather than
		 * inferred from a single step and scaled up. Scaling was tried and understated the width badly
		 * -- a band claiming half the outcomes held a third of them -- because errors compound and the
		 * fitted parameters are themselves uncertain, neither of which a one-step residual can show.
		 */
		double errorQuantile(int steps, double quantile)
		{
			double decay = Math.pow(phi, steps);
			int available = prices.length - steps;
			if (available < 2)
			{
				return 0;
			}
			double[] errors = new double[available];
			for (int i = 0; i < available; i++)
			{
				double predicted = median + (prices[i] - median) * decay;
				errors[i] = prices[i + steps] - predicted;
			}
			Arrays.sort(errors);
			return quantileOf(errors, quantile);
		}

		/**
		 * The same replay, but recording how far the highest price in the window ran past the prediction
		 * instead of where the window ended.
		 * <p>
		 * This is the quantity a resting offer is exposed to. At {@code steps == 1} it is identical to
		 * {@link #errorQuantile}, because with one observation left the maximum of the window is the end
		 * of it; past that the two separate, and the gap between them is the value of being able to sell
		 * at any moment rather than only at the close.
		 */
		double runningMaxQuantile(int steps, double quantile)
		{
			double decay = Math.pow(phi, steps);
			int available = prices.length - steps;
			if (available < 2)
			{
				return 0;
			}
			double[] excesses = new double[available];
			for (int i = 0; i < available; i++)
			{
				double predicted = median + (prices[i] - median) * decay;
				double best = prices[i + 1];
				for (int j = 2; j <= steps; j++)
				{
					best = Math.max(best, prices[i + j]);
				}
				excesses[i] = best - predicted;
			}
			Arrays.sort(excesses);
			return quantileOf(excesses, quantile);
		}

		/** Linear interpolation into a sorted sample. One copy, so the two readings cannot drift apart. */
		private static double quantileOf(double[] sorted, double quantile)
		{
			double clamped = Math.max(0, Math.min(1, quantile));
			double position = clamped * (sorted.length - 1);
			int lower = (int) Math.floor(position);
			int upper = (int) Math.ceil(position);
			if (lower == upper)
			{
				return sorted[lower];
			}
			double weight = position - lower;
			return sorted[lower] * (1 - weight) + sorted[upper] * weight;
		}

		private static double median(double[] values)
		{
			Arrays.sort(values);
			int middle = values.length / 2;
			return values.length % 2 == 0
				? (values[middle - 1] + values[middle]) / 2.0
				: values[middle];
		}
	}
}
