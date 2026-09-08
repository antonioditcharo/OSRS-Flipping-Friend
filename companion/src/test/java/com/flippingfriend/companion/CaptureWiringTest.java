package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

/**
 * End-to-end check that a measured capture rate reaches order sizing.
 *
 * <p>{@link CaptureRatesTest} covers whether the number is right. This covers whether it arrives,
 * which is a separate failure and the one this repository keeps having — audit item 11 is a list of
 * setters that exist, compile, and are never called. A measurement the sizing never consults is that
 * bug wearing a new name, and capture is the worst possible place for it: every order in the system
 * is proportional to this figure, so a learner nothing reads leaves every order sized by a dropdown.
 */
public class CaptureWiringTest
{
	private static final int ITEM = 561;
	private static final int OTHER = 4151;
	private static final long T0 = 1_700_000_000L / 300 * 300;

	/**
	 * A mid around 100,000 with a 5% spread, on an item that moves 3%.
	 *
	 * <p>Not arbitrary: the quote has to survive screening before sizing is reached, and two vetoes
	 * pull against each other. The spread must clear the 2% tax with something left over, while a
	 * quote far from the item's median in units of its own median absolute deviation is read as
	 * manipulation — so an item that barely moves cannot carry a spread wide enough to be worth
	 * trading. A placid item with a fat spread is precisely what a manipulated quote looks like.
	 */
	private static final int MID = 100_000;
	private static final int WOBBLE = 3_000;
	private static final int BUY = MID - MID * 5 / 200;
	private static final int SELL = MID + MID * 5 / 200;

	private static SeriesSource history()
	{
		return (itemId, timestep) ->
		{
			List<Candle> series = new ArrayList<>();
			long step = "5m".equals(timestep) ? 300 : 3600;
			for (int i = 0; i < 288; i++)
			{
				int drift = ((i % 3) - 1) * WOBBLE;
				series.add(new Candle(T0 - (288 - i) * step, MID + drift + 1, MID + drift - 1,
					5_000, 5_000));
			}
			return series;
		};
	}

	private static CandidateFactory.QuotedItem quoted()
	{
		MarketIngestionService.Item item =
			new MarketIngestionService.Item(ITEM, "Nature rune", 25_000, true, true);
		Candle bar = new Candle(T0, SELL, BUY, 20_000, 20_000);
		return new CandidateFactory.QuotedItem(item, new LatestPrice(SELL, T0, BUY, T0), bar, bar);
	}

	/** One planning pass; the quantity the factory proposes for the item, or 0 if it proposed none. */
	private static int quantityFrom(CandidateFactory factory)
	{
		List<PortfolioCandidate> candidates = factory.build(
			Collections.singletonList(quoted()), 4.0, new HashMap<>(), 2_000_000_000L,
			Instant.ofEpochSecond(T0));
		for (PortfolioCandidate candidate : candidates)
		{
			if (candidate.getItemId() == ITEM)
			{
				return candidate.getQuantity();
			}
		}
		return 0;
	}

	/** Rates that have watched this item lose almost every unit that passed it. */
	private static CaptureRates measuredLow()
	{
		CaptureRates rates = new CaptureRates();
		for (int i = 0; i < 20; i++)
		{
			List<Candle> bars = new ArrayList<>();
			for (int bar = 0; bar < 12; bar++)
			{
				bars.add(new Candle(T0 + bar * 300, SELL, BUY, 5_000, 5_000));
			}
			rates.observe(OfferEvent.builder("t", T0 + 3_600, "BOUGHT")
				.item(ITEM, "Nature rune")
				.buying(true)
				.price(BUY)
				.quantities(60_000, 1_500)
				.firstSeenAt(T0)
				.build(), bars);
		}
		return rates;
	}

	private static CandidateFactory wired(CaptureRates rates)
	{
		CandidateFactory factory = new CandidateFactory(history());
		factory.setCaptureRates(rates);
		return factory;
	}

	@Test
	public void anUnwiredFactorySizesExactlyAsItDidBefore()
	{
		// Null is a supported state — the backtester builds a factory without one — and it must mean
		// "use the appetite's number", not "use nothing".
		CandidateFactory factory = new CandidateFactory(history());

		int quantity = quantityFrom(factory);

		assertTrue("the premise: this item is tradeable at all, veto=" + factory.lastVetoFor(ITEM),
			quantity > 0);
		assertEquals("an unwired factory must match a wired one that has learned nothing",
			quantity, quantityFrom(wired(new CaptureRates())));
	}

	@Test
	public void aMeasuredCaptureRateShrinksTheOrder()
	{
		// The whole point. Winning a fortieth of the flow rather than the assumed half means the order
		// about to be placed is far larger than this market will fill, and the slot it occupies is
		// held far longer than the plan claimed.
		int assumed = quantityFrom(new CandidateFactory(history()));
		int measured = quantityFrom(wired(measuredLow()));

		assertTrue("the premise: both plans still want to trade this", assumed > 0 && measured > 0);
		assertTrue("a market we barely win must be ordered into more carefully: "
			+ measured + " vs " + assumed, measured < assumed);
	}

	@Test
	public void theHoldDecisionSeesItToo()
	{
		// The other production path through the per-item model, and a deterministic one: buyHoldValue
		// prices a resting offer against what the slot could earn instead, from the same throughput
		// figure. A capture rate that reached sizing but not holding would keep offers alive on a
		// throughput the entry decision had already stopped believing.
		double assumed = new CandidateFactory(history()).buyHoldValue(ITEM, BUY, 5_000, SELL, 2.0);
		double measured = wired(measuredLow()).buyHoldValue(ITEM, BUY, 5_000, SELL, 2.0);

		assertTrue("the premise: this offer is worth something on the assumed rate: " + assumed,
			assumed > 0);
		assertTrue("winning less of the flow makes the same resting offer worth less: "
			+ measured + " vs " + assumed, measured < assumed);
	}

	@Test
	public void theMeasurementReachesTheItemItWasMeasuredOn()
	{
		// Per item, not one global multiplier: queue competition is a property of an item, and an
		// unrelated item must not be re-sized to whatever this one showed.
		CaptureRates rates = measuredLow();
		double learned = rates.rateFor(ITEM, 0.5);
		double elsewhere = rates.rateFor(OTHER, 0.5);

		assertTrue("the item we measured moved: " + learned, learned < 0.1);
		assertTrue("an item with no evidence of its own inherits the pooled figure rather than the "
			+ "appetite's: " + elsewhere, elsewhere < 0.5);
		assertTrue("but is not pinned to the measured item's own rate: " + elsewhere,
			elsewhere >= learned);
	}

	@Test
	public void replacingTheRatesReplacesTheModelsBuiltFromThem()
	{
		// Models are cached by rounded rate. A cache surviving a new set of rates would keep sizing
		// from evidence that had been superseded — which is how a restored snapshot would quietly
		// fail to take effect on a running companion.
		CandidateFactory factory = new CandidateFactory(history());
		int before = quantityFrom(factory);

		factory.setCaptureRates(measuredLow());
		int after = quantityFrom(factory);

		assertTrue("the same factory must re-size once it is given evidence: " + after + " vs "
			+ before, after < before);
	}
}
