package com.flippingfriend.session;

import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.model.TaxCalculator;

/**
 * How much the session is currently down, which is what the drawdown circuit breaker acts on.
 * <p>
 * Realised profit alone is not enough. A session that has sold nothing but is holding a position
 * that has fallen twenty percent has plainly lost money, and a breaker that only counts closed
 * trades would keep buying all the way down — precisely the situation it exists to stop. So open
 * positions are marked to what they would fetch if sold now: the current instant-sell price, net of
 * Grand Exchange tax.
 * <p>
 * Marking at the bid does make a freshly bought position show a small loss straight away, because
 * the spread you are trying to capture has not been captured yet. That is not an artefact to correct
 * for — it is the honest answer to "what is this worth if I had to get out right now", which is the
 * only question a risk limit should be asking. The drag is on the order of the spread, a few percent
 * of deployed capital, and the breaker sits at fifteen percent of total equity, so it does not put
 * the two anywhere near each other.
 * <p>
 * Positions whose cost is unknown — items that were already in the bank when the plugin first ran —
 * are skipped entirely rather than guessed at. There is no cost basis to compare against, so any
 * number here would be invented, and an invented loss could freeze trading for no reason.
 */
public final class SessionRisk
{
	private SessionRisk()
	{
	}

	/**
	 * Coins lost this session, counting both closed trades and the current value of open positions.
	 * Never negative: a session in profit has no drawdown, it simply has no loss to budget against.
	 */
	public static long markedDrawdown(SessionStats stats, PositionBook positions,
		MarketSnapshot market, TaxCalculator tax)
	{
		long realised = stats == null ? 0 : stats.getProfit();
		long unrealised = unrealisedProfit(positions, market, tax);
		long net = realised + unrealised;
		return net < 0 ? -net : 0;
	}

	/** Mark-to-market profit on everything still held, at what it would net if sold now. */
	private static long unrealisedProfit(PositionBook positions, MarketSnapshot market,
		TaxCalculator tax)
	{
		if (positions == null || market == null || !market.isUsable())
		{
			return 0;
		}

		long total = 0;
		for (Position position : positions.all())
		{
			if (!position.isCostKnown() || position.getQuantity() <= 0)
			{
				continue;
			}
			LatestPrice price = market.latest(position.getItemId());
			// getLow() is a nullable Integer -- an item with no recorded instant-sell has no low at
			// all -- and this is the first statement in the engine's refresh worker. Unboxing a null
			// here threw out of the whole cycle before the account publish, the plan refresh, the
			// suggestion and the walkthrough, leaving the panel frozen on stale data with one line in
			// the client log. A missing quote is not an error; it is an item we cannot mark today.
			Integer low = price == null ? null : price.getLow();
			if (low == null || low <= 0)
			{
				// No believable quote: leave it at cost rather than inventing a move either way.
				continue;
			}
			long proceeds = tax.netProceeds(position.getItemId(), low,
				position.getQuantity());
			total += proceeds - position.getTotalCost();
		}
		return total;
	}
}
