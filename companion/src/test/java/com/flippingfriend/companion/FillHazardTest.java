package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;

/**
 * Whether waiting gets worse, and whether anyone can tell.
 *
 * <p>{@link com.flippingfriend.model.FillModel} models arrivals as Poisson, which is memoryless:
 * twenty minutes of silence is supposed to tell you nothing about the next twenty. That is a
 * convenient assumption rather than an observed one, and the only way to check it is our own offers
 * sitting in the queue — the price feed records what traded and has never seen where we were in the
 * line.
 *
 * <p>The tests below are in two halves. First that the life table is built correctly, including the
 * censoring that makes it possible at all. Then that the correction it feeds is <b>inert when the
 * assumption holds</b> — because a correction that fires on a flat hazard would be doing damage on
 * the strength of nothing.
 */
public class FillHazardTest
{
	private static final int ITEM = 561;
	private static final int OTHER = 4151;

	/** An offer that filled after this long. */
	private static void filled(FillHazard hazard, double minutes)
	{
		hazard.observe(ITEM, true, minutes, true);
	}

	/** An offer cancelled after this long, still unfilled. Censored, not a fill time. */
	private static void cancelled(FillHazard hazard, double minutes)
	{
		hazard.observe(ITEM, true, minutes, false);
	}

	@Test
	public void anOfferIsAtRiskInEverySliceItSurvived()
	{
		FillHazard hazard = new FillHazard();
		// Filled at 25 minutes: it was open through the 0, 2, 5, 10 and 20 minute slices.
		filled(hazard, 25);

		assertEquals("at risk from the first slice", 1, hazard.atRisk(0));
		assertEquals(1, hazard.atRisk(FillHazard.bucketOf(20)));
		assertEquals("and not in one it never reached", 0, hazard.atRisk(FillHazard.bucketOf(80)));
	}

	@Test
	public void theEventLandsInTheSliceItHappenedIn()
	{
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 50; i++)
		{
			filled(hazard, 25);
		}

		// 0.95 rather than 1.0: a hazard of exactly one makes survival zero and every later slice
		// meaningless, so it is clamped. The clamp is the answer here, not an approximation of it.
		assertEquals("everything filled in the 20-minute slice", 0.95,
			hazard.pooledHazard(FillHazard.bucketOf(20)), 1e-9);
		assertEquals("and nothing filled before it", 0.0, hazard.pooledHazard(0), 1e-9);
	}

	@Test
	public void aCancelledOfferCountsAsHavingNotFilledYet()
	{
		// The half that could not be used before. ExecutionRecorder is right to refuse a cancelled
		// offer's duration as a fill time -- it is not one -- but "had not filled by ten minutes" is
		// exactly what a hazard is made of, and about a third of settled offers are cancellations.
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 20; i++)
		{
			cancelled(hazard, 25);
		}

		assertEquals("it was at risk the whole time", 20, hazard.atRisk(FillHazard.bucketOf(20)));
		assertEquals("and never filled", 0.0, hazard.pooledHazard(FillHazard.bucketOf(20)), 1e-9);
		assertEquals("so it belongs in the sample", 20, hazard.observationCount());
	}

	@Test
	public void censoringChangesTheAnswerRatherThanBeingIgnored()
	{
		// Ten offers reach the 20-minute slice. Five fill there, five are cancelled there. The hazard
		// is a half. Dropping the cancellations would make it one, and the difference is the entire
		// reason survival analysis exists.
		FillHazard withCensoring = new FillHazard();
		for (int i = 0; i < 5; i++)
		{
			filled(withCensoring, 25);
			cancelled(withCensoring, 25);
		}

		assertEquals(0.5, withCensoring.pooledHazard(FillHazard.bucketOf(20)), 1e-9);
	}

	// --- the memoryless assumption, and departures from it ---

	@Test
	public void aFlatHazardMeansTheAnalyticalModelWasRight()
	{
		// A genuinely memoryless process: a constant chance of filling in every slice. Duration
		// dependence must come out at one, which is the signal to leave this switched off.
		FillHazard hazard = constantRate(0.05, 40_000, FillHazard.BUCKET_MINUTES.length);

		assertEquals("a flat rate is the Poisson case", 1.0, hazard.durationDependence(), 0.15);
		// The property that makes the correction safe to ship before anyone knows whether it helps.
		// If this drifts, the wait penalty is firing on a process that has no memory, and it is
		// taking money off perfectly good offers on the strength of nothing.
		assertEquals("the conditional odds must not move with the wait",
			hazard.completionWithin(ITEM, true, 0, 60),
			hazard.completionWithin(ITEM, true, 40, 60), 0.05);
	}

	@Test
	public void aDecliningHazardIsVisibleAndMeasured()
	{
		// What a queue actually looks like: the easy fills go early, and what is left is the offers
		// that were badly placed. An offer that has already waited is worth less than a fresh one,
		// which is the thing the price feed structurally cannot say.
		FillHazard hazard = decliningHazard();

		assertTrue("waiting must be measured as costing something: " + hazard.durationDependence(),
			hazard.durationDependence() > 2.0);
		assertTrue("and a waited offer must be less likely to fill than a fresh one",
			hazard.completionWithin(ITEM, true, 40, 60)
				< hazard.completionWithin(ITEM, true, 0, 60));
	}

	@Test
	public void survivalFallsAsTimePasses()
	{
		FillHazard hazard = constantRate(0.05, 20_000, FillHazard.BUCKET_MINUTES.length);

		assertTrue(hazard.survival(ITEM, true, 60) < hazard.survival(ITEM, true, 5));
		assertTrue("and never leaves the unit interval", hazard.survival(ITEM, true, 10_000) >= 0);
	}

	@Test
	public void nothingIsClaimedUntilThereIsEnoughToClaimIt()
	{
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 20; i++)
		{
			filled(hazard, 3);
		}

		assertFalse("twenty offers is not a curve", hazard.isUsable());
		assertTrue("and the health line says so", hazard.summary().contains("not yet used"));
	}

	@Test
	public void anItemIsShrunkTowardTheRestRatherThanTowardItself()
	{
		// Leave-one-out, the same discipline LearnedDurations applies, and applied per slice because
		// the late slices are always the thinnest and are what a hold decision leans on hardest.
		FillHazard hazard = new FillHazard();
		// A dominant item that fills three times in five. Not five times in five, because a hazard of
		// one is clamped and a clamped comparison cannot show shrinkage doing anything.
		for (int i = 0; i < 500; i++)
		{
			hazard.observe(ITEM, true, 3, i % 5 < 3);
		}
		// One thin observation on a different item, in the same slice, going the other way.
		hazard.observe(OTHER, true, 3, false);

		int slice = FillHazard.bucketOf(3);
		double dominant = hazard.hazard(ITEM, true, slice);
		double thin = hazard.hazard(OTHER, true, slice);

		// 0.577, not 0.6, and the gap is the shrinkage doing its job. The leave-one-out prior for
		// this item is the one observation that is not its own -- which did not fill -- so even an
		// item carrying 500 of the 501 observations is pulled slightly off its own rate. An item
		// shrunk toward a mean containing itself would have come back at exactly 0.6.
		assertEquals("the dominant item is nearly, but not quite, its own evidence", 0.577,
			dominant, 0.005);
		assertTrue("the thin one is pulled toward the rest rather than sitting at zero: " + thin,
			thin > 0.4);
		assertTrue("but is not simply the dominant item's number: " + thin, thin < dominant);
	}

	@Test
	public void buysAndSellsAreCountedSeparately()
	{
		// Different sides of the book, different processes -- FillModel already knows that, using the
		// low side for a buy and the high side for a sell. Pooling them here would blur two curves.
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 300; i++)
		{
			hazard.observe(ITEM, true, 3, true);
			hazard.observe(ITEM, false, 90, true);
		}

		int quick = FillHazard.bucketOf(3);
		int slow = FillHazard.bucketOf(90);

		assertTrue("buys fill in the three-minute slice", hazard.hazard(ITEM, true, quick) > 0.5);
		assertTrue("sells are still open there", hazard.hazard(ITEM, false, quick) < 0.2);
		assertTrue("and fill an hour and a half later", hazard.hazard(ITEM, false, slow) > 0.5);
	}

	@Test
	public void evidenceSurvivesARestart() throws Exception
	{
		Path database = Files.createTempDirectory("flipping-friend-hazard").resolve("test.db");
		try (SqliteStore store = new SqliteStore(database))
		{
			store.recordFillHazard(ITEM, true, FillHazard.bucketOf(25), true);
			store.recordFillHazard(ITEM, true, FillHazard.bucketOf(25), false);
			store.recordFillHazard(ITEM, false, FillHazard.bucketOf(3), true);
		}

		try (SqliteStore reopened = new SqliteStore(database))
		{
			Map<Long, FillHazard.Counts[]> table = reopened.fillHazards();
			FillHazard hazard = new FillHazard();
			hazard.restore(table, reopened.fillHazardObservations());

			assertEquals("three offers, counted once each", 3, hazard.observationCount());
			assertEquals("one of the two that reached twenty minutes filled there", 0.5,
				hazard.pooledHazard(FillHazard.bucketOf(20)), 1e-9);
			// The counts, not the shrunk hazard: one observation cannot outweigh a prior of twenty
			// however it is stored, so a restart test that asserted on the hazard would be asserting
			// on the shrinkage instead of on the round trip.
			assertEquals("the sell reached the three-minute slice", 3,
				hazard.atRisk(FillHazard.bucketOf(3)));
			assertEquals("and only the buys are still open at twenty minutes", 2,
				hazard.atRisk(FillHazard.bucketOf(20)));
		}
	}

	@Test
	public void anOfferWhoseAgeIsUnknownIsNotCountedAsInstant()
	{
		// firstSeenAt is zero when the plugin never saw the offer appear -- a login replay. Treating
		// that as an age of zero would record a fill in the first slice that did not happen there,
		// and every one of those would steepen the curve.
		FillHazard hazard = new FillHazard();
		hazard.observe(ITEM, true, -1, true);

		assertEquals(0, hazard.observationCount());
	}

	/**
	 * A memoryless process: a constant hazard <em>rate</em> per minute.
	 *
	 * <p>Not a constant chance per slice, which was the first version of this and is a different
	 * thing entirely. The slices are geometric, so holding the per-slice chance fixed is a steeply
	 * falling rate — and a generator like that reports duration dependence on a process that has
	 * none. It was that mismatch, in a test, that found the same error in the class it was testing.
	 *
	 * @param perMinute the constant rate; a slice of width w then fills 1 - exp(-rate * w) of what
	 *                  reaches it
	 */
	private static FillHazard constantRate(double perMinute, int offers, int slices)
	{
		FillHazard hazard = new FillHazard();
		int remaining = offers;
		for (int slice = 0; slice < slices && remaining > 0; slice++)
		{
			double chance = 1.0 - Math.exp(-perMinute * FillHazard.widthOf(slice));
			int fills = (int) Math.round(remaining * chance);
			double minutes = FillHazard.BUCKET_MINUTES[slice] + 0.5;
			for (int i = 0; i < fills; i++)
			{
				hazard.observe(ITEM, true, minutes, true);
			}
			remaining -= fills;
		}
		// Censored past the end of the table rather than inside the last slice: parking the remainder
		// in a slice it never had a chance to fill in gives that slice a rate of zero, which is a
		// cliff and not a flat line.
		for (int i = 0; i < remaining; i++)
		{
			hazard.observe(ITEM, true, FillHazard.BUCKET_MINUTES[FillHazard.BUCKET_MINUTES.length - 1]
				+ FillHazard.widthOf(FillHazard.BUCKET_MINUTES.length - 1) + 1, false);
		}
		return hazard;
	}

	/** A queue: the easy fills go early and the rate falls away. */
	private static FillHazard decliningHazard()
	{
		FillHazard hazard = new FillHazard();
		// Per-minute rates, falling away as the queue empties of the offers that were going to fill.
		double[] rates = {0.30, 0.15, 0.06, 0.02, 0.008, 0.003, 0.001, 0.0005};
		int remaining = 40_000;
		for (int slice = 0; slice < rates.length && remaining > 0; slice++)
		{
			double chance = 1.0 - Math.exp(-rates[slice] * FillHazard.widthOf(slice));
			int fills = (int) Math.round(remaining * chance);
			double minutes = FillHazard.BUCKET_MINUTES[slice] + 0.5;
			for (int i = 0; i < fills; i++)
			{
				hazard.observe(ITEM, true, minutes, true);
			}
			remaining -= fills;
		}
		for (int i = 0; i < remaining; i++)
		{
			hazard.observe(ITEM, true, 200, false);
		}
		return hazard;
	}
}
