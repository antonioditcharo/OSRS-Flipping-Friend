package com.flippingfriend;

/**
 * Tuning bundles for the three risk levels.
 * <p>
 * <b>Why the horizons are longer than they look like they should be.</b> Profit per slot-hour is
 * margin times order size divided by time. Order size scales linearly with the fill horizon, while
 * the certainty demanded of that fill only costs a logarithmic factor — so a longer horizon buys
 * proportionally more throughput than it costs in slot-time. On a bankroll large enough that capital
 * is not the binding constraint, patience is therefore worth more than speed, and the backtest at
 * 100M bears that out sharply.
 * <p>
 * Every threshold the engine uses lives here rather than being scattered as magic numbers, so the
 * backtester can sweep them and so a change of risk level genuinely changes behaviour everywhere at
 * once. The starting values were chosen to be defensible from first principles and are refined by
 * {@code com.flippingfriend.backtest.Backtester}.
 */
public enum RiskProfile
{
	// The volume gate is expressed against the buy limit because that is the natural scale for an
	// item's turnover. It is not a demand that the whole limit trade many times over: order sizes
	// are a fraction of the limit, and the position sizer already caps them at what the market
	// actually supplies. An earlier, much stricter setting here was rejecting literally every item
	// on the low profile, so the thresholds below are the ones the backtester shows to trade.
	LOW(
		"Low",
		"Safest. Only busy, stable items with small but very reliable margins.",
		/* minVolumeToLimitRatio  */ 2.0,
		/* minNetMarginPct        */ 0.010,
		/* maxHoldMinutes         */ 90,
		/* maxCapitalFraction     */ 0.25,
		/* kellyFraction          */ 0.15,
		/* madRejectionK          */ 2.5,
		/* allowTrendPlays        */ false,
		/* lossCutPct             */ 0.02,
		/* minFillProbability     */ 0.60,
		/* maxVolatility          */ 0.03,
		/* maxVolatilityRatio     */ 1.5,
		/* maxPricePercentile     */ 0.75),

	MODERATE(
		"Moderate",
		"Balanced. A good mix of margin and speed. Recommended if you are unsure.",
		0.6,
		0.015,
		360,
		0.35,
		0.30,
		3.0,
		false,
		0.05,
		0.35,
		0.08,
		2.0,
		0.85),

	HIGH(
		"High",
		"Aggressive. Bigger margins on thinner items, with longer holds and real losing trades.",
		0.3,
		0.040,
		480,
		0.60,
		0.50,
		4.5,
		true,
		0.12,
		0.30,
		0.20,
		3.0,
		0.95);

	private final String displayName;
	private final String description;

	/** Required ratio of 1-hour traded volume to the item's 4-hour buy limit. */
	private final double minVolumeToLimitRatio;
	/** Minimum margin, as a fraction of buy price, after Grand Exchange tax. */
	private final double minNetMarginPct;
	/** Longest expected round trip we are willing to suggest. */
	private final int maxHoldMinutes;
	/** Hard ceiling on the share of available cash a single flip may consume. */
	private final double maxCapitalFraction;
	/** Fraction of the full Kelly stake to actually use. Full Kelly is far too swingy in practice. */
	private final double kellyFraction;
	/** How many median-absolute-deviations a price may sit from the median before we call it manipulated. */
	private final double madRejectionK;
	/** Whether momentum/trend entries are allowed, or only mean-reversion. */
	private final boolean allowTrendPlays;
	/** Drop from entry price at which we recommend cutting the position. */
	private final double lossCutPct;
	/** Minimum modelled probability that both sides of the flip fill. */
	private final double minFillProbability;
	/** Maximum tolerated realised volatility of the item. */
	private final double maxVolatility;

	/**
	 * How much jumpier than its own fortnight norm an item may currently be. Catches items that
	 * look calm over a day only because the storm started yesterday.
	 */
	private final double maxVolatilityRatio;
	/**
	 * How near the top of its fortnight range an item may be bought. Near the high there is more
	 * room to fall than to rise, whatever the immediate spread suggests.
	 */
	private final double maxPricePercentile;

	RiskProfile(String displayName, String description, double minVolumeToLimitRatio, double minNetMarginPct,
		int maxHoldMinutes, double maxCapitalFraction, double kellyFraction, double madRejectionK,
		boolean allowTrendPlays, double lossCutPct, double minFillProbability, double maxVolatility,
		double maxVolatilityRatio, double maxPricePercentile)
	{
		this.maxVolatilityRatio = maxVolatilityRatio;
		this.maxPricePercentile = maxPricePercentile;
		this.displayName = displayName;
		this.description = description;
		this.minVolumeToLimitRatio = minVolumeToLimitRatio;
		this.minNetMarginPct = minNetMarginPct;
		this.maxHoldMinutes = maxHoldMinutes;
		this.maxCapitalFraction = maxCapitalFraction;
		this.kellyFraction = kellyFraction;
		this.madRejectionK = madRejectionK;
		this.allowTrendPlays = allowTrendPlays;
		this.lossCutPct = lossCutPct;
		this.minFillProbability = minFillProbability;
		this.maxVolatility = maxVolatility;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getDescription()
	{
		return description;
	}

	public double getMinVolumeToLimitRatio()
	{
		return minVolumeToLimitRatio;
	}

	public double getMinNetMarginPct()
	{
		return minNetMarginPct;
	}

	public int getMaxHoldMinutes()
	{
		return maxHoldMinutes;
	}

	public double getMaxCapitalFraction()
	{
		return maxCapitalFraction;
	}

	public double getKellyFraction()
	{
		return kellyFraction;
	}

	public double getMadRejectionK()
	{
		return madRejectionK;
	}

	public boolean isAllowTrendPlays()
	{
		return allowTrendPlays;
	}

	public double getLossCutPct()
	{
		return lossCutPct;
	}

	public double getMinFillProbability()
	{
		return minFillProbability;
	}

	public double getMaxVolatility()
	{
		return maxVolatility;
	}

	public double getMaxVolatilityRatio()
	{
		return maxVolatilityRatio;
	}

	public double getMaxPricePercentile()
	{
		return maxPricePercentile;
	}

	/** The veto bars for this profile, for the legacy fallback path. */
	public com.flippingfriend.model.VetoThresholds vetoThresholds()
	{
		return new com.flippingfriend.model.VetoThresholds(minVolumeToLimitRatio, maxVolatility,
			madRejectionK, allowTrendPlays, maxVolatilityRatio, maxPricePercentile);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
