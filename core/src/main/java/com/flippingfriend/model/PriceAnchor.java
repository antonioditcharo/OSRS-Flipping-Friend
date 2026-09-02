package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;

/**
 * Reconciles the single most recent trade against the five-minute volume-weighted average.
 * <p>
 * {@code /latest} is the freshest signal available and normally the right one to price against —
 * but it is exactly one transaction, so one person buying a single item at a silly price moves it.
 * The five-minute average is slower but is weighted across everything that traded, so a lone
 * outlier barely registers.
 * <p>
 * Taking either one alone is wrong in a different way: trust the spot price and you occasionally
 * price an offer off somebody's misclick; trust the average and you are always a few minutes behind
 * a market that moves. So the spot price is used as-is while it agrees with the average, and
 * replaced by the average when it does not.
 * <p>
 * This complements the manipulation filter rather than duplicating it. The filter throws away items
 * whose whole price picture looks wrong; this keeps good items but stops one bad tick setting the
 * price we would actually offer.
 */
public final class PriceAnchor
{
	/** How far the single latest trade may sit from the five-minute average before it is distrusted. */
	private static final double MAX_DEVIATION = 0.03;

	private PriceAnchor()
	{
	}

	/**
	 * The price to actually offer against: the spot price where it agrees with the average, and the
	 * average where it does not.
	 */
	public static LatestPrice anchor(LatestPrice latest, Candle fiveMinute)
	{
		if (latest == null || !latest.isComplete() || fiveMinute == null)
		{
			return latest;
		}

		Integer high = reconcile(latest.getHigh(), fiveMinute.getAvgHighPrice());
		Integer low = reconcile(latest.getLow(), fiveMinute.getAvgLowPrice());
		if (high.equals(latest.getHigh()) && low.equals(latest.getLow()))
		{
			return latest;
		}
		return new LatestPrice(high, latest.getHighTime(), low, latest.getLowTime());
	}

	/** Keeps the spot price while it is within tolerance of the average, otherwise takes the average. */
	private static Integer reconcile(Integer spot, Integer average)
	{
		if (spot == null || spot <= 0)
		{
			return spot;
		}
		if (average == null || average <= 0)
		{
			return spot;
		}
		double deviation = Math.abs(spot - average) / (double) average;
		return deviation > MAX_DEVIATION ? average : spot;
	}

	// wasAdjusted(LatestPrice, LatestPrice) lived here. It had no callers, and it compared the two by
	// reference identity -- which would have surprised anyone who did start using it.
}
