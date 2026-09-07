package com.flippingfriend;

/**
 * Combines the risk profile's patience with how often the player actually looks at the Grand
 * Exchange, and produces the timings the rest of the model works in.
 * <p>
 * Keeping this in one place matters because the two settings interact, and the interaction is not
 * obvious. A "fast flips" risk profile and an "every few hours" checking habit do not cancel out —
 * the honest answer is that offers must be sized to fill within a checking cycle, whatever the
 * profile would prefer in isolation. Scattering that reasoning across the scorer, the sell logic
 * and the stale-offer check would guarantee they eventually disagreed.
 */
public class TradingHorizon
{
	/** A round trip needs two visits: one to place the buy, one to place the sell. */
	private static final int VISITS_PER_FLIP = 2;
	/** Aim to be comfortably finished within a cycle rather than exactly on the buzzer. */
	private static final double COMFORT_FACTOR = 0.8;

	private final RiskProfile profile;
	private final CheckInterval interval;
	/** How long the player wants a flip to take, or 0 to take the profile's word for it. */
	private final int targetHoldMinutes;

	private TradingHorizon(RiskProfile profile, CheckInterval interval, int targetHoldMinutes)
	{
		this.profile = profile;
		this.interval = interval;
		this.targetHoldMinutes = targetHoldMinutes;
	}

	public static TradingHorizon of(RiskProfile profile, CheckInterval interval)
	{
		return of(profile, interval, 0);
	}

	/**
	 * How long a flip should take is a separate question from how often you look.
	 * <p>
	 * These were one setting, and the conflation was invisible until the forecast needed a horizon:
	 * asking "what price does this reach before I want to be out" is not the same as asking "when will
	 * I next be at the exchange", and deriving the first from the second made slow flips impossible for
	 * anyone standing at the Grand Exchange. They are now separate, and the checking habit keeps only
	 * the job it was always right about -- when an unfilled offer is worth revisiting, and the floor
	 * below which a round trip cannot physically fit.
	 *
	 * @param targetHoldMinutes how long a flip should take, or 0 to fall back to the risk profile
	 */
	public static TradingHorizon of(RiskProfile profile, CheckInterval interval,
		int targetHoldMinutes)
	{
		return new TradingHorizon(
			profile == null ? RiskProfile.MODERATE : profile,
			interval == null ? CheckInterval.FIFTEEN_MINUTES : interval,
			Math.max(0, targetHoldMinutes));
	}

	/** The patience budget: what was asked for, or the profile's own if nothing was. */
	private int wantedHoldMinutes()
	{
		return targetHoldMinutes > 0 ? targetHoldMinutes : profile.getMaxHoldMinutes();
	}

	public RiskProfile getProfile()
	{
		return profile;
	}

	public CheckInterval getInterval()
	{
		return interval;
	}

	/**
	 * Longest acceptable round trip. Never shorter than two checking cycles, because a flip cannot
	 * physically complete faster than the player can come back and place the second offer.
	 */
	public int maxHoldMinutes()
	{
		return Math.max(wantedHoldMinutes(), interval.getMinutes() * VISITS_PER_FLIP);
	}

	/**
	 * How long one leg has to fill, in hours. This is what sizes the order: a longer window means a
	 * bigger order can still be expected to complete, which is exactly why someone checking hourly
	 * should be given chunkier trades than someone stood at the Exchange.
	 */
	public double legHorizonHours()
	{
		double fromWanted = wantedHoldMinutes() / (double) VISITS_PER_FLIP;
		double fromHabit = interval.getMinutes() * COMFORT_FACTOR;
		return Math.max(fromWanted, fromHabit) / 60.0;
	}

	/**
	 * How long an unfilled offer may sit before it is worth re-pricing. Always at least one full
	 * checking cycle: telling someone to fix an offer they will not see for another hour is noise,
	 * and by the time they look the advice is stale anyway.
	 */
	public long staleOfferMinutes()
	{
		return Math.max(8, interval.getMinutes());
	}

	/**
	 * Determines if an offer is genuinely outbid by checking both the volatile spot price
	 * and the more stable 5-minute trend, reducing noisy re-price alerts.
	 */
	public boolean isOutbid(boolean isBuying, int currentPrice, com.flippingfriend.data.LatestPrice spot, com.flippingfriend.data.Candle trend)
	{
		if (spot == null || !spot.isComplete())
		{
			return false;
		}

		if (isBuying)
		{
			boolean spotOutbid = currentPrice < spot.getLow();
			boolean trendOutbid = trend == null || trend.getAvgLowPrice() == null || currentPrice < trend.getAvgLowPrice();
			return spotOutbid && trendOutbid;
		}
		else
		{
			boolean spotOutbid = currentPrice > spot.getHigh();
			boolean trendOutbid = trend == null || trend.getAvgHighPrice() == null || currentPrice > trend.getAvgHighPrice();
			return spotOutbid && trendOutbid;
		}
	}

	/** Plain-English summary for the panel. */
	public String describe()
	{
		return profile.getDisplayName() + " risk, " + interval.getDisplayName().toLowerCase();
	}
}
