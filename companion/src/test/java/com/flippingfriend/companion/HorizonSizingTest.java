package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The planner end to end, over a universe of one, at two different patience budgets.
 * <p>
 * This is the path that actually chooses what gets recommended, and the leg horizon is what sizes
 * the order on it: a longer window means more of the item's flow can cross at our price, so a
 * bigger order is still expected to complete. That is the whole mechanism behind the "how long a
 * flip should take" setting — which, until it was put on the wire, never reached here at all.
 * <p>
 * Running the real factory rather than its parts also keeps the screen, the vetoes and the fill
 * estimates honest: a change that quietly stops producing candidates fails here rather than in
 * front of a player.
 */
public class HorizonSizingTest
{
	private static final int ITEM = 2361;
	private static final int BID = 2_000;
	private static final int ASK = 2_120;
	private static final int BUCKET_SECONDS = 300;
	private static final String SHORT_STEP = "5m";
	private static final String LONG_STEP = "1h";
	private static final long NOW = 1_700_000_000L + 300L * 300L;
	/** Units crossing each side of the book every five minutes on an ordinary staple. */
	private static final int BUSY = 4_000;
	/** The same item on a quiet day: still liquid enough to pass the volume gate, but much thinner. */
	private static final int QUIET = 1_200;
	private static final int BUY_LIMIT = 30_000;

	/**
	 * A busy, ordinary book: four thousand units crossing every five minutes at a price that drifts
	 * a little.
	 * <p>
	 * The drift is not decoration. A perfectly flat series has a median absolute deviation of zero,
	 * so every quote is infinitely many deviations from the median and the manipulation filter --
	 * correctly -- refuses to believe any of them. An item has to look like an item.
	 */
	private static List<Candle> bars(int count, int stepSeconds, int volumePerSide)
	{
		List<Candle> series = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(40 * Math.sin(i * 0.37));
			series.add(new Candle(NOW - (long) (count - i) * stepSeconds,
				ASK + drift, BID + drift, volumePerSide, volumePerSide));
		}
		return series;
	}

	private static CandidateFactory factory()
	{
		return factory(BUSY);
	}

	private static CandidateFactory factory(int volumePerSide)
	{
		CandidateFactory factory = new CandidateFactory(
			(itemId, timestep) -> SHORT_STEP.equals(timestep)
				? bars(300, BUCKET_SECONDS, volumePerSide) : bars(400, 3_600, volumePerSide * 12),
			new com.flippingfriend.model.TaxCalculator(), SHORT_STEP, LONG_STEP, BUCKET_SECONDS);
		factory.setRiskAppetite(com.flippingfriend.model.RiskAppetite.BALANCED);
		return factory;
	}

	private static List<PortfolioCandidate> plan(CandidateFactory factory, double horizonHours)
	{
		return plan(factory, horizonHours, BUSY);
	}

	private static List<PortfolioCandidate> plan(CandidateFactory factory, double horizonHours,
		int volumePerSide)
	{
		MarketIngestionService.Item item =
			new MarketIngestionService.Item(ITEM, "Adamant bar", BUY_LIMIT);
		// Both sides quoted a minute ago: a book with one stale side is rejected as untrustworthy,
		// and rightly so, but that is not what is being measured here.
		CandidateFactory.QuotedItem quoted = new CandidateFactory.QuotedItem(item,
			new LatestPrice(ASK, NOW - 60, BID, NOW - 60),
			new Candle(NOW, ASK, BID, volumePerSide, volumePerSide),
			new Candle(NOW, ASK, BID, volumePerSide * 12, volumePerSide * 12));

		Map<Integer, Integer> limits = new HashMap<>();
		limits.put(ITEM, BUY_LIMIT);
		return factory.build(Collections.singletonList(quoted), horizonHours, limits,
			1_000_000_000L, true, Instant.ofEpochSecond(NOW));
	}

	private static PortfolioCandidate best(List<PortfolioCandidate> candidates)
	{
		return candidates.stream()
			.max(Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour))
			.orElse(null);
	}

	@Test
	public void aLiquidItemWithARealSpreadProducesATrade()
	{
		CandidateFactory factory = factory();
		List<PortfolioCandidate> candidates = plan(factory, 2.5);

		assertFalse("the planner has to be able to find a trade in a six-percent spread on a busy "
			+ "item, or nothing downstream of it means anything: "
			+ factory.lastFunnel(0, 0).getVetoCounts(), candidates.isEmpty());
		PortfolioCandidate top = best(candidates);
		assertTrue("and it has to be worth doing after tax: " + top.getNetProfit(),
			top.getNetProfit() > 0);
		assertTrue("with both legs expected inside the horizon",
			top.getBuyHours() + top.getSellHours() <= 5.0);
	}

	@Test
	public void alongerPatienceBudgetBuysABiggerOrder()
	{
		// The mechanism the "how long a flip should take" setting works through. Order size scales
		// with the time the order has to cross, so asking for slow flips has to produce chunkier
		// trades than asking for fast ones -- on identical market data, with only the budget changed.
		PortfolioCandidate patient = best(plan(factory(), 4.0));
		PortfolioCandidate hurried = best(plan(factory(), 0.25));

		assertTrue("a four-hour leg has to order more than a fifteen-minute one: "
				+ patient.getQuantity() + " against " + hurried.getQuantity(),
			patient.getQuantity() > hurried.getQuantity());
		assertTrue("and both still have to be real orders", hurried.getQuantity() > 0);
		assertTrue("the patient one is worth more per flip, which is the point of asking for it",
			patient.getNetProfit() > hurried.getNetProfit());
	}

	@Test
	public void theOrderSizeRespondsToHowBusyTheItemIs()
	{
		// The consequence of the ONNX gate being shut. The placeholder classifier reported roughly a
		// coin flip per leg whatever it was shown, so the Kelly fraction sat on its 0.1 floor for
		// every item on the board and the size stopped being a judgement about the market at all.
		// With the measured fill model back in charge, the same buy limit against a busy book and a
		// quiet one has to produce different orders.
		CandidateFactory busy = factory(BUSY);

		PortfolioCandidate onABusyBook = best(plan(busy, 2.5, BUSY));
		PortfolioCandidate onAQuietBook = best(plan(factory(QUIET), 2.5, QUIET));

		assertTrue("a thin book still produces a trade, just a smaller one", onAQuietBook != null);
		assertTrue("a thin book must not be ordered in the same size as a staple: "
				+ onABusyBook.getQuantity() + " against " + onAQuietBook.getQuantity(),
			onABusyBook.getQuantity() > onAQuietBook.getQuantity());
		assertTrue("and the busy book has to be given a materially larger order, not a token one",
			onABusyBook.getQuantity() > 1.5 * onAQuietBook.getQuantity());
	}
}
