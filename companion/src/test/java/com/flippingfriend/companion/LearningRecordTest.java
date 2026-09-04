package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/**
 * Whether the companion can say what it knew yesterday.
 *
 * <p>It could not. Every learned table is cumulative — {@code capture_stat} holds totals,
 * {@code fill_hazard} holds a life table, {@code execution_stat} holds sums — so each answers "what
 * is the capture rate" and none answers "what was it on Tuesday, and what changed". A monitor built
 * on those is a gauge. It shows a reading, and <b>a reading cannot show improvement</b>, which is the
 * one thing the monitor is supposed to show.
 *
 * <p>That is the price archive's lesson arriving in a second place: history is not recoverable after
 * the fact. Either it is being written down, or the question can never be asked.
 *
 * <h2>The sample is not optional</h2>
 *
 * <p>Half these tests are about the count stored beside each value, because that is the difference
 * between a record and a decoration. A capture rate moving 0.50 → 0.34 is two completely different
 * events depending on whether the evidence went from 8 to 11 or from 40 to 900, and a chart of values
 * alone renders them identically — then invites somebody to explain a wobble that was noise.
 */
public class LearningRecordTest
{
	private static final long T0 = 1_700_000_000L;

	private static Path database() throws Exception
	{
		return Files.createTempDirectory("flipping-friend-learning").resolve("test.db");
	}

	private static LearningSnapshot snapshot(long at, double capture, long sample)
	{
		return new LearningSnapshot(at)
			.record("capture.pooled", capture, sample)
			.record("capture.assumed", 0.5, sample);
	}

	@Test
	public void aMetricCanBeReadBackAsASeriesRatherThanAReading() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordLearningSnapshot(snapshot(T0, 0.50, 0));
			store.recordLearningSnapshot(snapshot(T0 + 900, 0.44, 120));
			store.recordLearningSnapshot(snapshot(T0 + 1800, 0.34, 900));
		}

		try (SqliteStore reopened = new SqliteStore(path))
		{
			List<LearningSnapshot.Metric> series =
				reopened.learningHistory("capture.pooled", 0, T0 + 10_000);

			assertEquals("three moments, in order", 3, series.size());
			assertEquals(0.50, series.get(0).value, 1e-9);
			assertEquals(0.34, series.get(2).value, 1e-9);
			assertTrue("and it survives the process, which is the whole point",
				series.get(0).value > series.get(2).value);
		}
	}

	@Test
	public void everyValueArrivesWithTheEvidenceBehindIt() throws Exception
	{
		// The rule that makes this a record rather than a decoration. Without the sample, the series
		// above is a line that fell, and a reader has no way to tell learning from noise.
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordLearningSnapshot(snapshot(T0, 0.50, 0));
			store.recordLearningSnapshot(snapshot(T0 + 900, 0.34, 900));

			List<LearningSnapshot.Metric> series =
				store.learningHistory("capture.pooled", 0, T0 + 10_000);

			assertEquals("it began knowing nothing", 0, series.get(0).sample);
			assertEquals("and moved on nine hundred offers, not on luck", 900, series.get(1).sample);
		}
	}

	@Test
	public void aStalledMetricIsDistinguishableFromAnImprovingOne() throws Exception
	{
		// The question the sample exists to answer, asked directly. Two metrics whose values move
		// identically; only one of them learned anything.
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordLearningSnapshot(new LearningSnapshot(T0)
				.record("learned", 0.50, 40)
				.record("wandered", 0.50, 40));
			store.recordLearningSnapshot(new LearningSnapshot(T0 + 900)
				.record("learned", 0.34, 900)
				.record("wandered", 0.34, 41));

			long learnedGrowth = store.learningHistory("learned", 0, T0 + 10_000).get(1).sample
				- store.learningHistory("learned", 0, T0 + 10_000).get(0).sample;
			long wanderedGrowth = store.learningHistory("wandered", 0, T0 + 10_000).get(1).sample
				- store.learningHistory("wandered", 0, T0 + 10_000).get(0).sample;

			assertEquals("the same fall in value", 860, learnedGrowth);
			assertEquals("but one of them was one extra offer", 1, wanderedGrowth);
		}
	}

	@Test
	public void aWindowReturnsOnlyWhatFellInsideIt() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			for (int i = 0; i < 10; i++)
			{
				store.recordLearningSnapshot(snapshot(T0 + i * 900L, 0.4, 100L * i));
			}

			assertEquals(3, store.learningHistory("capture.pooled", T0 + 900, T0 + 2700).size());
			assertEquals(10, store.learningHistory("capture.pooled", 0, T0 + 100_000).size());
		}
	}

	@Test
	public void aPanelCanDiscoverWhatThereIsToPlot() throws Exception
	{
		// A hardcoded list of metric names in a front end is a list that goes stale silently: a
		// component added here shows up nowhere, and one removed leaves an empty chart.
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordLearningSnapshot(new LearningSnapshot(T0)
				.record("capture.pooled", 0.4, 10)
				.record("hazard.duration_dependence", 1.8, 400));

			List<String> metrics = store.learningMetrics();

			assertTrue(metrics.contains("capture.pooled"));
			assertTrue(metrics.contains("hazard.duration_dependence"));
			assertEquals(2, metrics.size());
		}
	}

	@Test
	public void twoSnapshotsAtOneMomentDoNotDouble() throws Exception
	{
		// The scheduler can fire twice across a restart. A repeated moment must overwrite rather than
		// accumulate, or the chart grows a step nobody can account for.
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordLearningSnapshot(snapshot(T0, 0.50, 100));
			store.recordLearningSnapshot(snapshot(T0, 0.51, 101));

			List<LearningSnapshot.Metric> series =
				store.learningHistory("capture.pooled", 0, T0 + 10_000);

			assertEquals(1, series.size());
			assertEquals("the later write wins", 0.51, series.get(0).value, 1e-9);
			assertEquals(1, store.learningSnapshotCount());
		}
	}

	@Test
	public void anEmptySnapshotWritesNothingRatherThanAnEmptyMoment() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordLearningSnapshot(new LearningSnapshot(T0));
			store.recordLearningSnapshot(null);

			assertEquals(0, store.learningSnapshotCount());
		}
	}

	// --- what actually gets gathered ---

	@Test
	public void theSnapshotGathersEveryLearnerIncludingTheOnesEarningNothing() throws Exception
	{
		// A component contributing nothing is a fact, and it belongs on the chart as a flat line at
		// zero rather than as a gap. A gap reads as "not measured"; zero reads as "measured, and it
		// is not helping", and those call for different responses.
		CaptureRates capture = new CaptureRates();
		FillHazard hazard = new FillHazard();
		FillCalibration calibration = new FillCalibration();
		ShadowTrader shadow = new ShadowTrader(new com.flippingfriend.model.TaxCalculator());
		LearnedFillModel learned = new LearnedFillModel();

		LearningSnapshot snapshot =
			LearningSnapshot.of(T0, capture, 0.5, hazard, calibration, shadow, learned);

		assertNotNull("the assumed share it is being compared against", snapshot.get("capture.assumed"));
		assertEquals("with nothing learned, capture is exactly the appetite's number",
			0.5, snapshot.get("capture.pooled").value, 1e-9);
		assertEquals("and no evidence behind it", 0, snapshot.get("capture.pooled").sample);
		assertNotNull(snapshot.get("hazard.duration_dependence"));
		assertNotNull(snapshot.get("calibration.skill"));
		assertNotNull("a gate granting no weight is worth plotting at zero",
			snapshot.get("gate.weight"));
		assertEquals(0.0, snapshot.get("gate.weight").value, 1e-9);
	}

	@Test
	public void theHazardCurveIsStoredSliceBySlice()
	{
		// One series per slice rather than a single summary, so a reader can watch the shape change.
		// A curve collapsed to one number moves for reasons nobody can see.
		FillHazard hazard = new FillHazard();
		for (int i = 0; i < 200; i++)
		{
			hazard.observe(561, true, 3, true);
		}

		LearningSnapshot snapshot = LearningSnapshot.of(T0, null, 0.5, hazard, null, null, null);

		for (int minutes : FillHazard.BUCKET_MINUTES)
		{
			assertNotNull("slice " + minutes + " must have its own series",
				snapshot.get("hazard.rate." + minutes + "m"));
		}
	}

	@Test
	public void aMissingComponentIsSkippedRatherThanRecordedAsZero()
	{
		// Null means "not wired here", which is not the same as "measured at zero". Recording it as
		// zero would put a confident flat line on the chart for something nobody is running.
		LearningSnapshot snapshot = LearningSnapshot.of(T0, null, 0.5, null, null, null, null);

		assertEquals(0, snapshot.size());
	}
}
