package com.flippingfriend.model;

import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Prices, sizes and scores a single trade idea.
 * <p>
 * Two things here are worth stating plainly, because they are where most flipping tools go wrong.
 * <p>
 * <b>The price is optimised, not assumed.</b> The obvious move is to buy at the last instant-sell
 * price and sell at the last instant-buy price. But those two prices are the ones that fill
 * <em>slowest</em>, because everyone else is queued at them too. Paying a little more to buy, or
 * asking a little less to sell, trades margin for speed — and since the thing we actually care
 * about is profit per slot per hour, the best price is usually not the best margin. So a small grid
 * of prices around the spread is evaluated and the pair that maximises hourly profit wins.
 * <p>
 * <b>The score is an expectation, including the losses.</b> A flip that works 60% of the time and
 * is cut for a loss the other 40% is not worth 60% of its margin — it is worth its margin minus
 * what the losing cases cost. Scoring the upside alone is how a tool ends up recommending trades
 * with a great headline number and a negative expectation.
 */
@Singleton
public class Scorer
{
	/** Multiplicative offsets from the quoted buy price. Positive means paying more to fill sooner. */
	private static final double[] BUY_OFFSETS = {-0.004, -0.002, 0.0, 0.001, 0.002, 0.004, 0.008};
	/** Offsets from the quoted sell price. Negative means asking less to fill sooner. */
	private static final double[] SELL_OFFSETS = {-0.008, -0.004, -0.002, -0.001, 0.0, 0.002, 0.004};

	/** Buying into a rising market helps the exit; buying into a falling one fights it. */
	private static final double RISING_BONUS = 1.10;
	private static final double FALLING_PENALTY = 0.70;
	/** How hard to punish jumpy items. Tuned so a 2% volatility item loses about a sixth of its score. */
	private static final double VOLATILITY_PENALTY = 8.0;
	/** How much the position within the fortnight range tilts the score, at most 20% either way. */
	private static final double RANGE_WEIGHT = 0.4;
	/** How much further inside the spread the conservative setting reaches. */
	private static final double CONSERVATIVE_STEP = 1.6;

	private final TaxCalculator taxCalculator;
	private final FillModel fillModel;
	private final PositionSizer positionSizer;
	private final Calibrator calibrator;

	@Inject
	public Scorer(TaxCalculator taxCalculator, FillModel fillModel, PositionSizer positionSizer,
		Calibrator calibrator)
	{
		this.taxCalculator = taxCalculator;
		this.fillModel = fillModel;
		this.positionSizer = positionSizer;
		this.calibrator = calibrator;
	}

	/**
	 * @return the best trade available on this item, or null if nothing clears the profile's bars
	 */
	public Candidate score(ItemMetadata metadata, LatestPrice latest, ItemFeatures features,
		List<Candle> series, TradingHorizon horizon, long spendableCoins, int buyLimitRemaining,
		boolean useCalibration, int natureRunePrice)
	{
		return score(metadata, latest, features, series, MarketContext.unknown(), horizon, spendableCoins,
			buyLimitRemaining, useCalibration, natureRunePrice);
	}

	/**
	 * @param context the fortnight view, supplying the hour-of-day liquidity adjustment and the
	 *                position of the current price within its recent range
	 */
	public Candidate score(ItemMetadata metadata, LatestPrice latest, ItemFeatures features,
		List<Candle> series, MarketContext context, TradingHorizon horizon, long spendableCoins,
		int buyLimitRemaining, boolean useCalibration, int natureRunePrice)
	{
		return score(metadata, latest, features, series, context, horizon, spendableCoins,
			buyLimitRemaining, useCalibration, natureRunePrice, Instant.now());
	}

	/**
	 * @param at the moment being evaluated. Live this is simply now; the backtester passes the
	 *           simulated time so that the hour-of-day liquidity adjustment reflects the hour the
	 *           decision was actually made rather than whenever the analysis happens to be run —
	 *           without which the same data replays to different answers.
	 */

	public Candidate score(ItemMetadata metadata, LatestPrice latest, ItemFeatures features,
		List<Candle> series, MarketContext context, TradingHorizon horizon, long spendableCoins,
		int buyLimitRemaining, boolean useCalibration, int natureRunePrice, Instant at)
	{
		RiskProfile profile = horizon.getProfile();
		if (metadata == null || latest == null || !latest.isComplete() || series == null || series.isEmpty())
		{
			return null;
		}
		if (buyLimitRemaining <= 0 || spendableCoins <= 0)
		{
			return null;
		}

		int itemId = metadata.getId();
		int quotedBuy = latest.getLow();
		int quotedSell = latest.getHigh();

		// How long one leg has to fill, accounting for both the risk profile and how often the
		// player actually returns to place the other side of the trade.
		double legHorizon = horizon.legHorizonHours();

		double calibration = useCalibration ? calibrator.scoreMultiplier(itemId) : 1.0;
		// Fill rates are estimated from history averaged over every hour; this corrects them for the
		// hour the player is actually trading in.
		double season = context.isUsable()
			? context.liquidityMultiplier(at.atZone(ZoneOffset.UTC).getHour())
			: 1.0;

		// Built once and reused across the whole price grid. Rebuilding it per price would make the
		// grid search quadratic in the length of the history for no gain.
		FillCurve curve = FillCurve.from(series);
		if (curve.isEmpty())
		{
			return null;
		}

		Candidate best = null;

		for (double buyOffset : BUY_OFFSETS)
		{
			int buyPrice = applyOffset(quotedBuy, buyOffset);
			if (buyPrice <= 0 || buyPrice > spendableCoins)
			{
				continue;
			}

			// unitsPerHour does not depend on order size, so one probe gives us the flow rate we
			// need in order to choose a size, before re-estimating with that size.
			FillEstimate buyProbe = fillModel.estimateBuy(curve, buyPrice, 1, legHorizon, season);
			if (!buyProbe.isPlausible())
			{
				continue;
			}

			double momentum = features.getPredictedMomentum();
			boolean usePrediction = (momentum != 0.0);
			double[] sellOffsetsToUse = usePrediction ? new double[]{0} : SELL_OFFSETS;

			for (double sellOffset : sellOffsetsToUse)
			{
				int sellPrice;
				if (usePrediction)
				{
					sellPrice = (int) Math.round(quotedSell * (1 + momentum));
				}
				else
				{
					sellPrice = applyOffset(quotedSell, sellOffset);
				}

				if (sellPrice <= buyPrice)
				{
					continue;
				}

				long marginPerItem = taxCalculator.netMarginPerItem(itemId, buyPrice, sellPrice);
				if (marginPerItem <= 0)
				{
					continue;
				}

				double marginPct = (double) marginPerItem / buyPrice;
				if (marginPct < profile.getMinNetMarginPct())
				{
					continue;
				}

				FillEstimate sellProbe = fillModel.estimateSell(curve, sellPrice, 1, legHorizon, season);
				if (!sellProbe.isPlausible())
				{
					continue;
				}

				// The size is chosen so that an order this big actually fills inside the leg
				// horizon with the confidence the profile asks for, rather than being sized first
				// and vetted afterwards.
				// The sizer asks this rather than reimplementing it, so it can never be inverting a
				// different fill model from the one two lines below that will judge the result.
				final int probePrice = buyPrice;
				final double probeHorizon = legHorizon;
				int quantity = positionSizer.size(profile, spendableCoins, buyPrice, buyLimitRemaining,
					q -> fillModel.estimateBuy(curve, probePrice, q, probeHorizon, season).getProbability(),
					marginPct, sellProbe.getProbability());
				if (quantity <= 0)
				{
					continue;
				}

				FillEstimate buyFill = fillModel.estimateBuy(curve, buyPrice, quantity, legHorizon, season);
				FillEstimate sellFill = fillModel.estimateSell(curve, sellPrice, quantity, legHorizon, season);
				if (!buyFill.isPlausible() || !sellFill.isPlausible())
				{
					continue;
				}

				double completion = buyFill.getProbability() * sellFill.getProbability();
				if (completion < profile.getMinFillProbability())
				{
					continue;
				}

				Candidate candidate = build(metadata, features, context, horizon, buyPrice, sellPrice,
					quantity, marginPerItem, buyFill, sellFill, buyLimitRemaining, calibration, natureRunePrice);

				if (candidate != null && (best == null || candidate.getScore() > best.getScore()))
				{
					best = candidate;
				}
			}
		}

		return best;
	}

	private Candidate build(ItemMetadata metadata, ItemFeatures features, MarketContext context,
		TradingHorizon horizon, int buyPrice, int sellPrice, int quantity, long marginPerItem,
		FillEstimate buyFill, FillEstimate sellFill, int buyLimitRemaining, double calibration, int natureRunePrice)
	{
		RiskProfile profile = horizon.getProfile();
		int itemId = metadata.getId();
		long capital = (long) buyPrice * quantity;
		// Already net of Grand Exchange tax: marginPerItem comes from netMarginPerItem. A separate
		// per-candidate tax was computed here for every price and size point in the sweep and stored
		// on the candidate, where nothing read it and where it was not an input to the score either.
		long netProfit = marginPerItem * quantity;

		double roundTripHours = buyFill.getExpectedHours() + sellFill.getExpectedHours();
		if (roundTripHours <= 0 || roundTripHours * 60 > horizon.maxHoldMinutes())
		{
			return null;
		}

		// The honest expectation: we only reach the sell leg if the buy fills, and when the sell
		// does not complete we take the profile's loss cut rather than magically breaking even.
		double pBuy = buyFill.getProbability();
		double pSell = sellFill.getProbability();
		
		int pAlchFloor = metadata.getHighAlch() - natureRunePrice;
		double standardExitPrice = buyPrice * (1.0 - profile.getLossCutPct());
		double actualExitPrice = Math.max(standardExitPrice, pAlchFloor);
		
		double lossIfStuck = quantity * (buyPrice - actualExitPrice);
		
		double expectedValue = pBuy * (pSell * netProfit - (1 - pSell) * lossIfStuck);
		if (expectedValue <= 0)
		{
			return null;
		}

		double regimeFactor = regimeFactor(features.getRegime(), profile);
		double volatilityFactor = 1.0 / (1.0 + features.getVolatility() * VOLATILITY_PENALTY);

		// Buying low in the fortnight range is worth a premium and buying high a discount, because
		// the room left to move is not symmetric. Centred on 1.0 so an item at its median is neutral.
		double rangeFactor = context.isUsable()
			? 1.0 + (0.5 - context.getPricePercentile()) * RANGE_WEIGHT
			: 1.0;

		double score = (expectedValue / roundTripHours) * regimeFactor * volatilityFactor * rangeFactor
			* calibration;
		if (score <= 0)
		{
			return null;
		}

		double confidence = pBuy * pSell * blend(features.getSpreadStability());
		int breakEven = taxCalculator.breakEvenSellPrice(itemId, buyPrice);

		return new Candidate(itemId, metadata.getName(), buyPrice, sellPrice, quantity, netProfit,
			score, clamp01(confidence), buyFill, sellFill, features, breakEven, buyLimitRemaining, calibration);
	}

	private static double regimeFactor(Regime regime, RiskProfile profile)
	{
		switch (regime)
		{
			case RISING:
				return RISING_BONUS;
			case FALLING:
				// Only reachable on profiles that allow trend plays; the filter rejects it otherwise.
				return profile.isAllowTrendPlays() ? FALLING_PENALTY : 0;
			default:
				return 1.0;
		}
	}

	/**
	 * Pulls a 0..1 quality measure towards the middle so a single soft signal cannot drag the whole
	 * confidence figure to zero.
	 */
	private static double blend(double quality)
	{
		return 0.5 + 0.5 * clamp01(quality);
	}

	private static double clamp01(double value)
	{
		if (Double.isNaN(value))
		{
			return 0;
		}
		return Math.max(0, Math.min(1, value));
	}

	static int applyOffset(int price, double offset)
	{
		// One coin minimum step, so the grid still separates prices on cheap items where a
		// fraction of a percent rounds to nothing.
		int shift = (int) Math.round(price * offset);
		if (shift == 0 && offset != 0)
		{
			shift = offset > 0 ? 1 : -1;
		}
		return Math.max(1, price + shift);
	}
}
