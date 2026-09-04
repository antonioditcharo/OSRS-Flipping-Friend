package com.flippingfriend.model;

import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Prices a conversion the game itself offers: components into a set, doses into a potion.
 *
 * <p>This is a different profit source from flipping and it is worth saying why. A flip earns the
 * spread and needs the market to cooperate twice — somebody has to sell to you and somebody has to
 * buy from you, at prices that have not moved in between. A conversion earns the gap between what
 * the parts cost and what the whole is worth, and the conversion itself is <b>free, instant and
 * certain</b>: the Grand Exchange clerk packs a set on the spot, Bob Barter decants on the spot, and
 * neither can fail or move against you. The only market risk left is the buy leg and the sell leg,
 * which the flip already has.
 *
 * <h2>What the previous evaluation got wrong</h2>
 *
 * <p><b>The tax was 1%.</b> It is 2%, and every other line in this codebase knows that —
 * {@link TaxCalculator#TAX_RATE}. A second copy of the tax rule, written inline and half the real
 * rate, made every conversion look about twice as profitable as it is on thin margins and turned
 * losers into winners. That is the failure {@code OfferEvent}'s own javadoc warns about, arriving in
 * a new place: this class takes {@link TaxCalculator} and does not know what the rate is.
 *
 * <p><b>Nothing checked the buy limits.</b> A conversion needs every component, and a four-hour limit
 * on any one of them caps the whole run. A recipe reported as profitable that cannot be bought is a
 * suggestion the player cannot act on.
 *
 * <p><b>It always proposed one.</b> Quantity came from the output count, so a recipe worth running
 * two hundred times was advertised as a single conversion — the profit shown was per run and the
 * work of finding it was spent regardless.
 *
 * <p><b>It returned the first profitable recipe, not the best one.</b> A loop with a {@code return}
 * inside it ranks by registry order.
 *
 * <h2>Which side of the book</h2>
 *
 * <p>Inputs are priced at the instant-buy price and outputs at the instant-sell price: the
 * conversion is assumed to cross the spread on both legs. That is the pessimistic reading and it is
 * the right one to advertise on, because a conversion whose profit only exists if both legs are
 * patiently offered is a flip with extra steps, and the certainty is what makes this worth doing.
 */
public final class ConversionEngine
{
	/**
	 * How stale a quote may be before a conversion built on it is fiction.
	 * <p>
	 * Tighter than the flip path allows, because a conversion is advertised as near-certain profit.
	 * An hour-old quote on one component of a six-part set is enough to invert the whole answer, and
	 * the player will have already bought the other five by the time they find out.
	 */
	private static final long MAX_QUOTE_AGE_SECONDS = 20 * 60;

	private final TaxCalculator tax;

	public ConversionEngine(TaxCalculator tax)
	{
		this.tax = tax;
	}

	/** What one run of a recipe costs, earns and nets, and how many runs are actually possible. */
	public static final class Conversion
	{
		private final ConversionRecipe recipe;
		private final long costPerRun;
		private final long netRevenuePerRun;
		private final int runs;

		Conversion(ConversionRecipe recipe, long costPerRun, long netRevenuePerRun, int runs)
		{
			this.recipe = recipe;
			this.costPerRun = costPerRun;
			this.netRevenuePerRun = netRevenuePerRun;
			this.runs = runs;
		}

		public ConversionRecipe getRecipe()
		{
			return recipe;
		}

		/** Coins out per run, after Grand Exchange tax on everything sold. */
		public long getNetRevenuePerRun()
		{
			return netRevenuePerRun;
		}

		public long getCostPerRun()
		{
			return costPerRun;
		}

		public long getProfitPerRun()
		{
			return netRevenuePerRun - costPerRun;
		}

		/** Runs the buy limits and the player's coins actually allow. */
		public int getRuns()
		{
			return runs;
		}

		public long getTotalProfit()
		{
			return getProfitPerRun() * runs;
		}

		public long getCapitalRequired()
		{
			return costPerRun * runs;
		}

		public boolean isWorthDoing()
		{
			return runs > 0 && getProfitPerRun() > 0;
		}
	}

	/**
	 * Prices one recipe against the current book.
	 *
	 * @param quotes            the latest price per item id
	 * @param buyLimitRemaining what is left of each input's four-hour limit; absent means unlimited
	 * @param spendableCoins    the ceiling on capital
	 * @return the priced conversion, or null when it cannot be priced at all
	 */
	public Conversion evaluate(ConversionRecipe recipe, Map<Integer, LatestPrice> quotes,
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, Instant now)
	{
		if (recipe == null || quotes == null)
		{
			return null;
		}

		long costPerRun = 0;
		int runsAllowed = Integer.MAX_VALUE;
		for (Map.Entry<Integer, Integer> input : recipe.getInputs().entrySet())
		{
			LatestPrice quote = fresh(quotes.get(input.getKey()), now);
			if (quote == null)
			{
				return null;
			}
			int perUnit = quote.getHigh();
			int needed = input.getValue();
			costPerRun += (long) perUnit * needed;

			// Every component gates the whole run, so the binding limit is whichever is tightest.
			Integer remaining = buyLimitRemaining == null ? null : buyLimitRemaining.get(input.getKey());
			if (remaining != null)
			{
				runsAllowed = Math.min(runsAllowed, remaining / Math.max(1, needed));
			}
		}

		long netRevenuePerRun = 0;
		for (Map.Entry<Integer, Integer> output : recipe.getOutputs().entrySet())
		{
			LatestPrice quote = fresh(quotes.get(output.getKey()), now);
			if (quote == null)
			{
				return null;
			}
			int price = quote.getLow();
			// The one implementation of the tax rule, which knows about the cap, the exemptions and
			// the floor to whole coins. Nothing here needs to know the rate.
			long perUnit = price - tax.taxPerItem(output.getKey(), price);
			netRevenuePerRun += perUnit * output.getValue();
		}

		if (costPerRun <= 0)
		{
			return null;
		}
		if (runsAllowed == Integer.MAX_VALUE)
		{
			runsAllowed = Integer.MAX_VALUE / 2;
		}
		int affordable = (int) Math.max(0, Math.min(runsAllowed, spendableCoins / costPerRun));
		return new Conversion(recipe, costPerRun, netRevenuePerRun, affordable);
	}

	/**
	 * The most profitable conversion available right now, or null if none clears {@code minProfit}.
	 *
	 * <p>Ranked on profit per run rather than total, because the total is bounded by a buy limit that
	 * refreshes every four hours while the per-run margin is the thing that is actually true about
	 * the market. A recipe worth 40k a run and limited to two beats one worth 500 a run and limited
	 * to a thousand, for a player who will be back in four hours.
	 */
	public Conversion best(Iterable<ConversionRecipe> recipes, Map<Integer, LatestPrice> quotes,
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, long minProfit, Instant now)
	{
		Conversion best = null;
		for (ConversionRecipe recipe : recipes)
		{
			Conversion conversion = evaluate(recipe, quotes, buyLimitRemaining, spendableCoins, now);
			if (conversion == null || !conversion.isWorthDoing()
				|| conversion.getProfitPerRun() < minProfit)
			{
				continue;
			}
			if (best == null || conversion.getProfitPerRun() > best.getProfitPerRun())
			{
				best = conversion;
			}
		}
		return best;
	}

	/** Every priced conversion worth doing, for a panel that wants to show more than one. */
	public Map<String, Conversion> all(Iterable<ConversionRecipe> recipes,
		Map<Integer, LatestPrice> quotes, Map<Integer, Integer> buyLimitRemaining,
		long spendableCoins, long minProfit, Instant now)
	{
		Map<String, Conversion> found = new HashMap<>();
		for (ConversionRecipe recipe : recipes)
		{
			Conversion conversion = evaluate(recipe, quotes, buyLimitRemaining, spendableCoins, now);
			if (conversion != null && conversion.isWorthDoing()
				&& conversion.getProfitPerRun() >= minProfit)
			{
				found.put(recipe.getName(), conversion);
			}
		}
		return found;
	}

	/** A quote with both sides, both recent enough to buy and sell against. */
	private static LatestPrice fresh(LatestPrice quote, Instant now)
	{
		if (quote == null || !quote.isComplete() || quote.getHigh() <= 0 || quote.getLow() <= 0)
		{
			return null;
		}
		if (now != null && quote.stalestSideSeconds(now) > MAX_QUOTE_AGE_SECONDS)
		{
			return null;
		}
		return quote;
	}
}
