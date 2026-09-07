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
		CaptureRates.Observation seen = rates.observe(
			offer(ITEM, true, 100, 5_000, 1_200, 12 * BUCKET), bars(12, 100, 1_000));

		// The RATE is still identified from the flow that passed: 1,200 of 12,000 is a tenth, and the
		// assertions below pin it. What is capped is the WEIGHT -- this offer asked for 5,000, so it
		// counts for 5,000 and not for the 12,000 it was never competing for.
		assertEquals("weighted by what was actually at stake", 5_000.0, seen.flow, 1.0);
		assertEquals("carrying the tenth it demonstrated", 500.0, seen.filled, 1.0);
		double learned = rates.rateFor(ITEM, APPETITE);
		assertTrue("the measurement must pull the rate well below the assumption: " + learned,
			learned < 0.2);
		assertTrue("but not below the truth it measured: " + learned, learned > 0.10);
	}

	@Test
	public void oneLongRunningOfferCannotOwnTheEstimate()
	{
		// The live failure. A buy left open for hours on a liquid rune accumulated 1,077,132 units of
		// flow against zero fills -- one observation holding 73% of all the weight in the system,
		// setting the rate that every other item was shrunk toward. The pooled rate sat at 9% against
		// a prior of 0.85 and every order in the plan was sized from it.
		//
		// We could never have taken more than we asked for, so flow past our order size is not
		// evidence about us: we were not competing for it. The zero is still counted, and still
		// pulls the rate down. It just cannot drown out everything else.
		CaptureRates dominated = new CaptureRates();
		dominated.observe(offer(ITEM, true, 100, 5_000, 0, 12 * BUCKET), bars(12, 100, 100_000));
		dominated.observe(offer(OTHER, true, 100, 5_000, 4_000, 12 * BUCKET), bars(12, 100, 1_000));

		// The good item must still read as good, rather than being dragged to the bad one's rate.
		assertTrue("a well-executing item must survive a neighbour's bad hour: "
				+ dominated.rateFor(OTHER, APPETITE),
			dominated.rateFor(OTHER, APPETITE) > APPETITE / 2);
	}

	@Test
	public void whatIsCountedIsWhatGetsStored()
	{
		// The caller persists what observe() returns, and it has to be the same numbers that went into
		// the running totals. It used to persist the RAW fill quantity beside this class's capped
		// flow, so the durable record disagreed with memory and a restart read the disagreement back
		// as fact: rows with 20,850 filled against 5,221 of flow, a ratio of four, which cannot happen.
		CaptureRates rates = new CaptureRates();

		CaptureRates.Observation seen = rates.observe(
			offer(ITEM, true, 100, 90_000, 90_000, 12 * BUCKET), bars(12, 100, 1_000));

		assertTrue("a stored row can never claim more filled than flowed: "
			+ seen.filled + " of " + seen.flow, seen.filled <= seen.flow);
	}

	@Test
	public void anOrderThatFilledCompletelyIsALowerBoundAndNotAMeasurement()
	{
		// Same flow, same fills, and they do NOT carry the same evidence.
		//
		// This test used to assert that they did, on the reasoning that the order size capped the
		// numerator and ended the interval in the same act. That is true and it is the problem: an
		// order that filled completely never found out what it could not have had. Scoring it as
		// filled/flow reads "we won a tenth of what passed" off an order that was only ever asking
		// for a tenth -- and capture scales the next order, so the next one asks for less, and
		// measures lower.
		//
		// It is not a hypothetical. Six offers on the live account recorded a pooled capture of 0.074
		// against a prior of 0.85; every order in the plan was cut elevenfold and the plan fell from
		// 437,506 gp per slot-hour to 60,889.
		CaptureRates completed = new CaptureRates();
		completed.observe(offer(ITEM, true, 100, 1_200, 1_200, 12 * BUCKET), bars(12, 100, 1_000));

		CaptureRates partial = new CaptureRates();
		partial.observe(offer(ITEM, true, 100, 9_999, 1_200, 12 * BUCKET), bars(12, 100, 1_000));

		assertTrue("taking everything asked for cannot read as worse than being outbid: "
				+ completed.rateFor(ITEM, APPETITE) + " vs " + partial.rateFor(ITEM, APPETITE),
			completed.rateFor(ITEM, APPETITE) > partial.rateFor(ITEM, APPETITE));
		assertTrue("and it should read at or above the assumption, being a bound and not a ceiling: "
			+ completed.rateFor(ITEM, APPETITE), completed.rateFor(ITEM, APPETITE) > APPETITE);
	}

	@Test
	public void beingOutbidIsStillMeasuredAgainstTheWholeFlow()
	{
		// The other half, unchanged and load-bearing: when the market limited us rather than our own
		// order size, the flow is exactly the right denominator and the rate must fall.
		CaptureRates rates = new CaptureRates();
		rates.observe(offer(ITEM, true, 100, 9_999, 1_200, 12 * BUCKET), bars(12, 100, 1_000));

		assertTrue("outbid for nine tenths of it: " + rates.rateFor(ITEM, APPETITE),
			rates.rateFor(ITEM, APPETITE) < APPETITE);
	}

	@Test
	public void aFastCompleteFillIsEvidenceRatherThanNoise()
	{
		// An offer open for one minute is normally refused, because its flow estimate leans on a
		// prorated bar and the polling slop is a large share of the interval. That is a reason to
		// distrust the DENOMINATOR -- and an order that filled completely does not need one.
		//
		// Refusing them was not conservative. A fast complete fill is the strongest capture evidence
		// there is, and dropping it while keeping the offers that sat for an hour and won nothing is
		// a filter that admits only failures. On the live account it admitted six, all of them zeroes,
		// while seventeen buys and eighty-three sales had completed.
		CaptureRates rates = new CaptureRates();

		CaptureRates.Observation measured =
			rates.observe(offer(ITEM, true, 100, 400, 400, 60), bars(4, 100, 1_000));

		assertTrue("a complete fill counts however quick it was", measured.isSomething());
		assertEquals(1, rates.observationCount());
		assertTrue("and it must not drag the rate down: " + rates.rateFor(ITEM, APPETITE),
			rates.rateFor(ITEM, APPETITE) > APPETITE);
	}

	@Test
	public void executingWellDoesNotShrinkTheNextOrder()
	{
		// The spiral, end to end. An account that keeps filling its orders completely must not end up
		// with a lower capture rate than the appetite it started from, because that rate is what sets
		// the size of the next order.
		CaptureRates rates = new CaptureRates();
		for (int i = 0; i < 6; i++)
		{
			rates.observe(offer(ITEM, true, 100, 1_000, 1_000, 12 * BUCKET), bars(12, 100, 1_000));
		}

		assertTrue("six orders, every one filled in full: " + rates.rateFor(ITEM, APPETITE),
			rates.rateFor(ITEM, APPETITE) >= APPETITE);
	}

	@Test
	public void anOfferThatWonNothingIsTheMostInformativeOneThereIs()
	{
		// An hour of flow passing and none of it ours says more about the queue than any fill does.
		// Dropping the zeroes would bias the estimate up by exactly the quantity being measured.
		CaptureRates rates = new CaptureRates();
		CaptureRates.Observation seen = rates.observe(offer(ITEM, true, 100, 5_000, 0, 12 * BUCKET),
			bars(12, 100, 1_000));

		assertTrue("a zero-fill offer must be counted", seen.isSomething());
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
		assertTrue("and an offer measured against nothing teaches nothing",
			!rates.observe(offer(ITEM, true, 100, 5_000, 0, 12 * BUCKET), bars(12, 500, 1_000))
				.isSomething());
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

		assertTrue(!rates.observe(offer(ITEM, true, 100, 500, 100, 60), bars(4, 100, 1_000))
			.isSomething());
		assertEquals(0, rates.observationCount());
	}

	@Test
	public void fillsBeyondTheFlowAreCappedRatherThanThrownAway()
	{
		// We cannot have won more than everything, so this is missing history rather than a
		// spectacular queue position -- the companion was down for part of the interval, or the
		// archive has a gap.
		//
		// It used to be discarded for that reason, and discarding it is worse than the wrong
		// denominator it was avoiding: the offers this rule catches are the ones that filled hugely,
		// so refusing them removes the best observations and leaves the failures. Two of the eight
		// observations on the live account went this way, and the six that remained were all zeroes.
		//
		// Counted at the ceiling instead, and still reported, because a systematically short archive
		// is worth seeing on the health line.
		CaptureRates rates = new CaptureRates();

		CaptureRates.Observation seen = rates.observe(
			offer(ITEM, true, 100, 90_000, 90_000, 12 * BUCKET), bars(12, 100, 1_000));

		assertTrue("the observation is kept", seen.isSomething());
		assertEquals(1, rates.observationCount());
		assertEquals("and the health line can still see it happened", 1, rates.overflowCount());
		assertTrue("filling everything available cannot lower the rate: "
			+ rates.rateFor(ITEM, APPETITE), rates.rateFor(ITEM, APPETITE) > APPETITE);
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
		assertTrue("a thick one must not: " + afterThick, afterThick < APPETITE * 0.6);
		assertTrue("and it must move the rate much further than the trickle did: "
			+ afterThick + " vs " + afterThin, afterThick < afterThin / 1.5);
		// Both offers asked for 500 units, so neither can count for more than 500 however much flowed
		// past. That ceiling is deliberate -- an offer is only evidence about the race it entered --
		// and it is why the thick case no longer collapses the rate to almost nothing on one reading.
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
