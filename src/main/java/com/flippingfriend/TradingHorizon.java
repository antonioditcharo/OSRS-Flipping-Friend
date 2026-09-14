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
	 * How long an offer is left alone before repricing it is even considered.
	 * <p>
	 * <b>An offer is slow relative to how long it was expected to take, not to how often you look at
	 * it.</b> The bar used to be {@link #staleOfferMinutes()} alone, which is the checking interval —
	 * so on a fifteen-minute check the plugin began agitating to move the price fifteen minutes in,
	 * on a leg the plan itself had predicted would take two hours. Reported from a live session: a
	 * trade entered for 90k of profit was told, a quarter of an hour after being listed, to drop its
	 * price by nearly 200k. Nothing had gone wrong with it. It had been on the market for an eighth
	 * of the time it was supposed to need.
	 * <p>
	 * Half the predicted time is the point where the estimate is starting to look wrong rather than
	 * merely unfulfilled. Below that there is no information yet, only impatience — and impatience
	 * here is expensive, because the thing it buys is a worse price.
	 *
	 * @param predictedMinutes what this leg was predicted to take, or 0 when that is not known
	 */
	public long patienceMinutes(double predictedMinutes)
	{
		double expected = predictedMinutes > 0 ? predictedMinutes : legHorizonHours() * 60.0;
		return Math.max(staleOfferMinutes(), Math.round(expected * PATIENCE_SHARE));
	}

	/**
	 * The share of a leg's predicted time that has to pass before its price is questioned. Halfway
	 * is when being unfilled stops being consistent with the estimate and starts contradicting it.
	 */
	private static final double PATIENCE_SHARE = 0.5;

	/**
	 * How far the market has to move past an offer before saying so is worth the player's attention.
	 * <p>
	 * Half a percent of the price being tested. The thing being filtered out is noise in the quote,
	 * and quote noise scales with the price: a gp of movement is meaningless on a 400 gp item and
	 * invisible on a 280,000 gp one. Without any deadband the comparison was strict, and a quote
	 * drifting a gp either side of the offer price crossed it every time the feed updated -- measured
	 * on a stranded 400 gp buy with the bid alternating 399 / 401, the plugin alternated between
	 * "reprice your Grapes offer" and "buy Adamant bars" on <em>every single refresh</em>.
	 * <p>
	 * Scaling it to the item's spread was tried first and is wrong in the other direction: on an
	 * Awakener's orb quoted 282,023 / 299,999 that made the bar 2,696 gp, which would have suppressed
	 * the real 1,583 gp gap the player was right to act on. A share of the price puts the bar at 2 gp
	 * on the grapes and 1,402 on the orb, which is the shape the problem actually has.
	 * <p>
	 * It is not free: a gap of, say, 0.4% is now left alone. Against a trade whose whole margin is
	 * six percent that is a fourteenth of the margin, and worth less than being told about it twice a
	 * minute and never being sure which price to type.
	 */
	private static final double OUTBID_DEADBAND_SHARE = 0.005;

	/**
	 * Determines if an offer is genuinely outbid by checking both the volatile spot price and the
	 * more stable 5-minute trend, and by requiring the gap to be big enough to be worth acting on.
	 */
	public boolean isOutbid(boolean isBuying, int currentPrice, com.flippingfriend.data.LatestPrice spot, com.flippingfriend.data.Candle trend)
	{
		if (spot == null || !spot.isComplete())
		{
			return false;
		}

		int deadband = outbidDeadband(currentPrice);
		if (isBuying)
		{
			boolean spotOutbid = spot.getLow() - currentPrice >= deadband;
			boolean trendOutbid = trend == null || trend.getAvgHighPrice() == null || currentPrice < trend.getAvgHighPrice();
			return spotOutbid && trendOutbid;
		}
		else
		{
			boolean spotOutbid = currentPrice - spot.getHigh() >= deadband;
			boolean trendOutbid = trend == null || trend.getAvgLowPrice() == null || currentPrice > trend.getAvgLowPrice();
			return spotOutbid && trendOutbid;
		}
	}

	/**
	 * The gap that counts as the market having moved, at this price. Never less than a gp, so an item
	 * cheap enough that half a percent rounds away is still answerable.
	 */
	public int outbidDeadband(int price)
	{
		return Math.max(1, (int) Math.round(Math.max(0, price) * OUTBID_DEADBAND_SHARE));
	}

	/** Plain-English summary for the panel. */
	public String describe()
	{
		return profile.getDisplayName() + " risk, " + interval.getDisplayName().toLowerCase();
	}
}
