package com.flippingfriend.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every write path is exercised against a real database, because a statement referring to a table
 * nobody created compiles perfectly and fails only at the moment it is used.
 * <p>
 * That is not hypothetical: the gate table was added as an INSERT without its CREATE, and the replay
 * ran to completion, printed a full report, and threw on the last line — so the results looked fine
 * and were never stored. A schema is only real if something has written to it.
 */
public class SqliteStoreTest
{
	private Path database() throws Exception
	{
		return Files.createTempDirectory("flipping-friend-store").resolve("test.db");
	}

	@Test
	public void everyWritePathHasATableBehindIt() throws Exception
	{
		try (SqliteStore store = new SqliteStore(database()))
		{
			store.recordEvent(1, "c", "BOUGHT", "{}");
			store.savePlan(1, 2, "c", "{}");
			store.recordExecution(4151, true, 5.0, 4.0);
			store.recordCapture(4151, 120, 900);
			store.markMigration("legacy", "backup");
			store.recordGates(1, "5m", Arrays.asList(
				new GateReport.Gate("Profitable", true, "1,000 gp"),
				new GateReport.Gate("Beats baseline", false, "0.83x")), "report");
		}
	}

	/**
	 * Capture evidence has to outlive the process, or the measurement restarts every time the
	 * companion does and never accumulates enough to outweigh the risk appetite's guess.
	 */
	@Test
	public void captureEvidenceSurvivesTheProcess() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordCapture(561, 100, 1_000);
			store.recordCapture(561, 50, 1_000);
			store.recordCapture(4151, 10, 40);
		}

		try (SqliteStore reopened = new SqliteStore(path))
		{
			java.util.Map<Integer, CaptureRates.Totals> stats = reopened.captureStats();

			assertEquals("two items measured", 2, stats.size());
			assertEquals("sums accumulate rather than overwrite", 150.0, stats.get(561).filled, 1e-9);
			assertEquals(2_000.0, stats.get(561).flow, 1e-9);
			assertEquals("and the offer count with them", 2, stats.get(561).observations);

			CaptureRates rates = new CaptureRates();
			rates.restore(stats);
			assertTrue("a rate rebuilt from the table must reflect what was stored",
				rates.rateFor(561, 0.5) < 0.5);
		}
	}

	@Test
	public void gateResultsArePersistedForLaterComparison() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordGates(1_000, "5m", Arrays.asList(
				new GateReport.Gate("Profitable", true, "1,000 gp"),
				new GateReport.Gate("Beats baseline", false, "0.83x")), "report");
			store.recordGates(2_000, "1h", Arrays.asList(
				new GateReport.Gate("Profitable", true, "2,000 gp")), "report");
		}

		// Reopened, because the point of storing them is that they outlive the run.
		try (SqliteStore reopened = new SqliteStore(path))
		{
			List<String> rows = reopened.recentOfferEvents(0);
			assertTrue("gate rows must not leak into the offer event stream", rows.isEmpty());
		}
	}

	@Test
	public void executionStatisticsAccumulateAcrossCalls() throws Exception
	{
		try (SqliteStore store = new SqliteStore(database()))
		{
			store.recordExecution(4151, true, 4.0, 3.0);
			store.recordExecution(4151, false, 0.0, 0.0);
			store.recordExecution(4151, true, 6.0, 5.0);

			SqliteStore.ExecutionStat stat = store.executionStats().get(4151);
			assertEquals(3, stat.observed);
			assertEquals(2, stat.completed);
			assertEquals(5.0, stat.meanFillMinutes(), 1e-9);
			// Ten minutes of real fills against eight predicted: the ratio the learner corrects by.
			assertEquals(10.0 / 8.0, stat.durationRatio(), 1e-9);
		}
	}

	/**
	 * The largest table in the schema had no bound at all: 62MB of an 81MB database, growing by about
	 * 34MB a day. It hid from a size audit because the name and the JSON are separated by a NUL and
	 * SQLite's length() stops counting there, so every 690KB row measured fifteen bytes.
	 */
	@Test
	public void supersededModelWeightsAreReclaimedButTheirHistoryIsNot() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			for (int i = 0; i < 4; i++)
			{
				store.saveModel("buy-completion", "{\"trees\":[" + i + "]}");
				store.saveModel("sell-completion", "{\"trees\":[" + i + "]}");
			}

			assertEquals(6, store.pruneModels(1));

			// The weights that still matter are the newest of each name, and they must survive intact.
			assertEquals("{\"trees\":[3]}", store.loadModel("buy-completion"));
			assertEquals("{\"trees\":[3]}", store.loadModel("sell-completion"));
			// The rows themselves stay: version numbers and timestamps are the retrain history the
			// monitor charts, and deleting them would rewrite the chart to say it never happened.
			assertEquals(8, store.modelVersionCount());
			// Idempotent, so the hourly sweep does no work once it has caught up.
			assertEquals(0, store.pruneModels(1));
		}
	}

	@Test
	public void migrationIsRecordedOnlyOnce() throws Exception
	{
		try (SqliteStore store = new SqliteStore(database()))
		{
			store.markMigration("positions", "backup-1");
			store.markMigration("positions", "backup-2");

			assertTrue(store.wasMigrated("positions"));
			assertTrue("an unmigrated source must not be claimed", !store.wasMigrated("journal"));
		}
	}
}
