package com.flippingfriend.model;

import com.flippingfriend.data.ItemMetadata;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Grand Exchange sale tax, computed exactly rather than approximately.
 * <p>
 * Getting this slightly wrong is the single most common way a flipping tool produces confident,
 * losing suggestions: a 2% error is larger than the entire margin on most high-volume items. So the
 * rules are implemented literally as the game applies them.
 * <ul>
 *   <li>2% of the sale price, charged to the seller only, since 29 May 2025 (it was 1% before).</li>
 *   <li>Rounded <em>down</em> to a whole coin, per item, before multiplying by quantity. That is
 *       why anything sold below 50 gp is untaxed, and why a price that is an exact multiple of 50
 *       can be undercut by one coin for free.</li>
 *   <li>Capped at 5,000,000 gp per item, so anything at or above 250,000,000 pays a flat 5m.</li>
 *   <li>A fixed list of items is exempt entirely.</li>
 * </ul>
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Grand_Exchange#Convenience_fee_and_item_sink">Convenience fee</a>
 */
@Singleton
public class TaxCalculator
{
	public static final double TAX_RATE = 0.02;
	public static final int MAX_TAX_PER_ITEM = 5_000_000;
	/** Above this sale price the per-item cap binds and the rate stops mattering. */
	public static final int CAP_THRESHOLD = (int) (MAX_TAX_PER_ITEM / TAX_RATE);

	/**
	 * Items the game never taxes. Held as names and resolved against the wiki mapping at runtime,
	 * because item ids would need hand-maintaining every time Jagex extends this list, whereas the
	 * names come straight from the wiki page that documents it.
	 */
	private static final Set<String> EXEMPT_NAMES = new HashSet<>(Arrays.asList(
		// Bonds
		"old school bond",
		// Low level combat consumables
		"bronze arrow", "bronze dart", "iron arrow", "iron dart", "mind rune", "steel arrow", "steel dart",
		// Low level food
		"bass", "bread", "cake", "cooked chicken", "cooked meat", "herring", "lobster", "mackerel",
		"meat pie", "pike", "salmon", "shrimps", "tuna",
		// Teleports
		"ardougne teleport", "camelot teleport", "civitas illa fortis teleport", "falador teleport",
		"games necklace(8)", "kourend castle teleport", "lumbridge teleport", "ring of dueling(8)",
		"teleport to house", "varrock teleport",
		// Tools
		"chisel", "gardening trowel", "glassblowing pipe", "hammer", "needle", "pestle and mortar",
		"rake", "saw", "secateurs", "seed dibber", "shears", "spade", "watering can"));

	/** Exempt regardless of dose, so "Energy potion(4)" and friends all match. */
	private static final Set<String> EXEMPT_DOSED = new HashSet<>(Collections.singletonList("energy potion"));

	private volatile Set<Integer> exemptIds = Collections.emptySet();

	/**
	 * Resolves the exempt names against the loaded item mapping. Safe to call repeatedly; until it
	 * is called the calculator simply taxes everything, which errs towards under-promising profit.
	 */
	public void resolveExemptions(Collection<ItemMetadata> items)
	{
		Set<Integer> resolved = new HashSet<>();
		for (ItemMetadata item : items)
		{
			if (isExemptName(item.getName()))
			{
				resolved.add(item.getId());
			}
		}
		exemptIds = Collections.unmodifiableSet(resolved);
	}

	static boolean isExemptName(String rawName)
	{
		if (rawName == null)
		{
			return false;
		}
		String name = rawName.toLowerCase(Locale.ROOT).trim();
		if (EXEMPT_NAMES.contains(name))
		{
			return true;
		}

		int dose = name.lastIndexOf('(');
		if (dose > 0 && name.endsWith(")"))
		{
			return EXEMPT_DOSED.contains(name.substring(0, dose).trim());
		}
		return EXEMPT_DOSED.contains(name);
	}

	public boolean isExempt(int itemId)
	{
		return exemptIds.contains(itemId);
	}

	public int exemptItemCount()
	{
		return exemptIds.size();
	}

	/** Tax charged on a single item sold at {@code sellPrice}. */
	public int taxPerItem(int itemId, int sellPrice)
	{
		if (sellPrice <= 0 || isExempt(itemId))
		{
			return 0;
		}
		if (sellPrice >= CAP_THRESHOLD)
		{
			return MAX_TAX_PER_ITEM;
		}
		// Integer arithmetic so the floor is exact; sellPrice * 2 cannot overflow a long here.
		return (int) ((long) sellPrice * 2L / 100L);
	}

	public long taxFor(int itemId, int sellPrice, int quantity)
	{
		return (long) taxPerItem(itemId, sellPrice) * Math.max(0, quantity);
	}

	/** What actually lands in your coin pouch after selling {@code quantity} at {@code sellPrice}. */
	public long netProceeds(int itemId, int sellPrice, int quantity)
	{
		long gross = (long) sellPrice * Math.max(0, quantity);
		return gross - taxFor(itemId, sellPrice, quantity);
	}

	/** Profit per item after tax. Negative when the spread does not cover the fee. */
	public long netMarginPerItem(int itemId, int buyPrice, int sellPrice)
	{
		return (long) sellPrice - taxPerItem(itemId, sellPrice) - buyPrice;
	}

	public long netProfit(int itemId, int buyPrice, int sellPrice, int quantity)
	{
		return netMarginPerItem(itemId, buyPrice, sellPrice) * Math.max(0, quantity);
	}

	/**
	 * Lowest sale price at which a flip bought at {@code buyPrice} does not lose money. Worth
	 * showing in the UI, because "you need at least X to break even" is the number a new flipper
	 * actually needs.
	 * <p>
	 * There are two regimes and they have to be handled separately. Below the cap the fee is 2% of
	 * the price, so break-even is near {@code buy / 0.98} — but only near, because the fee is
	 * floored, so the exact answer is found by stepping the last few coins. At or above the cap the
	 * fee stops scaling and becomes a flat 5m, so break-even is simply the buy price plus that.
	 * Applying the 2% form there overstates the required price by millions, which is precisely the
	 * range where being wrong is most expensive.
	 */
	public int breakEvenSellPrice(int itemId, int buyPrice)
	{
		if (buyPrice <= 0)
		{
			return 0;
		}
		if (isExempt(itemId))
		{
			return buyPrice;
		}

		long best = Long.MAX_VALUE;

		// Uncapped band. The closed form is never more than a few coins away, so start just below
		// it and walk up to the first price that genuinely clears.
		long start = Math.max(buyPrice, (long) Math.floor(buyPrice / (1.0 - TAX_RATE)) - 3);
		for (long price = start; price < start + 16 && price < CAP_THRESHOLD; price++)
		{
			if (netMarginPerItem(itemId, buyPrice, (int) price) >= 0)
			{
				best = price;
				break;
			}
		}

		// Capped band, which is cheaper than the 2% form for anything expensive enough to reach it.
		long capped = (long) buyPrice + MAX_TAX_PER_ITEM;
		if (capped >= CAP_THRESHOLD && capped < best)
		{
			best = capped;
		}

		return (int) Math.min(Integer.MAX_VALUE, best == Long.MAX_VALUE ? capped : best);
	}
}
