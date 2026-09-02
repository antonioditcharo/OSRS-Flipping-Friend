package com.flippingfriend.model;

import com.flippingfriend.data.Candle;

/**
 * Detects latency arbitrage opportunities based on instant buy/sell disparities.
 * This looks for scenarios where the immediate buy margin is extremely wide
 * and there is high likelihood of immediate liquidity.
 */
public class ArbitrageEngine
{
	private static final double MIN_ARBITRAGE_SPREAD_PCT = 0.05; // 5% minimum spread for arbitrage
	private static final int MIN_HOURLY_VOLUME = 100; // Need liquidity to actually fill

	public static boolean isArbitrageOpportunity(ItemFeatures features, Candle latestCandle)
	{
		if (!features.isUsable() || latestCandle == null || !latestCandle.hasBothSides())
		{
			return false;
		}

		// Calculate current instant spread
		double currentSpread = latestCandle.getAvgHighPrice() - latestCandle.getAvgLowPrice();
		double spreadPct = currentSpread / (double) latestCandle.getAvgLowPrice();

		// Ensure the spread is wide enough to justify the risk
		if (spreadPct < MIN_ARBITRAGE_SPREAD_PCT)
		{
			return false;
		}

		// Ensure there is sufficient liquidity
		if (features.getHourlyVolume() < MIN_HOURLY_VOLUME)
		{
			return false;
		}

		// Ensure we are in a relatively stable or rising regime (don't catch falling knives)
		if (features.getRegime() == Regime.FALLING || features.getRegime() == Regime.OVERSOLD)
		{
			return false;
		}

		return true;
	}
}
