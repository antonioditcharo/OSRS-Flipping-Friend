package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The optimizer is what makes this a portfolio rather than a ranked list, so the properties worth
 * pinning down are the ones a greedy "take the best repeatedly" approach would get wrong: spreading
 * across items, respecting every budget simultaneously, and refusing to plan at all when the risk
 * budget cannot absorb the worst case.
 */
public class PortfolioOptimizerTest
{
	private static final long NOW = 10_000;

	/** Net profit is supplied by the caller now, because exact tax needs the exempt-item list. */
	private static PortfolioCandidate candidate(int itemId, String name, String group, int buy,
		int sell, int quantity, long worstLoss)
	{
		long netProfit = ((long) sell - taxOf(sell) - buy) * quantity;
		// Both legs very likely to fill, and a small cost if the sell leg strands.
		return new PortfolioCandidate(itemId, name, group, buy, buy, buy, sell, sell, sell, quantity, 100, netProfit,
			worstLoss, netProfit / 4, 0.95, 0.95, 0.4, 0.4, 1.5, NOW + 100);
	}

	private static int taxOf(int sellPrice)
	{
		return sellPrice >= 250_000_000 ? 5_000_000 : (int) ((long) sellPrice * 2 / 100);
	}

	@Test
	public void capitalAlreadyHeldCountsAgainstTheItemCeiling()
	{
		// The ceiling is 15,000 per item and 12,000 of this item is already held. Only 3,000 of head
		// room is left, so a 10,000 order must be refused and the other item taken instead.
		//
		// The exposure maps used to start empty on every plan, so the ceiling only ever limited a plan
		// against itself: collect a large buy and the very next plan would recommend the same item
		// again, because as far as the search was concerned nothing had been bought.
		PortfolioCandidate held = candidate(1, "Held", "potions", 100, 130, 100, 500);
		PortfolioCandidate other = candidate(2, "Other", "runes", 100, 115, 100, 500);

		java.util.Map<Integer, Long> committed = new java.util.HashMap<>();
		committed.put(1, 12_000L);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(
			Arrays.asList(held, other),
			new PortfolioConstraints(2, 25_000, 2_000, 15_000, 20_000)
				.withCommitted(committed, java.util.Collections.emptyMap(), 0),
			"test", NOW);

		assertEquals("READY", plan.getStatus());
		assertTrue("the item already at its ceiling must not be added to",
			plan.getAllocations().stream().noneMatch(a -> a.getCandidate().getItemId() == 1));
	}

	@Test
	public void tradesBelowTheProfitFloorAreNotSuggested()
	{
		// The floor lived only in the built-in engine, whose buy result this plan replaces on every
		// cycle -- so a 5,000 gp setting still produced 200 gp flips.
		PortfolioCandidate thin = candidate(1, "Thin", "potions", 100, 103, 100, 500);
		PortfolioCandidate fat = candidate(2, "Fat", "runes", 100, 160, 100, 500);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(
			Arrays.asList(thin, fat),
			new PortfolioConstraints(2, 25_000, 20_000, 25_000, 25_000)
				.withCommitted(java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), 3_000),
			"test", NOW);

		assertTrue("nothing under the floor may be suggested",
			plan.getAllocations().stream().allMatch(a -> a.getCandidate().getNetProfit() >= 3_000));
		assertTrue("and the trade that clears it still is",
			plan.getAllocations().stream().anyMatch(a -> a.getCandidate().getItemId() == 2));
	}

	@Test
	public void spreadsAcrossItemsRatherThanRepeatingTheBest()
	{
		// Two tactics on the same item plus one on another. A greedy pass would take the two best
		// scores, which are both the same item; a portfolio must not.
		PortfolioCandidate best = candidate(1, "Best", "potions", 100, 120, 100, 500);
		PortfolioCandidate duplicate = candidate(1, "Best", "potions", 100, 130, 100, 500);
		PortfolioCandidate second = candidate(2, "Second", "runes", 100, 115, 100, 500);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(
			Arrays.asList(best, duplicate, second),
			new PortfolioConstraints(2, 25_000, 2_000, 15_000, 20_000), "test", NOW);

		assertEquals("READY", plan.getStatus());
		assertEquals(2, plan.getAllocations().size());
		assertEquals("both slots must hold different items", 2,
			plan.getAllocations().stream().map(a -> a.getCandidate().getItemId()).distinct().count());
	}

	@Test
	public void refusesToPlanWhenTheWorstCaseBreachesTheLossBudget()
	{
		// The drawdown circuit breaker in its sharpest form: a single candidate whose worst case
		// alone exceeds what the session can afford to lose.
		PortfolioCandidate risky = candidate(1, "Risk", "", 100, 130, 100, 2_000);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(Arrays.asList(risky),
			new PortfolioConstraints(1, 20_000, 1_000, 20_000, 20_000), "test", 100);

		assertEquals("UNAVAILABLE", plan.getStatus());
	}

	@Test
	public void neverCommitsMoreCoinsThanAreAvailable()
	{
		List<PortfolioCandidate> many = new ArrayList<>();
		for (int i = 1; i <= 8; i++)
		{
			many.add(candidate(i, "Item " + i, "group" + i, 100, 130, 100, 100));
		}

		// Room for eight slots, but only enough coins for three positions of 10,000 each.
		PortfolioPlan plan = new PortfolioOptimizer().optimize(many,
			new PortfolioConstraints(8, 30_000, 1_000_000, 1_000_000, 1_000_000), "test", NOW);

		long committed = plan.getAllocations().stream()
			.mapToLong(a -> a.getCandidate().getCapitalRequired()).sum();
		assertTrue("committed " + committed + " exceeds the 30,000 available", committed <= 30_000);
	}

	@Test
	public void respectsTheCorrelatedGroupCeiling()
	{
		// Eight different items that all move together. Diversified by name, not by exposure.
		List<PortfolioCandidate> sameGroup = new ArrayList<>();
		for (int i = 1; i <= 8; i++)
		{
			sameGroup.add(candidate(i, "Bar " + i, "bars and ores", 100, 130, 100, 100));
		}

		PortfolioPlan plan = new PortfolioOptimizer().optimize(sameGroup,
			new PortfolioConstraints(8, 1_000_000, 1_000_000, 1_000_000, 25_000), "test", NOW);

		long inGroup = plan.getAllocations().stream()
			.filter(a -> "bars and ores".equals(a.getCandidate().getGroup()))
			.mapToLong(a -> a.getCandidate().getCapitalRequired()).sum();
		assertTrue("group exposure " + inGroup + " exceeds the 25,000 ceiling", inGroup <= 25_000);
	}

	@Test
	public void neverFillsMoreSlotsThanAreFree()
	{
		List<PortfolioCandidate> many = new ArrayList<>();
		for (int i = 1; i <= 8; i++)
		{
			many.add(candidate(i, "Item " + i, "group" + i, 100, 130, 100, 100));
		}

		PortfolioPlan plan = new PortfolioOptimizer().optimize(many,
			new PortfolioConstraints(3, 1_000_000, 1_000_000, 1_000_000, 1_000_000), "test", NOW);

		assertTrue("only three slots are free", plan.getAllocations().size() <= 3);
	}

	@Test
	public void saysSoWhenThereIsNothingWorthDoing()
	{
		PortfolioPlan plan = new PortfolioOptimizer().optimize(new ArrayList<>(),
			new PortfolioConstraints(8, 1_000_000, 1_000_000, 1_000_000, 1_000_000), "test", NOW);

		assertEquals("UNAVAILABLE", plan.getStatus());
		assertTrue("an empty plan must explain itself", plan.getReason() != null
			&& !plan.getReason().isEmpty());
	}

	@Test
	public void aBuyThatNeverFillsCostsSlotTimeRatherThanCapital()
	{
		// The failure that dominates real flipping: the offer just sits there. Cancelling returns
		// every coin, so this must stay clearly profitable rather than being charged a stop loss.
		// Modelling it as a loss is what made the whole market score as unprofitable.
		PortfolioCandidate rarelyFills = new PortfolioCandidate(1, "Slow", "", 1_000, 1_000, 1_000, 1_030, 1_030, 1_030, 100,
			100, 2_000, 50_000, 1_000, 0.35, 1.0, 1.0, 0.5, 1.5, NOW + 100);

		assertTrue("an unfilled buy loses nothing, so expected profit must stay positive",
			rarelyFills.expectedProfit() > 0);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(Arrays.asList(rarelyFills),
			new PortfolioConstraints(8, 1_000_000, 1_000_000, 1_000_000, 1_000_000), "test", NOW);
		assertEquals("READY", plan.getStatus());
	}

	@Test
	public void aTypicalTwoPercentMarginSurvivesAnImperfectFillRate()
	{
		// Regression on the miscalibration that produced empty plans against a live market: a 2%
		// margin charged a 5% stop on every non-completion needs a ~70% round-trip fill rate before
		// it is worth doing, which almost nothing clears.
		long netProfit = 20_000;      // 2% on 1,000,000 of capital
		long worstLoss = 50_000;      // 5% stop, for the risk budget only
		long unwindLoss = 3_000;      // what exiting into the bid actually costs
		PortfolioCandidate ordinary = new PortfolioCandidate(1, "Ordinary", "", 1_000, 1_000, 1_000, 1_025, 1_025, 1_025, 1_000,
			5_000, netProfit, worstLoss, unwindLoss, 0.8, 0.8, 0.5, 0.6, 1.5, NOW + 100);

		assertTrue("a 64% round-trip flip at a 2% margin is profitable and must be planned",
			ordinary.expectedProfit() > 0);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(Arrays.asList(ordinary),
			new PortfolioConstraints(8, 100_000_000, 15_000_000, 35_000_000, 50_000_000), "test", NOW);
		assertEquals("READY", plan.getStatus());
	}

	@Test
	public void strandedSellLegsStillCountAgainstATactic()
	{
		// The opposite guard: the unwind cost must not be ignored either. A sell leg that almost
		// never fills, on an item that moves against you when it strands, is a losing trade.
		PortfolioCandidate stranding = new PortfolioCandidate(1, "Trap", "", 1_000, 1_000, 1_000, 1_010, 1_010, 1_010, 1_000,
			5_000, 4_000, 50_000, 90_000, 0.95, 0.1, 0.3, 1.4, 1.5, NOW + 100);

		assertTrue("a position that reliably strands must score negative",
			stranding.expectedProfit() < 0);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(Arrays.asList(stranding),
			new PortfolioConstraints(8, 100_000_000, 15_000_000, 35_000_000, 50_000_000), "test", NOW);
		assertEquals("UNAVAILABLE", plan.getStatus());
	}

	@Test
	public void slotTimeIsChargedForOffersThatNeverFill()
	{
		// Two tactics with identical profit when they work; one fills promptly, the other usually
		// leaves the slot idle for the full horizon. Per slot-hour they are not equivalent.
		PortfolioCandidate prompt = new PortfolioCandidate(1, "Prompt", "", 1_000, 1_000, 1_000, 1_030, 1_030, 1_030, 100, 100,
			2_000, 5_000, 500, 0.95, 0.95, 0.2, 0.2, 1.5, NOW + 100);
		PortfolioCandidate sluggish = new PortfolioCandidate(2, "Sluggish", "", 1_000, 1_000, 1_000, 1_030, 1_030, 1_030, 100,
			100, 2_000, 5_000, 500, 0.30, 0.95, 0.2, 0.2, 1.5, NOW + 100);

		assertTrue("the slot-hour objective must prefer the tactic that actually fills",
			prompt.expectedGpPerSlotHour() > sluggish.expectedGpPerSlotHour());
	}

	@Test
	public void aRefusalIsReadableForAsLongAsAnyOtherPlan()
	{
		// Refusals used to expire the moment they were created, so every caller checking the expiry
		// discarded them and showed a generic message instead. The reason was computed correctly and
		// then thrown away every time, which is worse than not computing it: buys stop and nothing
		// anywhere can say why.
		PortfolioPlan refusal = new PortfolioOptimizer().optimize(new ArrayList<>(),
			new PortfolioConstraints(8, 1_000_000, 1_000_000, 1_000_000, 1_000_000), "test", NOW);

		assertEquals("UNAVAILABLE", refusal.getStatus());
		assertTrue("the reason must outlive the instant it was produced",
			refusal.getExpiresAt() > NOW);
	}

	@Test
	public void ignoresExpiredCandidates()
	{
		PortfolioCandidate stale = new PortfolioCandidate(1, "Stale", "", 100, 100, 100, 130, 130, 130, 100, 100,
			2_800, 100, 100, 0.9, 0.9, 0.4, 0.4, 1.5, NOW - 1);

		PortfolioPlan plan = new PortfolioOptimizer().optimize(Arrays.asList(stale),
			new PortfolioConstraints(8, 1_000_000, 1_000_000, 1_000_000, 1_000_000), "test", NOW);

		assertEquals("prices that have expired must not be acted on", "UNAVAILABLE", plan.getStatus());
	}
}
