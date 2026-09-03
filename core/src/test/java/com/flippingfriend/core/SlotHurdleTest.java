package com.flippingfriend.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Covers the acceptance floor: a trade must be worth the slot it occupies, not merely profitable.
 *
 * <p>The optimiser maximises a sum, so before this floor existed any positive rate improved the
 * total and eight slots would fill with trades earning a fraction of what a slot is worth — each
 * holding its slot for a whole horizon. Slot-time is the binding constraint at scale, and a slot left
 * free is re-planned within a cycle, so refusing a poor trade costs minutes while taking one costs
 * hours.
 */
public class SlotHurdleTest
{
	private static final long NOW = 1_700_000_000L;

	/**
	 * A candidate whose rate is set by the margin: profit is what varies, and the horizon and fill
	 * probabilities are held constant so {@code expectedGpPerSlotHour} moves with it alone.
	 */
	private static PortfolioCandidate candidate(int itemId, String name, long netProfit)
	{
		return new PortfolioCandidate(itemId, name, "", 1_000, 1_000, 1_000, 1_100, 1_100, 1_100,
			100, 100, netProfit, 1_000, 1_000, 0.9, 0.9, 0.5, 0.5, 4.0, NOW + 600);
	}

	private static PortfolioConstraints slots(int free, long coins)
	{
		return new PortfolioConstraints(free, coins, 100_000_000L, 100_000_000L, 100_000_000L);
	}

	private static List<PortfolioCandidate> mixed()
	{
		return new ArrayList<>(Arrays.asList(
			candidate(1, "Strong", 500_000),
			candidate(2, "Decent", 200_000),
			candidate(3, "Marginal", 2_000),
			candidate(4, "Barely worth it", 500)));
	}

	@Test
	public void withoutAHurdleEveryPositiveTradeTakesASlot()
	{
		PortfolioPlan plan = new PortfolioOptimizer()
			.optimize(mixed(), slots(4, 100_000_000L), "c", NOW);

		assertEquals("the old behaviour: anything positive improves the sum",
			4, plan.getAllocations().size());
	}

	@Test
	public void aHurdleTurnsAwayTradesNotWorthTheirSlot()
	{
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		PortfolioPlan board = optimizer.optimize(mixed(), slots(4, 100_000_000L), "c", NOW);
		double boardRate = board.getExpectedGpPerSlotHour() / board.getAllocations().size();

		PortfolioPlan plan = optimizer.optimize(mixed(),
			slots(4, 100_000_000L).withHurdle(0.25 * boardRate), "c", NOW);

		assertTrue("the weak trades must be refused", plan.getAllocations().size() < 4);
		assertTrue("and the strong one kept", plan.getAllocations().size() >= 1);
		for (PortfolioAllocation allocation : plan.getAllocations())
		{
			assertTrue("nothing below the hurdle may be selected",
				allocation.getCandidate().expectedGpPerSlotHour() >= 0.25 * boardRate);
		}
	}

	/**
	 * A slot refused is a slot available next cycle, so what <em>is</em> taken must earn more per
	 * slot — which is the entire argument for the floor.
	 *
	 * <p>Note the units. {@code PortfolioPlan.getExpectedGpPerSlotHour()} reads like a rate but is the
	 * <b>sum</b> of the selected candidates' rates, which is why {@code boardSize} is carried beside
	 * it. Refusing trades therefore always lowers the total; the number that must rise is the total
	 * divided by the slots actually used.
	 */
	@Test
	public void refusingWeakTradesRaisesTheRatePerSlot()
	{
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		PortfolioPlan unhurdled = optimizer.optimize(mixed(), slots(4, 100_000_000L), "c", NOW);
		double perSlot = unhurdled.getExpectedGpPerSlotHour() / unhurdled.getAllocations().size();

		PortfolioPlan hurdled = optimizer.optimize(mixed(),
			slots(4, 100_000_000L).withHurdle(0.25 * perSlot), "c", NOW);

		assertTrue("fewer slots used", hurdled.getAllocations().size() < unhurdled.getAllocations().size());
		assertTrue("total falls, because the figure is a sum",
			hurdled.getExpectedGpPerSlotHour() < unhurdled.getExpectedGpPerSlotHour());
		assertTrue("but each slot used earns more",
			hurdled.getExpectedGpPerSlotHour() / hurdled.getAllocations().size() > perSlot);
	}

	/** A hurdle nothing can clear must say so, rather than reading as a dead market. */
	@Test
	public void anEmptyPlanNamesTheHurdleRatherThanBlamingTheMarket()
	{
		PortfolioPlan plan = new PortfolioOptimizer()
			.optimize(mixed(), slots(4, 100_000_000L).withHurdle(1e12), "c", NOW);

		assertTrue(plan.getAllocations().isEmpty());
		assertTrue("the reason must name the floor, not the market: " + plan.getReason(),
			plan.getReason().contains("leaving the slot free"));
	}

	@Test
	public void zeroHurdlePreservesTheOldBehaviourExactly()
	{
		PortfolioOptimizer optimizer = new PortfolioOptimizer();
		PortfolioPlan without = optimizer.optimize(mixed(), slots(4, 100_000_000L), "c", NOW);
		PortfolioPlan withZero = optimizer.optimize(mixed(),
			slots(4, 100_000_000L).withHurdle(0.0), "c", NOW);

		assertEquals(without.getAllocations().size(), withZero.getAllocations().size());
		assertEquals(without.getExpectedGpPerSlotHour(), withZero.getExpectedGpPerSlotHour(), 1e-9);
	}

	/** The hurdle must survive the with-ers the planner uses to build the measurement board. */
	@Test
	public void theHurdleSurvivesWithSlotsAndWithCoins()
	{
		PortfolioConstraints base = slots(2, 5_000_000L).withHurdle(1234.5);

		assertEquals(1234.5, base.withSlots(8).getHurdleGpPerSlotHour(), 1e-9);
		assertEquals(1234.5, base.withCoins(9_000_000L).getHurdleGpPerSlotHour(), 1e-9);
		assertEquals("and the board is built without one",
			0.0, slots(8, 5_000_000L).getHurdleGpPerSlotHour(), 1e-9);
	}

	@Test
	public void aNegativeOrNonFiniteHurdleIsTreatedAsNone()
	{
		assertEquals(0.0, slots(4, 1_000L).withHurdle(-5.0).getHurdleGpPerSlotHour(), 1e-9);
		assertEquals(0.0, slots(4, 1_000L).withHurdle(Double.NaN).getHurdleGpPerSlotHour(), 1e-9);
		assertFalse(Double.isInfinite(
			slots(4, 1_000L).withHurdle(Double.POSITIVE_INFINITY).getHurdleGpPerSlotHour()));
	}
}
