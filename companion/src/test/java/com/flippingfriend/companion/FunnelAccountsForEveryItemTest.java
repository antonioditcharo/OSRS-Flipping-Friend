package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Every item that falls out of the plan can say why it did.
 *
 * <p>Audit item 67. The funnel could report "3,088 priced → 412 liquid enough → 90 studied → 0
 * cleared" and not account for a single item that fell out between those numbers, because four
 * discard paths in {@code screen()} left through a bare {@code continue}. Nothing landed anywhere: no
 * count, no reason, no name. A player looking at an empty plan had no way to find out why it was
 * empty, and {@link ShadowTrader} had nothing to attribute a counterfactual to.
 *
 * <p>The distinction that matters is <b>which discards are decisions</b>. A veto is a judgement — the
 * trade was available and was passed up — and gets paper-traded so the refusal can be scored. A drop
 * is an item that was never on the table: a members item on a free account is not a missed
 * opportunity, and counting it as one would inflate the measured cost of every real refusal.
 */
public class FunnelAccountsForEveryItemTest
{
	private static final long T0 = 1_700_000_000L / 300 * 300;

	private static SeriesSource noHistory()
	{
		return (itemId, timestep) -> Collections.<Candle>emptyList();
	}

	private static CandidateFactory.QuotedItem quoted(int id, String name, boolean members,
		Integer low, Integer high)
	{
		MarketIngestionService.Item item =
			new MarketIngestionService.Item(id, name, 1_000, members, true);
		LatestPrice price = new LatestPrice(high, T0, low, T0);
		Candle bar = high == null || low == null
			? new Candle(T0, high, low, 5_000, 5_000)
			: new Candle(T0, high, low, 5_000, 5_000);
		return new CandidateFactory.QuotedItem(item, price, bar, bar);
	}

	private static PlanDiagnostics runPlan(List<CandidateFactory.QuotedItem> universe,
		long spendable, boolean members)
	{
		CandidateFactory factory = new CandidateFactory(noHistory());
		factory.build(universe, 1.0, new HashMap<>(), spendable, members,
			Instant.ofEpochSecond(T0));
		return factory.lastFunnel(0, spendable);
	}

	private static int totalDiscarded(PlanDiagnostics funnel)
	{
		int total = 0;
		for (int count : funnel.getVetoCounts().values())
		{
			total += count;
		}
		return total;
	}

	@Test
	public void aMembersItemOnAFreeAccountSaysSo()
	{
		PlanDiagnostics funnel = runPlan(Collections.singletonList(
			quoted(4151, "Abyssal whip", true, 1_000_000, 1_100_000)), 100_000_000L, false);

		assertTrue("the reason must be recorded: " + funnel.getVetoCounts(),
			funnel.getVetoCounts().containsKey("Members item, which this account cannot trade."));
		assertTrue("and the item named", funnel.getVetoExamples()
			.get("Members item, which this account cannot trade.").contains("Abyssal whip"));
	}

	@Test
	public void aOneSidedQuoteSaysSo()
	{
		PlanDiagnostics funnel = runPlan(Collections.singletonList(
			quoted(561, "Nature rune", false, 100, null)), 100_000_000L, true);

		assertTrue(funnel.getVetoCounts()
			.containsKey("Only one side of this item has a recent price."));
	}

	@Test
	public void anItemWithNoGapSaysSo()
	{
		// A judgement rather than an impossibility: the item was there, priced, and had nothing to
		// capture. That is the sort of refusal worth scoring later.
		PlanDiagnostics funnel = runPlan(Collections.singletonList(
			quoted(561, "Nature rune", false, 1_000, 1_000)), 100_000_000L, true);

		assertTrue(funnel.getVetoCounts()
			.containsKey("No gap between the buy and sell price."));
	}

	@Test
	public void anItemBeyondTheBankrollSaysSo()
	{
		// The three cases that used to be one silent branch. A player is owed the difference between
		// "nobody is trading this", "there is no gap" and "you cannot afford one of these".
		PlanDiagnostics funnel = runPlan(Collections.singletonList(
			quoted(4151, "Twisted bow", false, 1_500_000_000, 1_600_000_000)), 1_000_000L, true);

		assertTrue("the bankroll is the reason, and it is not the item's fault: "
			+ funnel.getVetoCounts(),
			funnel.getVetoCounts().containsKey("Costs more than the whole bankroll."));
	}

	@Test
	public void everyItemInTheFeedCanSayWhyItIsNotInThePlan()
	{
		// The property the whole thing exists for, and it is stated as "nothing vanishes" rather than
		// as an arithmetic identity on the counts. Those cannot balance, because an item can pass the
		// screen and be vetoed later during deep analysis -- so it is counted as shortlisted and
		// carries a reason as well. That is correct behaviour and a summed identity would forbid it.
		List<CandidateFactory.QuotedItem> universe = new ArrayList<>();
		universe.add(quoted(1, "Members thing", true, 1_000, 1_100));
		universe.add(quoted(2, "One-sided", false, 100, null));
		universe.add(quoted(3, "No gap", false, 1_000, 1_000));
		universe.add(quoted(4, "Too dear", false, 900_000_000, 950_000_000));
		universe.add(quoted(5, "Thin", false, 1_000, 1_400));

		CandidateFactory factory = new CandidateFactory(noHistory());
		List<com.flippingfriend.core.PortfolioCandidate> plan = factory.build(universe, 1.0,
			new HashMap<>(), 5_000_000L, false, Instant.ofEpochSecond(T0));

		java.util.Set<Integer> recommended = new java.util.HashSet<>();
		for (com.flippingfriend.core.PortfolioCandidate candidate : plan)
		{
			recommended.add(candidate.getItemId());
		}
		for (CandidateFactory.QuotedItem item : universe)
		{
			int id = item.itemId();
			assertTrue("item " + id + " left the plan without saying why",
				recommended.contains(id) || factory.lastReasonFor(id) != null);
		}
		assertEquals("five went in", 5, factory.lastFunnel(0, 0).getItemsInFeed());
	}

	@Test
	public void theRecordDoesNotSurviveIntoTheNextPlan()
	{
		// A discard map that outlives a pass turns the funnel into a history of the process rather
		// than an account of this plan, and the counts climb for ever.
		CandidateFactory factory = new CandidateFactory(noHistory());
		List<CandidateFactory.QuotedItem> universe =
			Collections.singletonList(quoted(1, "Members thing", true, 1_000, 1_100));

		factory.build(universe, 1.0, new HashMap<>(), 5_000_000L, false, Instant.ofEpochSecond(T0));
		factory.build(universe, 1.0, new HashMap<>(), 5_000_000L, false, Instant.ofEpochSecond(T0));
		PlanDiagnostics funnel = factory.lastFunnel(0, 5_000_000L);

		assertEquals("one item, discarded once, however many plans have run", 1,
			totalDiscarded(funnel));
	}

	@Test
	public void anItemThatWasNeverAvailableIsNotPaperTraded()
	{
		// The distinction the two records exist for. A members item on a free account is not a trade
		// this system passed up; scoring it as a missed opportunity would fill the counterfactual
		// channel with trades that never existed and inflate the cost of every real refusal.
		CandidateFactory factory = new CandidateFactory(noHistory());
		ShadowTrader trader = new ShadowTrader(new com.flippingfriend.model.TaxCalculator());
		factory.setShadowTrader(trader);

		factory.build(Collections.singletonList(quoted(4151, "Abyssal whip", true, 1_000, 1_400)),
			1.0, new HashMap<>(), 100_000_000L, false, Instant.ofEpochSecond(T0));

		assertEquals("an unavailable item is accounted for but not counted as missed", 0,
			trader.openCount());
	}

	@Test
	public void anItemThatWasPassedUpStillIsPaperTraded()
	{
		// And the other half: a genuine judgement must still reach the shadow channel, or fixing the
		// accounting would have quietly switched the counterfactual off.
		CandidateFactory factory = new CandidateFactory(noHistory());
		ShadowTrader trader = new ShadowTrader(new com.flippingfriend.model.TaxCalculator());
		factory.setShadowTrader(trader);

		factory.build(Collections.singletonList(
				quoted(4151, "Abyssal whip", false, 1_000_000, 1_005_000)),
			1.0, new HashMap<>(), 100_000_000L, true, Instant.ofEpochSecond(T0));

		assertEquals("a spread too thin to clear tax is a decision, and decisions get scored", 1,
			trader.openCount());
	}

	@Test
	public void reasonsAreCountedNotJustListed()
	{
		List<CandidateFactory.QuotedItem> universe = new ArrayList<>();
		for (int i = 1; i <= 7; i++)
		{
			universe.add(quoted(i, "Members thing " + i, true, 1_000, 1_100));
		}

		PlanDiagnostics funnel = runPlan(universe, 100_000_000L, false);
		Map<String, Integer> counts = funnel.getVetoCounts();

		assertEquals(Integer.valueOf(7),
			counts.get("Members item, which this account cannot trade."));
		assertTrue("and only a handful are named, so the panel stays readable",
			funnel.getVetoExamples().get("Members item, which this account cannot trade.").size()
				<= 5);
	}
}
