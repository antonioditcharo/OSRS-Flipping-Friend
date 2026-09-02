package com.flippingfriend.model;

import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import javax.inject.Singleton;

/**
 * Throws out the items that look profitable but are not.
 * <p>
 * Almost every eye-catching margin on the Grand Exchange is fake. Someone lists a single item at a
 * silly price to bait a tool exactly like this one; an item stops trading and its last quotes drift
 * apart; a price collapses and the stale high looks like a spread. This class is the difference
 * between a suggestion list you can trade and one that quietly bleeds gold, so it is deliberately
 * quick to say no.
 */
@Singleton
public class ManipulationFilter
{
	/** Nothing legitimate on the Grand Exchange sustains a spread this wide. */
	private static final double IMPLAUSIBLE_SPREAD_PCT = 0.40;
	/** A side of the book older than this has stopped being a price and become a memory. */
	private static final long BASE_STALENESS_SECONDS = 30 * 60;
	private static final long MAX_STALENESS_SECONDS = 4 * 60 * 60;
	/** Above this share of dead buckets the item is not really traded. */
	private static final double MAX_EMPTY_BUCKET_FRACTION = 0.5;
	/** A spread that has only just appeared is usually one person, not a market. */
	private static final double MIN_SPREAD_STABILITY = 0.25;

	public FilterResult screen(ItemMetadata metadata, LatestPrice price, ItemFeatures features,
		VetoThresholds thresholds, boolean canBuyMembersItems, Instant now)
	{
		return screen(metadata, price, features, MarketContext.unknown(), thresholds, canBuyMembersItems, now);
	}

	/**
	 * @param context the fortnight view, which catches problems a single day of data cannot show
	 */
	public FilterResult screen(ItemMetadata metadata, LatestPrice price, ItemFeatures features,
		MarketContext context, VetoThresholds thresholds, boolean canBuyMembersItems, Instant now)
	{
		if (metadata == null)
		{
			return FilterResult.rejected("No item data available yet.");
		}

		if (!canBuyMembersItems && metadata.isMembers())
		{
			return FilterResult.rejected("Members item, which a free-to-play account cannot buy.");
		}

		if (price == null || !price.isComplete())
		{
			return FilterResult.rejected("No recent buy and sell prices.");
		}

		int buyAt = price.getLow();
		int sellAt = price.getHigh();

		if (sellAt <= buyAt)
		{
			return FilterResult.rejected("There is no gap between the buy and sell price.");
		}

		double spreadPct = (double) (sellAt - buyAt) / buyAt;
		if (spreadPct > IMPLAUSIBLE_SPREAD_PCT)
		{
			return FilterResult.rejected("The price gap is too large to be real. Usually someone "
				+ "trying to bait automated tools.");
		}

		// Staleness is scaled by how busy the item normally is. Half an hour of silence means
		// nothing for a rare, and everything for cannonballs.
		long staleness = price.stalestSideSeconds(now);
		long allowedStaleness = allowedStaleness(features);
		if (staleness > allowedStaleness)
		{
			return FilterResult.rejected("One side of this item has not traded in "
				+ (staleness / 60) + " minutes, so the price gap is not trustworthy.");
		}

		if (!features.isUsable())
		{
			return FilterResult.rejected("Not enough price history yet.");
		}

		if (features.getEmptyBucketFraction() > MAX_EMPTY_BUCKET_FRACTION)
		{
			return FilterResult.rejected("This item barely trades.");
		}

		// The core manipulation check: is a current quote far outside where this item normally
		// sits, measured in a way a single outlier cannot skew?
		double k = thresholds.getMadRejectionK();
		if (features.madDistance(buyAt) > k)
		{
			return FilterResult.rejected("The current buy price is far outside this item's normal range.");
		}
		if (features.madDistance(sellAt) > k)
		{
			return FilterResult.rejected("The current sell price is far outside this item's normal range.");
		}

		if (features.getVolatility() > thresholds.getMaxVolatility())
		{
			return FilterResult.rejected("This item's price moves too much for your risk level.");
		}

		if (features.getSpreadStability() < MIN_SPREAD_STABILITY)
		{
			return FilterResult.rejected("The price gap on this item keeps appearing and disappearing.");
		}

		double volumeRatio = features.getHourlyVolume() / Math.max(1, metadata.getBuyLimit());
		if (volumeRatio < thresholds.getMinVolumeToLimitRatio())
		{
			return FilterResult.rejected("Not enough of this item trades each hour for your risk level.");
		}

		if (features.getRegime() == Regime.FALLING && !thresholds.isAllowTrendPlays())
		{
			return FilterResult.rejected("The price is falling, so buying now risks being left holding it.");
		}

		if (context.isUsable())
		{
			// A recent shift in price level means a game update or a crash has changed what this
			// item is worth. Everything the model knows about it describes the old market.
			if (context.hasStructuralBreak())
			{
				return FilterResult.rejected("This item's price has just moved to a new level, so its "
					+ "past behaviour is no longer a guide.");
			}

			// Currently far jumpier than it normally is: something is happening that the last day of
			// data cannot explain.
			if (context.volatilityRatio(features.getVolatility()) > thresholds.getMaxVolatilityRatio())
			{
				return FilterResult.rejected("This item is moving far more than it usually does.");
			}

			if (context.getPricePercentile() > thresholds.getMaxPricePercentile())
			{
				return FilterResult.rejected("This is near the most expensive it has been in a "
					+ "fortnight, so there is more room to fall than to rise.");
			}
		}

		return FilterResult.accepted();
	}

	/**
	 * Every item gets at least the base allowance; quiet ones get proportionally more rope, up to a
	 * hard ceiling past which nothing is trustworthy.
	 * <p>
	 * This used to claim that busy items should be quote-fresh within minutes, and carried a
	 * five-minute floor to that effect. The floor was unreachable — the base allowance below it is
	 * thirty minutes — and that is just as well, because the rule it was expressing does not
	 * survive contact with the numbers: twenty trades' worth of time at 10,000 trades an hour is
	 * seven seconds, and a seven-second freshness bar rejects essentially every liquid item on the
	 * Grand Exchange. Only the "quiet items get more rope" half was ever doing anything, and only
	 * that half is worth keeping.
	 */
	private static long allowedStaleness(ItemFeatures features)
	{
		double hourlyVolume = features.getHourlyVolume();
		if (hourlyVolume <= 0)
		{
			return BASE_STALENESS_SECONDS;
		}
		// Roughly: allow the time in which we would expect ~20 trades to have happened.
		long scaled = (long) (3600.0 * 20.0 / hourlyVolume);
		return Math.min(MAX_STALENESS_SECONDS, Math.max(BASE_STALENESS_SECONDS, scaled));
	}
}
