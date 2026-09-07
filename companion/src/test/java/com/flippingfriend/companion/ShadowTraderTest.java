package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.Test;

/**
 * Covers the counterfactual channel: whether a rejected trade would have worked.
 *
 * <p>The output these build toward is the table in {@code VetoThresholds} — a per-veto gp figure
 * where positive means the bar is too strict and negative means it is earning its keep. Getting the
 * sign convention and the never-bought case right is the whole point: a veto that only ever blocks
 * trades which would not have filled anyway is free, and must not be credited with a saving.
 */
public class ShadowTraderTest
{
	private static final int ITEM = 4151;
	private static final long T0 = 1_000_000L;
	private static final String STEP = "5m";

	private final ShadowTrader shadow = new ShadowTrader(new com.flippingfriend.model.TaxCalculator(), null, null);

	/** A series source over fixed bars, keyed by item. */
	private static SeriesSource historyOf(Map<Integer, List<Candle>> bars)
	{
		return (itemId, timestep) -> bars.getOrDefault(itemId, Collections.emptyList());
	}

	/** Candle(timestamp, avgHigh, avgLow, highVolume, lowVolume) - note high comes first. */
	private static Candle bar(long at, int low, int high)
	{
		return new Candle(at, high, low, 1000, 1000);
	}

	private static SeriesSource series(Candle... bars)
	{
		Map<Integer, List<Candle>> map = new HashMap<>();
		map.put(ITEM, new ArrayList<>(Arrays.asList(bars)));
		return historyOf(map);
	}

	@Test
	public void aTradeWhoseHorizonHasNotElapsedIsLeftOpen()
	{
		shadow.open(ITEM, "Abyssal whip", "too illiquid", 1_000_000, 1_100_000, 1, T0, 1.0);

		assertEquals(0, shadow.resolve(series(bar(T0 + 300, 900_000, 1_200_000)), STEP, T0 + 600));
		assertEquals("must still be waiting", 1, shadow.openCount());
		assertEquals(0, shadow.resolvedCount());
	}

	@Test
	public void bothLegsReachedCountsAsProfitForgone()
	{
		shadow.open(ITEM, "Abyssal whip", "sell price outside normal range", 1_000_000, 1_100_000, 1, T0, 1.0);

		// Low drops to the bid, then the high later reaches the ask.
		int resolvedCount = shadow.resolve(series(
			bar(T0 + 300, 990_000, 1_005_000),
			bar(T0 + 600, 1_020_000, 1_150_000)), STEP, T0 + 7200);

		assertEquals(1, resolvedCount);
		List<ShadowTrader.VetoCost> costs = shadow.report(0);
		assertEquals(1, costs.size());
		ShadowTrader.VetoCost cost = costs.get(0);
		assertEquals(1, cost.getCompleted());
		assertTrue("blocking a completing trade forgoes profit, so the veto reads too strict",
			cost.isTooStrict());
		// 1.1M sale, 2% tax capped well below the 5M ceiling, against a 1.0M cost.
		assertEquals(78_000, cost.getNetGp(), 1_000);
	}

	@Test
	public void boughtButNeverSoldIsChargedTheUnwind()
	{
		shadow.open(ITEM, "Abyssal whip", "too volatile", 1_000_000, 1_100_000, 1, T0, 1.0);

		// The bid is reached; the ask never is.
		shadow.resolve(series(
			bar(T0 + 300, 990_000, 1_000_000),
			bar(T0 + 600, 980_000, 1_010_000)), STEP, T0 + 7200);

		ShadowTrader.VetoCost cost = shadow.report(0).get(0);
		assertEquals(1, cost.getStranded());
		assertEquals("2% of the 1.0M committed", -20_000, cost.getNetGp(), 1.0);
		assertFalse("blocking a losing trade means the veto earned its keep", cost.isTooStrict());
	}

	/**
	 * The case that decides whether the table means anything. A veto that blocks trades which would
	 * never have filled has saved nothing and cost nothing; crediting it would make every veto look
	 * valuable in proportion to how much it rejected.
	 */
	@Test
	public void neverBoughtIsWorthExactlyZero()
	{
		shadow.open(ITEM, "Abyssal whip", "stale side of the book", 1_000_000, 1_100_000, 10, T0, 1.0);

		// Price never comes down to the bid.
		shadow.resolve(series(
			bar(T0 + 300, 1_500_000, 1_600_000),
			bar(T0 + 600, 1_400_000, 1_500_000)), STEP, T0 + 7200);

		ShadowTrader.VetoCost cost = shadow.report(0).get(0);
		assertEquals(1, cost.getNeverBought());
		assertEquals("no capital committed, so no cost and no saving", 0.0, cost.getNetGp(), 0.0);
	}

	@Test
	public void aSellIsNotCreditedFromTheBarThatFilledTheBuy()
	{
		shadow.open(ITEM, "Abyssal whip", "too strict", 1_000_000, 1_100_000, 1, T0, 1.0);

		// One bar spanning both prices. A position cannot be sold before it is held, so this must
		// resolve as stranded rather than as an instant round trip.
		shadow.resolve(series(bar(T0 + 300, 990_000, 1_200_000)), STEP, T0 + 7200);

		assertEquals(1, shadow.report(0).get(0).getStranded());
	}

	@Test
	public void unaffordableTradesAreExcludedFromTheReport()
	{
		shadow.open(ITEM, "Whip", "too strict", 1_000_000, 1_100_000, 500, T0, 1.0);   // 500M
		shadow.open(ITEM, "Whip", "too strict", 1_000_000, 1_100_000, 1, T0, 1.0);     // 1M
		shadow.resolve(series(
			bar(T0 + 300, 990_000, 1_005_000),
			bar(T0 + 600, 1_020_000, 1_150_000)), STEP, T0 + 7200);

		assertEquals("both resolved", 2, shadow.resolvedCount());
		assertEquals("only the affordable one counts", 1,
			shadow.report(100_000_000L).get(0).getTrades());
		assertEquals("with no cap, both count", 2, shadow.report(0).get(0).getTrades());
	}

	@Test
	public void reportRanksTheCostliestVetoFirst()
	{
		shadow.open(ITEM, "Whip", "mildly strict", 1_000_000, 1_010_000, 1, T0, 1.0);
		shadow.open(ITEM, "Whip", "very strict", 1_000_000, 1_200_000, 1, T0, 1.0);
		SeriesSource history = series(
			bar(T0 + 300, 990_000, 1_005_000),
			bar(T0 + 600, 1_020_000, 1_250_000));
		shadow.resolve(history, STEP, T0 + 7200);

		List<ShadowTrader.VetoCost> costs = shadow.report(0);
		assertEquals(2, costs.size());
		assertEquals("the veto forgoing most profit is the one to loosen first",
			"very strict", costs.get(0).getVetoReason());
		assertTrue(costs.get(0).getNetGp() > costs.get(1).getNetGp());
	}

	@Test
	public void acceptedCandidatesShareThePathSoTheyAreComparable()
	{
		shadow.open(ITEM, "Whip", null, 1_000_000, 1_100_000, 1, T0, 1.0);
		shadow.resolve(series(
			bar(T0 + 300, 990_000, 1_005_000),
			bar(T0 + 600, 1_020_000, 1_150_000)), STEP, T0 + 7200);

		assertEquals("a null reason records as accepted, not as a veto",
			ShadowTrader.ACCEPTED, shadow.report(0).get(0).getVetoReason());
	}

	@Test
	public void healthSaysSomethingBeforeAnythingResolves()
	{
		assertTrue(shadow.summary().contains("none resolved"));
		shadow.open(ITEM, "Whip", "too strict", 1_000_000, 1_100_000, 1, T0, 1.0);
		assertTrue(shadow.summary().contains("1 open"));
	}
}
