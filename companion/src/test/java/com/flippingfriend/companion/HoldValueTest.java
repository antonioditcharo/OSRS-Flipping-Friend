package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Covers the hold side of the slot hurdle: whether a resting offer still earns its slot.
 *
 * <p>Repricing fired on a staleness timer before this, which answers a different question. An
 * offer's age says nothing on its own — a slow fill about to complete is worth keeping and a dead
 * one is not, and an hour on the clock cannot tell them apart. What decides it is whether the slot
 * would earn more doing something else, scored with the same fill model the entry decision used so
 * the two are on one scale.
 */
public class HoldValueTest
{
	private static final int ITEM = 4151;
	private static final long T0 = 1_700_000_000L;

	/** Liquid history at around 1,000 gp, so the fill model has something to work with. */
	private static SeriesSource liquidHistory()
	{
		List<Candle> bars = new ArrayList<>();
		for (int i = 0; i < 60; i++)
		{
			bars.add(new Candle(T0 - (60 - i) * 300L, 1_010, 1_000, 5_000, 5_000));
		}
		return (itemId, timestep) -> itemId == ITEM ? bars : Collections.<Candle>emptyList();
	}

	private CandidateFactory factory()
	{
		return new CandidateFactory(liquidHistory());
	}

	@Test
	public void anOfferAtTheMarketIsWorthKeeping()
	{
		// Buying at 1,000 and the market pays 1,100: a real spread on a liquid item.
		double value = factory().buyHoldValue(ITEM, 1_000, 100, 1_100, 1.0);

		assertTrue("a fillable offer with a live margin must be worth something, got " + value,
			value > 0);
	}

	@Test
	public void anOfferWhoseSpreadNoLongerCoversTaxIsWorthNothing()
	{
		// Market has fallen to just above the offer: 2% tax eats the difference.
		assertEquals("no margin left means no value in holding",
			0.0, factory().buyHoldValue(ITEM, 1_000, 100, 1_005, 1.0), 1e-9);
	}

	@Test
	public void anOfferAboveTheMarketCannotBeValued()
	{
		assertTrue("a market below the bid is not a spread, it is a loss",
			factory().buyHoldValue(ITEM, 1_000, 100, 900, 1.0) < 0);
	}

	/**
	 * Unknown must not read as worthless. Returning zero for an item with no history would have the
	 * planner recommend cancelling every offer it cannot price, which is the opposite of caution.
	 */
	@Test
	public void notEnoughHistoryIsNegativeRatherThanZero()
	{
		double value = factory().buyHoldValue(99_999, 1_000, 100, 1_100, 1.0);

		assertTrue("no history must be reported as unknown, got " + value, value < 0);
	}

	/**
	 * Only the time still ahead counts. Slot-time already spent is gone either way, and charging it
	 * again would keep an offer alive precisely because it has been expensive so far — the sunk-cost
	 * mistake, wired into an engine.
	 */
	@Test
	public void lessRemainingTimeMeansAHigherRateForTheSameProfit()
	{
		CandidateFactory factory = factory();
		double overTwoHours = factory.buyHoldValue(ITEM, 1_000, 100, 1_100, 2.0);
		double overHalfAnHour = factory.buyHoldValue(ITEM, 1_000, 100, 1_100, 0.5);

		assertTrue("both must be valuable", overTwoHours > 0 && overHalfAnHour > 0);
		assertTrue("the same profit earned sooner is a higher rate",
			overHalfAnHour > overTwoHours);
	}

	@Test
	public void degenerateInputsAreRefusedRatherThanGuessed()
	{
		CandidateFactory factory = factory();
		assertTrue(factory.buyHoldValue(ITEM, 0, 100, 1_100, 1.0) < 0);
		assertTrue(factory.buyHoldValue(ITEM, 1_000, 0, 1_100, 1.0) < 0);
		assertTrue(factory.buyHoldValue(ITEM, 1_000, 100, 1_100, 0.0) < 0);
	}

	/**
	 * The sell side is deliberately not valued this way. Cancelling a resting buy frees the slot and
	 * costs nothing, because no capital has changed hands; cancelling a sell leaves the item in your
	 * inventory, so the slot is not really freed and the decision is about exit pricing instead.
	 * Applying one hurdle to both would recommend abandoning positions to chase a better entry.
	 */
	@Test
	public void sellSideUsesTheGeneralValuationNotTheBuyHurdle()
	{
		// Priced inside the range the history actually trades at. A sell at 1,100 against a market
		// topping out at 1,010 is refused as implausible, which is the fill model being right rather
		// than the valuation being broken.
		double reachable = factory().holdValue(ITEM, 1_005, 100, false, 80, 1.0);
		double unreachable = factory().holdValue(ITEM, 1_100, 100, false, 80, 1.0);

		assertTrue("the general form values a sell the market can reach, got " + reachable,
			reachable > 0);
		assertTrue("and refuses one it cannot, got " + unreachable, unreachable < 0);
	}
}
