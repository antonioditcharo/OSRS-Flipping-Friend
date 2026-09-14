package com.flippingfriend.session;

import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.Regime;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Prices the sale of something we are holding. It does not decide whether to sell.
 * <p>
 * <b>Everything held is listed on the pass that sees it.</b> There is no target to wait for, no
 * profit floor to clear, no "nobody is buying this at the moment", and no patience budget to spend
 * first. This class used to weigh all of those and answer HOLD, and every one of those answers was
 * a position sitting in a bank earning nothing while the reason it was being kept quietly stopped
 * being true. An unlisted item is not a trade; it is a decision deferred.
 * <p>
 * What is left is the part that is genuinely a judgement: <b>what price to ask</b>. A grid around
 * the current quote is evaluated against the item's own fill history and the price that converts
 * the position to coins fastest per gold given up wins. Where the sale cannot afford to wait in the
 * queue at all — a stop that has been hit, a position past its horizon, a session being wound down
 * — the grid is widened so it can cross the spread and meet the standing bid.
 * <p>
 * The only answer that is not a sale is the one where there is no price anywhere to quote: not in
 * the live feed, not in the item's history, not even a cost basis. That is missing data, not
 * patience.
 */
@Singleton
public class SellTimingEngine
{
	/** Sell-price grid, as offsets from the current instant-buy price. */
	private static final double[] SELL_OFFSETS = {-0.010, -0.006, -0.003, -0.001, 0.0, 0.002};

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
	 * @param minProfitPerFlip kept on the signature for callers that still pass it. It no longer
	 *                         gates anything: an exit worth very little is still an exit, and the
	 *                         question of whether a trade is worth a slot belongs to opening one.
	 */
	public SellDecision evaluate(Position position, LatestPrice latest, ItemFeatures features,
		List<Candle> series, TradingHorizon horizon, Instant now, long minProfitPerFlip,
		boolean inInventory, boolean sellOnly, boolean isSkipped)
	{
		RiskProfile profile = horizon.getProfile();
		if (position == null || position.getQuantity() <= 0)
		{
			// Not a decision about a holding. There is no holding.
			return SellDecision.hold("Nothing held.", 0);
		}

		int itemId = position.getItemId();
		int quantity = position.getQuantity();
		long minutesHeld = position.minutesHeld(now.getEpochSecond());

		int marketSell = quoteOrLastTraded(latest, series, position);
		if (marketSell <= 0)
		{
			// The one honest exception, and it is not a decision to hold: there is no price anywhere
			// -- not in the quote, not in the history, not even a cost basis -- so there is no number
			// to put in the offer. Anything else here would be inventing one.
			return SellDecision.hold("Waiting for a current price on this item.", 0);
		}

		int averageCost = position.isCostKnown() ? position.getAverageCost() : 0;
		int lossCutPrice = position.getStopPrice();
		if (lossCutPrice <= 0 && averageCost > 0)
		{
			lossCutPrice = averageCost - (int) Math.round(averageCost * profile.getLossCutPct());
			lossCutPrice = Math.min(averageCost - 1, lossCutPrice);
		}
		boolean throughStop = position.isCostKnown() && marketSell <= lossCutPrice;
		boolean pastHorizon = minutesHeld >= profile.getMaxHoldMinutes();
		// How far the pricing may reach to get the offer filled. Crossing the spread costs real gold,
		// so it is spent where the sale genuinely cannot wait for the queue: a stop that has been hit,
		// a position past its patience budget, or a session being wound down.
		boolean urgent = sellOnly || throughStop || (pastHorizon && position.isCostKnown());

		int marketBuy = latest == null || latest.getLow() == null ? 0 : latest.getLow();
		// Below this the sale realises a loss, whatever the fill model thinks of the price.
		int breakEven = averageCost > 0 ? taxCalculator.breakEvenSellPrice(itemId, averageCost) : 0;
		// Only these two authorise one. Time passing does not: a position past its patience budget
		// is still a position above its stop, and the stop is where the risk control lives.
		boolean lossAllowed = throughStop || sellOnly;
		PricedExit exit = bestExit(series, itemId, marketSell, marketBuy, quantity, horizon,
			averageCost, urgent, breakEven, position.getTargetSellPrice(), lossAllowed);

		long expectedProfit = position.isCostKnown()
			? taxCalculator.netProfit(itemId, averageCost, exit.price, quantity)
			: taxCalculator.netProceeds(itemId, exit.price, quantity);

		if (throughStop)
		{
			// Still a sale -- the player places a sell offer to do it -- and it happens now like every
			// other one. The action differs only so the card and the overlay can warn that this one
			// realises a loss.
			return new SellDecision(SellDecision.Action.CUT, exit.price,
				"This has fallen through its stop loss, so it is being sold at the best price "
					+ "available rather than held any longer.", exit.expectedMinutes, expectedProfit);
		}

		return new SellDecision(SellDecision.Action.SELL, exit.price,
			reasonFor(position, features, minutesHeld, profile, inInventory, sellOnly, marketSell),
			exit.expectedMinutes, expectedProfit);
	}

	/**
	 * Why this is being sold now. Every branch ends in a sale; only the wording differs.
	 * <p>
	 * The wording is not decoration. A player who is told to sell wants to know whether the plan
	 * worked, whether it went wrong, or whether this is simply the rule -- and those three read very
	 * differently on a card.
	 */
	private static String reasonFor(Position position, ItemFeatures features, long minutesHeld,
		RiskProfile profile, boolean inInventory, boolean sellOnly, int marketSell)
	{
		if (sellOnly)
		{
			return "Sell-only mode is on, so this position is being closed out.";
		}
		if (!position.isCostKnown())
		{
			return inInventory
				? "This is in your inventory, so it is being listed for sale."
				: "Listing this banked item.";
		}
		int target = position.getTargetSellPrice();
		if (target > 0 && marketSell >= target)
		{
			return "Target price reached. Selling.";
		}
		if (minutesHeld >= profile.getMaxHoldMinutes())
		{
			return "Held past its horizon limit. Selling at the best price available.";
		}
		if (features != null && features.isUsable() && features.getRegime() == Regime.FALLING)
		{
			return "Momentum has turned against us. Market trend is falling.";
		}
		return "Listed now rather than waited on. An item sitting unlisted earns nothing, and the "
			+ "price it is being kept back for may never arrive.";
	}

	/**
	 * A price to quote the offer at, from the live quote if there is one and from the last bar that
	 * traded if there is not.
	 * <p>
	 * "Nobody is buying this at the moment" used to be a reason to hold, and it is not one: a quote
	 * that has gone quiet is missing information, not evidence that the item cannot be sold. The
	 * history is the next best witness, and the price paid is the last -- an offer at cost is a
	 * worse trade than an offer at the market, but it is enormously better than a position nobody
	 * ever lists.
	 */
	private static int quoteOrLastTraded(LatestPrice latest, List<Candle> series, Position position)
	{
		if (latest != null && latest.getHigh() != null && latest.getHigh() > 0)
		{
			return latest.getHigh();
		}
		if (series != null)
		{
			for (int i = series.size() - 1; i >= 0; i--)
			{
				Candle bar = series.get(i);
				if (bar.getAvgHighPrice() != null && bar.getAvgHighPrice() > 0)
				{
					return bar.getAvgHighPrice();
				}
				if (bar.getAvgLowPrice() != null && bar.getAvgLowPrice() > 0)
				{
					return bar.getAvgLowPrice();
				}
			}
		}
		return position.isCostKnown() ? position.getAverageCost() : 0;
	}

	private PricedExit bestExit(List<Candle> series, int itemId, int marketSell, int marketBuy,
		int quantity, TradingHorizon horizon, int averageCost, boolean urgent, int breakEven,
		int target, boolean lossAllowed)
	{
		// The cheapest price this sale is allowed to name. See exitPrices: the grid is anchored on
		// the market, and a position whose break-even sits above the market has no profitable price
		// in it at all -- so the floor is what stops the search returning the least-bad loss.
		int floor = lossAllowed || breakEven <= 0 ? 1 : breakEven;

		if (series == null || series.isEmpty())
		{
			return new PricedExit(Math.max(floor, marketSell), Double.NaN);
		}

		// Whatever happens below, this is what comes back. The search used to be able to return null
		// when no price on the grid looked plausible, and the caller read that as "nobody is buying
		// this, hold until they are" -- which is a position never listed at all on the strength of a
		// fill model declining to commit. The offer goes on at the market price instead.
		PricedExit fallback = new PricedExit(Math.max(floor, marketSell), Double.NaN);

		double horizonHours = horizon.legHorizonHours();
		FillCurve curve = FillCurve.overRecentHistory(series);
		PricedExit best = null;
		// A position already under water has no positive-gain exit, so the best available one is
		// still the one to take; starting the search at zero would return nothing and hold forever.
		double bestRate = -Double.MAX_VALUE;

		for (int price : exitPrices(marketSell, marketBuy, urgent, breakEven, target))
		{
			if (price < floor)
			{
				continue;
			}
			FillEstimate fill = fillModel.estimateSell(curve, price, quantity, horizonHours);
			if (!fill.isPlausible())
			{
				continue;
			}

			long proceeds = taxCalculator.netProceeds(itemId, price, quantity);
			long gain = averageCost > 0 ? proceeds - (long) averageCost * quantity : proceeds;
			double hours = Math.max(fill.getExpectedHours(), 1.0 / 60.0);
			double impatience = urgent ? 1.5 : 1.0;

			double rate;
			if (gain > 0)
			{
				rate = gain * fill.getProbability() / Math.pow(hours, impatience);
			}
			else
			{
				// For a negative gain the rate is maximised by making it as close to zero as
				// possible, so the two penalties invert: a long wait and a low probability both
				// make the number more negative rather than less.
				rate = gain * Math.pow(hours, impatience) / fill.getProbability();
			}

			if (rate > bestRate)
			{
				bestRate = rate;
				best = new PricedExit(price, fill.getExpectedMinutes());
			}
		}

		return best == null ? fallback : best;
	}

	/**
	 * The prices the exit search may consider.
	 * <p>
	 * The ordinary grid runs from one percent under the instant-buy price to a fraction over it,
	 * which is the right range for a sale that is wanted rather than needed: it trades a little
	 * margin for a faster fill without giving the spread away.
	 * <p>
	 * It is the wrong range for an exit that has to happen. A stop that has been hit, a position
	 * past its horizon, and a sell-only wind-down all need to be able to <em>cross</em> the spread,
	 * and on an item quoting five percent wide an ask one percent under the top of the book is
	 * still queued behind everybody. So those three cases get the standing bid, a coin under it and
	 * the midpoint added to the search. The fill model still judges every one of them, so a slower
	 * price that keeps more of the position's value can still win.
	 *
	 * @param marketBuy the instant-sell price, or 0 when the quote has no bid side
	 */
	static List<Integer> exitPrices(int marketSell, int marketBuy, boolean urgent)
	{
		return exitPrices(marketSell, marketBuy, urgent, 0, 0);
	}

	/**
	 * @param breakEven the price at which this position stops losing money, or 0 when unknown
	 * @param target    the exit the trade was opened for, or 0 when there was never a plan
	 */
	static List<Integer> exitPrices(int marketSell, int marketBuy, boolean urgent, int breakEven,
		int target)
	{
		List<Integer> prices = new ArrayList<>(SELL_OFFSETS.length + 8);
		for (double offset : SELL_OFFSETS)
		{
			add(prices, marketSell + (int) Math.round(marketSell * offset));
		}

		// The two prices that decide whether this trade made money, neither of which the offsets
		// above can reach.
		//
		// <b>The grid tops out a fifth of a percent over the market, and break-even is cost plus two
		// percent of tax.</b> So unless the price had already risen since the buy, no price in the
		// search made money and the best the engine could do was pick the smallest loss -- which it
		// then reported as the recommendation. Live, on 696 Anti-venom(4) bought at 10,767 with the
		// market at 10,871: break-even 10,986, grid maximum 10,893, recommendation 10,893, expected
		// profit <b>minus 63,300 gp</b>, on a position eighteen minutes old whose stop was more than
		// a thousand gp lower. That was not a judgement about this item; it is arithmetic, and it
		// applied to every position the market had not already carried upwards.
		//
		// Adding them puts a profitable answer in front of the search. Whether it wins is still the
		// fill model's call -- a break-even price nobody will pay scores badly and loses to a better
		// one -- but it can no longer be absent from the question.
		if (breakEven > 0)
		{
			add(prices, breakEven);
			add(prices, breakEven + (int) Math.round(breakEven * 0.002));
			add(prices, breakEven + (int) Math.round(breakEven * 0.006));
		}
		if (target > breakEven && target > 0)
		{
			add(prices, target);
			// Between break-even and the target, so giving up on the plan is not all-or-nothing.
			if (breakEven > 0)
			{
				add(prices, (breakEven + target) / 2);
			}
		}

		if (urgent && marketBuy > 0 && marketBuy < marketSell)
		{
			add(prices, (marketBuy + marketSell) / 2);
			add(prices, marketBuy);
			add(prices, marketBuy - 1);
		}
		return prices;
	}

	private static void add(List<Integer> prices, int price)
	{
		int clamped = Math.max(1, price);
		if (!prices.contains(clamped))
		{
			prices.add(clamped);
		}
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
