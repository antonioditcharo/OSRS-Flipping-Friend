package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * End-to-end check that a rejected item becomes a priced counterfactual.
 *
 * <p>{@link ShadowTraderTest} covers the resolution arithmetic. This covers the part that has failed
 * repeatedly in this codebase: a component that is correct, tested, and never called. Audit item 11
 * is a list of exactly that, and both previous wiring commits added a test for arrival precisely
 * because correctness tests do not catch it.
 */
public class ShadowWiringTest
{
	private static final long T0 = 1_700_000_000L;

	private static Candle bar(long at, int low, int high)
	{
		return new Candle(at, high, low, 5_000, 5_000);
	}

	/**
	 * No stored history. Note this does <em>not</em> screen anything out on its own: {@code screen()}
	 * judges an item from its quote and its 5m/1h bars, and only {@code tacticsFor} reads the series.
	 * An earlier version of this test assumed otherwise and asserted on rejections that never happened.
	 */
	private static SeriesSource emptyHistory()
	{
		return (itemId, timestep) -> Collections.emptyList();
	}

	private static CandidateFactory.QuotedItem quoted(int id, String name, int limit, int low, int high)
	{
		MarketIngestionService.Item item = new MarketIngestionService.Item(id, name, limit, true, true);
		LatestPrice price = new LatestPrice(high, T0, low, T0);
		return new CandidateFactory.QuotedItem(item, price, bar(T0, low, high), bar(T0, low, high));
	}

	@Test
	public void aFactoryWithNoTraderDoesNoShadowWork()
	{
		CandidateFactory factory = new CandidateFactory(emptyHistory());

		assertNull("no inert default: an unwired factory must accumulate nothing",
			factory.shadowTrader());

		factory.build(Collections.singletonList(quoted(4151, "Abyssal whip", 70, 1_000_000, 1_100_000)),
			1.0, new HashMap<>(), 100_000_000L, Instant.ofEpochSecond(T0));
		// Nothing to assert beyond it not throwing - the point is that null is a supported state.
	}

	/**
	 * A spread too thin to clear the 2% tax is the one veto that fires from the quote alone, with no
	 * dependence on liquidity thresholds or history, so it is the deterministic way to prove the
	 * wiring. 5,000 gross against roughly 20,100 of tax on a 1,005,000 sale is comfortably negative.
	 */
	@Test
	public void vetoedItemsBecomeOpenShadowPositions()
	{
		CandidateFactory factory = new CandidateFactory(emptyHistory());
		ShadowTrader trader = new ShadowTrader(new TaxCalculator());
		factory.setShadowTrader(trader);
		assertSame(trader, factory.shadowTrader());

		List<CandidateFactory.QuotedItem> universe = new ArrayList<>();
		universe.add(quoted(4151, "Abyssal whip", 70, 1_000_000, 1_005_000));

		factory.build(universe, 1.0, new HashMap<>(), 100_000_000L, Instant.ofEpochSecond(T0));

		assertEquals("the vetoed item must be paper-traded", 1, trader.openCount());
		assertTrue("and the health line must say so", trader.summary().contains("1 open"));
	}

	/**
	 * Not every rejection is a veto. {@code screen()} drops an item with an incomplete quote, or one
	 * nothing is affordable of, with a bare {@code continue} and no recorded reason — so there is
	 * nothing to attribute a counterfactual to and nothing is opened. Pinned so the gap is a known
	 * limitation rather than a surprise: those silent paths are also why the funnel in audit item 67
	 * cannot yet account for every item it discards.
	 */
	@Test
	public void silentlyDroppedItemsAreNotShadowed()
	{
		CandidateFactory factory = new CandidateFactory(emptyHistory());
		ShadowTrader trader = new ShadowTrader(new TaxCalculator());
		factory.setShadowTrader(trader);

		MarketIngestionService.Item item = new MarketIngestionService.Item(4151, "Abyssal whip", 70, true, true);
		// No quote at all: dropped before any veto can be recorded.
		CandidateFactory.QuotedItem unquoted =
			new CandidateFactory.QuotedItem(item, new LatestPrice(null, T0, null, T0), null, null);

		factory.build(Collections.singletonList(unquoted), 1.0, new HashMap<>(), 100_000_000L,
			Instant.ofEpochSecond(T0));

		assertEquals("no reason recorded means no counterfactual to price", 0, trader.openCount());
	}

	/**
	 * The whole point: after the horizon elapses and the market has moved, the veto has a price in
	 * gp rather than an opinion.
	 */
	@Test
	public void aVetoAcquiresAGpFigureOnceTheHorizonElapses()
	{
		ShadowTrader trader = new ShadowTrader(new TaxCalculator());
		trader.open(4151, "Abyssal whip", "too illiquid", 1_000_000, 1_100_000, 1, T0, 1.0);

		Map<Integer, List<Candle>> bars = new HashMap<>();
		bars.put(4151, new ArrayList<>(java.util.Arrays.asList(
			bar(T0 + 300, 990_000, 1_005_000),
			bar(T0 + 900, 1_020_000, 1_150_000))));
		SeriesSource history = (itemId, timestep) -> bars.getOrDefault(itemId, Collections.emptyList());

		assertEquals(1, trader.resolve(history, "5m", T0 + 7200));

		List<ShadowTrader.VetoCost> costs = trader.report(100_000_000L);
		assertEquals(1, costs.size());
		assertEquals("too illiquid", costs.get(0).getVetoReason());
		assertTrue("a veto that blocked a completing trade reads as too strict",
			costs.get(0).isTooStrict());
		assertTrue("and it is priced, not described", costs.get(0).getNetGp() > 0);
	}
}
