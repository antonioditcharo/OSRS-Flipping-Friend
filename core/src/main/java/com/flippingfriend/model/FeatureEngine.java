package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Turns a raw candle series into the statistics the rest of the model reasons about.
 * <p>
 * The choice of robust statistics here is deliberate. Grand Exchange data is full of single
 * transactions at absurd prices — misclicks, deliberate manipulation, and thin items where one
 * trade moves the average. Mean and standard deviation are trivially poisoned by those; median and
 * median-absolute-deviation are not, so they are what the accept/reject decisions are built on.
 */
@Singleton
public class FeatureEngine
{
	/** Below this many buckets there is not enough signal to say anything useful. */
	private static final int MIN_SAMPLES = 12;
	/** Smoothing spans, in buckets. At the 5m timestep these are 30 minutes and 3 hours. */
	private static final int EMA_FAST_SPAN = 6;
	private static final int EMA_SLOW_SPAN = 36;
	/** A trend has to be worth more than the noise before we call it one. */
	private static final double TREND_THRESHOLD = 0.5;

	private Map<Integer, Double> predictedMomentums = Collections.emptyMap();

	public void setPredictedMomentums(Map<Integer, Double> predictedMomentums)
	{
		this.predictedMomentums = predictedMomentums;
	}

	/**
	 * @param bucketSeconds length of one candle, used to convert per-bucket rates into per-hour ones
	 */
	public ItemFeatures compute(int itemId, List<Candle> series, int bucketSeconds)
	{
		if (series == null || series.size() < MIN_SAMPLES)
		{
			return ItemFeatures.unknown(itemId);
		}

		List<Double> mids = new ArrayList<>(series.size());
		List<Double> spreadPcts = new ArrayList<>(series.size());
		double volumeSum = 0;
		int emptyBuckets = 0;

		for (Candle candle : series)
		{
			double mid = candle.midPrice();
			if (mid > 0)
			{
				mids.add(mid);
			}
			else
			{
				emptyBuckets++;
			}

			if (candle.hasBothSides())
			{
				double spread = candle.getAvgHighPrice() - candle.getAvgLowPrice();
				spreadPcts.add(spread / candle.getAvgLowPrice());
			}

			volumeSum += candle.getTotalVolume();
		}

		if (mids.size() < MIN_SAMPLES)
		{
			return ItemFeatures.unknown(itemId);
		}

		double median = median(new ArrayList<>(mids));
		double mad = medianAbsoluteDeviation(mids, median);
		double volatility = logReturnStdDev(mids);
		double emaFast = ema(mids, EMA_FAST_SPAN);
		double emaSlow = ema(mids, EMA_SLOW_SPAN);
		
		int bollingerSpan = Math.min(mids.size(), EMA_SLOW_SPAN);
		List<Double> bollingerSlice = mids.subList(mids.size() - bollingerSpan, mids.size());
		double bollingerMean = mean(bollingerSlice);
		double bollingerStdDev = standardDeviation(bollingerSlice, bollingerMean);
		double bollingerUpper = bollingerMean + 2 * bollingerStdDev;
		double bollingerLower = bollingerMean - 2 * bollingerStdDev;
		// A z-score used to be computed here, at the cost of two more full passes over the series --
		// once for the mean and once for the standard deviation -- on every shortlisted item on every
		// planning pass. Nothing read it: not the scorer, not the filter, and it is absent from the
		// feature vector the learned model is trained on.

		double bucketsPerHour = 3600.0 / Math.max(1, bucketSeconds);
		double hourlyVolume = (volumeSum / series.size()) * bucketsPerHour;

		double meanSpreadPct = spreadPcts.isEmpty() ? 0 : mean(spreadPcts);
		double spreadStability = stability(spreadPcts, meanSpreadPct);
		double emptyFraction = (double) emptyBuckets / series.size();

		double slopePerBucket = linearSlope(mids);
		double slopePerHour = median > 0 ? (slopePerBucket * bucketsPerHour) / median : 0;
		Regime regime = classify(emaFast, emaSlow, volatility, slopePerHour, bollingerUpper, bollingerLower);

		double momentum = predictedMomentums.getOrDefault(itemId, 0.0);

		return new ItemFeatures(itemId, mids.size(), median, mad, volatility, emaFast, emaSlow,
			bollingerUpper, bollingerLower,
			slopePerHour, regime, hourlyVolume, meanSpreadPct, spreadStability, emptyFraction, momentum);
	}

	/**
	 * A trend counts only when the gap between the fast and slow averages is large relative to the
	 * item's own noise. Without that scaling, every volatile item looks like it is always trending.
	 */
	private static Regime classify(double emaFast, double emaSlow, double volatility, double slopePerHour, double bollingerUpper, double bollingerLower)
	{
		if (emaSlow <= 0)
		{
			return Regime.STABLE;
		}
		
		if (bollingerUpper > 0 && emaFast > bollingerUpper)
		{
			return Regime.OVERBOUGHT;
		}
		if (bollingerLower > 0 && emaFast < bollingerLower)
		{
			return Regime.OVERSOLD;
		}

		double separation = (emaFast - emaSlow) / emaSlow;
		double noise = Math.max(volatility, 0.001);
		double signal = separation / noise;

		if (signal > TREND_THRESHOLD && slopePerHour > 0)
		{
			return Regime.RISING;
		}
		if (signal < -TREND_THRESHOLD && slopePerHour < 0)
		{
			return Regime.FALLING;
		}
		return Regime.STABLE;
	}

	static double median(List<Double> values)
	{
		if (values.isEmpty())
		{
			return 0;
		}
		List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int mid = sorted.size() / 2;
		return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
	}

	static double medianAbsoluteDeviation(List<Double> values, double median)
	{
		if (values.isEmpty())
		{
			return 0;
		}
		List<Double> deviations = new ArrayList<>(values.size());
		for (double value : values)
		{
			deviations.add(Math.abs(value - median));
		}
		// 1.4826 rescales MAD so that, for normally distributed data, it estimates the standard
		// deviation. That keeps the risk-profile thresholds interpretable as "sigmas".
		return median(deviations) * 1.4826;
	}

	static double mean(List<Double> values)
	{
		if (values.isEmpty())
		{
			return 0;
		}
		double sum = 0;
		for (double value : values)
		{
			sum += value;
		}
		return sum / values.size();
	}

	static double standardDeviation(List<Double> values, double mean)
	{
		if (values.isEmpty())
		{
			return 0;
		}
		double sumSq = 0;
		for (double value : values)
		{
			sumSq += (value - mean) * (value - mean);
		}
		return Math.sqrt(sumSq / values.size());
	}

	static double stdDev(List<Double> values, double mean)
	{
		if (values.size() < 2)
		{
			return 0;
		}
		double sum = 0;
		for (double value : values)
		{
			double diff = value - mean;
			sum += diff * diff;
		}
		return Math.sqrt(sum / (values.size() - 1));
	}

	/**
	 * Volatility as the standard deviation of log returns, which makes items of wildly different
	 * prices directly comparable: 1% movement is 1% movement whether the item costs 50 gp or 50m.
	 */
	static double logReturnStdDev(List<Double> prices)
	{
		if (prices.size() < 3)
		{
			return 0;
		}
		List<Double> returns = new ArrayList<>(prices.size() - 1);
		for (int i = 1; i < prices.size(); i++)
		{
			double previous = prices.get(i - 1);
			double current = prices.get(i);
			if (previous > 0 && current > 0)
			{
				returns.add(Math.log(current / previous));
			}
		}
		return stdDev(returns, mean(returns));
	}

	static double ema(List<Double> values, int span)
	{
		if (values.isEmpty())
		{
			return 0;
		}
		double alpha = 2.0 / (span + 1.0);
		double ema = values.get(0);
		for (int i = 1; i < values.size(); i++)
		{
			ema = alpha * values.get(i) + (1 - alpha) * ema;
		}
		return ema;
	}

	/** Ordinary least squares slope against bucket index. */
	static double linearSlope(List<Double> values)
	{
		int n = values.size();
		if (n < 2)
		{
			return 0;
		}
		double meanX = (n - 1) / 2.0;
		double meanY = mean(values);
		double numerator = 0;
		double denominator = 0;
		for (int i = 0; i < n; i++)
		{
			double dx = i - meanX;
			numerator += dx * (values.get(i) - meanY);
			denominator += dx * dx;
		}
		return denominator == 0 ? 0 : numerator / denominator;
	}

	/**
	 * Maps the coefficient of variation onto 0..1, where 1 means the value barely moved. Used for
	 * spread consistency, where "usually about this wide" matters more than the exact width.
	 */
	static double stability(List<Double> values, double mean)
	{
		if (values.size() < 2 || mean <= 0)
		{
			return 0;
		}
		double cv = stdDev(values, mean) / mean;
		return 1.0 / (1.0 + cv);
	}
}
