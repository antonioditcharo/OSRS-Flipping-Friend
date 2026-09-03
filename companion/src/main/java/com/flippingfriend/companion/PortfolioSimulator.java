package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Replays a strategy against stored bars, simulating slots, buy limits, capital and partial fills.
 *
 * <p>Recovered on 2 September 2026 from {@code flipping-friend-trainer.jar} and verified by bytecode
 * diff against the original. The javadoc is reconstructed; the code is exact.
 *
 * <p>{@link Strategy} is an interface, and that is the important thing about this class: the
 * simulator has no opinion about what picks the trades. Anything that can turn quoted items into
 * {@link PortfolioCandidate}s can be replayed against the same bars — which is what makes it possible
 * to score the ONNX fill path against the analytical {@code FillModel} on identical data, without
 * involving the learner that ONNX replaced.
 *
 * <p>{@link Result} records predictions alongside outcomes rather than profit alone.
 * {@link Result#slotHourBias}, {@link Result#profitBias} and the gap between
 * {@link Result#predictedCompletionRate} and {@link Result#actualCompletionRate} are the comparison
 * the audit says has never been made: what the model claimed, against what happened. Profit is far
 * too noisy to steer by; those ratios are not.
 *
 * <p>Time advances in fixed steps. At each one the visible history is moved forward, resting orders
 * are settled against that bar, equity is marked, and new orders are placed only if the drawdown
 * freeze has not fired. Fills are partial and priced by where the order sits inside the bar's
 * dispersion — see {@link #fillable} — so an order at a bad price fills slowly rather than not at all.
 */
final class PortfolioSimulator
{
	static final String HOURLY = "1h";
	static final String FIVE_MINUTE = "5m";

	/** Matches PortfolioPlanner.DRAWDOWN_LIMIT. A replay that ignores the breaker is not the shipping system. */
	private static final double DRAWDOWN_LIMIT = 0.15;

	private final String step;
	private final long stepSeconds;
	private final double horizonHours;
	private final double captureRate;
	private final ReplaySeriesSource history;
	private final Map<Integer, MarketIngestionService.Item> universe;
	private final TaxCalculator tax;
	private final Strategy strategy;

	PortfolioSimulator(ReplaySeriesSource history, Map<Integer, MarketIngestionService.Item> universe,
		TaxCalculator tax, Strategy strategy, String step, long stepSeconds, double horizonHours,
		double captureRate)
	{
		this.horizonHours = horizonHours;
		this.captureRate = captureRate;
		this.history = history;
		this.universe = universe;
		this.tax = tax;
		this.strategy = strategy;
		this.step = step;
		this.stepSeconds = stepSeconds;
	}

	Result run(long fromEpochSeconds, long toEpochSeconds, long startingCoins, int slots)
	{
		long coins = startingCoins;
		List<OpenOrder> open = new ArrayList<>();
		BuyLimitLedger limits = new BuyLimitLedger();
		Result result = new Result(strategy.name(), startingCoins);

		// Start at a bar that exists rather than at a clock time nothing was recorded at.
		for (long now = history.firstBarAtOrAfter(step, fromEpochSeconds); now <= toEpochSeconds; now += stepSeconds)
		{
			history.seekTo(now);
			coins += settle(open, now, result);

			// Equity is coins plus stock at cost, so the freeze reacts to money committed rather than
			// to cash alone - otherwise placing an order would look like a loss.
			long heldAtCost = 0L;
			for (OpenOrder order : open)
			{
				heldAtCost += (long) order.buyPrice * (long) Math.max(0, order.quantity - order.soldQuantity);
			}
			long equity = coins + heldAtCost;
			result.markEquity(equity);

			boolean frozen = startingCoins > 0L
				&& startingCoins - equity >= (long) ((double) startingCoins * DRAWDOWN_LIMIT);
			if (frozen)
			{
				result.frozenSteps++;
			}

			int freeSlots = slots - open.size();
			if (!frozen && freeSlots > 0 && coins > 0L)
			{
				coins -= place(open, limits, coins, freeSlots, now, result);
			}

			result.slotHoursOccupied += (double) open.size() * ((double) stepSeconds / 3600.0);
			result.hoursSimulated += (double) stepSeconds / 3600.0;
		}

		coins += liquidate(open, toEpochSeconds, result);
		result.endingCoins = coins;
		return result;
	}

	/**
	 * Advances every resting order by one step. A buy that never completes inside the horizon releases
	 * its unspent capital and either dies or converts to a smaller position; a sell that never
	 * completes is unwound at the bar's bid.
	 */
	private long settle(List<OpenOrder> open, long now, Result result)
	{
		long released = 0L;
		Iterator<OpenOrder> iterator = open.iterator();
		while (iterator.hasNext())
		{
			OpenOrder order = iterator.next();
			Candle bar = history.barAt(order.itemId, step, now);
			order.hoursOpen += (double) stepSeconds / 3600.0;

			if (!order.bought)
			{
				int filled = fillable(bar, order.buyPrice, true, order.remaining());
				order.filled += filled;
				if (order.filled >= order.quantity)
				{
					order.bought = true;
					order.buyHours = order.hoursOpen;
					result.buysCompleted++;
					continue;
				}
				if (order.hoursOpen >= horizonHours)
				{
					released += (long) order.buyPrice * (long) order.remaining();
					result.buysAbandoned++;
					if (order.filled <= 0)
					{
						// Nothing filled: the slot was spent and no position exists.
						result.realizedSlotHours += order.hoursOpen;
						iterator.remove();
						continue;
					}
					// Partly filled: keep what was bought and sell that instead.
					order.quantity = order.filled;
					order.bought = true;
					order.buyHours = order.hoursOpen;
				}
				continue;
			}

			int sold = fillable(bar, order.sellPrice, false, order.quantity - order.soldQuantity);
			long receipt = proceeds(order, order.sellPrice, sold);
			order.grossProceeds += receipt;
			released += receipt;
			order.soldQuantity += sold;
			if (order.soldQuantity >= order.quantity)
			{
				bookProfit(order, result, true);
				result.realizedSlotHours += order.hoursOpen;
				result.actualCompletions++;
				iterator.remove();
				continue;
			}
			if (order.hoursOpen - order.buyHours >= horizonHours)
			{
				// Unwind at the bid, which is what dumping into the book actually gets.
				int exit = bar != null && bar.getAvgLowPrice() != null ? bar.getAvgLowPrice() : order.buyPrice;
				int outstanding = order.quantity - order.soldQuantity;
				long recovered = proceeds(order, exit, outstanding);
				order.grossProceeds += recovered;
				released += recovered;
				bookProfit(order, result, false);
				result.realizedSlotHours += order.hoursOpen;
				result.sellsUnwound++;
				iterator.remove();
			}
		}
		return released;
	}

	/** Asks the strategy for candidates and opens whichever the live constraints actually admit. */
	private long place(List<OpenOrder> open, BuyLimitLedger limits, long coins, int freeSlots, long now,
		Result result)
	{
		List<CandidateFactory.QuotedItem> quoted = quoteUniverse(now);
		if (quoted.isEmpty())
		{
			return 0L;
		}

		Map<Integer, Integer> remaining = limits.remaining(universe);
		// One position per item: an item already held offers no remaining limit this pass.
		for (OpenOrder order : open)
		{
			remaining.put(order.itemId, 0);
		}

		List<PortfolioCandidate> chosen = strategy.choose(quoted, remaining, coins, freeSlots,
			Instant.ofEpochSecond(now));
		int slotCap = open.size() + freeSlots;
		long committed = 0L;
		for (PortfolioCandidate candidate : chosen)
		{
			long cost = candidate.getCapitalRequired();
			if (cost <= 0L || committed + cost > coins || open.size() >= slotCap)
			{
				continue;
			}
			committed += cost;
			// Recorded at placement so the prediction can be compared with the outcome later.
			result.predictedSlotHours += candidate.expectedSlotHours();
			result.predictedProfit += candidate.expectedProfit();
			result.predictedCompletions += candidate.getCompletionProbability();
			open.add(new OpenOrder(candidate, now));
			limits.apply(OfferEvent.builder("replay", now, "BOUGHT")
				.item(candidate.getItemId(), candidate.getItemName())
				.buying(true)
				.quantities(candidate.getQuantity(), candidate.getQuantity())
				.build());
			result.ordersPlaced++;
		}
		return committed;
	}

	/** Closes everything still open at the end of the window, so the result is not flattered by stock in hand. */
	private long liquidate(List<OpenOrder> open, long at, Result result)
	{
		long released = 0L;
		for (OpenOrder order : open)
		{
			if (!order.bought)
			{
				released += (long) order.buyPrice * (long) order.remaining();
				result.buysAbandoned++;
				if (order.filled <= 0)
				{
					result.realizedSlotHours += order.hoursOpen;
					continue;
				}
				order.quantity = order.filled;
				order.bought = true;
			}
			Candle bar = lastBarAtOrBefore(order.itemId, at);
			int exit = bar != null && bar.getAvgLowPrice() != null ? bar.getAvgLowPrice() : order.buyPrice;
			int outstanding = order.quantity - order.soldQuantity;
			long recovered = proceeds(order, exit, outstanding);
			order.grossProceeds += recovered;
			released += recovered;
			bookProfit(order, result, false);
			result.realizedSlotHours += order.hoursOpen;
			result.unwoundAtEnd++;
		}
		open.clear();
		return released;
	}

	private void bookProfit(OpenOrder order, Result result, boolean completed)
	{
		long cost = (long) order.buyPrice * (long) order.quantity;
		long profit = order.grossProceeds - cost;
		result.netProfit += profit;
		result.flips++;
		if (profit > 0L)
		{
			result.wins++;
		}
		if (completed)
		{
			result.sellsCompleted++;
		}
	}

	/** Net of tax, using the same calculator the live engine uses. */
	private long proceeds(OpenOrder order, int price, int quantity)
	{
		return quantity <= 0 ? 0L : tax.netProceeds(order.itemId, price, quantity);
	}

	/**
	 * How much of a bar's volume this order wins.
	 *
	 * <p>A bar reports one VWAP per side, but trades happened across a spread around it. Treating the
	 * VWAP as the only price makes a fill binary — all or nothing on a single comparison — which is
	 * both wrong and unstable. Instead the half-spread is taken as a dispersion and the order's price
	 * is located inside that band, giving a share between 0 and 1 that degrades smoothly as the quote
	 * gets worse. Only when there is no spread to work with does it fall back to the binary test.
	 *
	 * <p>{@code captureRate} is the fraction of the flow at your price you actually win — the model's
	 * one irreducible blind spot, since the feed publishes what traded and not the book.
	 */
	private int fillable(Candle bar, int price, boolean buying, int wanted)
	{
		if (bar == null || wanted <= 0)
		{
			return 0;
		}
		Integer barPrice = buying ? bar.getAvgLowPrice() : bar.getAvgHighPrice();
		int volume = buying ? bar.getLowPriceVolume() : bar.getHighPriceVolume();
		if (barPrice == null || barPrice <= 0 || volume <= 0)
		{
			return 0;
		}

		Integer bid = bar.getAvgLowPrice();
		Integer ask = bar.getAvgHighPrice();
		int dispersion = bid != null && ask != null && ask > bid ? (ask - bid) / 2 : 0;

		double share;
		if (dispersion <= 0)
		{
			share = (buying ? barPrice <= price : barPrice >= price) ? 1.0 : 0.0;
		}
		else
		{
			share = buying
				? (double) (price - (barPrice - dispersion)) / (2.0 * (double) dispersion)
				: (double) (barPrice + dispersion - price) / (2.0 * (double) dispersion);
			share = share < 0.0 ? 0.0 : (share > 1.0 ? 1.0 : share);
		}
		if (share <= 0.0)
		{
			return 0;
		}
		return (int) Math.min((double) wanted, Math.max(0.0, (double) volume * captureRate * share));
	}

	/** Builds the quoted universe as the engine would have seen it at this instant. */
	private List<CandidateFactory.QuotedItem> quoteUniverse(long now)
	{
		List<CandidateFactory.QuotedItem> quoted = new ArrayList<>();
		for (Map.Entry<Integer, MarketIngestionService.Item> entry : universe.entrySet())
		{
			Candle bar = history.barAt(entry.getKey(), step, now);
			if (bar == null || bar.getAvgHighPrice() == null || bar.getAvgLowPrice() == null)
			{
				continue;
			}
			LatestPrice quote = new LatestPrice(bar.getAvgHighPrice(), now, bar.getAvgLowPrice(), now);
			quoted.add(new CandidateFactory.QuotedItem(entry.getValue(), quote, bar, bar));
		}
		return quoted;
	}

	/** Walks back through the visible series only, so this cannot see past the cutoff either. */
	private Candle lastBarAtOrBefore(int itemId, long at)
	{
		List<Candle> visible = history.series(itemId, step);
		for (int i = visible.size() - 1; i >= 0; i--)
		{
			if (visible.get(i).getTimestamp() <= at)
			{
				return visible.get(i);
			}
		}
		return null;
	}

	/** Whatever chooses the trades. The simulator does not care how. */
	interface Strategy
	{
		List<PortfolioCandidate> choose(List<CandidateFactory.QuotedItem> quoted,
			Map<Integer, Integer> buyLimitRemaining, long coins, int freeSlots, Instant now);

		String name();
	}

	/**
	 * Outcome of one replay. Predictions are accumulated at placement and outcomes as they resolve, so
	 * the bias ratios below compare the two over identical trades.
	 */
	static final class Result
	{
		private final String strategy;
		private final long startingCoins;
		private long endingCoins;
		private long lowWaterEquity = Long.MAX_VALUE;
		private int frozenSteps;
		private long netProfit;
		private int flips;
		private int wins;
		private int ordersPlaced;
		private int buysCompleted;
		private int buysAbandoned;
		private int sellsCompleted;
		private int unwoundAtEnd;
		private int sellsUnwound;
		private double slotHoursOccupied;
		private double hoursSimulated;
		private double predictedSlotHours;
		private double predictedProfit;
		private double predictedCompletions;
		private double realizedSlotHours;
		private int actualCompletions;

		void markEquity(long equity)
		{
			lowWaterEquity = Math.min(lowWaterEquity, equity);
		}

		Result(String strategy, long startingCoins)
		{
			this.strategy = strategy;
			this.startingCoins = startingCoins;
		}

		String getStrategy()
		{
			return strategy;
		}

		long getNetProfit()
		{
			return netProfit;
		}

		int getFlips()
		{
			return flips;
		}

		int getOrdersPlaced()
		{
			return ordersPlaced;
		}

		double getPredictedCompletions()
		{
			return predictedCompletions;
		}

		int getActualCompletions()
		{
			return actualCompletions;
		}

		int getBuysCompleted()
		{
			return buysCompleted;
		}

		int getBuysAbandoned()
		{
			return buysAbandoned;
		}

		double getSlotHoursOccupied()
		{
			return slotHoursOccupied;
		}

		double getPredictedProfit()
		{
			return predictedProfit;
		}

		double getPredictedSlotHours()
		{
			return predictedSlotHours;
		}

		double getRealizedSlotHours()
		{
			return realizedSlotHours;
		}

		int getSellsUnwound()
		{
			return sellsUnwound;
		}

		int getFrozenSteps()
		{
			return frozenSteps;
		}

		/** Above 1.0 means trades really took longer than the model said. */
		double slotHourBias()
		{
			return predictedSlotHours <= 0.0 ? 0.0 : realizedSlotHours / predictedSlotHours;
		}

		/** Below 1.0 means the model was optimistic about profit. */
		double profitBias()
		{
			return predictedProfit <= 0.0 ? 0.0 : (double) netProfit / predictedProfit;
		}

		double predictedCompletionRate()
		{
			return ordersPlaced <= 0 ? 0.0 : predictedCompletions / (double) ordersPlaced;
		}

		double actualCompletionRate()
		{
			return ordersPlaced <= 0 ? 0.0 : (double) actualCompletions / (double) ordersPlaced;
		}

		double getHoursSimulated()
		{
			return hoursSimulated;
		}

		long getEndingCoins()
		{
			return endingCoins;
		}

		double gpPerSlotHour()
		{
			return slotHoursOccupied <= 0.0 ? 0.0 : (double) netProfit / slotHoursOccupied;
		}

		double gpPerHour()
		{
			return hoursSimulated <= 0.0 ? 0.0 : (double) netProfit / hoursSimulated;
		}

		double winRate()
		{
			return flips <= 0 ? 0.0 : (double) wins / (double) flips;
		}

		double buyCompletionRate()
		{
			return ordersPlaced <= 0 ? 0.0 : (double) buysCompleted / (double) ordersPlaced;
		}

		/** Measured against the low-water mark, not just the endpoints, so an intra-run dip still counts. */
		double drawdownFraction()
		{
			long low = Math.min(Math.min(startingCoins, endingCoins), lowWaterEquity);
			return startingCoins <= 0L ? 0.0 : Math.max(0.0, (double) (startingCoins - low) / (double) startingCoins);
		}
	}

	private static final class OpenOrder
	{
		private final int itemId;
		private final int buyPrice;
		private final int sellPrice;
		private final double predictedSlotHours;
		private final double predictedProfit;
		private final double predictedCompletion;
		private int quantity;
		private int filled;
		private int soldQuantity;
		private long grossProceeds;
		private boolean bought;
		private double hoursOpen;
		private double buyHours;

		OpenOrder(PortfolioCandidate candidate, long placedAt)
		{
			this.itemId = candidate.getItemId();
			this.buyPrice = candidate.getBuyPrice();
			this.sellPrice = candidate.getSellPrice();
			this.quantity = candidate.getQuantity();
			this.predictedSlotHours = candidate.expectedSlotHours();
			this.predictedProfit = candidate.expectedProfit();
			this.predictedCompletion = candidate.getCompletionProbability();
		}

		int remaining()
		{
			return Math.max(0, quantity - filled);
		}
	}
}
