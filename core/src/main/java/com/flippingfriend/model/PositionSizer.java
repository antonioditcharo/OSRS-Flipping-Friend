package com.flippingfriend.model;

import com.flippingfriend.RiskProfile;
import javax.inject.Singleton;

/**
 * Decides how many of an item to buy.
 * <p>
 * Four separate ceilings apply, and the smallest wins:
 * <ol>
 *   <li><b>The buy limit.</b> Hard game rule — going over just leaves an offer stuck.</li>
 *   <li><b>Cash.</b> Including the risk profile's cap on how much of the bank one flip may use.</li>
 *   <li><b>Liquidity.</b> Ordering more than the market trades in the target window means the tail
 *       of the order sits unsold while the price moves, which is how a good flip becomes a bad one.</li>
 *   <li><b>Kelly.</b> The classic bet-sizing result, deliberately fractional.</li>
 * </ol>
 * Full Kelly maximises long-run growth but assumes the probability estimates are correct. Ours are
 * estimates from noisy data, so every profile uses a fraction of it — the standard defence against
 * being confidently wrong.
 */
@Singleton
public class PositionSizer
{
	/** Beyond this the required quantity collapses towards zero for no practical gain. */
	private static final double MAX_LEG_PROBABILITY = 0.95;
	/**
	 * Bankroll at which capital stops being the scarce resource, expressed as a multiple of what a
	 * full-buy-limit position would cost.
	 * <p>
	 * Measured against the live market, capital is the binding constraint on only about 25 of the
	 * 441 liquid items worth trading — for the rest it is the buy limit or the available volume. Below
	 * this threshold the conservative rules below are what keep an account solvent; above it they are
	 * throttling trades that were never going to exhaust the bank, and the throttle costs real gold.
	 */
	private static final double CAPITAL_ABUNDANT_MULTIPLE = 4.0;

	/**
	 * @param buyFillProbability  the model's own answer for "how likely is an order of this size to
	 *                            fill in time", asked rather than reimplemented
	 * @param sellFillProbability chance the sell side completes, which is what makes the flip a win
	 * @return the number of items to buy, possibly zero
	 */
	public int size(RiskProfile profile, long spendableCoins, int buyPrice, int buyLimitRemaining,
		java.util.function.IntToDoubleFunction buyFillProbability, double netMarginPct,
		double sellFillProbability)
	{
		if (buyPrice <= 0 || buyLimitRemaining <= 0 || spendableCoins < buyPrice)
		{
			return 0;
		}

		long fullLimitCost = (long) buyPrice * buyLimitRemaining;
		boolean capitalAbundant = spendableCoins >= fullLimitCost * CAPITAL_ABUNDANT_MULTIPLE;

		long capitalCeiling = (long) (spendableCoins * profile.getMaxCapitalFraction());
		int byCapital = (int) Math.min(Integer.MAX_VALUE, capitalCeiling / buyPrice);

		// Bisect only over what is actually affordable, which bounds the probe count and means the
		// liquidity answer is never larger than the other two ceilings anyway.
		int affordable = Math.min(buyLimitRemaining, byCapital);
		int quantity = liquidityCeiling(profile, buyFillProbability, affordable);

		// Kelly answers "how much of my bank should I risk on this bet?". That is the right question
		// when the bank is the scarce thing and the wrong one when the buy limit is: staking a
		// fraction of a bankroll that the trade could never have exhausted just leaves the limit
		// unused, and an unused limit is throughput that cannot be recovered later.
		if (!capitalAbundant)
		{
			double kellyFraction = kelly(profile, netMarginPct, sellFillProbability);
			long kellyCapital = (long) (spendableCoins * kellyFraction);
			int byKelly = (int) Math.min(Integer.MAX_VALUE, kellyCapital / buyPrice);
			quantity = Math.min(quantity, byKelly);
		}

		return Math.max(0, quantity);
	}

	/**
	 * The largest order that still fills with the confidence the risk profile demands.
	 * <p>
	 * This is derived from the fill model rather than picked, and that matters. Sizing to some
	 * fixed slice of hourly volume and <em>then</em> checking whether the result is likely to fill
	 * sets the two rules against each other: the size implies a fill time, the profile demands a
	 * shorter one, and every candidate gets thrown out.
	 * <p>
	 * It asks the model rather than inverting it. The previous version solved
	 * {@code 1 - e^(-λT/Q) = p} in closed form, which is the inverse of the fill model's
	 * <em>smooth</em> branch — and the shipping model uses the lumpy one, a normal CDF over the
	 * expected number of reachable buckets. So the sizer answered a question the scorer was not
	 * asking, and the scorer then rejected the result on its own rule: exactly the two-rules-against
	 * each-other failure this method exists to prevent. It also used the whole horizon where the
	 * model subtracts the counterparty wait first, so orders came out too large on that count and too
	 * small on the other.
	 * <p>
	 * Bisection over the model's own answer cannot drift from it. Fill probability falls as the
	 * order grows, so the largest acceptable size is a binary search — about thirty probes even for
	 * a buy limit in the millions — and whichever branch the model is configured to use is the
	 * branch that gets inverted.
	 */
	int liquidityCeiling(RiskProfile profile, java.util.function.IntToDoubleFunction fillProbability,
		int upperBound)
	{
		if (upperBound < 1)
		{
			return 0;
		}

		// Both legs have to complete, so each needs the square root of the overall requirement.
		double legProbability = Math.min(MAX_LEG_PROBABILITY,
			Math.sqrt(Math.max(0.01, profile.getMinFillProbability())));

		if (fillProbability.applyAsDouble(1) < legProbability)
		{
			// Not even one unit clears the bar, so there is no order worth placing here.
			return 0;
		}
		if (fillProbability.applyAsDouble(upperBound) >= legProbability)
		{
			return upperBound;
		}

		int low = 1;
		int high = upperBound;
		while (high - low > 1)
		{
			int mid = low + (high - low) / 2;
			if (fillProbability.applyAsDouble(mid) >= legProbability)
			{
				low = mid;
			}
			else
			{
				high = mid;
			}
		}
		return low;
	}

	/**
	 * Fractional Kelly for a flip.
	 * <p>
	 * The bet is: with probability {@code p} the sell completes and we make {@code netMarginPct};
	 * otherwise we cut the position and lose about {@code lossCutPct}. That gives odds
	 * {@code b = margin / loss} and the usual Kelly stake {@code p - (1 - p) / b}, scaled by the
	 * profile's fraction and capped by its per-flip ceiling.
	 */
	double kelly(RiskProfile profile, double netMarginPct, double sellFillProbability)
	{
		double loss = Math.max(profile.getLossCutPct(), 1e-4);
		double odds = netMarginPct / loss;
		if (odds <= 0)
		{
			return 0;
		}

		double p = Math.max(0, Math.min(1, sellFillProbability));
		double full = p - (1 - p) / odds;
		if (full <= 0)
		{
			return 0;
		}

		return Math.min(profile.getMaxCapitalFraction(), full * profile.getKellyFraction());
	}
}
