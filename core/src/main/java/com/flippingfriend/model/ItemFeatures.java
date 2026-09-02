package com.flippingfriend.model;

/**
 * Everything the scorer needs to know about how an item has been behaving, derived once per item
 * per pass so the same numbers are reused by the filter, the fill model and the exit logic.
 */
public class ItemFeatures
{
	/** Minimum spread scale, as a fraction of price, so a flat item still has a usable yardstick. */
	private static final double MIN_RELATIVE_SCALE = 0.001;

	private final int itemId;
	private final int sampleCount;
	private final double medianMid;
	/** Median absolute deviation: a spread measure that a single outlier cannot inflate. */
	private final double mad;
	private final double volatility;
	private final double emaFast;
	private final double emaSlow;
	private final double bollingerUpper;
	private final double bollingerLower;
	private final double trendSlopePerHour;
	private final Regime regime;
	private final double hourlyVolume;
	private final double meanSpreadPct;
	private final double spreadStability;
	private final double emptyBucketFraction;
	private final double predictedMomentum;

	ItemFeatures(int itemId, int sampleCount, double medianMid, double mad, double volatility, double emaFast,
		double emaSlow, double bollingerUpper, double bollingerLower, double trendSlopePerHour, Regime regime, double hourlyVolume,
		double meanSpreadPct, double spreadStability, double emptyBucketFraction, double predictedMomentum)
	{
		this.itemId = itemId;
		this.sampleCount = sampleCount;
		this.medianMid = medianMid;
		this.mad = mad;
		this.volatility = volatility;
		this.emaFast = emaFast;
		this.emaSlow = emaSlow;
		this.bollingerUpper = bollingerUpper;
		this.bollingerLower = bollingerLower;
		this.trendSlopePerHour = trendSlopePerHour;
		this.regime = regime;
		this.hourlyVolume = hourlyVolume;
		this.meanSpreadPct = meanSpreadPct;
		this.spreadStability = spreadStability;
		this.emptyBucketFraction = emptyBucketFraction;
		this.predictedMomentum = predictedMomentum;
	}

	/** A featureless placeholder for items we have no history for yet. */
	public static ItemFeatures unknown(int itemId)
	{
		return new ItemFeatures(itemId, 0, 0, 0, 0, 0, 0, 0, 0, 0, Regime.STABLE, 0, 0, 0, 1, 0);
	}

	public boolean isUsable()
	{
		return sampleCount >= 12 && medianMid > 0;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getSampleCount()
	{
		return sampleCount;
	}

	public double getMedianMid()
	{
		return medianMid;
	}

	public double getMad()
	{
		return mad;
	}

	/** Standard deviation of log returns between buckets. Roughly "how jumpy is this item". */
	public double getVolatility()
	{
		return volatility;
	}

	public double getEmaFast()
	{
		return emaFast;
	}

	public double getEmaSlow()
	{
		return emaSlow;
	}

	public double getBollingerUpper()
	{
		return bollingerUpper;
	}

	public double getBollingerLower()
	{
		return bollingerLower;
	}

	public double getTrendSlopePerHour()
	{
		return trendSlopePerHour;
	}

	public Regime getRegime()
	{
		return regime;
	}

	/** Items traded per hour, averaged across the window and counting both sides of the book. */
	public double getHourlyVolume()
	{
		return hourlyVolume;
	}

	public double getMeanSpreadPct()
	{
		return meanSpreadPct;
	}

	/**
	 * How consistent the spread has been, from 0 (erratic) to 1 (rock steady). A spread that is
	 * usually there is one you can plan around; a spread that only appeared in the last bucket is not.
	 */
	public double getSpreadStability()
	{
		return spreadStability;
	}

	/** Share of buckets in which nothing traded at all. High means the item is effectively dead. */
	public double getEmptyBucketFraction()
	{
		return emptyBucketFraction;
	}

	/** LSTM model's predicted percent change for this item's price. */
	public double getPredictedMomentum()
	{
		return predictedMomentum;
	}

	/**
	 * Distance of a price from the robust centre, measured in MADs. Used to spot manipulated quotes.
	 * <p>
	 * The scale is floored at a small fraction of the price. Without that floor, a perfectly steady
	 * item has a MAD of exactly zero and every deviation divides to nothing — which would leave the
	 * manipulation check silently disabled on precisely the stable, heavily traded items the
	 * low-risk profile prefers.
	 */
	public double madDistance(double price)
	{
		double scale = Math.max(mad, medianMid * MIN_RELATIVE_SCALE);
		if (scale <= 0)
		{
			return 0;
		}
		return Math.abs(price - medianMid) / scale;
	}
}
