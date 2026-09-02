package com.flippingfriend.model;

import java.util.Collection;

/**
 * An aggregate metric that calculates the overall percentage of tracked items
 * that are currently in a falling regime versus a rising regime.
 * Used to guard against macro-level crashes (e.g., mass panic selling).
 */
public class MarketFluxIndex
{
	/**
	 * Computes the Market Flux Index.
	 * 
	 * @return a value between -1.0 (extreme bearish) and 1.0 (extreme bullish)
	 */
	public static double compute(Collection<ItemFeatures> activeItems)
	{
		if (activeItems == null || activeItems.isEmpty())
		{
			return 0.0;
		}

		int total = 0;
		int positiveRegimes = 0;
		int negativeRegimes = 0;

		for (ItemFeatures features : activeItems)
		{
			if (!features.isUsable())
			{
				continue;
			}

			total++;
			switch (features.getRegime())
			{
				case OVERBOUGHT: // Overbought can mean momentum is high, but typically mean-reverts.
				case RISING:
					positiveRegimes++;
					break;
				case OVERSOLD:
				case FALLING:
					negativeRegimes++;
					break;
				case STABLE:
					break;
			}
		}

		if (total == 0)
		{
			return 0.0;
		}

		return (positiveRegimes - negativeRegimes) / (double) total;
	}
}
