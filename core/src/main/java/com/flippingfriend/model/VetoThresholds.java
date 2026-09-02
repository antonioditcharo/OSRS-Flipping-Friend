package com.flippingfriend.model;

/**
 * The bars an item must clear before it is allowed into a portfolio at all.
 * <p>
 * These used to live on the risk profile, which tied the safety checks to a concept the portfolio
 * optimizer is replacing. Separating them means the vetoes are about whether a trade is <em>real</em>
 * — is this spread genuine, has this item just repriced, is anyone actually trading it — while the
 * optimizer decides whether a real trade is <em>worth taking</em>.
 * <p>
 * <b>Which bars are worth having, measured rather than assumed.</b> The background learner
 * paper-trades the items these vetoes reject and records what would have happened, so the cost of
 * each veto is a number rather than an opinion. Counting only trades affordable on a hundred million
 * and charging a stuck position 2% to unwind, over about 1,500 rejected trades:
 * <pre>
 *   sell price outside normal range      +7.15M   too strict
 *   buy price outside normal range       +0.81M   too strict
 *   no price/size cleared the bars       +0.71M   too strict
 *   near fortnight high                  +0.51M   too strict
 *   too volatile / structural break      +0.40M   too strict
 *   too illiquid                         -0.96M   earning its keep
 *   stale side of the book              -16.31M   earning its keep, by a mile
 * </pre>
 * So the price-shaped bars were set far too tight and the liquidity and staleness ones were not.
 * The presets below loosen the former across the board and leave the latter alone at every risk
 * level — an illiquid item or a one-sided book is not a bolder trade, it is a worse one, and no
 * amount of appetite for risk makes a price nobody is trading at into a real price.
 */
public final class VetoThresholds
{
	/** Careful about what counts as a real trade, without being timid about the price. */
	public static final VetoThresholds CAUTIOUS =
		new VetoThresholds(0.3, 0.3, 4.5, false, 3.5, 0.95);

	/** The default. */
	public static final VetoThresholds BALANCED =
		new VetoThresholds(0.3, 0.4, 5.5, false, 4.5, 0.98);

	/** Bold on price, and still unwilling to touch something nobody is trading. */
	public static final VetoThresholds AGGRESSIVE =
		new VetoThresholds(0.3, 0.6, 7.0, true, 6.0, 1.0);

	// forRisk(String) lived here: a verbatim second copy of the risk-name switch in
	// RiskAppetite.forName, which is the one the planner actually calls. Two copies of one mapping
	// means editing the wrong one changes nothing and says nothing, which is the failure mode this
	// project has hit repeatedly. The live copy is the only copy now.
	//
	// A PORTFOLIO alias for BALANCED lived here too, kept "so existing callers keep compiling". There
	// were none.

	private final double minVolumeToLimitRatio;
	private final double maxVolatility;
	private final double madRejectionK;
	private final boolean allowTrendPlays;
	private final double maxVolatilityRatio;
	private final double maxPricePercentile;

	public VetoThresholds(double minVolumeToLimitRatio, double maxVolatility, double madRejectionK,
		boolean allowTrendPlays, double maxVolatilityRatio, double maxPricePercentile)
	{
		this.minVolumeToLimitRatio = minVolumeToLimitRatio;
		this.maxVolatility = maxVolatility;
		this.madRejectionK = madRejectionK;
		this.allowTrendPlays = allowTrendPlays;
		this.maxVolatilityRatio = maxVolatilityRatio;
		this.maxPricePercentile = maxPricePercentile;
	}

	public double getMinVolumeToLimitRatio()
	{
		return minVolumeToLimitRatio;
	}

	public double getMaxVolatility()
	{
		return maxVolatility;
	}

	public double getMadRejectionK()
	{
		return madRejectionK;
	}

	public boolean isAllowTrendPlays()
	{
		return allowTrendPlays;
	}

	public double getMaxVolatilityRatio()
	{
		return maxVolatilityRatio;
	}

	public double getMaxPricePercentile()
	{
		return maxPricePercentile;
	}
}
