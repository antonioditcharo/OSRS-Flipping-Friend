package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * How much of the flow at our price we actually win, learned from our own offers.
 *
 * <p>The number being learned here sizes every order in the system. If the assumed capture is twice
 * the real one, every order is twice as large as the market will fill and every slot is held twice
 * as long as the plan said — so the properties that matter are not only "does it learn" but "does it
 * stay put until it has grounds to move", and both are pinned below.
 *
 * <p>The subtle one is {@link #aCompletedOfferMeasuresCaptureJustAsWellAsAPartialOne()}. The first
 * instinct is that a completed offer cannot measure capture, because its fill count was capped by
 * the order size rather than by the queue. That reasoning is wrong and acting on it would have
 * thrown away most of the sample: completing also ends the interval, so it caps the flow by the same
 * act, and the ratio survives.
 */
public class CaptureRatesTest
{
	private static final int ITEM = 561;
	private static final int OTHER = 4151;
	private static final long BUCKET = 300;
	private static final long START = 1_700_000_000L / BUCKET * BUCKET;
	/** What "Balanced" asks for, and therefore what the learner must return knowing nothing. */
	private static final double APPETITE = 0.5;

	/** Bars where every bucket traded at {@code price} on both sides, with {@code volume} each. */
	private static List<Candle> bars(int count, int price, int volume)
	{
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			series.add(new Candle(START + i * BUCKET, price, price, volume, volume));
		}
		return series;
	}

	private static OfferEvent offer(int itemId, boolean buying, int price, int total, int filled,
		long openSeconds)
	{
		return OfferEvent.builder("c", START + openSeconds, buying ? "BOUGHT" : "SOLD")
			.item(itemId, "test")
			.buying(buying)
			.price(price)
			.quantities(total, filled)
			.firstSeenAt(START)
			.build();
	}

	@Test
	public void knowingNothingItReturnsExactlyWhatTheAppetiteAskedFor()
	{
		CaptureRates rates = new CaptureRates();

		assertEquals("no evidence must mean no correction, to the last decimal",
			APPETITE, rates.rateFor(ITEM, APPETITE), 1e-12);
		assertEquals(0.35, rates.rateFor(ITEM, 0.35), 1e-12);
		assertEquals(0.85, rates.rateFor(ITEM, 0.85), 1e-12);
	}

	@Test
	public void itLearnsTheRatioOfWhatWeWonToWhatPassedUs()
	{
		CaptureRates rates = new CaptureRates();
		// Twelve five-minute bars, 1,000 units each side: 12,000 units of flow past a buy at 100.
		// We took 1,200 of them, so the truth is 0.10 against an appetite that assumed 0.50.
		double flow = rates.observe(offer(ITEM, true, 100, 5_000, 1_200, 12 * BUCKET),
			bars(12, 100, 1_000));

		assertEquals("the denominator is the flow that passed at or beyond our price",
			12_000.0, flow, 1.0);
		double learned = rates.rateFor(ITEM, APPETITE);
		assertTrue("the measurement must pull the rate well below the assumption: " + learned,
			learned < 0.2);
		assertTrue("but not below the truth it measured: " + learned, learned > 0.10);
	}

	@Test
	public void aCompletedOfferMeasuresCaptureJustAsWellAsAPartialOne()
	{
		// Same flow, same fills. One offer wanted exactly what it got and one wanted far more, and
		// the evidence they carry about the queue is identical: the order size capped the numerator
		// in the first case and ended the interval in the same act.
		CaptureRates completed = new CaptureRates();
		completed.observe(offer(ITEM, true, 100, 1_200, 1_200, 12 * BUCKET), bars(12, 100, 1_000));

		CaptureRates partial = new CaptureRates();
		partial.observe(offer(ITEM, true, 100, 9_999, 1_200, 12 * BUCKET), bars(12, 100, 1_000));

		assertEquals("completing is not a reason to discard the measurement",
			partial.rateFor(ITEM, APPETITE), completed.rateFor(ITEM, APPETITE), 1e-12);
	}

	@Test
	public void anOfferThatWonNothingIsTheMostInformativeOneThereIs()
	{
		// An hour of flow passing and none of it ours says more about the queue than any fill does.
		// Dropping the zeroes would bias the estimate up by exactly the quantity being measured.
		CaptureRates rates = new CaptureRates();
		double flow = rates.observe(offer(ITEM, true, 100, 5_000, 0, 12 * BUCKET),
			bars(12, 100, 1_000));

		assertTrue("a zero-fill offer must be counted", flow > 0);
		assertEquals(1, rates.observationCount());
		assertTrue("and must pull the rate down: " + rates.rateFor(ITEM, APPETITE),
			rates.rateFor(ITEM, APPETITE) < APPETITE);
	}

	@Test
	public void onlyFlowThatReachedOurPriceCounts()
	{
		// A buy at 100 is not touched by a market whose sells never came below 120. The bars are
		// there and busy, and none of that volume was ever available to us.
		CaptureRates rates = new CaptureRates();

		double reached = CaptureRates.flowAtOrBeyond(bars(12, 100, 1_000), START, START + 12 * BUCKET,
			true, 100);
		double missed = CaptureRates.flowAtOrBeyond(bars(12, 120, 1_000), START, START + 12 * BUCKET,
			true, 100);

		assertTrue("flow at our price counts: " + reached, reached > 10_000);
		assertTrue("flow that never came to us does not: " + missed, missed < reached / 4);
		assertEquals("and an offer measured against nothing teaches nothing", 0,
			rates.observe(offer(ITEM, true, 100, 5_000, 0, 12 * BUCKET), bars(12, 500, 1_000)), 1e-9);
	}

	@Test
	public void aSellIsMeasuredAgainstTheOtherSideOfTheBook()
	{
		// A sell at 100 completes when somebody buys from us, so it is the high side that matters and
		// the relevant bars are the ones that reached up to our price rather than down to it.
		double reachable = CaptureRates.flowAtOrBeyond(bars(12, 140, 1_000), START,
			START + 12 * BUCKET, false, 100);
		double unreachable = CaptureRates.flowAtOrBeyond(bars(12, 60, 1_000), START,
			START + 12 * BUCKET, false, 100);

		assertTrue("buyers paying above our ask are ours to sell into: " + reachable,
			reachable > 10_000);
		assertTrue("a market trading below our ask never reaches us: " + unreachable,
			unreachable < reachable / 4);
	}

	@Test
	public void barsAtTheEdgesAreProratedRatherThanCountedWhole()
	{
		// An offer open for one bucket in the middle of two must not be charged both of them. Counting
		// whole bars at each end would inflate the denominator by up to two buckets, which on a short
		// offer is most of it - and every inflated denominator understates capture.
		List<Candle> series = bars(4, 100, 1_000);
		long open = START + BUCKET / 2;
		long closed = open + BUCKET;

		double flow = CaptureRates.flowAtOrBeyond(series, open, closed, true, 100);

		assertEquals("half of one bar plus half of the next", 1_000.0, flow, 60.0);
	}

	@Test
	public void anOfferTooShortToMeasureIsLeftAlone()
	{
		// Under one bar the polling slop is a large share of the interval and the whole denominator
		// rests on a single prorated bucket.
		CaptureRates rates = new CaptureRates();

		assertEquals(0, rates.observe(offer(ITEM, true, 100, 500, 100, 60), bars(4, 100, 1_000)), 1e-9);
		assertEquals(0, rates.observationCount());
	}

	@Test
	public void fillsBeyondTheFlowAreRefusedAndCounted()
	{
		// We cannot have won more than everything, so this is missing history rather than a spectacular
		// queue position - the companion was down for part of the interval, or the archive has a gap.
		// Recording it would put a wrong denominator into a permanent total.
		CaptureRates rates = new CaptureRates();

		double flow = rates.observe(offer(ITEM, true, 100, 90_000, 90_000, 12 * BUCKET),
			bars(12, 100, 1_000));

		assertEquals("nothing recorded", 0, flow, 1e-9);
		assertEquals(0, rates.observationCount());
		assertEquals("but the health line can see it happened", 1, rates.overflowCount());
		assertEquals("and the rate is untouched", APPETITE, rates.rateFor(ITEM, APPETITE), 1e-12);
	}

	@Test
	public void oneThinObservationBarelyMovesTheRate()
	{
		// Fifty units of flow is not an item's behaviour. The prior is expressed in units rather than
		// in offers precisely so that a measurement against a trickle counts as a trickle.
		CaptureRates thin = new CaptureRates();
		thin.observe(offer(ITEM, true, 100, 500, 0, 12 * BUCKET), bars(12, 100, 5));

		CaptureRates thick = new CaptureRates();
		thick.observe(offer(ITEM, true, 100, 500, 0, 12 * BUCKET), bars(12, 100, 2_000));

		double afterThin = thin.rateFor(ITEM, APPETITE);
		double afterThick = thick.rateFor(ITEM, APPETITE);

		assertTrue("a thin observation must leave the assumption nearly intact: " + afterThin,
			afterThin > APPETITE * 0.85);
		assertTrue("a thick one must not: " + afterThick, afterThick < APPETITE * 0.2);
	}

	@Test
	public void anItemIsShrunkTowardTheOthersRatherThanTowardItself()
	{
		// Leave-one-out, for the reason LearnedDurations already gives: an item carrying most of the
		// sample would otherwise be shrunk toward a pooled figure it wrote, which is no shrinkage at
		// all and lets one heavily-traded item pull its own target.
		CaptureRates rates = new CaptureRates();
		// A dominant item that wins almost nothing.
		for (int i = 0; i < 10; i++)
		{
			rates.observe(offer(ITEM, true, 100, 50_000, 100, 12 * BUCKET), bars(12, 100, 4_000));
		}
		// One quiet item that wins a lot, on far less flow.
		rates.observe(offer(OTHER, true, 100, 5_000, 900, 12 * BUCKET), bars(12, 100, 100));

		double dominant = rates.rateFor(ITEM, APPETITE);
		double quiet = rates.rateFor(OTHER, APPETITE);

		assertTrue("the dominant item's own evidence should win out: " + dominant, dominant < 0.06);
		assertTrue("the quiet item must not be dragged all the way to the dominant one's rate: "
			+ quiet, quiet > dominant);
		assertTrue("nor left at its own thin measurement: " + quiet, quiet < 0.75);
	}

	@Test
	public void anUnseenItemFallsBackToWhatEverythingElseShowed()
	{
		CaptureRates rates = new CaptureRates();
		for (int i = 0; i < 10; i++)
		{
			rates.observe(offer(ITEM, true, 100, 50_000, 500, 12 * BUCKET), bars(12, 100, 4_000));
		}

		double unseen = rates.rateFor(999_999, APPETITE);

		assertTrue("an item we have never traded inherits the pooled experience: " + unseen,
			unseen < APPETITE / 2);
		assertEquals(rates.pooledRate(APPETITE), unseen, 1e-12);
	}

	@Test
	public void evidenceSurvivesARestart()
	{
		CaptureRates first = new CaptureRates();
		for (int i = 0; i < 5; i++)
		{
			first.observe(offer(ITEM, true, 100, 50_000, 400, 12 * BUCKET), bars(12, 100, 4_000));
		}
		double before = first.rateFor(ITEM, APPETITE);

		Map<Integer, CaptureRates.Totals> saved = new HashMap<>(first.totals());
		CaptureRates second = new CaptureRates();
		second.restore(saved);

		assertEquals("months of measurement must not restart with the process", before,
			second.rateFor(ITEM, APPETITE), 1e-12);
		assertEquals(5, second.totals().get(ITEM).observations);
	}

	@Test
	public void aRestoreReplacesRatherThanAccumulates()
	{
		// Restoring on top of live evidence would double-count everything the process had already
		// seen — the same hazard RehydrationTest pins for execution statistics.
		CaptureRates rates = new CaptureRates();
		rates.observe(offer(ITEM, true, 100, 50_000, 400, 12 * BUCKET), bars(12, 100, 4_000));
		Map<Integer, CaptureRates.Totals> saved = new HashMap<>(rates.totals());

		rates.restore(saved);
		rates.restore(saved);

		assertEquals("one observation, however many times it is restored", 1,
			rates.observationCount());
	}

	@Test
	public void theRateStaysInsideTheRangeAShareCanTake()
	{
		CaptureRates rates = new CaptureRates();
		for (int i = 0; i < 200; i++)
		{
			rates.observe(offer(ITEM, true, 100, 50_000, 0, 12 * BUCKET), bars(12, 100, 4_000));
		}

		double floored = rates.rateFor(ITEM, APPETITE);
		assertTrue("never zero, or one bad week makes an item permanently untradeable: " + floored,
			floored > 0);
		assertTrue("and never above the whole flow", rates.rateFor(ITEM, 1.0) <= 1.0);
	}

	@Test
	public void theSummaryReportsWhatItActuallyMeasured()
	{
		CaptureRates rates = new CaptureRates();
		assertTrue(rates.summary(APPETITE).contains("no fills measured yet"));

		rates.observe(offer(ITEM, true, 100, 50_000, 1_200, 12 * BUCKET), bars(12, 100, 1_000));
		String summary = rates.summary(APPETITE);

		assertTrue(summary, summary.contains("1 offers"));
		assertTrue(summary, summary.contains("1 items"));
		assertNotEquals("", summary);
	}
}
