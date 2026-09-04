package com.flippingfriend.model;

import com.flippingfriend.data.ItemMetadata;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every conversion the game offers that this system knows how to price.
 *
 * <p>Two kinds, and they are gathered in opposite ways.
 *
 * <p><b>Sets are curated.</b> Which four items make up Dharok's armour set is a fact about the game
 * that no naming convention will tell you, so the list is written out. It is also short and stable.
 *
 * <p><b>Decants are derived.</b> Every potion in the game comes in one, two, three and four dose
 * variants named {@code "Prayer potion(3)"}, and Bob Barter converts freely between them while
 * conserving total doses. Hand-listing those is a data-entry job that is never finished: the
 * previous registry covered <b>one</b> potion, Saradomin brew, in three of its twelve directions,
 * out of the hundred-odd potions in the game. Reading the item mapping instead covers all of them,
 * in both directions, and keeps covering them the week Jagex adds another.
 *
 * <p>The derivation is by convention, which is a real caveat rather than a footnote: a name matching
 * {@code Something(2)} is <em>almost</em> always a decantable potion and not quite always. Known
 * exceptions are excluded by name in {@link #NOT_DECANTABLE} and the list is expected to grow —
 * better a short list of exclusions that can be corrected than a long list of inclusions that is
 * always out of date.
 */
public final class ConversionRegistry
{
	/** {@code Prayer potion(4)} — a base name and a dose count. */
	private static final Pattern DOSE = Pattern.compile("^(.*)\\(([1-4])\\)$");

	/**
	 * Families the dose convention matches but Bob Barter will not touch.
	 * <p>
	 * Blighted potions exist only for the Wilderness and cannot be decanted. Matched on prefix, and
	 * kept as a list to grow rather than a rule to guess, because being wrong here means advising a
	 * trip to an NPC who refuses.
	 */
	private static final String[] NOT_DECANTABLE = {"Blighted "};

	/**
	 * Doses that must both exist before a pair is offered.
	 * <p>
	 * Some potions only exist in some dose sizes, and a recipe naming an item that is not in the
	 * mapping cannot be priced anyway. Deriving from what is actually there rather than from what
	 * ought to be avoids a registry full of recipes that silently never evaluate.
	 */
	private static final int MIN_DOSES_IN_FAMILY = 2;

	private final List<ConversionRecipe> recipes = new ArrayList<>();

	/** The curated conversions alone, for a caller with no item mapping to hand. */
	public ConversionRegistry()
	{
		addExchangeSets();
		addOneWayCombinations();
	}

	/** The curated conversions plus every decant the mapping reveals. */
	public ConversionRegistry(Collection<ItemMetadata> mapping)
	{
		this();
		addDerivedDecants(mapping);
	}

	public List<ConversionRecipe> getRecipes()
	{
		return Collections.unmodifiableList(recipes);
	}

	public int size()
	{
		return recipes.size();
	}

	/**
	 * Grand Exchange armour sets: the clerk assembles and disassembles them for free, instantly, with
	 * no requirements, which is what makes both directions real.
	 */
	private void addExchangeSets()
	{
		addReversibleSet("Dharok's armour set", 11848, 4716, 4720, 4722, 4718);
		addReversibleSet("Guthan's armour set", 11850, 4724, 4728, 4730, 4726);
		addReversibleSet("Karil's armour set", 11852, 4732, 4736, 4738, 4734);
		addReversibleSet("Torag's armour set", 11854, 4745, 4749, 4751, 4747);
		addReversibleSet("Verac's armour set", 11856, 4753, 4757, 4759, 4755);
		addReversibleSet("Ahrim's armour set", 11846, 4708, 4712, 4714, 4710);
	}

	/**
	 * Combinations the game will make and will not undo.
	 *
	 * <p>Registered as one-way on purpose. The previous registry produced an unpack for every pack,
	 * so a favourable quote on an Armadyl hilt advised buying a godsword and taking it apart —
	 * which cannot be done at any price. Five of its eleven entries had a fictional reverse.
	 */
	private void addOneWayCombinations()
	{
		// Three shards, an anvil and 80 Smithing.
		addOneWay("Godsword blade", 11798, ConversionRecipe.Venue.SKILL,
			pairs(11818, 1, 11820, 1, 11822, 1));
		// Hilt onto blade. No skill, no anvil, and no way back.
		addOneWay("Armadyl godsword", 11802, ConversionRecipe.Venue.SKILL, pairs(11798, 1, 11810, 1));
		addOneWay("Bandos godsword", 11804, ConversionRecipe.Venue.SKILL, pairs(11798, 1, 11812, 1));
		addOneWay("Saradomin godsword", 11806, ConversionRecipe.Venue.SKILL, pairs(11798, 1, 11814, 1));
		addOneWay("Zamorak godsword", 11808, ConversionRecipe.Venue.SKILL, pairs(11798, 1, 11816, 1));
	}

	/**
	 * Every dose conversion the mapping supports.
	 *
	 * <p>For two dose sizes {@code a} and {@code b}, the smallest whole conversion turns
	 * {@code lcm/a} of the first into {@code lcm/b} of the second — four one-dose into one four-dose,
	 * three two-dose into two three-dose, and so on. Total doses are conserved, which is what makes
	 * this arithmetic and not an estimate, and Bob Barter runs it in either direction.
	 */
	private void addDerivedDecants(Collection<ItemMetadata> mapping)
	{
		if (mapping == null)
		{
			return;
		}

		// Base name to dose to item id, ordered so the generated recipes come out in a stable order.
		Map<String, TreeMap<Integer, Integer>> families = new TreeMap<>();
		for (ItemMetadata item : mapping)
		{
			if (item == null || item.getName() == null)
			{
				continue;
			}
			Matcher matcher = DOSE.matcher(item.getName());
			if (!matcher.matches())
			{
				continue;
			}
			String base = matcher.group(1).trim();
			if (base.isEmpty() || excluded(item.getName()))
			{
				continue;
			}
			families.computeIfAbsent(base, name -> new TreeMap<>())
				.put(Integer.parseInt(matcher.group(2)), item.getId());
		}

		for (Map.Entry<String, TreeMap<Integer, Integer>> family : families.entrySet())
		{
			TreeMap<Integer, Integer> doses = family.getValue();
			if (doses.size() < MIN_DOSES_IN_FAMILY)
			{
				continue;
			}
			List<Integer> sizes = new ArrayList<>(doses.keySet());
			for (int i = 0; i < sizes.size(); i++)
			{
				for (int j = i + 1; j < sizes.size(); j++)
				{
					addDecant(family.getKey(), sizes.get(i), doses.get(sizes.get(i)),
						sizes.get(j), doses.get(sizes.get(j)));
				}
			}
		}
	}

	private static boolean excluded(String name)
	{
		for (String prefix : NOT_DECANTABLE)
		{
			if (name.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}

	private void addDecant(String base, int fromDose, int fromId, int toDose, int toId)
	{
		int lcm = fromDose * toDose / gcd(fromDose, toDose);
		int fromCount = lcm / fromDose;
		int toCount = lcm / toDose;

		Map<Integer, Integer> inputs = new LinkedHashMap<>();
		inputs.put(fromId, fromCount);
		Map<Integer, Integer> outputs = new LinkedHashMap<>();
		outputs.put(toId, toCount);

		String forward = String.format("Decant %s(%d) into (%d)", base, fromDose, toDose);
		ConversionRecipe recipe = new ConversionRecipe(forward, inputs, outputs,
			ConversionRecipe.Venue.DECANTER, true);
		recipes.add(recipe);
		recipes.add(recipe.reverse(String.format("Decant %s(%d) into (%d)", base, toDose, fromDose)));
	}

	private void addReversibleSet(String name, int setId, int... componentIds)
	{
		Map<Integer, Integer> components = new LinkedHashMap<>();
		for (int componentId : componentIds)
		{
			components.merge(componentId, 1, Integer::sum);
		}
		Map<Integer, Integer> set = new LinkedHashMap<>();
		set.put(setId, 1);

		ConversionRecipe pack = new ConversionRecipe("Pack " + name, components, set,
			ConversionRecipe.Venue.EXCHANGE_CLERK, true);
		recipes.add(pack);
		recipes.add(pack.reverse("Unpack " + name));
	}

	private void addOneWay(String name, int outputId, ConversionRecipe.Venue venue,
		Map<Integer, Integer> inputs)
	{
		Map<Integer, Integer> output = new LinkedHashMap<>();
		output.put(outputId, 1);
		recipes.add(new ConversionRecipe("Make " + name, inputs, output, venue, false));
	}

	private static Map<Integer, Integer> pairs(int... idsAndCounts)
	{
		if (idsAndCounts.length % 2 != 0)
		{
			throw new IllegalArgumentException("ids must be paired with counts");
		}
		Map<Integer, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < idsAndCounts.length; i += 2)
		{
			map.put(idsAndCounts[i], idsAndCounts[i + 1]);
		}
		return map;
	}

	private static int gcd(int a, int b)
	{
		return b == 0 ? a : gcd(b, a % b);
	}

	/** For a caller that wants to look one up rather than sweep them all. */
	public Map<String, ConversionRecipe> byName()
	{
		Map<String, ConversionRecipe> index = new HashMap<>();
		for (ConversionRecipe recipe : recipes)
		{
			index.put(recipe.getName(), recipe);
		}
		return index;
	}
}
