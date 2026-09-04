package com.flippingfriend.model;

/**
 * The price below which an item stops being worth less.
 *
 * <p>High Level Alchemy turns an item into a fixed number of coins — the wiki publishes the figure
 * per item and it does not move between game updates. That makes it the only genuinely exogenous
 * price in this system: every other number here is derived from what the market did, and this one is
 * decided by Jagex and is the same tomorrow. An item whose Grand Exchange price falls far enough
 * below it gets bought and alched, which is real demand, which is why the floor holds.
 *
 * <p>What that buys a flipper is not a trade. It is a <b>bounded downside</b>. The unwind cost of a
 * stranded position is otherwise an estimate — how far might the price drift while we are stuck —
 * and for an alchable item it stops being an estimate below a certain point. Sizing, the stop, and
 * the expected value of every candidate all read that number.
 *
 * <h2>Three things this gets right that a subtraction does not</h2>
 *
 * <p><b>Alching pays no tax.</b> This is the one that matters and the one most easily missed. Selling
 * on the Grand Exchange costs 2%; alching is not a sale and costs nothing. So an alch that yields
 * {@code A} coins is worth more than a Grand Exchange sale at {@code A} — it is worth the same as a
 * sale at roughly {@code A / 0.98}. Comparing the alch value directly against a bid understates the
 * floor by the whole tax, on exactly the marginal items where the floor decides the trade.
 *
 * <p><b>The nature rune is the cost, and it moves.</b> Every cast consumes one, and the rune is
 * itself a traded item whose price the planner already has in front of it. Hardcoding a rune price
 * would put a stale number under every downside estimate in the system, and the runes are one of the
 * more actively traded items in the game.
 *
 * <p><b>The floor has a throughput.</b> A cast takes five ticks, so an hour of alching is about
 * {@value #CASTS_PER_HOUR} items. A holding of two hundred is protected; a holding of forty thousand
 * is protected in principle and not within any horizon a flip lives in. Treating an unlimited
 * quantity as floored is how a bounded downside becomes a fictional one, so the bound is applied to
 * the quantity that could actually be alched and the rest takes the market's price.
 *
 * <h2>What it assumes</h2>
 *
 * <p>That the player will alch rather than eat the loss — which requires 55 Magic, a fire staff and
 * a stock of nature runes, and above all the willingness. It is a floor available to them, not one
 * the market guarantees them. That is why it bounds the <em>unwind</em> estimate rather than being
 * treated as a price anyone can sell at.
 */
public final class AlchemyFloor
{
	/**
	 * Casts an hour, at five ticks each.
	 * <p>
	 * 1,200 rather than the higher figures quoted for tick-manipulated alching, because the number
	 * is being used to decide how much of a position is genuinely protected and the safe direction
	 * to be wrong in is downward.
	 */
	public static final int CASTS_PER_HOUR = 1_200;

	/** Nature runes when the live price is unavailable, which is roughly where they have sat. */
	public static final int DEFAULT_NATURE_RUNE_PRICE = 100;

	private final int natureRunePrice;
	private final TaxCalculator tax;

	public AlchemyFloor(TaxCalculator tax, int natureRunePrice)
	{
		this.tax = tax;
		this.natureRunePrice = natureRunePrice > 0 ? natureRunePrice : DEFAULT_NATURE_RUNE_PRICE;
	}

	/** The rune price this floor is being computed against, for reporting. */
	public int natureRunePrice()
	{
		return natureRunePrice;
	}

	/**
	 * Coins in hand from alching one of these, after paying for the rune.
	 *
	 * @param highAlch the wiki's published high alchemy value, or 0 when the item cannot be alched
	 * @return net coins per item, or 0 when alching it is worthless or impossible
	 */
	public int netAlchValue(int highAlch)
	{
		if (highAlch <= 0)
		{
			return 0;
		}
		return Math.max(0, highAlch - natureRunePrice);
	}

	/**
	 * The Grand Exchange price this floor supports, which is higher than the alch value itself.
	 *
	 * <p>A player indifferent between alching and selling compares coins with coins: alching yields
	 * {@code net} untaxed, and a Grand Exchange sale at {@code p} yields {@code p} minus 2%. So the
	 * price at which the two are equal is above {@code net}, and any bid below that is one a holder
	 * should refuse — they have a better exit in their inventory.
	 *
	 * <p>Computed by walking up from the closed form rather than dividing, for the same reason
	 * {@link TaxCalculator#breakEvenSellPrice} does: the fee is floored to whole coins and the cap
	 * makes the relationship non-linear at the top, so the exact answer is a few coins from where
	 * the arithmetic puts it.
	 *
	 * @return the equivalent Grand Exchange price, or 0 when the item cannot be alched
	 */
	public int supportedPrice(int itemId, int highAlch)
	{
		int net = netAlchValue(highAlch);
		if (net <= 0)
		{
			return 0;
		}
		// Start just below the closed form and step up to the first price that genuinely clears.
		long price = Math.max(1, (long) Math.floor(net / (1.0 - TaxCalculator.TAX_RATE)) - 3);
		for (int step = 0; step < 16; step++)
		{
			long proceeds = price - tax.taxPerItem(itemId, (int) Math.min(Integer.MAX_VALUE, price));
			if (proceeds >= net)
			{
				return (int) price;
			}
			price++;
		}
		return (int) price;
	}

	/**
	 * How many of a holding the floor actually reaches within the time available.
	 *
	 * <p>The floor is a rate, not a guarantee. A position larger than an hour or two of casting is
	 * only partly floored, and pretending otherwise turns a bounded downside back into a fictional
	 * one — precisely on the large positions where being wrong is most expensive.
	 */
	public int alchableWithin(int quantity, double hours)
	{
		if (quantity <= 0 || hours <= 0)
		{
			return 0;
		}
		double casts = CASTS_PER_HOUR * hours;
		return (int) Math.max(0, Math.min(quantity, Math.floor(casts)));
	}

	/**
	 * What a holding is really worth if it has to be given up, with the alch floor taken into
	 * account.
	 *
	 * <p>Each item leaves by whichever route pays more — the bid net of tax, or the furnace — and the
	 * furnace only takes as many as there is time to cast on.
	 *
	 * @param bidPrice what the market is currently paying
	 * @param hours    how long there is to get out
	 * @return total coins recovered from the whole holding
	 */
	public long unwindValue(int itemId, int highAlch, int bidPrice, int quantity, double hours)
	{
		if (quantity <= 0)
		{
			return 0;
		}
		long perItemOnMarket = Math.max(0, bidPrice - tax.taxPerItem(itemId, Math.max(1, bidPrice)));
		int net = netAlchValue(highAlch);
		if (net <= perItemOnMarket)
		{
			// The market is paying better than the furnace, which is the ordinary case and the reason
			// the floor is a floor rather than a plan.
			return perItemOnMarket * quantity;
		}

		int alched = alchableWithin(quantity, hours);
		return (long) net * alched + perItemOnMarket * (long) (quantity - alched);
	}

	/**
	 * True when the market is paying less for this item than burning it would.
	 * <p>
	 * Worth saying out loud rather than only feeding into a number: it means the quoted price is
	 * beneath a floor the game itself sets, which is a different situation from an item that is
	 * merely cheap, and it is the strongest downside signal available anywhere in this system.
	 */
	public boolean isBelowFloor(int itemId, int highAlch, int bidPrice)
	{
		int supported = supportedPrice(itemId, highAlch);
		return supported > 0 && bidPrice < supported;
	}
}
