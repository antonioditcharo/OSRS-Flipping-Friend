package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Pricing the conversions the game itself offers.
 *
 * <p>A conversion is a better trade than a flip where one exists, because the conversion cannot fail:
 * the clerk packs a set on the spot and Bob Barter decants on the spot. That certainty is exactly
 * why the arithmetic has to be right — a flip that is mispriced by a percent is a worse flip, and a
 * conversion that is mispriced by a percent is a loss dressed up as a certainty.
 *
 * <p>Four things the previous inline pricing got wrong are pinned here, and all four flattered the
 * answer.
 */
public class ConversionEngineTest
{
	private static final int SET = 11848;
	private static final int HELM = 4716;
	private static final int BODY = 4720;
	private static final int LEGS = 4722;
	private static final int AXE = 4718;

	private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

	private final TaxCalculator tax = new TaxCalculator();
	private final ConversionEngine engine = new ConversionEngine(tax);

	private static Map<Integer, LatestPrice> quotes(Object... idsAndPrices)
	{
		Map<Integer, LatestPrice> map = new HashMap<>();
		long at = NOW.getEpochSecond();
		for (int i = 0; i < idsAndPrices.length; i += 2)
		{
			int id = (Integer) idsAndPrices[i];
			int price = (Integer) idsAndPrices[i + 1];
			map.put(id, new LatestPrice(price, at, price, at));
		}
		return map;
	}

	/** Four pieces into one set, the way the Grand Exchange clerk does it. */
	private static ConversionRecipe packDharok()
	{
		Map<Integer, Integer> parts = new LinkedHashMap<>();
		parts.put(HELM, 1);
		parts.put(BODY, 1);
		parts.put(LEGS, 1);
		parts.put(AXE, 1);
		Map<Integer, Integer> set = new LinkedHashMap<>();
		set.put(SET, 1);
		return new ConversionRecipe("Pack Dharok's armour set", parts, set,
			ConversionRecipe.Venue.EXCHANGE_CLERK, true);
	}

	@Test
	public void theTaxIsTwoPercentAndComesFromTheOneImplementation()
	{
		// The defect that mattered most. The inline version charged 1% -- half the real rate -- with
		// its own cap and its own truncation, while TaxCalculator sat beside it knowing the answer.
		// On a set selling for a million that is ten thousand coins of imaginary profit per run.
		Map<Integer, LatestPrice> book =
			quotes(HELM, 250_000, BODY, 250_000, LEGS, 250_000, AXE, 250_000, SET, 1_020_000);

		ConversionEngine.Conversion conversion =
			engine.evaluate(packDharok(), book, null, 10_000_000L, NOW);

		assertNotNull(conversion);
		long expectedNet = 1_020_000 - tax.taxPerItem(SET, 1_020_000);
		assertEquals("revenue must be net of the real tax", expectedNet,
			conversion.getNetRevenuePerRun());
		assertEquals(1_000_000L, conversion.getCostPerRun());
		assertEquals("which is a loss, not the profit a 1% rate would have shown",
			expectedNet - 1_000_000L, conversion.getProfitPerRun());
		assertTrue("and it must come out negative: " + conversion.getProfitPerRun(),
			conversion.getProfitPerRun() < 0);
		assertFalse(conversion.isWorthDoing());
	}

	@Test
	public void aGenuinelyProfitableSetIsPricedAsSuch()
	{
		Map<Integer, LatestPrice> book =
			quotes(HELM, 200_000, BODY, 200_000, LEGS, 200_000, AXE, 200_000, SET, 1_000_000);

		ConversionEngine.Conversion conversion =
			engine.evaluate(packDharok(), book, null, 100_000_000L, NOW);

		assertTrue(conversion.isWorthDoing());
		assertEquals(800_000L, conversion.getCostPerRun());
		assertEquals(1_000_000 - tax.taxPerItem(SET, 1_000_000) - 800_000L,
			conversion.getProfitPerRun());
	}

	// --- buy limits ---

	@Test
	public void theTightestComponentLimitCapsTheWholeRun()
	{
		// A conversion needs every part. A recipe reported as available that cannot be bought is a
		// suggestion the player cannot act on, and nothing checked this at all.
		Map<Integer, LatestPrice> book =
			quotes(HELM, 200_000, BODY, 200_000, LEGS, 200_000, AXE, 200_000, SET, 1_000_000);
		Map<Integer, Integer> limits = new HashMap<>();
		limits.put(HELM, 50);
		limits.put(BODY, 7);
		limits.put(LEGS, 40);
		limits.put(AXE, 60);

		ConversionEngine.Conversion conversion =
			engine.evaluate(packDharok(), book, limits, 1_000_000_000L, NOW);

		assertEquals("seven bodies is seven sets, whatever else is available", 7,
			conversion.getRuns());
	}

	@Test
	public void aComponentNeededSeveralTimesUsesUpItsLimitFaster()
	{
		Map<Integer, Integer> inputs = new LinkedHashMap<>();
		inputs.put(HELM, 4);
		Map<Integer, Integer> outputs = new LinkedHashMap<>();
		outputs.put(SET, 1);
		ConversionRecipe fourOfThem = new ConversionRecipe("Four helms", inputs, outputs,
			ConversionRecipe.Venue.EXCHANGE_CLERK, false);

		Map<Integer, Integer> limits = new HashMap<>();
		limits.put(HELM, 30);

		ConversionEngine.Conversion conversion = engine.evaluate(fourOfThem,
			quotes(HELM, 1_000, SET, 10_000), limits, 1_000_000_000L, NOW);

		assertEquals("thirty helms is seven runs of four, not thirty", 7, conversion.getRuns());
	}

	@Test
	public void anItemWithNoWindowOpenIsNotTreatedAsExhausted()
	{
		// A missing entry means nothing has been bought yet, which is unlimited rather than zero.
		// Reading it as zero would silence every conversion at the start of a session.
		ConversionEngine.Conversion conversion = engine.evaluate(packDharok(),
			quotes(HELM, 200_000, BODY, 200_000, LEGS, 200_000, AXE, 200_000, SET, 1_000_000),
			new HashMap<>(), 100_000_000L, NOW);

		assertTrue("a fresh session must not be capped at zero runs: " + conversion.getRuns(),
			conversion.getRuns() > 0);
	}

	@Test
	public void capitalCapsTheRunsToo()
	{
		ConversionEngine.Conversion conversion = engine.evaluate(packDharok(),
			quotes(HELM, 200_000, BODY, 200_000, LEGS, 200_000, AXE, 200_000, SET, 1_000_000),
			null, 2_500_000L, NOW);

		assertEquals("two and a half million buys three sets' worth of parts", 3,
			conversion.getRuns());
	}

	// --- staleness ---

	@Test
	public void aStaleQuoteOnAnyComponentKillsTheConversion()
	{
		// The reason this is stricter than the flip path: a conversion is advertised as near-certain,
		// and an hour-old quote on one part of a four-part set is enough to invert the answer -- by
		// which time the player has already bought the other three.
		Map<Integer, LatestPrice> book =
			quotes(HELM, 200_000, BODY, 200_000, LEGS, 200_000, AXE, 200_000, SET, 1_000_000);
		long old = NOW.getEpochSecond() - 3600;
		book.put(LEGS, new LatestPrice(200_000, old, 200_000, old));

		assertNull(engine.evaluate(packDharok(), book, null, 100_000_000L, NOW));
	}

	@Test
	public void aMissingQuoteKillsItToo()
	{
		Map<Integer, LatestPrice> book =
			quotes(HELM, 200_000, BODY, 200_000, LEGS, 200_000, SET, 1_000_000);

		assertNull("no price for the axe means no answer, not a cheaper set",
			engine.evaluate(packDharok(), book, null, 100_000_000L, NOW));
	}

	// --- ranking ---

	@Test
	public void theBestConversionWinsRatherThanTheFirstOne()
	{
		// A loop with a return inside it ranks by the order somebody typed the registry in. Here the
		// thin one is registered first, so returning the first profitable recipe returns the wrong one.
		Map<Integer, Integer> thinIn = new LinkedHashMap<>();
		thinIn.put(HELM, 1);
		Map<Integer, Integer> thinOut = new LinkedHashMap<>();
		thinOut.put(BODY, 1);
		ConversionRecipe thin = new ConversionRecipe("Thin", thinIn, thinOut,
			ConversionRecipe.Venue.EXCHANGE_CLERK, false);

		Map<Integer, Integer> fatIn = new LinkedHashMap<>();
		fatIn.put(LEGS, 1);
		Map<Integer, Integer> fatOut = new LinkedHashMap<>();
		fatOut.put(SET, 1);
		ConversionRecipe fat = new ConversionRecipe("Fat", fatIn, fatOut,
			ConversionRecipe.Venue.EXCHANGE_CLERK, false);

		Map<Integer, LatestPrice> book = quotes(HELM, 1_000, BODY, 1_200, LEGS, 1_000, SET, 20_000);

		ConversionEngine.Conversion best = engine.best(Arrays.asList(thin, fat), book, null,
			100_000_000L, 1, NOW);

		assertEquals("Fat", best.getRecipe().getName());
	}

	@Test
	public void nothingWorthDoingReturnsNothing()
	{
		Map<Integer, LatestPrice> book =
			quotes(HELM, 250_000, BODY, 250_000, LEGS, 250_000, AXE, 250_000, SET, 900_000);

		assertNull(engine.best(Collections.singletonList(packDharok()), book, null,
			100_000_000L, 1, NOW));
	}

	// --- the registry, and the recipes that were impossible ---

	@Test
	public void aGodswordCannotBeTakenApart()
	{
		// The most serious defect in the old registry: it produced an unpack for every pack, so a
		// favourable quote on an Armadyl hilt advised buying a godsword and separating it. There is
		// no price at which that can be done.
		for (ConversionRecipe recipe : new ConversionRegistry().getRecipes())
		{
			if (recipe.getName().contains("godsword") || recipe.getName().contains("Godsword"))
			{
				assertFalse("a one-way combination must not offer a reverse: " + recipe.getName(),
					recipe.isReversible());
				assertNull(recipe.reverse("Unpack"));
			}
		}
	}

	@Test
	public void aGrandExchangeSetGoesBothWays()
	{
		List<String> names = new ArrayList<>();
		for (ConversionRecipe recipe : new ConversionRegistry().getRecipes())
		{
			names.add(recipe.getName());
		}

		assertTrue(names.contains("Pack Dharok's armour set"));
		assertTrue("the clerk will take it apart again as happily as it put it together",
			names.contains("Unpack Dharok's armour set"));
	}

	@Test
	public void decantsAreDerivedFromTheMappingRatherThanTyped()
	{
		// The previous registry covered one potion, in three of its twelve directions, out of the
		// hundred-odd in the game. The convention covers all of them and keeps covering them.
		List<ItemMetadata> mapping = Arrays.asList(
			new ItemMetadata(2434, "Prayer potion(4)", true, 2_000, 0),
			new ItemMetadata(139, "Prayer potion(3)", true, 2_000, 0),
			new ItemMetadata(141, "Prayer potion(2)", true, 2_000, 0),
			new ItemMetadata(143, "Prayer potion(1)", true, 2_000, 0),
			new ItemMetadata(4151, "Abyssal whip", true, 70, 0));

		Map<String, ConversionRecipe> byName = new ConversionRegistry(mapping).byName();

		// Six unordered pairs from four dose sizes, both ways: twelve recipes for one potion.
		int decants = 0;
		for (String name : byName.keySet())
		{
			if (name.startsWith("Decant Prayer potion"))
			{
				decants++;
			}
		}
		assertEquals(12, decants);

		ConversionRecipe fourOnes = byName.get("Decant Prayer potion(1) into (4)");
		assertNotNull(fourOnes);
		assertEquals("four single doses make one quadruple", Integer.valueOf(4),
			fourOnes.getInputs().get(143));
		assertEquals(Integer.valueOf(1), fourOnes.getOutputs().get(2434));

		ConversionRecipe threeIntoTwo = byName.get("Decant Prayer potion(3) into (2)");
		assertEquals("two triples are six doses, which is three doubles", Integer.valueOf(2),
			threeIntoTwo.getInputs().get(139));
		assertEquals(Integer.valueOf(3), threeIntoTwo.getOutputs().get(141));
	}

	@Test
	public void everyDecantConservesTotalDoses()
	{
		// The property that makes decanting arithmetic rather than estimation. Checked over every
		// derived recipe rather than a sample, because a single wrong pair is free money that is not
		// there.
		List<ItemMetadata> mapping = new ArrayList<>();
		int id = 100;
		for (String base : new String[]{"Prayer potion", "Super restore", "Saradomin brew"})
		{
			for (int dose = 1; dose <= 4; dose++)
			{
				mapping.add(new ItemMetadata(id++, base + "(" + dose + ")", true, 2_000, 0));
			}
		}
		Map<Integer, Integer> doseOf = new HashMap<>();
		id = 100;
		for (int family = 0; family < 3; family++)
		{
			for (int dose = 1; dose <= 4; dose++)
			{
				doseOf.put(id++, dose);
			}
		}

		int checked = 0;
		for (ConversionRecipe recipe : new ConversionRegistry(mapping).getRecipes())
		{
			if (!recipe.getName().startsWith("Decant"))
			{
				continue;
			}
			int in = 0;
			for (Map.Entry<Integer, Integer> entry : recipe.getInputs().entrySet())
			{
				in += doseOf.get(entry.getKey()) * entry.getValue();
			}
			int out = 0;
			for (Map.Entry<Integer, Integer> entry : recipe.getOutputs().entrySet())
			{
				out += doseOf.get(entry.getKey()) * entry.getValue();
			}
			assertEquals(recipe.getName() + " must conserve doses", in, out);
			checked++;
		}
		assertEquals("three potions, twelve directions each", 36, checked);
	}

	@Test
	public void namesThatOnlyLookLikePotionsAreLeftAlone()
	{
		List<ItemMetadata> mapping = Arrays.asList(
			new ItemMetadata(1, "Blighted super restore(4)", true, 2_000, 0),
			new ItemMetadata(2, "Blighted super restore(3)", true, 2_000, 0),
			new ItemMetadata(3, "Rune arrow(p)", true, 2_000, 0),
			new ItemMetadata(4, "Dragon spear(p++)", true, 2_000, 0));

		for (ConversionRecipe recipe : new ConversionRegistry(mapping).getRecipes())
		{
			assertFalse("Bob Barter will not decant a Wilderness potion: " + recipe.getName(),
				recipe.getName().contains("Blighted"));
		}
	}

	@Test
	public void aFamilyWithOnlyOneDoseSizeHasNothingToConvert()
	{
		List<ItemMetadata> mapping = Collections.singletonList(
			new ItemMetadata(1, "Odd thing(2)", true, 2_000, 0));

		assertEquals("a lone dose size produces no pairs",
			new ConversionRegistry().size(), new ConversionRegistry(mapping).size());
	}
}
