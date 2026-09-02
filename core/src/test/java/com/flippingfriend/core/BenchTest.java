package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The queue of trades behind the ones you can place right now.
 * <p>
 * These were the same list. The plan is sized to free slots, so as you placed trades the portfolio
 * shortened — six, five, four, three — and at zero free slots it was refused outright and showed
 * nothing. That is exactly the moment knowing what is next is worth most, and it is also what made
 * the learning monitor read "no plan at all" for long stretches when the truth was "a plan was made
 * and there was nowhere to put it".
 * <p>
 * A full board was already being ranked every cycle and then thrown away down to two numbers.
 */
public class BenchTest
{
	private static final long NOW = 10_000;

	private static PortfolioCandidate candidate(int itemId, String name)
	{
		return new PortfolioCandidate(itemId, name, "", 1_000, 1_000, 1_000, 1_200, 1_200, 1_200, 1_000, 100_000, 200_000,
			100_000, 50_000, 0.95, 0.95, 0.4, 0.4, 1.5, NOW + 100);
	}

	private static PortfolioAllocation allocation(int rank, int itemId, String name)
	{
		return new PortfolioAllocation(rank, candidate(itemId, name), "BUY");
	}

	private static PortfolioPlan plan(List<PortfolioAllocation> allocations)
	{
		return new PortfolioPlan("c", NOW, NOW + 90, "READY", "", 500, allocations);
	}

	@Test
	public void aPlanWithNoBenchFallsBackToWhatItCanPlace()
	{
		// A plan from an older build, or one Gson filled without running a constructor. It must show
		// the list it does have rather than nothing.
		PortfolioPlan bare = plan(Collections.singletonList(allocation(1, 1513, "Magic logs")));

		assertEquals(1, bare.getBench().size());
		assertEquals(1513, bare.getBench().get(0).getCandidate().getItemId());
	}

	@Test
	public void theBenchIsKeptAcrossDiagnostics()
	{
		// Diagnostics are attached after the board, so a copy that dropped the bench would empty the
		// portfolio on every single plan -- and nothing else would look wrong.
		PortfolioPlan withBench = plan(Collections.<PortfolioAllocation>emptyList())
			.withBoard(500, 2, Arrays.asList(allocation(1, 1513, "Magic logs"),
				allocation(2, 1515, "Yew logs")));

		PortfolioPlan diagnosed = withBench.withDiagnostics(null);

		assertEquals("the bench must survive having diagnostics bolted on", 2,
			diagnosed.getBench().size());
	}

	@Test
	public void aBenchSurvivesAPlanThatCanPlaceNothing()
	{
		// Every slot busy. The plan offers nothing and must still say what is queued.
		PortfolioPlan busy = PortfolioPlan.unavailable("c", "Every Grand Exchange slot is occupied.",
			NOW).withBoard(500, 3, Arrays.asList(allocation(1, 1513, "Magic logs"),
				allocation(2, 1515, "Yew logs"), allocation(3, 561, "Nature rune")));

		assertTrue("nothing can be placed", busy.getAllocations().isEmpty());
		assertEquals("but the queue is still there", 3, busy.getBench().size());
	}

	@Test
	public void theBenchNeverSubstitutesForWhatCanBePlaced()
	{
		// The one thing that must not happen. The buy card reads getAllocations(); if the bench ever
		// leaked into it the plugin would tell the player to place a trade they have no slot for.
		List<PortfolioAllocation> offered = new ArrayList<>();
		offered.add(allocation(1, 1513, "Magic logs"));

		PortfolioPlan withQueue = plan(offered).withBoard(500, 4,
			Arrays.asList(allocation(1, 1513, "Magic logs"), allocation(2, 1515, "Yew logs"),
				allocation(3, 561, "Nature rune"), allocation(4, 565, "Blood rune")));

		assertEquals("only one slot was free, so only one trade is offered", 1,
			withQueue.getAllocations().size());
		assertEquals("while the queue is the full board", 4, withQueue.getBench().size());
	}

	@Test
	public void theHeadOfTheBenchIsTheTradeBeingOffered()
	{
		// Otherwise the list disagrees with the card above it, which is worse than a short list.
		PortfolioAllocation offered = allocation(1, 1513, "Magic logs");
		PortfolioPlan withQueue = plan(Collections.singletonList(offered))
			.withBoard(500, 2, Arrays.asList(offered, allocation(2, 1515, "Yew logs")));

		assertSame(offered, withQueue.getBench().get(0));
	}
}
