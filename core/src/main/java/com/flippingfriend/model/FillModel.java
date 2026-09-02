package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.List;
import javax.inject.Singleton;

/**
 * Estimates whether an offer at a given price will actually complete.
 * <p>
 * The model is deliberately simple and honest about what the data can support. We can see, per
 * five-minute bucket, roughly where trades happened and how many. We cannot see the order book, so
 * we cannot know our place in the queue. What we can do is ask two answerable questions:
 * <ol>
 *   <li>How often does the market actually reach our price? (fraction of buckets that traded
 *       through it)</li>
 *   <li>When it does, how much volume goes through? (mean volume in those buckets)</li>
 * </ol>
 * Those two combine into the volume that reached our price over the window as a whole, which is
 * then scaled down for the other players competing at the same price to give a defensible
 * units-per-hour figure. Fill time then falls out of that, and fill probability is
 * modelled as a Poisson arrival process, which puts completion at about 63% when the expected flow
 * over the horizon exactly equals the order size — appropriately short of certainty.
 */
@Singleton
public class FillModel
{
	/**
	 * Share of the flow at our price level we assume we capture. We are one of several buyers at
	 * any popular price, and assuming otherwise is how a model ends up promising two-minute fills
	 * that take an hour.
	 */
	private static final double DEFAULT_CAPTURE_RATE = 0.35;

	/**
	 * Share of the flow at our price we assume we capture.
	 * <p>
	 * Now set by the risk appetite rather than fixed, because it is the single biggest lever on how
	 * much capital gets deployed: buy limits allow tens of millions per item, and what actually caps
	 * an order is how much of the item's trading we are willing to claim. Raising it puts more gold
	 * to work and makes each fill less certain, which is a trade worth making deliberately.
	 */
	private final double captureRate;
	/** Never claim an instant fill; even the busiest item needs the offer to be seen. */
	private static final double MIN_HOURS = 1.0 / 60.0;

	/**
	 * How hard to discount a throughput estimate for resting on few observations, in standard errors.
	 * <p>
	 * <b>Off by default, because it was measured and it did not work.</b> The reasoning for it was
	 * sound as far as it went: the optimizer ranks candidates on throughput, and ranking on a noisy
	 * quantity selects for its errors.
	 * <p>
	 * Run against its own absence on identical folds, a one-standard-error discount improved
	 * calibration exactly as intended (2.57x down to 2.25x) and made the strategy <em>less</em>
	 * profitable, by 5.6%. The likely reason is that throughput feeds order sizing as well as
	 * ranking, so discounting it shrinks every order rather than only reordering the choices — the
	 * experiment cannot separate the two, and the sizing effect appears to dominate. A clean test of
	 * the original hypothesis would discount the figure used for ranking while sizing from the
	 * undiscounted one.
	 * <p>
	 * It also turned out to be treating a symptom. The duration error was not mainly noise, it was a
	 * missing counterparty wait — see {@link #waitWeight}, which fixed calibration outright and
	 * removed most of the reason to shrink anything. Kept configurable rather than deleted so the
	 * clean version of the test can still be run, and so this result is not rediscovered from
	 * scratch.
	 */
	private final double evidenceWeight;

	/**
	 * How much of the counterparty wait to charge, as a multiple of the estimated first-fill delay.
	 * One charges it in full, zero disables it.
	 * <p>
	 * <b>On by default, because it was measured and it worked.</b> Against its own absence on
	 * identical folds it moved slot-time calibration from 2.20x optimistic to 1.04x and raised profit
	 * by 32%, taking the optimizer from losing every fold against a plain highest-profit rule to
	 * winning. Unlike the other adjustments tried here, this one was large enough to see clearly
	 * above the noise, and timing calibration has held near 1.1x on every run since.
	 */
	private final double waitWeight;

	/**
	 * Whether completion accounts for volume arriving in lumps rather than as a smooth stream.
	 * <p>
	 * <b>On by default on grounds of construction, not measured benefit.</b> The smooth formula
	 * cannot represent the difference between a horizon containing three reachable buckets and one
	 * containing thirty with the same total volume; this one can, and those are genuinely different
	 * risks. That is the reason it is here.
	 * <p>
	 * It is <em>not</em> here because it was shown to make more money. Run against the smooth formula
	 * on three separate windows it came out +14.5%, +0.8% and -9.4% — mean +2%, with a standard error
	 * of about 7%. The first of those was reported as a win before the other two existed, which was
	 * premature. Treat the profit effect as unmeasured.
	 * <p>
	 * The reason it cannot be settled is structural: five-minute history reaches back about
	 * thirty-one hours, which yields three seven-hour folds, and per-fold results within a single run
	 * range from 0.53x to 5.53x. The noise floor is far above the size of the effect. Changes of this
	 * magnitude are not measurable with the data that exists, and tuning against this harness past
	 * this point is fitting to noise.
	 * <p>
	 * False restores the smooth formula so the comparison can be repeated.
	 */
	private final boolean lumpyArrivals;

	/** Floor on reachability, so an almost-never-reached price yields a long wait, not an infinite one. */
	private static final double MIN_REACH_RATE = 0.01;

	public FillModel()
	{
		this(0.0, 1.0);
	}

	public FillModel(double evidenceWeight)
	{
		this(evidenceWeight, 1.0);
	}

	public FillModel(double evidenceWeight, double waitWeight)
	{
		this(evidenceWeight, waitWeight, true);
	}

	public FillModel(double evidenceWeight, double waitWeight, boolean lumpyArrivals)
	{
		this(evidenceWeight, waitWeight, lumpyArrivals, DEFAULT_CAPTURE_RATE);
	}

	public FillModel(double evidenceWeight, double waitWeight, boolean lumpyArrivals,
		double captureRate)
	{
		this.evidenceWeight = Math.max(0, evidenceWeight);
		this.waitWeight = Math.max(0, waitWeight);
		this.lumpyArrivals = lumpyArrivals;
		this.captureRate = Math.max(0.01, Math.min(1.0, captureRate));
	}

	/**
	 * The same model with a different capture share, keeping every other setting.
	 * <p>
	 * Rebuilding from scratch instead would silently discard whatever the caller had configured —
	 * which is exactly what happened when the risk appetite began rebuilding this model: an
	 * experiment that set {@code lumpyArrivals} to false had it quietly reset to true, and both arms
	 * of the comparison ran the identical configuration while appearing to differ.
	 */
	public FillModel withCaptureRate(double newCaptureRate)
	{
		return new FillModel(evidenceWeight, waitWeight, lumpyArrivals, newCaptureRate);
	}

	/**
	 * A buy offer completes when somebody sells into it, which the feed records as an instant-sell
	 * at {@code avgLowPrice}. So we care about buckets whose low price came down to ours or below.
	 */
	public FillEstimate estimateBuy(FillCurve curve, int price, int quantity, double horizonHours)
	{
		return estimateBuy(curve, price, quantity, horizonHours, 1.0);
	}

	/**
	 * @param seasonalMultiplier how busy this item is at the current hour relative to its own daily
	 *                           average, from {@link MarketContext}
	 */
	public FillEstimate estimateBuy(FillCurve curve, int price, int quantity, double horizonHours,
		double seasonalMultiplier)
	{
		if (curve == null || price <= 0 || quantity <= 0)
		{
			return FillEstimate.never();
		}
		return finish(curve.getWindowSeconds(), curve.buyScoredBuckets(), curve.buyReachableBuckets(price),
			curve.buyVolumeAtOrBelow(price), quantity, horizonHours, seasonalMultiplier);
	}

	/**
	 * A sell offer completes when somebody buys from it, recorded as an instant-buy at
	 * {@code avgHighPrice}. So we care about buckets whose high price reached ours or above.
	 */
	public FillEstimate estimateSell(FillCurve curve, int price, int quantity, double horizonHours)
	{
		return estimateSell(curve, price, quantity, horizonHours, 1.0);
	}

	public FillEstimate estimateSell(FillCurve curve, int price, int quantity, double horizonHours,
		double seasonalMultiplier)
	{
		if (curve == null || price <= 0 || quantity <= 0)
		{
			return FillEstimate.never();
		}
		return finish(curve.getWindowSeconds(), curve.sellScoredBuckets(), curve.sellReachableBuckets(price),
			curve.sellVolumeAtOrAbove(price), quantity, horizonHours, seasonalMultiplier);
	}

	/**
	 * Units per hour we can expect to buy at this price, before any order size is chosen.
	 * <p>
	 * This is the figure order size should be derived from, and it is not the item's overall volume.
	 * An item can turn over ten thousand units an hour while only a few hundred of them cross at the
	 * price we are willing to pay; sizing against the headline number produces an order the market
	 * never reaches, which then holds a slot for a day. Throughput does not depend on quantity, so it
	 * can be asked first and the order sized to fit.
	 */
	public double buyThroughput(FillCurve curve, int price, double seasonalMultiplier)
	{
		return curve == null || price <= 0 ? 0
			: estimateBuy(curve, price, 1, 1.0, seasonalMultiplier).getUnitsPerHour();
	}

	/** Units per hour we can expect to sell at this price. See {@link #buyThroughput}. */
	public double sellThroughput(FillCurve curve, int price, double seasonalMultiplier)
	{
		return curve == null || price <= 0 ? 0
			: estimateSell(curve, price, 1, 1.0, seasonalMultiplier).getUnitsPerHour();
	}

	/**
	 * Convenience for callers that evaluate a single price and have no curve to reuse. Anything
	 * sweeping a grid should build one {@link FillCurve} and pass it, which is the whole point of it.
	 */
	public FillEstimate estimateBuy(List<Candle> series, int price, int quantity, double horizonHours)
	{
		return estimateBuy(FillCurve.from(series), price, quantity, horizonHours);
	}

	public FillEstimate estimateSell(List<Candle> series, int price, int quantity, double horizonHours)
	{
		return estimateSell(FillCurve.from(series), price, quantity, horizonHours);
	}

	private FillEstimate finish(long windowSeconds, int scoredBuckets, int reachableBuckets,
		long volumeWhenReachable, int quantity, double horizonHours, double seasonalMultiplier)
	{
		if (scoredBuckets == 0 || reachableBuckets == 0 || volumeWhenReachable <= 0 || windowSeconds <= 0)
		{
			return FillEstimate.never();
		}

		double hoursInWindow = windowSeconds / 3600.0;

		// Volume that went through at or beyond our price, spread over the whole window rather than
		// only the buckets it happened in. That already embodies how often the market reaches us:
		// buckets that never came to our price contribute no volume but still contribute time.
		//
		// Reachability must not then be applied a second time. Writing the two-question form out in
		// full, reachRate * (volumeWhenReachable / reachableBuckets) * (scoredBuckets / hours)
		// cancels exactly to volumeWhenReachable / hours — so an extra reachRate here is not
		// conservatism, it is the same discount charged twice. On an item whose price is reached one
		// bucket in ten it made a routine fill look ten times slower than it is, which was enough to
		// score the entire market as untradeable.
		double volumePerHourAtPrice = volumeWhenReachable / hoursInWindow;

		// The history averages across every hour of the day, but an item can trade three times as
		// fast at peak as it does in its quiet hours. Without this, fill times are optimistic
		// overnight and pessimistic at peak — and the error lands directly on order size.
		double season = seasonalMultiplier <= 0 ? 1.0 : seasonalMultiplier;

		// Discount the estimate by how little evidence stands behind it.
		//
		// This is not general caution, it is a correction for a specific and measured failure. The
		// optimizer ranks candidates on throughput, and ranking on a noisy quantity does not merely
		// inherit the noise — it selects for it. Whichever item happened to look fastest is
		// disproportionately the one whose estimate was luckiest, so the chosen trades are
		// systematically the ones the model is most wrong about. Held-out replay measured exactly
		// that shape: orders the optimizer chose ran 2.25x over their predicted time while the same
		// estimator, used by a strategy that does not rank on it, was accurate to 1.18x.
		//
		// An estimate from four reachable buckets and one from four hundred are not equally
		// believable, and the ranking has no way to know that unless it is told. Dividing by
		// (1 + 1/sqrt(n)) costs a well-observed item almost nothing and halves a barely-observed one,
		// which is what stops thin evidence from winning on enthusiasm alone.
		double evidenceDiscount = 1.0 + evidenceWeight / Math.sqrt(reachableBuckets);
		double unitsPerHour = volumePerHourAtPrice * captureRate * season / evidenceDiscount;
		if (unitsPerHour <= 0)
		{
			return FillEstimate.never();
		}

		// Time before anybody trades with us at all, which is not the same thing as throughput.
		//
		// Dividing quantity by a rate says a small enough order fills instantly, and that is simply
		// not how a queue works: an order for one item does not complete in three seconds, it waits
		// for a counterparty to arrive at our price. The wait is roughly one bucket divided by the
		// fraction of buckets that reach us — five-minute bars on an item reached three times in ten
		// give about seventeen minutes before the first fill, whatever the size.
		//
		// Leaving this out biases every estimate, but not evenly, and the unevenness is what does the
		// damage. A predicted twelve minutes that really takes thirty is wrong by 2.5x; a predicted
		// two hours that takes two and a half is wrong by 1.2x. Since the optimizer ranks on profit
		// divided by duration, it is drawn to precisely the short-duration trades whose duration is
		// most understated — which is the measured 2.25x against the baseline's 1.22x, one error
		// striking two different mixes of trade rather than two separate faults.
		//
		// Additive on time and independent of size, so unlike a discount on throughput it cannot
		// quietly shrink every order as a side effect.
		double bucketHours = hoursInWindow / scoredBuckets;
		double reachRate = (double) reachableBuckets / scoredBuckets;
		double counterpartyWait = waitWeight * bucketHours / Math.max(reachRate, MIN_REACH_RATE);

		double expectedHours = Math.max(MIN_HOURS, counterpartyWait + quantity / unitsPerHour);

		// Completion must account for the wait too: an order cannot fill inside a horizon it spends
		// waiting, so only the time left after the wait is available for trading.
		double tradingHours = Math.max(0, horizonHours - counterpartyWait);
		double expectedUnitsInHorizon = unitsPerHour * tradingHours;

		// How certain the supply is, not just how much of it there is on average.
		//
		// Treating flow as a smooth stream — completion = 1 - exp(-supply/demand) — asks only whether
		// there is enough volume in expectation. It ignores that the volume arrives in lumps, in
		// whichever buckets happen to reach our price, and that a horizon containing three such
		// buckets is a far riskier proposition than one containing thirty with the same total. The
		// smooth model cannot tell those apart and calls both nearly certain.
		//
		// Measured, that is where the optimism lives: buy legs predicted at about 98% completed 76%
		// of the time, and because expected profit is weighted by completion, the profit estimate
		// inherited the error and came in around half of what was promised.
		//
		// Counting the expected number of reachable buckets gives the supply a spread as well as a
		// mean. With volumes roughly as variable as their own size, the total over n buckets has a
		// relative spread of 1/sqrt(n), and completion becomes the chance that total clears the
		// order — which is appropriately unsure when n is small and approaches certainty when it
		// is large.
		double probability;
		if (lumpyArrivals)
		{
			double bucketsInHorizon = bucketHours <= 0 ? 0 : tradingHours / bucketHours;
			double expectedReachable = Math.max(1.0, reachRate * bucketsInHorizon);

			// Process noise only.
			//
			// Adding the uncertainty in the rate estimate itself was tried here and removed. The
			// reasoning was sound — a rate inferred from a short history is a weaker claim than one
			// inferred from a long one — but it failed at the job it was brought in for: predicted
			// completion moved by a single point, from 93% to 92%, against a realised 75%, and the
			// run came out worse. Whatever remains of the overconfidence is not explained by how much
			// history the rate came from.
			double spread = expectedUnitsInHorizon / Math.sqrt(expectedReachable);
			probability = spread <= 0 ? 0
				: standardNormalCdf((expectedUnitsInHorizon - quantity) / spread);
		}
		else
		{
			probability = 1.0 - Math.exp(-expectedUnitsInHorizon / quantity);
		}

		return new FillEstimate(clamp(probability), expectedHours, unitsPerHour, counterpartyWait);
	}

	/**
	 * Standard normal CDF, via the Abramowitz and Stegun 7.1.26 approximation to the error function.
	 * Accurate to about 1.5e-7, which is far beyond what a fill estimate can justify caring about.
	 */
	static double standardNormalCdf(double z)
	{
		if (!Double.isFinite(z))
		{
			return z > 0 ? 1 : 0;
		}
		double sign = z < 0 ? -1 : 1;
		double x = Math.abs(z) / Math.sqrt(2);
		double t = 1.0 / (1.0 + 0.3275911 * x);
		double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
			- 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
		return 0.5 * (1.0 + sign * y);
	}

	private static double clamp(double value)
	{
		if (Double.isNaN(value))
		{
			return 0;
		}
		return Math.max(0, Math.min(1, value));
	}
}
