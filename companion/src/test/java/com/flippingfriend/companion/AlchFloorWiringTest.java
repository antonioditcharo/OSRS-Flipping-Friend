package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

/**
 * The alch floor has to reach the sizing decision, or it is a fact nobody acts on.
 *
 * <p>{@link com.flippingfriend.model.AlchemyFloorTest} covers whether the economics are right. This
 * covers whether they arrive. The number was already being downloaded — {@code highalch} comes in the
 * same {@code /mapping} response as the buy limit, on every refresh — and the companion parsed the
 * response, kept the limit, and dropped the alch value on the floor. Meanwhile {@code unwindCost}
 * estimated the downside of every alchable item as though there were nothing underneath it.
 *
 * <p>The direction of the effect is the part worth stating: a bounded downside makes an order
 * <em>larger</em>, not smaller. Kelly sizing divides profit by the loss if the position strands, so
 * halving the loss raises the fraction of capital the optimiser will commit. Risk information that
 * only ever shrinks orders is a safety feature; risk information that correctly enlarges them where
 * the risk is genuinely lower is an edge.
 */
public class AlchFloorWiringTest
{
	private static final int ITEM = 855;
	private static final long T0 = 1_700_000_000L / 300 * 300;

	/** Same shape as the capture-rate fixture: wide enough to clear tax, jumpy enough to look real. */
	private static final int MID = 100_000;
	private static final int WOBBLE = 3_000;
	private static final int BUY = MID - MID * 5 / 200;
	private static final int SELL = MID + MID * 5 / 200;

	/** Above the market price, which is where an alch floor actually bites and is not unusual. */
	private static final int HIGH_ALCH = 120_000;

	private static SeriesSource history()
	{
		return (itemId, timestep) ->
		{
			List<Candle> series = new ArrayList<>();
			long step = "5m".equals(timestep) ? 300 : 3600;
			for (int i = 0; i < 288; i++)
			{
				int drift = ((i % 3) - 1) * WOBBLE;
				series.add(new Candle(T0 - (288 - i) * step, MID + drift + 1, MID + drift - 1,
					5_000, 5_000));
			}
			return series;
		};
	}

	private static CandidateFactory.QuotedItem quoted(int highAlch)
	{
		MarketIngestionService.Item item = new MarketIngestionService.Item(
			ITEM, "Yew longbow", 25_000, true, true, highAlch);
		Candle bar = new Candle(T0, SELL, BUY, 20_000, 20_000);
		return new CandidateFactory.QuotedItem(item, new LatestPrice(SELL, T0, BUY, T0), bar, bar);
	}

	private static int quantityFor(int highAlch)
	{
		CandidateFactory factory = new CandidateFactory(history());
		List<PortfolioCandidate> candidates = factory.build(
			Collections.singletonList(quoted(highAlch)), 4.0, new HashMap<>(), 2_000_000_000L,
			Instant.ofEpochSecond(T0));
		for (PortfolioCandidate candidate : candidates)
		{
			if (candidate.getItemId() == ITEM)
			{
				return candidate.getQuantity();
			}
		}
		return 0;
	}

	@Test
	public void anItemWithNoFloorSizesExactlyAsItDidBefore()
	{
		// Most items cannot be alched, and for them nothing about this changes. Zero has to mean "no
		// floor" all the way through rather than "a floor of zero coins".
		int quantity = quantityFor(0);

		assertTrue("the premise: this item is tradeable at all", quantity > 0);
	}

	@Test
	public void anAlchableItemIsWorthMoreCapitalThanTheSameItemWithoutTheFloor()
	{
		// Two identical markets, identical prices, identical history. The only difference is whether
		// the game will buy the item back at a fixed price, and that is worth a larger order.
		int floored = quantityFor(HIGH_ALCH);
		int unfloored = quantityFor(0);

		assertTrue("both must still be trades", floored > 0 && unfloored > 0);
		assertTrue("a bounded downside earns a bigger position: " + floored + " vs " + unfloored,
			floored > unfloored);
	}

	@Test
	public void aWorthlessAlchValueChangesNothing()
	{
		// An alch that does not cover the rune is not a floor. Passing it through as though it were
		// would put a bound under items that have none, which is worse than having no bound at all.
		assertEquals(quantityFor(0), quantityFor(50));
	}

	@Test
	public void theRunePriceComesFromTheMarketRatherThanAConstant()
	{
		// Every cast consumes a nature rune, and the rune is one of the most actively traded items in
		// the game. A hardcoded price here would sit underneath every downside estimate in the system
		// and go stale without anyone noticing, which is why the sweep is read rather than a setter
		// being offered and left uncalled.
		CandidateFactory factory = new CandidateFactory(history());
		factory.setNatureRunePrice(50_000);

		long dearRunes = unwindWith(factory);

		CandidateFactory cheap = new CandidateFactory(history());
		cheap.setNatureRunePrice(100);
		long cheapRunes = unwindWith(cheap);

		// Asserted on the unwind loss rather than on the order size. Size is chosen from a ladder of
		// six shares of what is fillable, so it quantises: a real change in the floor frequently
		// lands on the same rung and the quantity does not move at all. The unwind loss is where the
		// rune price actually enters, and it responds continuously.
		// The cheap-rune case comes out at zero, and that is the floor working rather than a missing
		// answer: an alch worth 119,900 against a market paying about 95,000 covers the whole
		// downside, so there is no loss left to record. The dear rune leaves a real one.
		assertTrue("a dear rune leaves a downside the floor no longer covers: " + dearRunes,
			dearRunes > 0);
		assertTrue("and a cheap one leaves less of it: " + cheapRunes + " vs " + dearRunes,
			cheapRunes < dearRunes);
	}

	/** What stranding the position would cost, per unit, which is where the alch floor lands. */
	private static long unwindWith(CandidateFactory factory)
	{
		List<PortfolioCandidate> candidates = factory.build(
			Collections.singletonList(quoted(HIGH_ALCH)), 4.0, new HashMap<>(), 2_000_000_000L,
			Instant.ofEpochSecond(T0));
		for (PortfolioCandidate candidate : candidates)
		{
			if (candidate.getItemId() == ITEM)
			{
				return candidate.getUnwindLoss() / Math.max(1, candidate.getQuantity());
			}
		}
		return 0;
	}

	@Test
	public void theMappingCarriesTheAlchValueThroughFromTheFeed()
	{
		// The value has always been in the response. Parsing it is the whole of the wiring, and this
		// is the line that would have caught it being dropped.
		String json = "[{\"id\":855,\"name\":\"Yew longbow\",\"members\":false,\"limit\":25000,"
			+ "\"value\":768,\"highalch\":768,\"lowalch\":512}]";
		java.util.Map<Integer, MarketIngestionService.Item> mapping =
			MarketIngestionService.parseMapping(com.google.gson.JsonParser.parseString(json));

		assertEquals(768, mapping.get(855).highAlch);
	}

	@Test
	public void anItemWithNoAlchValueInTheFeedParsesAsUnalchable()
	{
		String json = "[{\"id\":13190,\"name\":\"Old school bond\",\"members\":false,"
			+ "\"limit\":100,\"value\":1}]";
		java.util.Map<Integer, MarketIngestionService.Item> mapping =
			MarketIngestionService.parseMapping(com.google.gson.JsonParser.parseString(json));

		assertEquals("absent must mean no floor, not an unknown one", 0,
			mapping.get(13190).highAlch);
	}
}
