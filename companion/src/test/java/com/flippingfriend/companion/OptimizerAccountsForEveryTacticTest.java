package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.core.PortfolioOptimizer;
import com.flippingfriend.core.PortfolioPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The last stretch of the funnel, which used to go dark exactly where the money is decided.
 *
 * <p>Screening could account for every one of four thousand items it threw away. Then the plan
 * reported <em>&ldquo;81 tactics generated, 1 slot filled&rdquo;</em> and explained none of the other
 * eighty — on a live account with eight free slots and forty-seven million idle coins.
 *
 * <p>That gap is worse than the one in {@code screen()}, because every filter here is a number the
 * player can change. On that live plan a single one of them — the minimum profit per flip, set to
 * 5,000 — removed 80 of the 81 tactics, and nothing anywhere said so. Its default is 50,000, which on
 * the same market would have removed all 81 and produced an empty plan with no way to find out why.
 */
public class OptimizerAccountsForEveryTacticTest
{
	private static final long NOW = 1_700_000_000L;

	/**
	 * Capital is not passed: {@link PortfolioCandidate} derives it as buy price times quantity, so a
	 * candidate that needs no capital is one with no quantity.
	 */
	private static PortfolioCandidate candidate(int itemId, long netProfit, int quantity,
		double probability, long expiresAt)
	{
		return new PortfolioCandidate(itemId, "Item " + itemId, "", 100, 100, 100, 200, 200, 200,
			quantity, 1_000, netProfit, Math.max(1, netProfit / 2), Math.max(1, netProfit / 3),
			probability, probability, 1.0, 1.0, 4.0, expiresAt);
	}

	private static PortfolioCandidate candidate(int itemId, long netProfit, int quantity,
		double probability)
	{
		return candidate(itemId, netProfit, quantity, probability, NOW + 600);
	}

	private static PortfolioConstraints limits(long minProfit, int slots, long coins)
	{
		return new PortfolioConstraints(slots, coins, 10_000_000L, 0, 0,
			new HashMap<>(), new HashMap<>(), minProfit);
	}

	@Test
	public void theMinimumProfitFilterSaysHowMuchItTurnedAway()
	{
		// The live case, reproduced. Eighty trades that would each have made something, all removed
		// by one setting, and the plan previously reporting only that they were gone.
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		List<PortfolioCandidate> tactics = new ArrayList<>();
		for (int i = 0; i < 80; i++)
		{
			tactics.add(candidate(i, 900, 100, 0.8));
		}
		tactics.add(candidate(999, 7_650, 580, 0.4));

		optimizer.optimize(tactics, limits(5_000, 8, 47_000_000L), "c", NOW);
		Map<String, Integer> rejections = optimizer.lastRejections();

		assertEquals("eighty turned away by one number", Integer.valueOf(80),
			rejections.get("Worth less than your 5000 gp minimum profit per flip."));
		assertEquals("and one survivor", 1, optimizer.lastConsidered());
	}

	@Test
	public void everyTacticIsAccountedForOneWayOrTheOther()
	{
		// The property, stated as the funnel's own arithmetic: considered plus rejected is what went
		// in. Unlike the screen — where an item can pass and be vetoed later — nothing here is
		// counted twice, so the identity genuinely holds and is worth asserting.
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		List<PortfolioCandidate> tactics = new ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			tactics.add(candidate(i, 900, 100, 0.8));
		}
		for (int i = 40; i < 60; i++)
		{
			tactics.add(candidate(i, 20_000, 100, 0.8));
		}
		// Expired, and one with no chance of completing.
		tactics.add(candidate(500, 20_000, 100, 0.8, NOW - 1));
		tactics.add(candidate(501, 20_000, 100, 0.0));

		optimizer.optimize(tactics, limits(5_000, 8, 47_000_000L), "c", NOW);

		int rejected = optimizer.lastRejections().values().stream().mapToInt(Integer::intValue).sum();
		assertEquals("nothing may vanish between a tactic and a slot",
			tactics.size(), rejected + optimizer.lastConsidered());
	}

	@Test
	public void eachFilterIsNamedSeparatelyBecauseEachIsADifferentFix()
	{
		// "Nothing was worth trading" is one sentence for four different situations, and the player's
		// response to each is different: lower a setting, wait for a fresh quote, or accept that the
		// market is thin right now.
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		List<PortfolioCandidate> tactics = new ArrayList<>();
		tactics.add(candidate(1, 900, 100, 0.8));
		tactics.add(candidate(2, 20_000, 100, 0.8, NOW - 1));
		tactics.add(candidate(3, 20_000, 100, 0.0));
		// No quantity, so no capital, so nothing to place.
		tactics.add(candidate(4, 20_000, 0, 0.8));

		optimizer.optimize(tactics, limits(5_000, 8, 47_000_000L), "c", NOW);
		Map<String, Integer> rejections = optimizer.lastRejections();

		assertEquals(4, rejections.size());
		assertTrue(rejections.containsKey("Worth less than your 5000 gp minimum profit per flip."));
		assertTrue(rejections.containsKey("Priced from a quote that has already expired."));
		assertTrue(rejections.containsKey("No chance of completing within the horizon."));
		assertTrue(rejections.containsKey("No capital required, so nothing to place."));
	}

	@Test
	public void theSlotHurdleIsNamedToo()
	{
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		List<PortfolioCandidate> tactics = new ArrayList<>();
		for (int i = 0; i < 5; i++)
		{
			tactics.add(candidate(i, 20_000, 100, 0.8));
		}

		optimizer.optimize(tactics, limits(5_000, 8, 47_000_000L).withHurdle(1_000_000), "c", NOW);

		assertEquals(Integer.valueOf(5), optimizer.lastRejections()
			.get("Worth less than leaving the slot free for a better trade."));
	}

	@Test
	public void theRecordIsClearedBetweenRuns()
	{
		// One optimizer serves every plan. A rejection record that survived a run would report a
		// history rather than this plan, and the counts would climb for ever.
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		List<PortfolioCandidate> thin = Collections.singletonList(candidate(1, 900, 100, 0.8));

		optimizer.optimize(thin, limits(5_000, 8, 47_000_000L), "c", NOW);
		optimizer.optimize(thin, limits(5_000, 8, 47_000_000L), "c", NOW);

		assertEquals("one tactic, rejected once, however many plans have run", 1,
			optimizer.lastRejections().values().stream().mapToInt(Integer::intValue).sum());
	}

	@Test
	public void aPlanThatFillsEverySlotRejectsNothing()
	{
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		List<PortfolioCandidate> tactics = new ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			tactics.add(candidate(i, 20_000, 100, 0.8));
		}

		PortfolioPlan plan = optimizer.optimize(tactics, limits(5_000, 8, 47_000_000L), "c", NOW);

		assertEquals(8, plan.getAllocations().size());
		assertTrue(optimizer.lastRejections().isEmpty());
	}
}
