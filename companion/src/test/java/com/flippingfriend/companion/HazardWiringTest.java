package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The hazard has to reach the hold decision, and has to leave it alone when it has nothing to say.
 *
 * <p>{@link FillHazardTest} covers whether the curve is right. This covers the two things that decide
 * whether it should be switched on at all.
 *
 * <p><b>It arrives.</b> {@code holdValue} used the unconditional fill probability, which is the
 * memoryless assumption written out in one line: an offer forty minutes old was priced exactly like a
 * fresh one. Audit item 11 is a list of components that exist, compile and are never called, and a
 * hazard nothing consults would be that again.
 *
 * <p><b>It is inert when the assumption holds.</b> This is the part that makes it safe to ship before
 * anyone knows whether it helps. The correction is a ratio against a fresh offer, so a flat hazard
 * gives exactly one and the answer is untouched. A correction that fired on a memoryless process
 * would be taking money off perfectly good offers on the strength of nothing.
 */
public class HazardWiringTest
{
	private static final int ITEM = 561;
	private static final long T0 = 1_700_000_000L / 300 * 300;

	private static SeriesSource liquidHistory()
	{
		List<Candle> bars = new ArrayList<>();
		for (int i = 0; i < 200; i++)
		{
			int wobble = (i % 7) - 3;
			bars.add(new Candle(T0 - (200 - i) * 300L, 1_010 + wobble, 1_000 + wobble, 5_000, 5_000));
		}
		return (itemId, timestep) -> itemId == ITEM ? bars : Collections.<Candle>emptyList();
	}

	/** A queue: the easy fills go early and the rate falls away. */
	private static FillHazard declining()
	{
		FillHazard hazard = new FillHazard();
		double[] rates = {0.30, 0.15, 0.06, 0.02, 0.008, 0.003, 0.001, 0.0005};
		int remaining = 40_000;
		for (int slice = 0; slice < rates.length && remaining > 0; slice++)
		{
			double chance = 1.0 - Math.exp(-rates[slice] * FillHazard.widthOf(slice));
			int fills = (int) Math.round(remaining * chance);
			for (int i = 0; i < fills; i++)
			{
				hazard.observe(ITEM, true, FillHazard.BUCKET_MINUTES[slice] + 0.5, true);
			}
			remaining -= fills;
		}
		// Past the last slice, so what is left is censored rather than sitting in the table as a
		// slice full of denominators and no fills.
		for (int i = 0; i < remaining; i++)
		{
			hazard.observe(ITEM, true, 320, false);
		}
		return hazard;
	}

	/**
	 * A memoryless process: a constant rate per minute, through every slice.
	 *
	 * <p>Every slice, including the last. Stopping one short and censoring the remainder there leaves
	 * the final slice with denominators and no fills — a rate of zero, which is a cliff rather than a
	 * flat line, and a ninety-minute wait lands squarely in it. That is a fault in the fixture and not
	 * in the class, and it showed up as the wait penalty appearing to bite on a process that has no
	 * memory.
	 */
	private static FillHazard flat()
	{
		FillHazard hazard = new FillHazard();
		int remaining = 40_000;
		for (int slice = 0; slice < FillHazard.BUCKET_MINUTES.length && remaining > 0; slice++)
		{
			double chance = 1.0 - Math.exp(-0.05 * FillHazard.widthOf(slice));
			int fills = (int) Math.round(remaining * chance);
			for (int i = 0; i < fills; i++)
			{
				hazard.observe(ITEM, true, FillHazard.BUCKET_MINUTES[slice] + 0.5, true);
			}
			remaining -= fills;
		}
		for (int i = 0; i < remaining; i++)
		{
			hazard.observe(ITEM, true, 320, false);
		}
		return hazard;
	}

	private static CandidateFactory factory(FillHazard hazard)
	{
		CandidateFactory factory = new CandidateFactory(liquidHistory());
		factory.setFillHazard(hazard);
		return factory;
	}

	private static double hold(CandidateFactory factory, double waitedMinutes)
	{
		return factory.buyHoldValue(ITEM, 1_000, 100, 1_100, 2.0, waitedMinutes);
	}

	@Test
	public void anUnwiredFactoryPricesAHoldExactlyAsItDidBefore()
	{
		CandidateFactory unwired = new CandidateFactory(liquidHistory());

		double fresh = hold(unwired, 0);
		double waited = hold(unwired, 90);

		assertTrue("the premise: this offer is worth something", fresh > 0);
		assertEquals("no hazard means no correction, however long it has waited", fresh, waited, 1e-9);
	}

	@Test
	public void aFlatHazardLeavesTheHoldValueAlone()
	{
		// The safety property. A memoryless process is what FillModel already assumes, so measuring
		// one must change nothing -- and it must change nothing by construction rather than by a
		// threshold somebody tuned.
		CandidateFactory factory = factory(flat());

		double fresh = hold(factory, 0);
		double waited = hold(factory, 90);

		assertTrue(fresh > 0);
		assertEquals("a flat hazard is the Poisson case, and Poisson has no memory",
			fresh, waited, fresh * 0.05);
	}

	@Test
	public void aDecliningHazardMakesAWaitedOfferWorthLess()
	{
		// The whole point. An offer that has sat for ninety minutes without filling has been telling
		// us so the entire time, and the price feed has no way to hear it -- it records what traded,
		// never where we were in the queue.
		CandidateFactory factory = factory(declining());

		double fresh = hold(factory, 0);
		double waited = hold(factory, 90);

		assertTrue("a fresh offer is unaffected", fresh > 0);
		assertTrue("and a stale one is worth materially less: " + waited + " vs " + fresh,
			waited < fresh * 0.9);
	}

	@Test
	public void theCorrectionNeverRaisesAHoldValue()
	{
		// The hazard is here to say waiting has cost something, not to talk a stale offer up past
		// what the book says about it. FillModel reads the actual volume at our price; this knows
		// only about elapsed time.
		CandidateFactory factory = factory(declining());
		double fresh = hold(factory, 0);

		for (double waited : new double[]{1, 5, 15, 45, 120, 400})
		{
			assertTrue("waiting " + waited + "m must not increase the value",
				hold(factory, waited) <= fresh + 1e-9);
		}
	}

	@Test
	public void theCorrectionIsFlooredSoAThinSliceCannotZeroAnOffer()
	{
		// The late slices are always the thinnest and are exactly what a long wait lands in. A handful
		// of unlucky offers in one of them should not be able to declare a good offer worthless.
		CandidateFactory factory = factory(declining());

		double fresh = hold(factory, 0);
		double veryStale = hold(factory, 600);

		assertTrue("even a very old offer keeps a floor of its value: " + veryStale,
			veryStale >= fresh * 0.2);
	}

	@Test
	public void aThinSampleIsNotConsultedAtAll()
	{
		// Nothing is claimed below a hundred offers. A curve fitted to a dozen would move the hold
		// decision on noise, and the hold decision is what cancels offers.
		FillHazard thin = new FillHazard();
		for (int i = 0; i < 30; i++)
		{
			thin.observe(ITEM, true, 0.5, true);
		}
		CandidateFactory factory = factory(thin);

		assertEquals("below the threshold the analytical answer stands untouched",
			hold(factory, 0), hold(factory, 120), 1e-9);
	}
}
