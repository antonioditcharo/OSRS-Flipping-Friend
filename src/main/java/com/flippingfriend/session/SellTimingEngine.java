package com.flippingfriend.session;

import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.PriceForecast;
import com.flippingfriend.model.Regime;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Decides when to sell something we are holding.
 * <p>
 * Knowing when to get out is harder than knowing when to get in, and it is where most of the money
 * is actually lost. Five things are weighed on every pass:
 * <ul>
 *   <li><b>Target reached.</b> The straightforward case.</li>
 *   <li><b>Momentum turned.</b> The short average crossing below the long one means the market has
 *       changed its mind since we bought, and waiting for the original target is now hope.</li>
 *   <li><b>Time decay.</b> The longer a position is held, the more of the hourly profit it has
 *       already burned, so the ask is walked down towards break-even rather than held out of
 *       stubbornness.</li>
 *   <li><b>Stop loss.</b> A hard floor, so one bad trade cannot consume a session's profit.</li>
 *   <li><b>Liquidity.</b> A target price nobody is buying at is not a target.</li>
 * </ul>
 */
@Singleton
public class SellTimingEngine
{
	/** Sell-price grid, as offsets from the current instant-buy price. */
	private static final double[] SELL_OFFSETS = {-0.010, -0.006, -0.003, -0.001, 0.0, 0.002};
	/**
	 * How optimistic to be about the price still to come.
	 * <p>
	 * The three-quarter mark: a price the market beats one time in four over the time remaining.
	 * Deliberately not the median, because a seller with hours in hand should hold out for better than
	 * the coin-flip outcome, and deliberately not the extreme, because a target the market reaches one
	 * time in twenty is a target that never gets hit and a position that never gets sold.
	 */
	private static final double REACHABLE_QUANTILE = 0.75;

	/** The candle spacing the sell-side history is fetched at. */
	private static final int BUCKET_SECONDS = 300;

	/**
	 * How close to the target counts as close enough to keep a slot for.
	 * <p>
	 * One pricing step, which is what {@link #SELL_OFFSETS} already spans: the grid runs from one
	 * percent under the market to a fraction over, so a position within that of its target is one
	 * move away from being sold rather than merely on its way. Not a new number — the engine's own
	 * idea of a price step, reused so the reservation and the pricing agree about what "nearly" means.
	 */
	private static final double NEAR_TARGET = 0.01;

	/**
	 * How much of the patience budget must be gone before the timed exit counts as imminent.
	 * <p>
	 * Past this the sale fires on the clock whatever the price does, so the slot is genuinely needed
	 * soon regardless of where the market is.
	 */
	private static final double NEAR_DEADLINE = 0.85;

	/**
	 * How wide the middle half of the forecast may be before it stops meaning anything.
	 * <p>
	 * A quarter of the price: past that the item is not predictable at the horizon being asked about,
	 * and a quantile drawn from it is noise wearing a number's clothes.
	 */
	private static final double MAX_USEFUL_BAND = 0.25;

	private final FillModel fillModel;
	private final TaxCalculator taxCalculator;

	@Inject
	public SellTimingEngine(FillModel fillModel, TaxCalculator taxCalculator)
	{
		this.fillModel = fillModel;
		this.taxCalculator = taxCalculator;
	}

	public SellDecision evaluate(Position position, LatestPrice latest, ItemFeatures features,
		List<Candle> series, TradingHorizon horizon, Instant now, boolean inInventory, boolean sellOnly)
	{
		return evaluate(position, latest, features, series, horizon, now, 0, inInventory, sellOnly, false);
	}

	/**
	 * @param minProfitPerFlip the player's floor for a whole trade, from which the exit's own floor is
	 *                         derived. Zero leaves exits ungated, which is the old behaviour.
	 */
	public SellDecision evaluate(Position position, LatestPrice latest, ItemFeatures features,
		List<Candle> series, TradingHorizon horizon, Instant now, long minProfitPerFlip, boolean inInventory, boolean sellOnly, boolean isSkipped)
	{
		RiskProfile profile = horizon.getProfile();
		if (position == null || position.getQuantity() <= 0)
		{
			return SellDecision.hold("Nothing held.", 0);
		}
		if (latest == null || latest.getHigh() == null || latest.getHigh() <= 0)
		{
			return SellDecision.hold("Waiting for a current price on this item.", 0);
		}

		int itemId = position.getItemId();
		int quantity = position.getQuantity();
		int marketSell = latest.getHigh();
		long minutesHeld = position.minutesHeld(now.getEpochSecond());

		PricedExit exit = bestExit(series, itemId, marketSell, quantity, horizon,
			position.isCostKnown() ? position.getAverageCost() : 0, sellOnly);
		if (exit == null)
		{
			return SellDecision.hold("Nobody is buying this at the moment. Holding until they are.", 0);
		}

		if (sellOnly)
		{
			long expectedProfit = position.isCostKnown() ? taxCalculator.netProfit(itemId, position.getAverageCost(), exit.price, quantity) : taxCalculator.netProceeds(itemId, exit.price, quantity);
			return new SellDecision(SellDecision.Action.SELL, exit.price,
				"Sell-only mode is on, so this position is being exited now rather than waiting for a target.",
				exit.expectedMinutes, expectedProfit);
		}

		// Items already in the bank have no cost basis, so there is no loss to manage — only the
		// question of whether now is a reasonable time to convert them to coins.
		if (!position.isCostKnown())
		{
			if (inInventory && !isSkipped)
			{
				return new SellDecision(SellDecision.Action.SELL, exit.price,
					"This is in your inventory, so the plugin is assuming you want to sell it now.",
					exit.expectedMinutes, taxCalculator.netProceeds(itemId, exit.price, quantity));
			}

			if (features.isUsable() && features.getRegime() == Regime.FALLING)
			{
				return new SellDecision(SellDecision.Action.SELL, exit.price,
					"The price on this is falling, so it is worth selling now rather than later.",
					exit.expectedMinutes, taxCalculator.netProceeds(itemId, exit.price, quantity));
			}

			if (features.isUsable() && features.getRegime() == Regime.RISING)
			{
				return new SellDecision(SellDecision.Action.SELL, exit.price,
					"The market is moving up. This is a good sell window for your banked item.",
					exit.expectedMinutes, taxCalculator.netProceeds(itemId, exit.price, quantity));
			}

			return new SellDecision(SellDecision.Action.SELL, exit.price,
				"Listing this banked item.",
				exit.expectedMinutes, taxCalculator.netProceeds(itemId, exit.price, quantity));
		}

		int averageCost = position.getAverageCost();
		long profitAtExit = taxCalculator.netProfit(itemId, averageCost, exit.price, quantity);

		int lossCutPrice = position.getStopPrice();
		if (lossCutPrice <= 0 && averageCost > 0)
		{
			lossCutPrice = averageCost - (int) Math.round(averageCost * profile.getLossCutPct());
			lossCutPrice = Math.min(averageCost - 1, lossCutPrice);
		}

		if (marketSell <= lossCutPrice)
		{
			return new SellDecision(SellDecision.Action.CUT, exit.price,
				"Price fell through the stop loss. Cutting the position.", exit.expectedMinutes, profitAtExit);
		}

		if (minutesHeld >= profile.getMaxHoldMinutes())
		{
			return new SellDecision(SellDecision.Action.SELL, exit.price,
				"Position held past its horizon limit. Exiting.", exit.expectedMinutes, profitAtExit);
		}

		// The target the position was opened with, revised down to what the market can still plausibly
		// reach in the time that is left. Without this a position holds its original ask until the hard
		// hold limit fires, which is the "hope" this class's own javadoc says it exists to avoid - and
		// the slot is spent either way.
		int targetPrice = reachableTarget(position, series, horizon, minutesHeld);
		boolean nearTarget = targetPrice > 0 && marketSell >= targetPrice * (1.0 - NEAR_TARGET);
		boolean nearDeadline = minutesHeld >= profile.getMaxHoldMinutes() * NEAR_DEADLINE;

		long floor = minProfitPerFlip / 2;

		if (profitAtExit < floor)
		{
			return SellDecision.hold("The expected profit is not worth a Grand Exchange slot.", 0)
				.withExitNear(nearTarget || nearDeadline);
		}

		if (features.isUsable() && features.getRegime() == Regime.FALLING)
		{
			return new SellDecision(SellDecision.Action.SELL, exit.price,
				"Momentum has turned against us. Market trend is falling.", exit.expectedMinutes, profitAtExit);
		}

		if (targetPrice > 0 && marketSell >= targetPrice)
		{
			return new SellDecision(SellDecision.Action.SELL, exit.price,
				"Target price reached. Selling.", exit.expectedMinutes, profitAtExit);
		}

		return SellDecision.hold("Holding for target.", 0).withExitNear(nearTarget || nearDeadline);
	}

	/**
	 * The best price still worth waiting for, given how much of the horizon is left.
	 *
	 * <p>Restored on 3 September 2026. {@link PriceForecast} was imported and unused, and
	 * {@link #REACHABLE_QUANTILE}, {@link #MAX_USEFUL_BAND} and {@link #BUCKET_SECONDS} were each
	 * declared and read by nothing: the method that used them had been deleted along with its test,
	 * leaving the constants and their reasoning as the only evidence it had ever existed. The class
	 * javadoc still promised the behaviour - "the ask is walked down towards break-even rather than
	 * held out of stubbornness" - while the code held the entry target unchanged until the hold limit.
	 *
	 * <p>The decay is a property of the model rather than a schedule. The number asked for is
	 * {@link PriceForecast#reachableWithin}: the price the market has historically <em>touched</em> at
	 * some point in a window this long, one window in four. A long window contains more chances to
	 * touch a high price than a short one, so the answer falls as the window closes - on its own, at
	 * the rate this particular item's own history says, with no timer to tune. A linear schedule cannot
	 * do that, because a schedule knows nothing about the item it is walking down.
	 *
	 * <p>Not {@link PriceForecast#quantile}, which prices where the item will <em>end up</em>. That was
	 * the first attempt and it failed on measurement rather than on principle: a resting offer fills
	 * the moment the price arrives and does not wait for the close, and because a fast-reverting fit
	 * stops moving once its reversion term has decayed, the endpoint reading gave targets six hours and
	 * twenty minutes apart that differed by one coin. Same constants, same model, right question.
	 *
	 * <p>Never below break-even. Walking an ask past the point where the sale loses money converts a
	 * patient position into a realised loss, and the hold limit and stop already exist to end those
	 * deliberately rather than by drift.
	 *
	 * <p>And never upward. The forecast is here to stop a position waiting for a price that is no
	 * longer coming, not to talk it into holding out for more than it was opened for - that would be
	 * the engine overriding the sizing decision that justified the trade in the first place.
	 *
	 * <p>Refuses the forecast when the middle half is wider than {@link #MAX_USEFUL_BAND}, when the
	 * HIGH side could not be fitted, or when there is no history: in each case the original target
	 * stands, because a quantile drawn from noise is a worse answer than the one already on record.
	 *
	 * @return the revised target, or the position's own when the forecast cannot improve on it
	 */
	int reachableTarget(Position position, List<Candle> series, TradingHorizon horizon,
		long minutesHeld)
	{
		int original = position.getTargetSellPrice();
		if (original <= 0 || series == null || series.isEmpty())
		{
			return original;
		}

		double remainingHours =
			Math.max(0, horizon.getProfile().getMaxHoldMinutes() - minutesHeld) / 60.0;
		if (remainingHours <= 0)
		{
			// Out of time. The hold-limit exit above has already fired by now; leave the target alone
			// rather than ask the forecast about a horizon of zero.
			return original;
		}

		PriceForecast forecast = PriceForecast.fit(series, BUCKET_SECONDS);
		if (!forecast.isUsable(PriceForecast.Side.HIGH))
		{
			return original;
		}
		if (forecast.relativeSpread(PriceForecast.Side.HIGH, remainingHours) > MAX_USEFUL_BAND)
		{
			return original;
		}

		double reachable =
			forecast.reachableWithin(PriceForecast.Side.HIGH, remainingHours, REACHABLE_QUANTILE);
		if (!Double.isFinite(reachable) || reachable <= 0)
		{
			return original;
		}

		// Zero when the cost basis is unknown, which is the honest answer for a position the plugin
		// inherited rather than opened. An unknown floor must not become a floor of the buy price.
		int breakEven = position.isCostKnown()
			? taxCalculator.breakEvenSellPrice(position.getItemId(), position.getAverageCost())
			: 0;
		int revised = (int) Math.round(reachable);
		return Math.max(breakEven, Math.min(original, revised));
	}

	/**
	 * Picks the sell price that converts the position to coins fastest per gold given up, rather
	 * than simply asking the highest price the market has recently paid.
	 *
	 * @param averageCost what the position cost per unit, or 0 when it was never bought — items that
	 *                    came out of the bank have no cost basis, and for those the fastest
	 *                    conversion to coins genuinely is the whole objective
	 */
	private PricedExit bestExit(List<Candle> series, int itemId, int marketSell, int quantity,
		TradingHorizon horizon, int averageCost, boolean sellOnly)
	{
		if (series == null || series.isEmpty())
		{
			return new PricedExit(marketSell, Double.NaN);
		}

		double horizonHours = horizon.legHorizonHours();
		FillCurve curve = FillCurve.from(series);
		PricedExit best = null;
		// A position already under water has no positive-gain exit, so the best available one is
		// still the one to take; starting the search at zero would return nothing and hold forever.
		double bestRate = -Double.MAX_VALUE;

		for (double offset : SELL_OFFSETS)
		{
			int price = Math.max(1, marketSell + (int) Math.round(marketSell * offset));
			FillEstimate fill = fillModel.estimateSell(curve, price, quantity, horizonHours);
			if (!fill.isPlausible())
			{
				continue;
			}

			long proceeds = taxCalculator.netProceeds(itemId, price, quantity);
			long gain = averageCost > 0 ? proceeds - (long) averageCost * quantity : proceeds;
			double hours = Math.max(fill.getExpectedHours(), 1.0 / 60.0);
			
			double rate;
			if (gain > 0)
			{
				rate = gain * fill.getProbability() / Math.pow(hours, sellOnly ? 1.5 : 1.0);
			}
			else
			{
				// For negative gain (loss), we want to maximize the rate (i.e. make it as close to 0 as possible).
				// Multiplying by hours and dividing by probability achieves this:
				// A long wait (high hours) makes the negative number larger (worse).
				// A low probability makes the negative number larger (worse).
				rate = gain * Math.pow(hours, sellOnly ? 1.5 : 1.0) / fill.getProbability();
			}

			if (rate > bestRate)
			{
				bestRate = rate;
				best = new PricedExit(price, fill.getExpectedMinutes());
			}
		}

		return best;
	}

	private static final class PricedExit
	{
		private final int price;
		private final double expectedMinutes;

		PricedExit(int price, double expectedMinutes)
		{
			this.price = price;
			this.expectedMinutes = expectedMinutes;
		}
	}
}
