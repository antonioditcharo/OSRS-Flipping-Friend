package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ranking a full board, and why it is ranked separately from what gets offered.
 * <p>
 * The rate used to be whatever the plan happened to be allocating, so one free slot read about a third
 * of three and a busy account read nothing at all. The "is it getting better" chart was therefore a
 * picture of how occupied the Grand Exchange was rather than of how well the planner was doing.
 * <p>
 * The trap in fixing it is that the optimizer maximises the total subject to shared capital and
 * exposure limits, so the best <em>trio</em> can open with a worse first trade in order to fit two
 * better ones behind it. You place one now and the plan is rebuilt the moment a slot frees, so the
 * board must never be allowed to decide what you are told to buy.
 */
public class BoardRankingTest
{
	private static final long NOW = 10_000;

	private final PortfolioOptimizer optimizer = new PortfolioOptimizer();

	private static PortfolioCandidate candidate(int itemId, String name, int buy, int sell,
		int quantity)
	{
		long netProfit = ((long) sell - buy) * quantity;
		return new PortfolioCandidate(itemId, name, "", buy, buy, buy, sell, sell, sell, quantity, 100_000, netProfit,
			netProfit / 2, netProfit / 4, 0.95, 0.95, 0.4, 0.4, 1.5, NOW + 100);
	}

	private static PortfolioConstraints slots(int free, long coins)
	{
		return new PortfolioConstraints(free, coins, 100_000_000L, 100_000_000L, 100_000_000L);
	}

	/** Three trades of clearly descending quality, each needing a million coins. */
	private List<PortfolioCandidate> threeTrades()
	{
		List<PortfolioCandidate> all = new ArrayList<>();
		all.add(candidate(1, "best", 1_000, 1_300, 1_000));
		all.add(candidate(2, "middle", 1_000, 1_200, 1_000));
		all.add(candidate(3, "worst", 1_000, 1_100, 1_000));
		return all;
	}

	@Test
	public void aBoardIsRankedEvenWhenNothingCanBePlaced()
	{
		// The hole: with no free slot there was no plan and no rate at all, so the metric could not
		// tell "the planner found nothing" from "the planner was never asked".
		PortfolioPlan board = optimizer.optimize(threeTrades(), slots(3, 100_000_000L), "c", NOW);

		assertEquals("READY", board.getStatus());
		assertEquals("a full board of three", 3, board.getAllocations().size());
		assertTrue("and it carries a rate", board.getExpectedGpPerSlotHour() > 0);
	}

	@Test
	public void theOldMeasureSwungWithSlotsAndTheBoardDoesNot()
	{
		double board = optimizer.optimize(threeTrades(), slots(3, 100_000_000L), "c", NOW)
			.getExpectedGpPerSlotHour();
		double oneSlotOnly = optimizer.optimize(threeTrades(), slots(1, 100_000_000L), "c", NOW)
			.getExpectedGpPerSlotHour();

		assertTrue("ranking only what you can place reads far lower on a busy account: "
			+ oneSlotOnly + " vs " + board, oneSlotOnly < board);
		// Ranked twice from the same opportunities, the board is the same number both times — which is
		// the whole property that makes it worth charting.
		assertEquals(board, optimizer.optimize(threeTrades(), slots(3, 100_000_000L), "c", NOW)
			.getExpectedGpPerSlotHour(), 1e-9);
	}

	@Test
	public void whatYouArePlacingIsStillChosenAgainstTheSlotsYouHave()
	{
		// The trade offered with one slot free is the best single trade, not the opening move of the
		// best trio. This is why the board is a second ranking rather than a wider first one.
		PortfolioPlan actionable = optimizer.optimize(threeTrades(), slots(1, 100_000_000L), "c", NOW);

		assertEquals("one slot free means one trade offered", 1, actionable.getAllocations().size());
		assertEquals("and it is the best one available", 1,
			actionable.getAllocations().get(0).getCandidate().getItemId());
	}

	@Test
	public void theBoardStillRespectsTheMoneyYouActuallyHave()
	{
		// A board is a ranking, not a wish: three trades needing a million each cannot all be fielded
		// on a million and a half, and the plan must not claim they can.
		PortfolioPlan board = optimizer.optimize(threeTrades(), slots(3, 1_500_000L), "c", NOW);

		assertTrue("only what the coins cover: " + board.getAllocations().size(),
			board.getAllocations().size() < 3);
	}

	@Test
	public void theBoardRateTravelsWithThePlanWithoutDisturbingItsOwn()
	{
		// The panel calls the plan's own number "per occupied slot-hour" and it has to stay true, so
		// the board rate is carried alongside rather than written over it.
		PortfolioPlan actionable = optimizer.optimize(threeTrades(), slots(1, 100_000_000L), "c", NOW);
		double own = actionable.getExpectedGpPerSlotHour();

		PortfolioPlan withBoard = actionable.withBoard(12_345.0, 3);

		assertEquals("the plan still reports what it is actually offering", own,
			withBoard.getExpectedGpPerSlotHour(), 1e-9);
		assertEquals(12_345.0, withBoard.getBoardGpPerSlotHour(), 1e-9);
		assertEquals(3, withBoard.getBoardSize());
	}
}
