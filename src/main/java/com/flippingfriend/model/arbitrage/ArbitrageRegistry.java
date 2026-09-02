package com.flippingfriend.model.arbitrage;

import com.flippingfriend.model.SuggestionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Registry containing well-known sets for Arbitrage evaluation.
 */
@Singleton
public class ArbitrageRegistry
{
	private final List<ArbitrageRecipe> recipes = new ArrayList<>();

	public ArbitrageRegistry()
	{
		// Godsword Sets
		addSet("Armadyl godsword", 11802, createMap(11798, 1, 11810, 1));
		addSet("Bandos godsword", 11804, createMap(11798, 1, 11812, 1));
		addSet("Saradomin godsword", 11806, createMap(11798, 1, 11814, 1));
		addSet("Zamorak godsword", 11808, createMap(11798, 1, 11816, 1));
		
		// Godsword Blade
		addSet("Godsword blade", 11798, createMap(11818, 1, 11820, 1, 11822, 1));

		// Barrows Armor Sets
		addSet("Dharok's armour set", 11848, createMap(4716, 1, 4720, 1, 4722, 1, 4718, 1));
		addSet("Guthan's armour set", 11850, createMap(4724, 1, 4728, 1, 4730, 1, 4726, 1));
		addSet("Karil's armour set", 11852, createMap(4732, 1, 4736, 1, 4738, 1, 4734, 1));
		addSet("Torag's armour set", 11854, createMap(4745, 1, 4749, 1, 4751, 1, 4747, 1));
		addSet("Verac's armour set", 11856, createMap(4753, 1, 4757, 1, 4759, 1, 4755, 1));
		addSet("Ahrim's armour set", 11846, createMap(4708, 1, 4712, 1, 4714, 1, 4710, 1));
		
		// Decant Potions Example (Saradomin brew)
		// 1-dose (6691), 2-dose (6689), 3-dose (6687), 4-dose (6685)
		addDecant("Saradomin brew(4)", 6685, 3, createMap(6685, 0, 6687, 4)); // e.g. 4x 3-dose -> 3x 4-dose
		addDecant("Saradomin brew(4)", 6685, 1, createMap(6685, 0, 6689, 2)); // e.g. 2x 2-dose -> 1x 4-dose
		addDecant("Saradomin brew(4)", 6685, 1, createMap(6685, 0, 6691, 4)); // e.g. 4x 1-dose -> 1x 4-dose
	}

	private void addSet(String name, int setId, Map<Integer, Integer> components)
	{
		Map<Integer, Integer> setMap = new HashMap<>();
		setMap.put(setId, 1);
		
		recipes.add(new ArbitrageRecipe("Pack " + name, components, setMap, SuggestionType.PACK));
		recipes.add(new ArbitrageRecipe("Unpack " + name, setMap, components, SuggestionType.UNPACK));
	}

	private void addDecant(String name, int outputId, int outputQuantity, Map<Integer, Integer> inputs)
	{
		Map<Integer, Integer> cleanInputs = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : inputs.entrySet())
		{
			if (entry.getValue() > 0)
			{
				cleanInputs.put(entry.getKey(), entry.getValue());
			}
		}
		
		Map<Integer, Integer> outputs = new HashMap<>();
		outputs.put(outputId, outputQuantity);
		
		recipes.add(new ArbitrageRecipe("Decant " + name, cleanInputs, outputs, SuggestionType.DECANT));
	}

	public List<ArbitrageRecipe> getRecipes()
	{
		return Collections.unmodifiableList(recipes);
	}

	private Map<Integer, Integer> createMap(int... kvPairs)
	{
		if (kvPairs.length % 2 != 0)
		{
			throw new IllegalArgumentException("Key-value pairs must be even");
		}
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 0; i < kvPairs.length; i += 2)
		{
			map.put(kvPairs[i], kvPairs[i + 1]);
		}
		return map;
	}
}
