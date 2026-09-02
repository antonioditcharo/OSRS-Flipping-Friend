package com.flippingfriend.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sorts items into families that tend to move together.
 * <p>
 * Eight Grand Exchange slots holding eight different bars is one position, not eight. Whatever moves
 * the price of steel bars — a smithing update, a bot ban wave, a change to Blast Furnace — moves them
 * all at once, and a portfolio that looks diversified by item name can be entirely undiversified by
 * exposure. With a large bankroll spread across every slot, that is the difference between a bad hour
 * and a bad day.
 * <p>
 * The grouping is by name keyword rather than a proper taxonomy, because the price feed does not
 * publish item categories and inventing a full classification for four thousand items would be a
 * large amount of work to get slightly better answers. Keywords catch the families that actually
 * matter — the bulk skilling supplies where a player is most likely to end up concentrated — and
 * anything unrecognised is simply left ungrouped, which is the safe failure: it constrains nothing
 * that was not going to be correlated anyway.
 */
public final class ItemGroups
{
	/** Ungrouped items are capped individually but share no group budget. */
	public static final String UNGROUPED = "";

	/**
	 * Ordered, because the first match wins and some names contain several keywords — "adamant
	 * arrow" is ammunition before it is a metal. More specific families are therefore listed first.
	 */
	private static final Map<String, String[]> GROUPS = new LinkedHashMap<>();

	static
	{
		GROUPS.put("ammunition", new String[]{"arrow", "bolt", "dart", "javelin", "knife", "cannonball"});
		GROUPS.put("runes", new String[]{" rune", "rune)", "essence"});
		GROUPS.put("potions", new String[]{"potion", "brew", "restore", "antifire", "antipoison", "serum"});
		GROUPS.put("herbs", new String[]{"grimy", "clean ", "herb"});
		GROUPS.put("seeds", new String[]{"seed", "sapling", "spore"});
		GROUPS.put("logs", new String[]{"logs", "plank", "bark"});
		GROUPS.put("bars and ores", new String[]{" bar", "ore", "coal", "ingot"});
		GROUPS.put("food", new String[]{"shark", "lobster", "swordfish", "monkfish", "anglerfish",
			"karambwan", "tuna", "salmon", "trout", "pie", "cake", "bread", "stew"});
		GROUPS.put("hides and leather", new String[]{"hide", "leather", "vambraces", "chaps"});
		GROUPS.put("gems", new String[]{"sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx",
			"zenyte", "uncut"});
		GROUPS.put("bones and ashes", new String[]{"bones", "ashes"});
		GROUPS.put("teleports", new String[]{"teleport", "tablet"});
	}

	private ItemGroups()
	{
	}

	/**
	 * The family this item belongs to, or {@link #UNGROUPED} when nothing recognises it.
	 */
	public static String groupOf(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return UNGROUPED;
		}

		String name = itemName.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String[]> entry : GROUPS.entrySet())
		{
			for (String keyword : entry.getValue())
			{
				if (name.contains(keyword))
				{
					return entry.getKey();
				}
			}
		}
		return UNGROUPED;
	}

	public static boolean isGrouped(String itemName)
	{
		return !UNGROUPED.equals(groupOf(itemName));
	}
}
