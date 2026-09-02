package com.flippingfriend.companion;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the companion believes about an item's four-hour buy limit, which is both an order-size cap and
 * the denominator of a feature the model learns from.
 * <p>
 * 508 of the 4,652 mapped items publish no limit at all — eleven per cent, including Bow of
 * Faerdhinen, Nihil horn and Ancient hilt. The value substituted for those has to be conservative, has
 * to agree with the one the plugin already uses, and above all has to be distinguishable from a
 * figure the wiki actually published.
 */
public class MarketMappingTest
{
	private static Map<Integer, MarketIngestionService.Item> parse(String json)
	{
		return MarketIngestionService.parseMapping(JsonParser.parseString(json));
	}

	@Test
	public void aPublishedLimitIsUsedAndMarkedAsPublished()
	{
		Map<Integer, MarketIngestionService.Item> items =
			parse("[{\"id\":561,\"name\":\"Nature rune\",\"limit\":18000,\"value\":180}]");

		assertEquals(18_000, items.get(561).buyLimit);
		assertTrue(items.get(561).buyLimitPublished);
	}

	@Test
	public void aCheapItemWithNoPublishedLimitIsNotCappedAtEight()
	{
		// The flat 8 meant an item worth a few gp could never be ordered more than eight at a time,
		// and the ledger then declared its allowance spent. The plugin has always used a
		// value-dependent guess for exactly this case; the companion now uses the same one.
		Map<Integer, MarketIngestionService.Item> items =
			parse("[{\"id\":1,\"name\":\"Cheap thing\",\"value\":50}]");

		assertEquals(100, items.get(1).buyLimit);
		assertFalse("a guess must never look like a published figure", items.get(1).buyLimitPublished);
	}

	@Test
	public void anExpensiveItemWithNoPublishedLimitStaysConservative()
	{
		// Erring low is the safe direction: an under-estimate costs a smaller order, while an
		// over-estimate produces an offer the game silently refuses to finish filling.
		Map<Integer, MarketIngestionService.Item> items =
			parse("[{\"id\":2,\"name\":\"Nihil horn\",\"value\":2000000}]");

		assertEquals(8, items.get(2).buyLimit);
		assertFalse(items.get(2).buyLimitPublished);
	}
}
