package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * How much to order is a question the objective should answer, not a constant.
 * <p>
 * Every price pair used to produce exactly one candidate, at one quantity, set by a fixed fraction
 * of what the market could absorb. So the search ranged over prices and never over size — which is
 * the one dimension it most needs to range over, because the thing it maximises is expected profit
 * per hour of slot occupancy and size is precisely the knob trading those two against each other.
 * Profit grows with the order; the time it ties a slot up grows more slowly, because much of the
 * wait is waiting for a first counterparty at all and that does not depend on size.
 * <p>
 * Two haircuts produced the orders that were actually being placed: the capture share, already
 * inside the fill model's throughput, and a fractional-Kelly stake on top of it. The second is also
 * the wrong shape — a Kelly fraction says what share of a <em>bankroll</em> to stake, and it was
 * applied to a quantity the market can absorb, which the buy limit, the coins on hand and the
 * optimizer's own exposure ceilings already bound. Measured on a live board, every order came out
 * between 0.3% and 4.3% of the buy limit, and the plan was seven trades of one unit each on a 128m
 * account.
 * <p>
 * The fraction is still there, as the middle of the grid. The point is not to overrule it but to
 * stop it being the only offer.
 */
public class SizeIsChosenTest
{
	private static final int ITEM = 2361;
	private static final int BID = 2_000;
	private static final int ASK = 2_120;
	private static final int BUY_LIMIT = 30_000;
	private static final long NOW = 1_700_000_000L + 300L * 300L;
	private static final double HORIZON = 2.5;

	/**
	 * A realistic book rather than a bottomless one: a few hundred units cross each side every five
	 * minutes, so what the market will absorb is what bounds the order -- which is the case on every
	 * real item. Given an unbounded book the buy limit binds instead, every entry in the size grid
	 * clamps to the same ceiling, and the question this file is about never arises.
	 */
	private static List<Candle> bars(int count, int step, int volumePerSide)
	{
		List<Candle> series = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(40 * Math.sin(i * 0.37));
			series.add(new Candle(NOW - (long) (count - i) * step,
				ASK + drift, BID + drift, volumePerSide, volumePerSide));
		}
		return series;
	}

	private static List<PortfolioCandidate> plan()
	{
		CandidateFactory factory = new CandidateFactory(
			(id, step) -> "5m".equals(step) ? bars(300, 300, 1_200) : bars(400, 3_600, 14_400),
			new TaxCalculator(), "5m", "1h", 300);
		factory.setRiskAppetite(RiskAppetite.BALANCED);

		MarketIngestionService.Item item =
			new MarketIngestionService.Item(ITEM, "Adamant bar", BUY_LIMIT);
		CandidateFactory.QuotedItem quoted = new CandidateFactory.QuotedItem(item,
			new LatestPrice(ASK, NOW - 60, BID, NOW - 60),
			new Candle(NOW, ASK, BID, 1_200, 1_200),
			new Candle(NOW, ASK, BID, 14_400, 14_400));
		Map<Integer, Integer> limits = new HashMap<>();
		limits.put(ITEM, BUY_LIMIT);

		return factory.build(Collections.singletonList(quoted), HORIZON, limits, 1_000_000_000L,
			true, Instant.ofEpochSecond(NOW));
	}

	@Test
	public void thePlannerOffersMoreThanOneSizeToChooseFrom()
	{
		List<PortfolioCandidate> tactics = plan();
		assertFalse("a busy item at a six percent spread has to produce trades", tactics.isEmpty());

		Set<Integer> sizes = new HashSet<>();
		for (PortfolioCandidate candidate : tactics)
		{
			sizes.add(candidate.getQuantity());
		}

		assertTrue("one quantity per price pair is the search not looking at size at all: "
			+ sizes, sizes.size() > 1);
	}

	@Test
	public void aBusyItemIsOrderedInSizeRatherThanInSlivers()
	{
		// The complaint, in one number. The fractional-Kelly point alone takes roughly a third of
		// what the market will absorb; asked properly, the objective reaches for a good deal more.
		List<PortfolioCandidate> tactics = plan();

		int largest = 0;
		for (PortfolioCandidate candidate : tactics)
		{
			largest = Math.max(largest, candidate.getQuantity());
		}

		assertTrue("the best available order was " + largest + " units where the fractional-Kelly "
			+ "point alone would have offered roughly a third of that, which is the shape of the "
			+ "original complaint", largest > 400);
	}

	@Test
	public void theSizeThatWinsIsTheOneWorthTheSlotTime()
	{
		// Not "bigger is better" -- bigger is offered, and the objective picks. What this pins is
		// that the winner is chosen on gp per slot-hour like everything else, so a size whose extra
		// units cost more in fill time than they earn in margin loses to a smaller one.
		List<PortfolioCandidate> tactics = plan();

		PortfolioCandidate best = tactics.get(0);
		for (PortfolioCandidate candidate : tactics)
		{
			assertTrue("the list has to come back ranked by the objective, best first",
				candidate.expectedGpPerSlotHour() <= best.expectedGpPerSlotHour() + 1e-6);
		}
		assertTrue("and the winner has to be worth doing", best.expectedGpPerSlotHour() > 0);
	}

	@Test
	public void theSameOrderIsNotOfferedTwice()
	{
		// Once the grid is against a ceiling every larger share clamps to the same quantity. Those
		// are identical candidates, which is branching for the optimizer to chew through and no
		// added choice.
		List<PortfolioCandidate> tactics = plan();

		Set<String> seen = new HashSet<>();
		for (PortfolioCandidate candidate : tactics)
		{
			String shape = candidate.getBuyPrice() + "@" + candidate.getSellPrice()
				+ "x" + candidate.getQuantity();
			assertTrue("duplicate candidate " + shape, seen.add(shape));
		}
	}

	@Test
	public void everySizeOfferedStaysInsideItsCeilings()
	{
		// Worth pinning now rather than before, because the orders are several times larger than they
		// used to be and the ceilings were never the thing under pressure. An order over the buy
		// limit cannot be placed at all, and one over the coins on hand is a trade the player cannot
		// take -- both would reach them as advice that simply does not work.
		List<PortfolioCandidate> tactics = plan();
		assertFalse(tactics.isEmpty());

		for (PortfolioCandidate candidate : tactics)
		{
			assertTrue("ordered " + candidate.getQuantity() + " against a buy limit of " + BUY_LIMIT,
				candidate.getQuantity() <= BUY_LIMIT);
			assertTrue("an order has to cost something", candidate.getCapitalRequired() > 0);
			assertTrue("ordered " + candidate.getCapitalRequired() + " gp of a 1,000,000,000 gp "
				+ "bankroll", candidate.getCapitalRequired() <= 1_000_000_000L);
		}
	}
}
