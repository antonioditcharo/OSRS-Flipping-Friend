package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.core.PortfolioOptimizer;
import com.flippingfriend.core.PortfolioPlan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Stitching the queue together, and measuring the board on the whole bankroll.
 * <p>
 * Two rankings are made over the same candidates: what can be placed against the slots actually free,
 * and a full board. The board was thrown away down to two numbers; it is now also the queue the
 * player sees. Merging matters because the two are ranked separately — the optimizer is a knapsack,
 * so the best <em>set</em> of eight can open with a different trade from the best single one — and a
 * list whose first row disagreed with the card above it would be worse than a short list.
 */
public class BenchMergeTest
{
	private static final long NOW = 10_000;

	private static PortfolioCandidate candidate(int itemId, String name, int sell, int quantity)
	{
		long netProfit = ((long) sell - 1_000) * quantity;
		return new PortfolioCandidate(itemId, name, "", 1_000, 1_000, 1_000, sell, sell, sell, quantity, 100_000, netProfit,
			netProfit / 2, netProfit / 4, 0.95, 0.95, 0.4, 0.4, 1.5, NOW + 100);
	}

	private static PortfolioAllocation allocation(int rank, int itemId, String name)
	{
		return new PortfolioAllocation(rank, candidate(itemId, name, 1_200, 1_000), "BUY");
	}

	@Test
	public void whatCanBePlacedComesFirstAndKeepsItsOrder()
	{
		List<PortfolioAllocation> offered = Arrays.asList(
			allocation(1, 1513, "Magic logs"), allocation(2, 1515, "Yew logs"));
		List<PortfolioAllocation> board = Arrays.asList(
			allocation(1, 561, "Nature rune"), allocation(2, 1513, "Magic logs"),
			allocation(3, 565, "Blood rune"), allocation(4, 1515, "Yew logs"));

		List<PortfolioAllocation> bench = PortfolioPlanner.bench(offered, board);

		assertEquals("the queue is the whole board, deduped", 4, bench.size());
		assertEquals("and it opens with what the card is telling you to place", 1513,
			bench.get(0).getCandidate().getItemId());
		assertEquals(1515, bench.get(1).getCandidate().getItemId());
	}

	@Test
	public void anItemIsNeverListedTwice()
	{
		// Both rankings run over the same pool, so overlap is the normal case, not the exception.
		List<PortfolioAllocation> offered = Collections.singletonList(allocation(1, 1513, "Magic logs"));
		List<PortfolioAllocation> board = Arrays.asList(
			allocation(1, 1513, "Magic logs"), allocation(2, 1513, "Magic logs"));

		assertEquals(1, PortfolioPlanner.bench(offered, board).size());
	}

	@Test
	public void ranksAreRenumberedAcrossTheMergedList()
	{
		// Each side numbers from one. Kept as they were, the panel would print two rank ones.
		List<PortfolioAllocation> bench = PortfolioPlanner.bench(
			Collections.singletonList(allocation(1, 1513, "Magic logs")),
			Arrays.asList(allocation(1, 561, "Nature rune"), allocation(2, 565, "Blood rune")));

		assertEquals(3, bench.size());
		for (int i = 0; i < bench.size(); i++)
		{
			assertEquals("rank " + (i + 1), i + 1, bench.get(i).getRank());
		}
	}

	@Test
	public void aBusyAccountStillGetsAFullQueue()
	{
		// The case that started this: nothing placeable, everything queued.
		List<PortfolioAllocation> board = Arrays.asList(
			allocation(1, 1513, "Magic logs"), allocation(2, 1515, "Yew logs"),
			allocation(3, 561, "Nature rune"));

		List<PortfolioAllocation> bench =
			PortfolioPlanner.bench(Collections.<PortfolioAllocation>emptyList(), board);

		assertEquals(3, bench.size());
		assertEquals(1, bench.get(0).getRank());
	}

	/**
	 * The measurement half. The board is meant to answer "how good are the opportunities", which has
	 * to be the same question whether or not the account's money is currently deployed — and it was
	 * not, because the board was ranked against uncommitted coins only.
	 */
	@Test
	public void theBoardIsRankedAgainstTheWholeBankroll()
	{
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		List<PortfolioCandidate> pool = new ArrayList<>();
		// Four trades, a million coins each.
		pool.add(candidate(1, "a", 1_300, 1_000));
		pool.add(candidate(2, "b", 1_250, 1_000));
		pool.add(candidate(3, "c", 1_200, 1_000));
		pool.add(candidate(4, "d", 1_150, 1_000));

		// An account with 4m of equity, 3m of it already committed to open trades.
		PortfolioConstraints idle = new PortfolioConstraints(4, 4_000_000L, 100_000_000L,
			100_000_000L, 100_000_000L);
		PortfolioConstraints busy = idle.withCoins(1_000_000L);

		PortfolioPlan onFreeCoins = optimizer.optimize(pool, busy, "c", NOW);
		PortfolioPlan onEquity = optimizer.optimize(pool, busy.withCoins(4_000_000L), "c", NOW);

		assertTrue("free coins alone field only one trade", onFreeCoins.getAllocations().size() < 4);
		assertEquals("the whole bankroll fields the whole board", 4,
			onEquity.getAllocations().size());
		assertEquals("which is what makes the board comparable with an idle account",
			optimizer.optimize(pool, idle, "c", NOW).getExpectedGpPerSlotHour(),
			onEquity.getExpectedGpPerSlotHour(), 0.001);
	}
}
